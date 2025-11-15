package cc.hachem.renderer;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class FloatingTextRenderer
{
    public static void render(WorldRenderContext context, BlockPos pos,
                              String text, float scale, int color,
                              float offsetX, float offsetY, float offsetZ)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        net.minecraft.client.font.TextRenderer textRenderer = client.textRenderer;
        var bufferProvider = client.getBufferBuilders().getEntityVertexConsumers();
        MatrixStack matrices = context.matrices();
        Vec3d camera = context.worldState().cameraRenderState.pos;

        matrices.push();

        double x = pos.getX() + offsetX - camera.x;
        double y = pos.getY() + offsetY - camera.y;
        double z = pos.getZ() + offsetZ - camera.z;

        matrices.translate(x, y, z);
        matrices.multiply(client.gameRenderer.getCamera().getRotation());
        matrices.scale(scale, -scale, scale);

        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++)
        {
            String line = lines[i];
            float textWidth = textRenderer.getWidth(line);
            float lineOffsetY = -textRenderer.fontHeight * i;
            textRenderer.draw(line, -textWidth / 2f, lineOffsetY, color, true,
                matrices.peek().getPositionMatrix(), bufferProvider,
                net.minecraft.client.font.TextRenderer.TextLayerType.NORMAL, 0, 15728880);
        }

        matrices.pop();
    }

    public static void renderBlockNametag(WorldRenderContext context, BlockPos pos, String text)
    {
        render(context, pos, text, 0.02f, 0xFFFFFFFF, 0.5f, 1.2f, 0.5f);
    }
}
