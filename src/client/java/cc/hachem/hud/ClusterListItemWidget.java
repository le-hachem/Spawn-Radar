package cc.hachem.hud;

import cc.hachem.config.ConfigManager;
import cc.hachem.core.ClusterManager;
import cc.hachem.core.SpawnerCluster;
import cc.hachem.core.SpawnerInfo;
import cc.hachem.renderer.ItemTextureRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.math.BlockPos;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;

import java.util.ArrayList;
import java.util.List;

public class ClusterListItemWidget extends Widget
{
    private final SpawnerCluster cluster;
    private boolean expanded = false;
    private final String baseLabel;

    private final ButtonWidget expandButton;
    private final ButtonWidget clusterButton;
    private final List<ChildRow> childRows = new ArrayList<>();
    private int rowWidth;
    private final int branchGlyphWidth;
    private int requiredWidth;
    private static final int CLUSTER_GAP = 5;
    private static final int CHILD_INDENT = 10;
    private static final int BRANCH_GAP = 4;
    private static final int ICON_GAP = 4;
    private static final int CHILD_VERTICAL_GAP = 6;
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
        float baseIconSize = textRenderer.fontHeight + 2;

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
        for (SpawnerInfo spawner : cluster.spawners())
        {
            BlockPos pos = spawner.pos();
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
            ItemStack iconStack = createSpawnEggStack(spawner);
            float iconSize = iconStack.isEmpty() ? 0f : baseIconSize;
            childRows.add(new ChildRow(childButton, iconStack, iconSize));

            int iconSpace = iconStack.isEmpty() ? 0 : (int) Math.ceil(iconSize) + ICON_GAP;
            int childContentWidth = expandButton.getWidth() + CHILD_INDENT + branchGlyphWidth + BRANCH_GAP
                + iconSpace + childButton.getContentWidth();
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
        for (ChildRow row : childRows)
            row.button().onMouseClick(mx, my, mouseButton);
    }

    @Override
    public void onMouseMove(int mx, int my)
    {
        expandButton.onMouseMove(mx, my);
        clusterButton.onMouseMove(mx, my);

        if (expanded)
        for (ChildRow row : childRows)
            row.button().onMouseMove(mx, my);
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
        if (!expanded || childRows.isEmpty())
            return expandButton.getHeight();
        int total = expandButton.getHeight();
        for (int i = 0; i < childRows.size(); i++)
        {
            total += getRowHeight(childRows.get(i));
            if (i < childRows.size() - 1)
                total += CHILD_VERTICAL_GAP;
        }
        return total;
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
        int childY = y + expandButton.getHeight() + CHILD_VERTICAL_GAP;

        for (int i = 0; i < childRows.size(); i++)
        {
            ChildRow row = childRows.get(i);
            ButtonWidget child = row.button();
            int rowHeight = getRowHeight(row);
            String branch = getBranchGlyph(i == childRows.size() - 1, mirror);
            int branchWidth = textRenderer.getWidth(branch);
            int childWidth = Math.max(child.getContentWidth(), 80);
            ItemStack iconStack = row.iconStack();
            int iconSpace = iconStack.isEmpty() ? 0 : (int) Math.ceil(row.iconSize()) + ICON_GAP;
            int branchY = childY + (rowHeight - textRenderer.fontHeight) / 2;
            int textY = childY + (rowHeight - child.getHeight()) / 2;
            child.setY(textY);

            if (!mirror)
            {
                int branchX = expandButton.getX() + expandButton.getWidth() + CHILD_INDENT;
                int childX = branchX + branchWidth + BRANCH_GAP + iconSpace;
                child.setX(childX);
                context.drawText(textRenderer, branch, branchX, branchY, Colors.DARK_GRAY, false);
                if (!iconStack.isEmpty())
                    renderSpawnEgg(iconStack, childX - iconSpace, childY + (rowHeight - Math.round(row.iconSize())) / 2, row.iconSize());
            }
            else
            {
                int branchX = expandButton.getX() - CHILD_INDENT - branchWidth;
                int childX = branchX - BRANCH_GAP - iconSpace - childWidth;
                child.setX(childX);
                context.drawText(textRenderer, branch, branchX, branchY, Colors.DARK_GRAY, false);
                if (!iconStack.isEmpty())
                    renderSpawnEgg(iconStack, childX + childWidth + ICON_GAP, childY + (rowHeight - Math.round(row.iconSize())) / 2, row.iconSize());
            }

            child.setWidth(childWidth);
            child.render(context);
            childY += rowHeight + CHILD_VERTICAL_GAP;
        }
    }

    private String getBranchGlyph(boolean isLast, boolean mirror)
    {
        if (mirror)
            return isLast ? RIGHT_BRANCH_END : RIGHT_BRANCH_CONTINUE;
        return isLast ? LEFT_BRANCH_END : LEFT_BRANCH_CONTINUE;
    }

    private static ItemStack createSpawnEggStack(SpawnerInfo spawner)
    {
        if (spawner == null || !spawner.hasKnownMob())
            return ItemStack.EMPTY;
        SpawnEggItem spawnEgg = SpawnEggItem.forEntity(spawner.entityType());
        return spawnEgg == null ? ItemStack.EMPTY : new ItemStack(spawnEgg);
    }

    private static void renderSpawnEgg(ItemStack iconStack, int x, int y, float size)
    {
        int renderSize = Math.round(size);
        ItemTextureRenderer.render(iconStack, x, y, renderSize, renderSize);
    }

    private static int getRowHeight(ChildRow row)
    {
        return Math.max(row.button().getHeight(), Math.round(row.iconSize()));
    }

    private record ChildRow(ButtonWidget button, ItemStack iconStack, float iconSize) {}
}
