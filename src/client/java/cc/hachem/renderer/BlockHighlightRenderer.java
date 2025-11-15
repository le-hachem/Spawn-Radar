package cc.hachem.renderer;

import java.util.*;
import java.util.List;

import cc.hachem.RadarClient;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.MappableRingBuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

public class BlockHighlightRenderer
{
    public record Color(float r, float g, float b)
    {
        public static Color fromHex(int color)
        {
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;
            return new Color(r, g, b);
        }
    }

    private record QuadVertices(float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float x3, float y3, float z3,
                                float x4, float y4, float z4)
    {
        QuadVertices reversed()
        {
            return new QuadVertices(x1, y1, z1,
                                     x4, y4, z4,
                                     x3, y3, z3,
                                     x2, y2, z2);
        }
    }

    private static final RenderPipeline FILLED_THROUGH_WALLS = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of(RadarClient.MOD_ID, "pipeline/highlights"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .build()
    );

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final BufferAllocator ALLOCATOR = new BufferAllocator(16_384);

    private static BufferBuilder buffer;
    private static MappableRingBuffer vertexBuffer;

    public static void draw(WorldRenderContext context, BlockPos position, int color, float a)
    {
        Color tempColor = Color.fromHex(color);
        float r = tempColor.r();
        float g = tempColor.g();
        float b = tempColor.b();

        MatrixStack matrices = context.matrices();
        Vec3d camera = context.worldState().cameraRenderState.pos;

        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        if (buffer == null)
            buffer = new BufferBuilder(ALLOCATOR, FILLED_THROUGH_WALLS.getVertexFormatMode(), FILLED_THROUGH_WALLS.getVertexFormat());

        int x = position.getX();
        int y = position.getY();
        int z = position.getZ();

        emitQuad(matrices, buffer, new GreedyMesher.Quad(GreedyMesher.Face.POS_X, x, y, z, 1, 1), r, g, b, a);
        emitQuad(matrices, buffer, new GreedyMesher.Quad(GreedyMesher.Face.NEG_X, x, y, z, 1, 1), r, g, b, a);
        emitQuad(matrices, buffer, new GreedyMesher.Quad(GreedyMesher.Face.POS_Y, x, y, z, 1, 1), r, g, b, a);
        emitQuad(matrices, buffer, new GreedyMesher.Quad(GreedyMesher.Face.NEG_Y, x, y, z, 1, 1), r, g, b, a);
        emitQuad(matrices, buffer, new GreedyMesher.Quad(GreedyMesher.Face.POS_Z, x, y, z, 1, 1), r, g, b, a);
        emitQuad(matrices, buffer, new GreedyMesher.Quad(GreedyMesher.Face.NEG_Z, x, y, z, 1, 1), r, g, b, a);

        matrices.pop();

        RadarClient.LOGGER.debug("Drawn block highlight at ({}, {}, {}) with color #{}, alpha {}", x, y, z, Integer.toHexString(color), a);
    }

    public static void fillRegionMesh(WorldRenderContext context, List<BlockPos> region, int color, float a)
    {
        if (region.isEmpty())
            return;

        Color tempColor = Color.fromHex(color);
        float r = tempColor.r();
        float g = tempColor.g();
        float b = tempColor.b();

        MatrixStack matrices = context.matrices();
        Vec3d camera = context.worldState().cameraRenderState.pos;

        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        if (buffer == null)
            buffer = new BufferBuilder(ALLOCATOR, FILLED_THROUGH_WALLS.getVertexFormatMode(), FILLED_THROUGH_WALLS.getVertexFormat());

        Set<BlockPos> set = new HashSet<>(region);
        List<GreedyMesher.Quad> quads = GreedyMesher.mesh(set);
        for (GreedyMesher.Quad q : quads)
            emitQuad(matrices, buffer, q, r, g, b, a);

        matrices.pop();
    }

    private static void emitQuad(MatrixStack matrices, BufferBuilder buffer,
                                 GreedyMesher.Quad quad,
                                 float r, float g, float b, float a)
    {
        float width = quad.width();
        float height = quad.height();

        if (width <= 0 || height <= 0)
        {
            if (RadarClient.LOGGER.isDebugEnabled())
                RadarClient.LOGGER.debug("Skipping degenerate quad {} at {},{},{} ({}x{})",
                    quad.face(), quad.x(), quad.y(), quad.z(), width, height);
            return;
        }

        QuadVertices vertices = buildVertices(quad);
        drawQuad(buffer, matrices, vertices, r, g, b, a);
        drawQuad(buffer, matrices, vertices.reversed(), r, g, b, a);
    }

    private static void drawQuad(BufferBuilder buffer, MatrixStack matrices, QuadVertices vertices,
                                 float r, float g, float b, float a)
    {
        quad(buffer, matrices,
             vertices.x1, vertices.y1, vertices.z1,
             vertices.x2, vertices.y2, vertices.z2,
             vertices.x3, vertices.y3, vertices.z3,
             vertices.x4, vertices.y4, vertices.z4,
             r, g, b, a);
    }

    private static QuadVertices buildVertices(GreedyMesher.Quad quad)
    {
        float x = quad.x();
        float y = quad.y();
        float z = quad.z();
        float width = quad.width();
        float height = quad.height();

        float px = x + 1;
        float py = y + 1;
        float pz = z + 1;

        return switch (quad.face())
        {
            case POS_X ->
            {
                float yMax = y + height;
                float zMax = z + width;
                yield new QuadVertices(px, y,    z,
                                       px, yMax, z,
                                       px, yMax, zMax,
                                       px, y,    zMax);
            }
            case NEG_X ->
            {
                float yMax = y + height;
                float zMax = z + width;
                yield new QuadVertices(x, y,    z,
                                       x, y,    zMax,
                                       x, yMax, zMax,
                                       x, yMax, z);
            }
            case POS_Y ->
            {
                float xMax = x + width;
                float zMax = z + height;
                yield new QuadVertices(x,    py, z,
                                       xMax, py, z,
                                       xMax, py, zMax,
                                       x,    py, zMax);
            }
            case NEG_Y ->
            {
                float xMax = x + width;
                float zMax = z + height;
                yield new QuadVertices(x,    y, zMax,
                                       xMax, y, zMax,
                                       xMax, y, z,
                                       x,    y, z);
            }
            case POS_Z ->
            {
                float xMax = x + width;
                float yMax = y + height;
                yield new QuadVertices(x,    y,    pz,
                                       xMax, y,    pz,
                                       xMax, yMax, pz,
                                       x,    yMax, pz);
            }
            case NEG_Z ->
            {
                float xMax = x + width;
                float yMax = y + height;
                yield new QuadVertices(x,    y,    z,
                                       x,    yMax, z,
                                       xMax, yMax, z,
                                       xMax, y,    z);
            }
        };
    }



    private static void quad(BufferBuilder buffer, MatrixStack matrices,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float x4, float y4, float z4,
                             float r, float g, float b, float a)
    {
        var matrix = matrices.peek().getPositionMatrix();

        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a);
        buffer.vertex(matrix, x3, y3, z3).color(r, g, b, a);

        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a);
        buffer.vertex(matrix, x3, y3, z3).color(r, g, b, a);
        buffer.vertex(matrix, x4, y4, z4).color(r, g, b, a);
    }

    public static void submit(MinecraftClient client)
    {
        if (buffer == null)
        {
            RadarClient.LOGGER.debug("submit called but buffer is null; nothing to submit.");
            return;
        }

        BuiltBuffer builtBuffer = buffer.end();
        BuiltBuffer.DrawParameters drawParams = builtBuffer.getDrawParameters();
        VertexFormat format = drawParams.format();

        GpuBuffer vertices = uploadToGPU(drawParams, format, builtBuffer);
        drawPipeline(client, builtBuffer, drawParams, vertices);

        if (vertexBuffer != null)
            vertexBuffer.rotate();
        buffer = null;

        RadarClient.LOGGER.debug("Submitted block highlights to GPU, vertex count: {}", drawParams.vertexCount());
    }

    private static GpuBuffer uploadToGPU(BuiltBuffer.DrawParameters drawParameters, VertexFormat format, BuiltBuffer builtBuffer)
    {
        int vertexBufferSize = drawParameters.vertexCount() * format.getVertexSize();

        if (vertexBuffer == null || vertexBuffer.size() < vertexBufferSize)
        {
            vertexBuffer = new MappableRingBuffer(() ->
                RadarClient.MOD_ID + "_highlight_renderer",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE,
                vertexBufferSize
            );
            RadarClient.LOGGER.debug("Created new MappableRingBuffer with size {}", vertexBufferSize);
        }

        CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();

        try (GpuBuffer.MappedView mappedView = commandEncoder
                 .mapBuffer(vertexBuffer.getBlocking()
                                        .slice(0, builtBuffer.getBuffer().remaining()),
                            false, true))
        {
            MemoryUtil.memCopy(builtBuffer.getBuffer(), mappedView.data());
        }

        RadarClient.LOGGER.debug("Uploaded {} vertices to GPU", drawParameters.vertexCount());
        return vertexBuffer.getBlocking();
    }

    private static void drawPipeline(MinecraftClient client, BuiltBuffer builtBuffer,
                                     BuiltBuffer.DrawParameters drawParameters, GpuBuffer vertices)
    {
        var shapeIndexBuffer = RenderSystem.getSequentialBuffer(BlockHighlightRenderer.FILLED_THROUGH_WALLS.getVertexFormatMode());
        GpuBuffer indices = shapeIndexBuffer.getIndexBuffer(drawParameters.indexCount());
        VertexFormat.IndexType indexType = shapeIndexBuffer.getIndexType();

        var dynamicTransforms = RenderSystem.getDynamicUniforms().write(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, new Vector3f(), RenderSystem.getTextureMatrix(), 1f);

        try (RenderPass renderPass = RenderSystem.getDevice()
                 .createCommandEncoder()
                 .createRenderPass(() -> RadarClient.MOD_ID + "_highlight",
                     client.getFramebuffer().getColorAttachmentView(),
                     OptionalInt.empty(),
                     client.getFramebuffer().getDepthAttachmentView(),
                     OptionalDouble.empty()))
        {
            renderPass.setPipeline(BlockHighlightRenderer.FILLED_THROUGH_WALLS);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setVertexBuffer(0, vertices);
            renderPass.setIndexBuffer(indices, indexType);
            renderPass.drawIndexed(0, 0, drawParameters.indexCount(), 1);
        }

        builtBuffer.close();
        RadarClient.LOGGER.debug("Executed drawPipeline for {} indices", drawParameters.indexCount());
    }

    public static void close()
    {
        ALLOCATOR.close();
        if (vertexBuffer != null)
        {
            vertexBuffer.close();
            vertexBuffer = null;
            RadarClient.LOGGER.debug("Closed vertex buffer and allocator.");
        }
    }
}