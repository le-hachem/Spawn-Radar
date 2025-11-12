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
    public static List<Widget> listItems = new ArrayList<>();
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
        listItems.clear();
        for (SpawnerCluster cluster : ClusterManager.getClusters())
        {
            Widget widget = new ClusterListItemWidget(cluster, 0, 0, 0);
            listItems.add(widget);
        }
    }

    @Override
    public void render(DrawContext context)
    {
        int elementY = y;
        for (Widget listItem : listItems)
        {
            listItem.setX(this.x);
            listItem.setY(elementY);
            listItem.setWidth(this.width);
            listItem.render(context);
            elementY += listItem.getHeight();
        }
    }
}
