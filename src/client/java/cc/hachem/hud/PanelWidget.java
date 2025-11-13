package cc.hachem.hud;

import cc.hachem.core.ClusterManager;
import cc.hachem.core.SpawnerCluster;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public class PanelWidget extends Widget
{
    public static List<Widget> children = new ArrayList<>();
    private final static int elementCount = 5;

    public PanelWidget(int x, int y)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;
        String placeholder = "[(xxx) Cluster#xxx]";

        this.x = x;
        this.y = y;
        this.width = textRenderer.getWidth(placeholder);
        this.height = (textRenderer.fontHeight+10)* elementCount;
    }

    public static void refresh()
    {
        children.clear();
        for (SpawnerCluster cluster : ClusterManager.getClusters())
        {
            Widget widget = new ClusterListItemWidget(cluster, 0, 0, 0);
            children.add(widget);
        }
    }

    public void onMouseClick(int mx, int my, int mouseButton)
    {
        for (Widget child : children)
            child.onMouseClick(mx, my, mouseButton);
    }

    public void onMouseRelease(int mx, int my, int mouseButton)
    {
        for (Widget child : children)
            child.onMouseRelease(mx, my, mouseButton);
    }

    @Override
    public void render(DrawContext context)
    {
        int elementY = y;
        for (Widget child : children)
        {
            child.setX(this.x);
            child.setY(elementY);
            child.setWidth(this.width);
            child.render(context);
            elementY += child.getHeight();
        }
    }
}
