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
    private int rowWidth;
    private final int branchGlyphWidth;
    private int requiredWidth;
    private static final int CLUSTER_GAP = 5;
    private static final int CHILD_INDENT = 10;
    private static final int BRANCH_GAP = 4;
    private static final String LEFT_BRANCH_CONTINUE = "┠━━";
    private static final String LEFT_BRANCH_END = "┗━━";
    private static final String RIGHT_BRANCH_CONTINUE = "━━┫";
    private static final String RIGHT_BRANCH_END = "━━┛";
    private ConfigManager.HudHorizontalAlignment alignment = ConfigManager.HudHorizontalAlignment.LEFT;

    public ClusterListItemWidget(SpawnerCluster cluster, int x, int y, int width)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;

        this.cluster = cluster;
        this.x = x;
        this.y = y;
        this.width = width;
        this.rowWidth = width;
        this.height = textRenderer.fontHeight + 5;

        expandButton = new ButtonWidget(x, y, "+", Colors.WHITE, this::toggleExpanded);
        expandButton.setDecorated(false);

        baseLabel = Text.translatable("button.spawn_radar.cluster_label", cluster.spawners().size(), cluster.id()).getString();
        clusterButton = new ButtonWidget(
            x + expandButton.getWidth() + CLUSTER_GAP,
            y,
            baseLabel,
            Colors.LIGHT_GRAY,
            () -> ClusterManager.toggleHighlightCluster(cluster.id())
        );
        clusterButton.setDecorated(false);

        int leftBranchWidth = Math.max(textRenderer.getWidth(LEFT_BRANCH_END), textRenderer.getWidth(LEFT_BRANCH_CONTINUE));
        int rightBranchWidth = Math.max(textRenderer.getWidth(RIGHT_BRANCH_END), textRenderer.getWidth(RIGHT_BRANCH_CONTINUE));
        branchGlyphWidth = Math.max(leftBranchWidth, rightBranchWidth);

        requiredWidth = expandButton.getWidth() + CLUSTER_GAP + textRenderer.getWidth("[*] " + baseLabel);

        int index = 1;
        for (BlockPos pos : cluster.spawners())
        {
            String label = String.format("%d. (%d, %d, %d)", index++, pos.getX(), pos.getY(), pos.getZ());
            ButtonWidget childButton = new ButtonWidget(
                x + CHILD_INDENT,
                y,
                label,
                Colors.ALTERNATE_WHITE,
                () -> {
                    if (client.player != null)
                        client.player.networkHandler.sendChatCommand(
                            String.format("tp %d %d %d", pos.getX(), pos.getY(), pos.getZ())
                        );
                }
            );
            childButton.setDecorated(false);
            children.add(childButton);

            int childContentWidth = expandButton.getWidth() + CHILD_INDENT + branchGlyphWidth + BRANCH_GAP + childButton.getContentWidth();
            if (childContentWidth > requiredWidth)
                requiredWidth = childContentWidth;
        }
    }

    @Override
    public void setWidth(int width)
    {
        super.setWidth(width);
        this.rowWidth = width;
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
        updateClusterRow(textRenderer);
        expandButton.render(context);
        clusterButton.render(context);

        if (expanded)
            renderChildren(context, textRenderer);
    }

    @Override
    public int getHeight()
    {
        if (!expanded || children.isEmpty())
            return expandButton.getHeight();
        return expandButton.getHeight() + children.size() * (children.getFirst().getHeight() + 2);
    }

    private void toggleExpanded()
    {
        expanded = !expanded;
    }

    public int getRequiredWidth()
    {
        return requiredWidth;
    }

    public void setAlignment(ConfigManager.HudHorizontalAlignment alignment)
    {
        this.alignment = alignment == null ? ConfigManager.HudHorizontalAlignment.LEFT : alignment;
    }

    private void updateClusterRow(TextRenderer textRenderer)
    {
        expandButton.setText(expanded ? "-" : "+");
        expandButton.setY(y);
        boolean highlighted = ClusterManager.isHighlighted(cluster.id());

        String statusMarker = highlighted ? "[*]" : "[ ]";
        String clusterText = statusMarker + " " + baseLabel;
        clusterButton.setText(clusterText);
        clusterButton.setY(y);

        int accentColor = 0xFF000000 | ConfigManager.getClusterColor(cluster.spawners().size());
        clusterButton.setColor(highlighted ? accentColor : Colors.LIGHT_GRAY);
        int clusterTextWidth = textRenderer.getWidth(clusterText);
        layoutClusterRow(clusterTextWidth);
    }

    private void layoutClusterRow(int clusterTextWidth)
    {
        boolean mirror = alignment == ConfigManager.HudHorizontalAlignment.RIGHT;
        int expandWidth = expandButton.getWidth();

        if (!mirror)
        {
            expandButton.setX(x);
            int clusterX = expandButton.getX() + expandWidth + CLUSTER_GAP;
            clusterButton.setX(clusterX);
        }
        else
        {
            int expandX = x + rowWidth - expandWidth;
            expandButton.setX(expandX);
            int clusterX = expandX - CLUSTER_GAP - clusterTextWidth;
            clusterButton.setX(clusterX);
        }

        clusterButton.setWidth(Math.max(clusterTextWidth, 80));
    }

    private void renderChildren(DrawContext context, TextRenderer textRenderer)
    {
        boolean mirror = alignment == ConfigManager.HudHorizontalAlignment.RIGHT;
        int childY = y + expandButton.getHeight() + 2;

        for (int i = 0; i < children.size(); i++)
        {
            ButtonWidget child = children.get(i);
            child.setY(childY);
            String branch = getBranchGlyph(i == children.size() - 1, mirror);
            int branchWidth = textRenderer.getWidth(branch);
            int childWidth = Math.max(child.getContentWidth(), 80);

            if (!mirror)
            {
                int branchX = expandButton.getX() + expandButton.getWidth() + CHILD_INDENT;
                int childX = branchX + branchWidth + BRANCH_GAP;
                child.setX(childX);
                context.drawText(textRenderer, branch, branchX, childY, Colors.DARK_GRAY, false);
            }
            else
            {
                int branchX = expandButton.getX() - CHILD_INDENT - branchWidth;
                int childX = branchX - BRANCH_GAP - childWidth;
                child.setX(childX);
                context.drawText(textRenderer, branch, branchX, childY, Colors.DARK_GRAY, false);
            }

            child.setWidth(childWidth);
            child.render(context);
            childY += child.getHeight() + 2;
        }
    }

    private String getBranchGlyph(boolean isLast, boolean mirror)
    {
        if (mirror)
            return isLast ? RIGHT_BRANCH_END : RIGHT_BRANCH_CONTINUE;
        return isLast ? LEFT_BRANCH_END : LEFT_BRANCH_CONTINUE;
    }
}
