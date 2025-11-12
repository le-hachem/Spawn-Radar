package cc.hachem.hud;

import cc.hachem.core.SpawnerCluster;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Colors;

public class ClusterListItemWidget extends Widget
{
    private final SpawnerCluster cluster;
    private boolean expanded = false;

    public ClusterListItemWidget(SpawnerCluster cluster, int x, int y, int width)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;
        String placeholder = "[(xxx) Cluster#xxx]";

        this.x = x;
        this.y = y;

        this.width = width;
        this.height = textRenderer.fontHeight+5;
        this.cluster = cluster;
    }

    @Override
    public void render(DrawContext context)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;

        int clusterSize = cluster.spawners().size();
        int clusterId = cluster.id();

        String label = String.format("[(%d)] Cluster #%d", clusterSize, clusterId);
        context.drawText(textRenderer, label, x, y, Colors.WHITE, true);
    }
}
