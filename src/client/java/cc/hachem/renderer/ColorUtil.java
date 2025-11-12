package cc.hachem.renderer;

public class ColorUtil
{
    public record Color(float r, float g, float b) { }

    public static Color fromHex(int color)
    {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        return new Color(r, g, b);
    }
}
