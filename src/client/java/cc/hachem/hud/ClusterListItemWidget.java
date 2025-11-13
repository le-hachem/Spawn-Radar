package cc.hachem.hud;

import cc.hachem.core.ClusterManager;
import cc.hachem.core.SpawnerCluster;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Colors;
import org.lwjgl.glfw.GLFW;

public class ClusterListItemWidget extends Widget
{
    private final SpawnerCluster cluster;

    public ClusterListItemWidget(SpawnerCluster cluster, int x, int y, int width)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;

        this.x = x;
        this.y = y;

        this.width = width;
        this.height = textRenderer.fontHeight+5;
        this.cluster = cluster;
    }

    @Override
    public void onMouseClick(int mx, int my, int mouseButton)
    {
        if (mouseButton != GLFW.GLFW_MOUSE_BUTTON_LEFT)
            return;
        if (!isMouseHover(mx, my))
            return;
        ClusterManager.toggleHighlightCluster(cluster.id());
    }

    @Override
    public void render(DrawContext context)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;

        int clusterSize = cluster.spawners().size();
        int clusterId = cluster.id();
        int color = ClusterManager.isHighlighted(clusterId) ? Colors.YELLOW : Colors.WHITE;

        String label = String.format("[(%d) Cluster #%d]", clusterSize, clusterId);
        context.drawText(textRenderer, label, x, y, color, true);
    }
}
