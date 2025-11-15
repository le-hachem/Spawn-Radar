package cc.hachem.hud;

import cc.hachem.config.ConfigManager;
import cc.hachem.core.ClusterManager;
import cc.hachem.core.SpawnerCluster;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class ClusterListItemWidget extends Widget
{
    private final SpawnerCluster cluster;
    private boolean expanded = false;
    private final String baseLabel;

    private final ButtonWidget expandButton;
    private final ButtonWidget clusterButton;
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

        expandButton = new ButtonWidget(x, y, "+", Colors.WHITE, this::toggleExpanded);
        expandButton.setDecorated(false);

        baseLabel = Text.translatable("button.spawn_radar.cluster_label", cluster.spawners().size(), cluster.id()).getString();
        clusterButton = new ButtonWidget(
            x + expandButton.getWidth() + 5,
            y,
            baseLabel,
            Colors.LIGHT_GRAY,
            () -> ClusterManager.toggleHighlightCluster(cluster.id())
        );
        clusterButton.setDecorated(false);

        for (BlockPos pos : cluster.spawners())
        {
            String label = Text.translatable(
                "button.spawn_radar.spawner_label",
                pos.getX(), pos.getY(), pos.getZ()
            ).getString();

            children.add(new ButtonWidget(
                x + 10,
                y,
                label,
                Colors.ALTERNATE_WHITE,
                () -> {
                    if (client.player != null)
                        client.player.networkHandler.sendChatCommand(
                            String.format("tp %d %d %d", pos.getX(), pos.getY(), pos.getZ())
                        );
                }
            ));
            children.getLast().setDecorated(false);
        }
    }

    @Override
    public void onMouseClick(int mx, int my, int mouseButton)
    {
        expandButton.onMouseClick(mx, my, mouseButton);
        clusterButton.onMouseClick(mx, my, mouseButton);

        if (expanded)
            for (ButtonWidget child : children)
                child.onMouseClick(mx, my, mouseButton);
    }

    @Override
    public void onMouseMove(int mx, int my)
    {
        expandButton.onMouseMove(mx, my);
        clusterButton.onMouseMove(mx, my);

        if (expanded)
            for (ButtonWidget child : children)
                child.onMouseMove(mx, my);
    }

    @Override
    public void render(DrawContext context)
    {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

        expandButton.setText(expanded ? "-" : "+");
        expandButton.setX(x);
        expandButton.setY(y);
        expandButton.render(context);

        clusterButton.setX(x + expandButton.getWidth() + 5);
        clusterButton.setY(y);
        boolean highlighted = ClusterManager.isHighlighted(cluster.id());

        String statusMarker = highlighted ? "[*]" : "[ ]";
        clusterButton.setText(statusMarker + " " + baseLabel);

        int accentColor = 0xFF000000 | ConfigManager.getClusterColor(cluster.spawners().size());
        clusterButton.setColor(highlighted ? accentColor : Colors.LIGHT_GRAY);
        clusterButton.render(context);

        if (expanded)
        {
            int childY = y + expandButton.getHeight() + 2;
            for (int i = 0; i < children.size(); i++)
            {
                ButtonWidget child = children.get(i);
                child.setX(x + expandButton.getWidth() + 10);
                child.setY(childY);
                String branch = (i == children.size() - 1) ? "┗━━" : "┠━━";
                int branchX = child.getX() - textRenderer.getWidth(branch) - 4;
                context.drawText(textRenderer, branch, branchX, childY, Colors.DARK_GRAY, false);
                child.render(context);
                childY += child.getHeight() + 2;
            }
        }
    }

    @Override
    public int getHeight()
    {
        if (!expanded) return expandButton.getHeight();
        return expandButton.getHeight() + children.size() * (children.getFirst().getHeight() + 2);
    }

    private void toggleExpanded()
    {
        expanded = !expanded;
    }
}
