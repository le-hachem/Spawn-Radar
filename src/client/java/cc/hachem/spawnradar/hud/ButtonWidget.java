package cc.hachem.spawnradar.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import org.lwjgl.glfw.GLFW;

public class ButtonWidget extends Widget
{
    private final Runnable callback;
    private String text;
    private int baseColor;
    private boolean hovered = false;
    private boolean decorated = true;
    private int contentWidth;

    public ButtonWidget(int x, int y, String text, int color, Runnable callback)
    {
        this.text = text;
        this.callback = callback;
        this.baseColor = color;

        this.x = x;
        this.y = y;

        recalcDimensions();
    }

    @Override
    public void render(DrawContext context)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;
        int drawColor = hovered ? lightenColor(baseColor) : baseColor;
        context.drawText(textRenderer, getDisplayText(), x, y, drawColor, true);
    }

    @Override
    public void onMouseClick(int mx, int my, int mouseButton)
    {
        if (mouseButton != GLFW.GLFW_MOUSE_BUTTON_LEFT)
            return;
        if (!isMouseHover(mx, my))
            return;
        callback.run();

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null)
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    @Override
    public void onMouseMove(int mx, int my)
    {
        hovered = isMouseHover(mx, my);
    }

    public void setText(String newText)
    {
        this.text = newText;
        recalcDimensions();
    }

    public void setColor(int color)
    {
        this.baseColor = color;
    }

    public void setDecorated(boolean decorated)
    {
        if (this.decorated == decorated)
            return;
        this.decorated = decorated;
        recalcDimensions();
    }

    private void recalcDimensions()
    {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;
        this.contentWidth = textRenderer.getWidth(getDisplayText());
        this.width = contentWidth;
        this.height = textRenderer.fontHeight;
    }

    private String getDisplayText()
    {
        return decorated ? "[ " + text + " ]" : text;
    }

    private int lightenColor(int color)
    {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        float luminance = 0.299f * r + 0.587f * g + 0.114f * b;

        if (luminance > 200)
        {
            r = (int) Math.max(0, r * (1 - (float) 0.3));
            g = (int) Math.max(0, g * (1 - (float) 0.3));
            b = (int) Math.max(0, b * (1 - (float) 0.3));
        } else
        {
            r = (int) Math.min(255, r * (1 + (float) 0.3));
            g = (int) Math.min(255, g * (1 + (float) 0.3));
            b = (int) Math.min(255, b * (1 + (float) 0.3));
        }

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public int getContentWidth()
    {
        return contentWidth;
    }

    public boolean isHovered()
    {
        return hovered;
    }
}
