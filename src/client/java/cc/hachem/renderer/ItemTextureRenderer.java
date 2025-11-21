package cc.hachem.renderer;

import cc.hachem.RadarClient;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ItemTextureRenderer
{
    private static final float DEFAULT_SIZE = 16f;
    private static final Queue<DrawRequest> drawQueue = new ConcurrentLinkedQueue<>();

    private ItemTextureRenderer() {}

    public static void init()
    {
        HudElementRegistry.attachElementAfter(
            VanillaHudElements.CHAT,
            Identifier.of(RadarClient.MOD_ID, "texture"),
            ItemTextureRenderer::flushQueue
        );
    }

    public static void render(Item item, int x, int y)
    {
        render(item, x, y, DEFAULT_SIZE, DEFAULT_SIZE);
    }

    public static void render(Item item, int x, int y, float width, float height)
    {
        if (item == null)
            return;
        render(new ItemStack(item), x, y, width, height);
    }

    public static void render(ItemStack stack, int x, int y, float width, float height)
    {
        if (stack == null || stack.isEmpty())
            return;
        if (width <= 0 || height <= 0)
            return;
        drawQueue.add(new DrawRequest(stack.copy(), x, y, width, height));
    }

    private static void flushQueue(DrawContext context, RenderTickCounter tickCounter)
    {
        if (drawQueue.isEmpty())
            return;

        DrawRequest request;
        while ((request = drawQueue.poll()) != null)
            drawRequest(context, request);
    }

    private static void drawRequest(DrawContext context, DrawRequest request)
    {
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate((float) request.x(), (float) request.y());
        matrices.scale(request.width() / DEFAULT_SIZE, request.height() / DEFAULT_SIZE);
        context.drawItem(request.stack(), 0, 0);
        matrices.popMatrix();
    }

    private record DrawRequest(ItemStack stack, int x, int y, float width, float height) {}
}
