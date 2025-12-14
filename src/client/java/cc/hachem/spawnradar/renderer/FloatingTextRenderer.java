package cc.hachem.spawnradar.renderer;

import com.mojang.blaze3d.vertex.PoseStack;import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;import net.minecraft.client.Minecraft;import net.minecraft.core.BlockPos;import net.minecraft.world.phys.Vec3;

public class FloatingTextRenderer
{
    public static void render(WorldRenderContext context, BlockPos pos,
                              String text, float scale, int color,
                              float offsetX, float offsetY, float offsetZ)
    {
        Minecraft client = Minecraft.getInstance();
        net.minecraft.client.gui.Font textRenderer = client.font;
        var bufferProvider = client.renderBuffers().bufferSource();
        PoseStack matrices = context.matrices();
        Vec3 camera = context.worldState().cameraRenderState.pos;

        matrices.pushPose();

        double x = pos.getX() + offsetX - camera.x;
        double y = pos.getY() + offsetY - camera.y;
        double z = pos.getZ() + offsetZ - camera.z;

        matrices.translate(x, y, z);
        matrices.mulPose(client.gameRenderer.getMainCamera().rotation());
        matrices.scale(scale, -scale, scale);

        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++)
        {
            String line = lines[i];
            float textWidth = textRenderer.width(line);
            float lineOffsetY = -textRenderer.lineHeight * i;
            textRenderer.drawInBatch(line, -textWidth / 2f, lineOffsetY, color, true,
                matrices.last().pose(), bufferProvider,
                net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 15728880);
        }

        matrices.popPose();
    }

    public static void renderBlockNametag(WorldRenderContext context, BlockPos pos, String text)
    {
        render(context, pos, text, 0.02f, 0xFFFFFFFF, 0.5f, 1.7f, 0.5f);
    }
}
