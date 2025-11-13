package cc.hachem.hud;

import cc.hachem.core.ClusterManager;
import cc.hachem.core.SpawnerCluster;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class ClusterListItemWidget extends Widget
{
    private final SpawnerCluster cluster;
    private boolean expanded = false;

    private final ButtonWidget expandButton;
    private final List<ButtonWidget> children = new ArrayList<>();

    public ClusterListItemWidget(SpawnerCluster cluster, int x, int y, int width)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;

        this.cluster = cluster;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = textRenderer.fontHeight + 5;

        expandButton = new ButtonWidget(x, y, ">", Colors.WHITE, this::toggleExpanded);
        for (BlockPos pos : cluster.spawners())
        {
            String label = String.format("- Spawners @ [%d, %d, %d]", pos.getX(), pos.getY(), pos.getZ());
            children.add(new ButtonWidget(x + 10, y, label, Colors.ALTERNATE_WHITE, () ->
            {
                String command = String.format("tp %d %d %d", pos.getX(), pos.getY(), pos.getZ());
                if (client.player != null)
                    client.player.networkHandler.sendChatCommand(command);
            }));
        }
    }

    private void toggleExpanded()
    {
        expanded = !expanded;
    }

    @Override
    public void onMouseClick(int mx, int my, int mouseButton)
    {
        if (mouseButton != GLFW.GLFW_MOUSE_BUTTON_LEFT)
            return;

        expandButton.onMouseClick(mx, my, mouseButton);
        if (isMouseHover(mx, my) && mx > expandButton.getX() + expandButton.getWidth())
            ClusterManager.toggleHighlightCluster(cluster.id());

        if (expanded)
        {
            for (ButtonWidget child : children)
                child.onMouseClick(mx, my, mouseButton);
        }
    }

    @Override
    public void render(DrawContext context)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;

        int clusterId = cluster.id();
        int clusterSize = cluster.spawners().size();
        int color = ClusterManager.isHighlighted(clusterId) ? Colors.WHITE : Colors.LIGHT_GRAY;

        expandButton.setText(expanded ? "v" : ">");
        expandButton.setX(x);
        expandButton.setY(y);
        expandButton.render(context);

        String label = String.format("[(%d) Cluster #%d]", clusterSize, clusterId);
        context.drawText(textRenderer, label, x + expandButton.getWidth() + 5, y, color, true);

        if (expanded)
        {
            int childY = y + textRenderer.fontHeight + 2;
            for (ButtonWidget child : children)
            {
                child.setX(x + expandButton.getWidth() + 10);
                child.setY(childY);
                child.render(context);
                childY += child.getHeight()+5;
            }
        }
    }

    @Override
    public int getHeight()
    {
        if (!expanded)
            return expandButton.getHeight();
        return
            expandButton.getHeight() + children.size() * (children.getFirst().getHeight() + 2);
    }
}
