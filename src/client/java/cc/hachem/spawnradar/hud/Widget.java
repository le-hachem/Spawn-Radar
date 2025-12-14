package cc.hachem.spawnradar.hud;

import net.minecraft.client.gui.GuiGraphics;

public abstract class Widget
{
    protected int x, y;
    protected int width, height;

    public void render(GuiGraphics context) { }

    public void onMouseClick(int mx, int my, int mouseButton)   { }
    public void onMouseRelease(int mx, int my, int mouseButton) { }
    public void onMouseMove(int mx, int my) { }

    public boolean isMouseHover(int mx, int my)
    {
        return (x <= mx && mx <= x+width) &&
               (y <= my && my <= y+height);
    }

    public int getX() { return x; }
    public int getY() { return y; }

    public int getWidth()  { return width; }
    public int getHeight() { return height; }

    public void setX(int x) { this.x = x; }
    public void setY(int y) { this.y = y; }

    public void setWidth(int width)  { this.width = width;   }
    public void setHeight(int height) { this.height = height; }
}
