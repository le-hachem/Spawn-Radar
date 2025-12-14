package cc.hachem.spawnradar.renderer;

import cc.hachem.spawnradar.RadarClient;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;

public final class BoxOutlineRenderer
{
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(
                RadarClient.MOD_ID,
                "pipeline/outline_edges"
            ))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .build()
    );

    private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(4_096);
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);

    private static BufferBuilder buffer;
    private static MappableRingBuffer vertexBuffer;

    private static final Map<MeshKey, List<Quad>> MESH_CACHE = new ConcurrentHashMap<>();

    private static final int[][] EDGE_INDEXES = {
        {0, 1}, {0, 2}, {0, 4},
        {1, 3}, {1, 5},
        {2, 3}, {2, 6},
        {3, 7},
        {4, 5}, {4, 6},
        {5, 7},
        {6, 7}
    };

    private record MeshKey(double width, double height, double depth, double thickness) {}

    private record Quad(
        float x1, float y1, float z1,
        float x2, float y2, float z2,
        float x3, float y3, float z3,
        float x4, float y4, float z4
    ) {}

    private BoxOutlineRenderer() {}

    public static void draw(WorldRenderContext context, BlockPos origin, int color, float alpha, float thickness)
    {
        draw(
            context,
            origin.getX(),
            origin.getY(),
            origin.getZ(),
            1, 1, 1,
            color,
            alpha,
            thickness
        );
    }

    public static void draw(
        WorldRenderContext context,
        double originX,
        double originY,
        double originZ,
        double width,
        double height,
        double depth,
        int color,
        float alpha,
        float thickness
    )
    {
        if (width <= 0 || height <= 0 || depth <= 0)
            return;

        AABB bounds = new AABB(
            originX,
            originY,
            originZ,
            originX + width,
            originY + height,
            originZ + depth
        );

        if (shouldCull(bounds))
            return;

        ensureBuffer();

        PoseStack matrices = context.matrices();
        Vec3 camera = context.worldState().cameraRenderState.pos;

        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        Matrix4f matrix = matrices.last().pose();
        float[] rgba = decodeColor(color, alpha);

        List<Quad> mesh = getOrCreateMesh(width, height, depth, thickness);
        emitMesh(matrix, mesh, rgba, originX, originY, originZ);

        matrices.popPose();
    }

    public static void submit(Minecraft client)
    {
        if (buffer == null)
            return;

        MeshData builtBuffer = buffer.buildOrThrow();
        MeshData.DrawState drawParameters = builtBuffer.drawState();
        VertexFormat format = drawParameters.format();

        GpuBuffer vertices = upload(drawParameters, format, builtBuffer);
        drawPipeline(client, builtBuffer, drawParameters, vertices);

        if (vertexBuffer != null)
            vertexBuffer.rotate();

        buffer = null;
    }

    private static void ensureBuffer()
    {
        if (buffer == null)
            buffer = new BufferBuilder(
                ALLOCATOR,
                PIPELINE.getVertexFormatMode(),
                PIPELINE.getVertexFormat()
            );
    }

    private static List<Quad> getOrCreateMesh(
        double width,
        double height,
        double depth,
        double thickness
    )
    {
        MeshKey key = new MeshKey(width, height, depth, thickness);
        return MESH_CACHE.computeIfAbsent(
            key,
            ignored -> buildMesh(width, height, depth, thickness)
        );
    }

    private static List<Quad> buildMesh(
        double width,
        double height,
        double depth,
        double thickness
    )
    {
        AABB baseBounds = new AABB(0, 0, 0, width, height, depth);
        Vec3[] corners = buildCorners(baseBounds);

        double diameter = Math.max(0.01, thickness / 16.0);
        double edgeInset = diameter * 0.5;

        List<Quad> quads = new ArrayList<>();

        for (Vec3 corner : corners)
            addCornerCubeQuads(quads, corner, edgeInset);

        for (int[] edge : EDGE_INDEXES)
        {
            Vec3 from = corners[edge[0]];
            Vec3 to = corners[edge[1]];
            Axis axis = determineAxis(from, to);
            addEdgeQuads(quads, from, to, edgeInset, axis, edgeInset);
        }

        return List.copyOf(quads);
    }

    private static void emitMesh(
        Matrix4f matrix,
        List<Quad> mesh,
        float[] rgba,
        double originX,
        double originY,
        double originZ
    )
    {
        for (Quad quad : mesh)
            emitQuad(matrix, quad, rgba, originX, originY, originZ);
    }

    private static void emitQuad(
        Matrix4f matrix,
        Quad quad,
        float[] rgba,
        double originX,
        double originY,
        double originZ
    )
    {
        emitDoubleSidedQuad(
            matrix,
            quad.x1 + originX, quad.y1 + originY, quad.z1 + originZ,
            quad.x2 + originX, quad.y2 + originY, quad.z2 + originZ,
            quad.x3 + originX, quad.y3 + originY, quad.z3 + originZ,
            quad.x4 + originX, quad.y4 + originY, quad.z4 + originZ,
            rgba
        );
    }

    private static void addEdgeQuads(
        List<Quad> quads,
        Vec3 from,
        Vec3 to,
        double halfThickness,
        Axis axis,
        double edgeInset
    )
    {
        double minX = Math.min(from.x, to.x);
        double maxX = Math.max(from.x, to.x);
        double minY = Math.min(from.y, to.y);
        double maxY = Math.max(from.y, to.y);
        double minZ = Math.min(from.z, to.z);
        double maxZ = Math.max(from.z, to.z);

        if (axis == Axis.X)
        {
            minX += edgeInset;
            maxX -= edgeInset;
            if (minX >= maxX) return;
        }
        else if (axis == Axis.Y)
        {
            minY += edgeInset;
            maxY -= edgeInset;
            if (minY >= maxY) return;
        }
        else
        {
            minZ += edgeInset;
            maxZ -= edgeInset;
            if (minZ >= maxZ) return;
        }

        if (axis != Axis.X) { minX -= halfThickness; maxX += halfThickness; }
        if (axis != Axis.Y) { minY -= halfThickness; maxY += halfThickness; }
        if (axis != Axis.Z) { minZ -= halfThickness; maxZ += halfThickness; }

        addPrismQuads(quads, minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static void addCornerCubeQuads(List<Quad> quads, Vec3 corner, double halfSize)
    {
        addPrismQuads(
            quads,
            corner.x - halfSize,
            corner.y - halfSize,
            corner.z - halfSize,
            corner.x + halfSize,
            corner.y + halfSize,
            corner.z + halfSize
        );
    }

    private static void addPrismQuads(
        List<Quad> quads,
        double x0, double y0, double z0,
        double x1, double y1, double z1
    )
    {
        addQuadVertices(quads, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
        addQuadVertices(quads, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        addQuadVertices(quads, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        addQuadVertices(quads, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
        addQuadVertices(quads, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
        addQuadVertices(quads, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0);
    }

    private static void addQuadVertices(
        List<Quad> quads,
        double x1, double y1, double z1,
        double x2, double y2, double z2,
        double x3, double y3, double z3,
        double x4, double y4, double z4
    )
    {
        quads.add(new Quad(
            (float) x1, (float) y1, (float) z1,
            (float) x2, (float) y2, (float) z2,
            (float) x3, (float) y3, (float) z3,
            (float) x4, (float) y4, (float) z4
        ));
    }

    private static void emitDoubleSidedQuad(
        Matrix4f matrix,
        double x1, double y1, double z1,
        double x2, double y2, double z2,
        double x3, double y3, double z3,
        double x4, double y4, double z4,
        float[] rgba
    )
    {
        addQuad(matrix, x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, rgba);
        addQuad(matrix, x1, y1, z1, x4, y4, z4, x3, y3, z3, x2, y2, z2, rgba);
    }

    private static void addQuad(
        Matrix4f matrix,
        double x1, double y1, double z1,
        double x2, double y2, double z2,
        double x3, double y3, double z3,
        double x4, double y4, double z4,
        float[] rgba
    )
    {
        buffer.addVertex(matrix, (float) x1, (float) y1, (float) z1).setColor(rgba[0], rgba[1], rgba[2], rgba[3]);
        buffer.addVertex(matrix, (float) x2, (float) y2, (float) z2).setColor(rgba[0], rgba[1], rgba[2], rgba[3]);
        buffer.addVertex(matrix, (float) x3, (float) y3, (float) z3).setColor(rgba[0], rgba[1], rgba[2], rgba[3]);
        buffer.addVertex(matrix, (float) x1, (float) y1, (float) z1).setColor(rgba[0], rgba[1], rgba[2], rgba[3]);
        buffer.addVertex(matrix, (float) x3, (float) y3, (float) z3).setColor(rgba[0], rgba[1], rgba[2], rgba[3]);
        buffer.addVertex(matrix, (float) x4, (float) y4, (float) z4).setColor(rgba[0], rgba[1], rgba[2], rgba[3]);
    }

    private static GpuBuffer upload(
        MeshData.DrawState drawParameters,
        VertexFormat format,
        MeshData builtBuffer
    )
    {
        int vertexBufferSize = drawParameters.vertexCount() * format.getVertexSize();

        if (vertexBuffer == null || vertexBuffer.size() < vertexBufferSize)
        {
            vertexBuffer = new MappableRingBuffer(
                () -> RadarClient.MOD_ID + "_outline_renderer",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE,
                vertexBufferSize
            );
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

        try (GpuBuffer.MappedView mappedView = encoder.mapBuffer(
            vertexBuffer.currentBuffer().slice(0, builtBuffer.vertexBuffer().remaining()),
            false,
            true
        ))
        {
            MemoryUtil.memCopy(builtBuffer.vertexBuffer(), mappedView.data());
        }

        return vertexBuffer.currentBuffer();
    }

    private static void drawPipeline(
        Minecraft client,
        MeshData builtBuffer,
        MeshData.DrawState drawParameters,
        GpuBuffer vertices
    )
    {
        var shapeIndexBuffer = RenderSystem.getSequentialBuffer(PIPELINE.getVertexFormatMode());
        GpuBuffer indices = shapeIndexBuffer.getBuffer(drawParameters.indexCount());
        VertexFormat.IndexType indexType = shapeIndexBuffer.type();

        var dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
            RenderSystem.getModelViewMatrix(),
            COLOR_MODULATOR,
            new Vector3f(),
            new Matrix4f()
        );

        try (RenderPass renderPass = RenderSystem.getDevice()
                                         .createCommandEncoder()
                                         .createRenderPass(
                                             () -> RadarClient.MOD_ID + "_outline_edges",
                                             client.getMainRenderTarget().getColorTextureView(),
                                             OptionalInt.empty(),
                                             client.getMainRenderTarget().getDepthTextureView(),
                                             OptionalDouble.empty()
                                         ))
        {
            renderPass.setPipeline(PIPELINE);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setVertexBuffer(0, vertices);
            renderPass.setIndexBuffer(indices, indexType);
            renderPass.drawIndexed(0, 0, drawParameters.indexCount(), 1);
        }

        builtBuffer.close();
    }

    public static void clearMeshCache()
    {
        MESH_CACHE.clear();
    }

    public static void close()
    {
        ALLOCATOR.close();

        if (vertexBuffer != null)
        {
            vertexBuffer.close();
            vertexBuffer = null;
        }

        clearMeshCache();
    }

    private static boolean shouldCull(AABB box)
    {
        if (RadarClient.config == null || !RadarClient.config.frustumCullingEnabled)
            return false;

        return !isWithinView(box);
    }

    private static boolean isWithinView(AABB box)
    {
        Minecraft client = Minecraft.getInstance();

        var camera = client.gameRenderer.getMainCamera();
        Vec3 camPos = camera.position();
        Vec3 center = new Vec3(
            (box.minX + box.maxX) * 0.5,
            (box.minY + box.maxY) * 0.5,
            (box.minZ + box.maxZ) * 0.5
        );

        Vec3 toCenter = center.subtract(camPos);
        double distance = toCenter.length();

        double width = box.maxX - box.minX;
        double height = box.maxY - box.minY;
        double depth = box.maxZ - box.minZ;

        double radius = Math.sqrt(width * width + height * height + depth * depth) * 0.5;

        if (distance <= 1e-3)
            return true;

        double yawRad = Math.toRadians(camera.yRot());
        double pitchRad = Math.toRadians(camera.xRot());

        Vec3 forward = new Vec3(
            -Math.sin(yawRad) * Math.cos(pitchRad),
            -Math.sin(pitchRad),
            Math.cos(yawRad) * Math.cos(pitchRad)
        ).normalize();

        double forwardComponent = toCenter.dot(forward);
        return forwardComponent + radius > 0;
    }

    private static Vec3[] buildCorners(AABB box)
    {
        return new Vec3[] {
            new Vec3(box.minX, box.minY, box.minZ),
            new Vec3(box.maxX, box.minY, box.minZ),
            new Vec3(box.minX, box.maxY, box.minZ),
            new Vec3(box.maxX, box.maxY, box.minZ),
            new Vec3(box.minX, box.minY, box.maxZ),
            new Vec3(box.maxX, box.minY, box.maxZ),
            new Vec3(box.minX, box.maxY, box.maxZ),
            new Vec3(box.maxX, box.maxY, box.maxZ)
        };
    }

    private static float[] decodeColor(int color, float alpha)
    {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        return new float[] { r, g, b, alpha };
    }

    private static Axis determineAxis(Vec3 from, Vec3 to)
    {
        if (Math.abs(to.x - from.x) > 1e-4)
            return Axis.X;
        if (Math.abs(to.y - from.y) > 1e-4)
            return Axis.Y;
        return Axis.Z;
    }

    private enum Axis { X, Y, Z }
}
