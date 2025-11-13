package cc.hachem.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

public class ButtonWidget extends Widget
{
    private final Runnable callback;
    private String text;
    private int color;

    public ButtonWidget(int x, int y, String text, int color, Runnable callback)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;

        this.text = text;
        this.callback = callback;
        this.color = color;

        this.x = x;
        this.y = y;
        this.width = textRenderer.getWidth(text);
        this.height = textRenderer.fontHeight;
    }

    @Override
    public void render(DrawContext context)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;
        context.drawText(textRenderer, text, x, y, color, true);
    }

    @Override
    public void onMouseClick(int mx, int my, int mouseButton)
    {
        if (mouseButton != GLFW.GLFW_MOUSE_BUTTON_LEFT)
            return;
        if (!isMouseHover(mx, my))
            return;
        callback.run();
    }

    public void setText(String newText)
    {
        this.text = newText;
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;
        this.width = textRenderer.getWidth(newText);
    }

    public void setColor(int color)
    {
        this.color = color;
    }
}
