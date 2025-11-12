package cc.hachem.hud;

import cc.hachem.RadarClient;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class HudRenderer
{
    private final static List<Widget> children = new ArrayList<>();

    public static void init()
    {
        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT,
            Identifier.of(RadarClient.MOD_ID, "hud"), HudRenderer::render);
    }

    public static void build()
    {
        PanelWidget panelWidget = new PanelWidget(10, 50);
        children.add(panelWidget);
    }

    public static void render(DrawContext context, RenderTickCounter tickCounter)
    {
        for (Widget child : children)
            child.render(context);
    }
}
