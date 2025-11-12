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
import net.minecraft.client.render.VertexRendering;
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

    private static final RenderPipeline FILLED_THROUGH_WALLS = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of(RadarClient.MOD_ID, "pipeline/highlights"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLE_STRIP)
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

        float x = position.getX();
        float y = position.getY();
        float z = position.getZ();

        VertexRendering.drawFilledBox(matrices, buffer,
            x,   y,   z,
            x+1, y+1, z+1,
            r, g, b,
            a);

        matrices.pop();

        RadarClient.LOGGER.debug("Drawn block highlight at ({}, {}, {}) with color #{}, alpha {}", x, y, z, Integer.toHexString(color), a);
    }

    public static void fillRegionMesh(WorldRenderContext context, List<BlockPos> region, int color, float a)
    {
        if (region.isEmpty())
        {
            RadarClient.LOGGER.debug("fillRegionMesh called with empty region.");
            return;
        }

        Color tempColor = Color.fromHex(color);
        float r = tempColor.r();
        float g = tempColor.g();
        float b = tempColor.b();

        Set<BlockPos> blocks = new HashSet<>(region);

        MatrixStack matrices = context.matrices();
        Vec3d camera = context.worldState().cameraRenderState.pos;
        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        if (buffer == null)
            buffer = new BufferBuilder(ALLOCATOR, FILLED_THROUGH_WALLS.getVertexFormatMode(), FILLED_THROUGH_WALLS.getVertexFormat());

        for (BlockPos pos : region)
        {
            float x = pos.getX();
            float y = pos.getY();
            float z = pos.getZ();

            if (!blocks.contains(pos.add(1, 0, 0)))  VertexRendering.drawFilledBox(matrices, buffer, x + 1, y,     z,     x + 1, y + 1, z + 1, r, g, b, a);
            if (!blocks.contains(pos.add(-1, 0, 0))) VertexRendering.drawFilledBox(matrices, buffer, x,     y,     z,     x,     y + 1, z + 1, r, g, b, a);
            if (!blocks.contains(pos.add(0, 1, 0)))  VertexRendering.drawFilledBox(matrices, buffer, x,     y + 1, z,     x + 1, y + 1, z + 1, r, g, b, a);
            if (!blocks.contains(pos.add(0, -1, 0))) VertexRendering.drawFilledBox(matrices, buffer, x,     y,     z,     x + 1, y,     z + 1, r, g, b, a);
            if (!blocks.contains(pos.add(0, 0, 1)))  VertexRendering.drawFilledBox(matrices, buffer, x,     y,     z + 1, x + 1, y + 1, z + 1, r, g, b, a);
            if (!blocks.contains(pos.add(0, 0, -1))) VertexRendering.drawFilledBox(matrices, buffer, x,     y,     z,     x + 1, y + 1, z,     r, g, b, a);
        }

        matrices.pop();

        RadarClient.LOGGER.debug("Filled region mesh of {} blocks with color #{}, alpha {}", region.size(), Integer.toHexString(color), a);
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

        try (GpuBuffer.MappedView mappedView = commandEncoder.mapBuffer(vertexBuffer.getBlocking().slice(0, builtBuffer.getBuffer().remaining()), false, true))
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
