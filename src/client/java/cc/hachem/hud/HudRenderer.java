package cc.hachem.hud;

import cc.hachem.RadarClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class HudRenderer
{
    private final static List<Widget> children = new ArrayList<>();
    private static boolean leftDown = false;
    private static boolean rightDown = false;

    public static void init()
    {
        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT,
            Identifier.of(RadarClient.MOD_ID, "hud"), HudRenderer::render);

        ClientTickEvents.END_CLIENT_TICK.register(client ->
        {
            double mx = client.mouse.getX() / client.getWindow().getScaleFactor();
            double my = client.mouse.getY() / client.getWindow().getScaleFactor();
            onMouseMove((int)mx, (int)my);

            long handle = client.getWindow().getHandle();

            boolean nowLeft = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            boolean nowRight = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;

            if (nowLeft && !leftDown)
                HudRenderer.onMouseClick((int) mx, (int) my, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            if (!nowLeft && leftDown)
                HudRenderer.onMouseRelease((int) mx, (int) my, GLFW.GLFW_MOUSE_BUTTON_LEFT);

            if (nowRight && !rightDown)
                HudRenderer.onMouseClick((int) mx, (int) my, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            if (!nowRight && rightDown)
                HudRenderer.onMouseRelease((int) mx, (int) my, GLFW.GLFW_MOUSE_BUTTON_RIGHT);

            leftDown = nowLeft;
            rightDown = nowRight;
        });
    }


    public static void updatePanelPosition()
    {
        if (!children.isEmpty() && PanelWidget.getInstance() != null)
        {
            var client = MinecraftClient.getInstance();
            int yOffset = (int) (RadarClient.config.verticalPanelOffset * client.getWindow().getScaledHeight());
            PanelWidget.getInstance().setY(50+yOffset);
            PanelWidget.updateButtonPositions();
            PanelWidget.refresh();
        }
    }


    public static void build()
    {
        var client = MinecraftClient.getInstance();
        int yOffset = (int) (RadarClient.config.verticalPanelOffset * client.getWindow().getScaledHeight());
        children.add(new PanelWidget(10, 50+yOffset));
    }

    private static void onMouseMove(int mx, int my)
    {
        children.forEach(child -> child.onMouseMove(mx, my));
    }

    private static void onMouseClick(int mx, int my, int mouseButton)
    {
        children.forEach(child -> child.onMouseClick(mx, my, mouseButton));
    }

    private static void onMouseRelease(int mx, int my, int mouseButton)
    {
        children.forEach(child -> child.onMouseRelease(mx, my, mouseButton));
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter)
    {
        children.forEach(child -> child.render(context));
    }
}
