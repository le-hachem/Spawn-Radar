package cc.hachem.hud;

import cc.hachem.RadarClient;
import cc.hachem.config.ConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.Window;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class HudRenderer
{
    private final static List<Widget> children = new ArrayList<>();
    private static boolean leftDown = false;
    private static boolean rightDown = false;
    private static int lastScreenWidth = -1;
    private static int lastScreenHeight = -1;

    private HudRenderer() { }

    public static void init()
    {
        registerHudElement();
        registerClientTickHandler();
    }

    private static void registerHudElement()
    {
        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT,
            Identifier.of(RadarClient.MOD_ID, "hud"), HudRenderer::render);
    }

    private static void registerClientTickHandler()
    {
        ClientTickEvents.END_CLIENT_TICK.register(HudRenderer::handleClientTick);
    }

    private static void handleClientTick(MinecraftClient client)
    {
        if (client == null)
            return;

        handleWindowResize(client);
        if (!shouldProcessInput(client))
            return;

        double scaleFactor = client.getWindow().getScaleFactor();
        int mx = (int) (client.mouse.getX() / scaleFactor);
        int my = (int) (client.mouse.getY() / scaleFactor);

        onMouseMove(mx, my);
        processMouseButtons(client, mx, my);
    }

    private static boolean shouldProcessInput(MinecraftClient client)
    {
        return client.currentScreen instanceof ChatScreen;
    }

    private static void processMouseButtons(MinecraftClient client, int mx, int my)
    {
        long handle = client.getWindow().getHandle();

        boolean nowLeft = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean nowRight = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;

        if (nowLeft && !leftDown)
            HudRenderer.onMouseClick(mx, my, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        if (!nowLeft && leftDown)
            HudRenderer.onMouseRelease(mx, my, GLFW.GLFW_MOUSE_BUTTON_LEFT);

        if (nowRight && !rightDown)
            HudRenderer.onMouseClick(mx, my, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        if (!nowRight && rightDown)
            HudRenderer.onMouseRelease(mx, my, GLFW.GLFW_MOUSE_BUTTON_RIGHT);

        leftDown = nowLeft;
        rightDown = nowRight;
    }

    public static void updatePanelPosition()
    {
        var panel = PanelWidget.getInstance();
        if (panel == null || RadarClient.config == null)
            return;

        var client = MinecraftClient.getInstance();
        int screenHeight = client.getWindow().getScaledHeight();
        int screenWidth = client.getWindow().getScaledWidth();

        int yOffset = (int) (RadarClient.config.verticalPanelOffset * screenHeight);
        panel.setY(50 + yOffset);
        panel.setX(computeAlignedX(panel, screenWidth));
        PanelWidget.onPanelMoved();
    }

    public static void build()
    {
        var client = MinecraftClient.getInstance();
        int yOffset = (int) (RadarClient.config.verticalPanelOffset * client.getWindow().getScaledHeight());

        children.removeIf(widget -> widget instanceof PanelWidget);
        PanelWidget.dispose();

        children.add(new PanelWidget(10, 50 + yOffset));
        Window window = client.getWindow();
        lastScreenWidth = window.getScaledWidth();
        lastScreenHeight = window.getScaledHeight();
        PanelWidget.refresh();
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

    private static void handleWindowResize(MinecraftClient client)
    {
        var panel = PanelWidget.getInstance();
        if (panel == null)
        {
            lastScreenWidth = -1;
            lastScreenHeight = -1;
            return;
        }

        Window window = client.getWindow();
        int width = window.getScaledWidth();
        int height = window.getScaledHeight();

        if (width != lastScreenWidth || height != lastScreenHeight)
        {
            lastScreenWidth = width;
            lastScreenHeight = height;
            updatePanelPosition();
        }
    }

    private static int computeAlignedX(PanelWidget panel, int screenWidth)
    {
        int margin = 10;
        int panelWidth = Math.max(panel.getWidth(), 140);
        int leftMost = margin;
        int rightMost = Math.max(margin, screenWidth - panelWidth - margin);

        ConfigManager.HudHorizontalAlignment alignment = RadarClient.config.panelHorizontalAlignment;
        if (alignment == null)
            alignment = ConfigManager.DEFAULT.panelHorizontalAlignment;

        return switch (alignment)
        {
            case RIGHT -> rightMost;
            case LEFT -> leftMost;
        };
    }
}
