package cc.hachem.hud;

import cc.hachem.RadarClient;
import cc.hachem.config.ConfigManager;
import cc.hachem.core.ClusterManager;
import cc.hachem.core.SpawnerCluster;
import cc.hachem.core.SpawnerInfo;
import cc.hachem.core.VolumeHighlightManager;
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
    private static final int TOGGLE_VERTICAL_GAP = 3;
    private static final int TOGGLE_GAP = 6;
    private static final int TOGGLE_SPACING = 4;
    private static final int TOGGLE_ACTIVE_COLOR = 0xFF4CAF50;
    private static final int TOGGLE_ACTIVE_SECONDARY_COLOR = 0xFFFFB74D;
    private static final int TOGGLE_INACTIVE_COLOR = Colors.ALTERNATE_WHITE;
    private static final String LEFT_BRANCH_EXPAND = "┏━━";
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
            ChildRow row = new ChildRow(childButton, iconStack, iconSize, spawner);

            ButtonWidget spawnToggle = createSpawnToggleButton(row);
            ButtonWidget mobCapToggle = createMobCapToggleButton(row);
            row.setToggles(spawnToggle, mobCapToggle);

            syncToggleColors(row);
            childRows.add(row);

            int iconSpace = iconStack.isEmpty() ? 0 : (int) Math.ceil(iconSize) + ICON_GAP;
            int toggleWidth = toggleBlockWidth(row);
            int childContentWidth = expandButton.getWidth() + CHILD_INDENT + branchGlyphWidth + BRANCH_GAP
                + iconSpace + childButton.getContentWidth() + toggleWidth;
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
            {
                row.button().onMouseClick(mx, my, mouseButton);
                row.spawnToggle().onMouseClick(mx, my, mouseButton);
                row.mobCapToggle().onMouseClick(mx, my, mouseButton);
            }
    }

    @Override
    public void onMouseMove(int mx, int my)
    {
        expandButton.onMouseMove(mx, my);
        clusterButton.onMouseMove(mx, my);

        if (expanded)
        {
            for (ChildRow row : childRows)
            {
                row.button().onMouseMove(mx, my);
                row.spawnToggle().onMouseMove(mx, my);
                row.mobCapToggle().onMouseMove(mx, my);
                boolean hovered = row.button().isHovered() || row.isWithinTree(mx, my);
                row.setHoverActive(hovered);
            }
        }
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
            syncToggleColors(row);
            boolean rowActive = isRowActive(row);
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

            if (rowActive)
                renderToggleBranch(context, textRenderer, row, child, childWidth, textY, mirror);
            else
                row.clearTreeBounds();

            childY += rowHeight + CHILD_VERTICAL_GAP;
        }
    }

    private String getBranchGlyph(boolean isLast, boolean mirror)
    {
        if (mirror)
            return isLast ? RIGHT_BRANCH_END : RIGHT_BRANCH_CONTINUE;
        return isLast ? LEFT_BRANCH_END : LEFT_BRANCH_CONTINUE;
    }

    private ButtonWidget createSpawnToggleButton(ChildRow row)
    {
        final ButtonWidget[] holder = new ButtonWidget[1];
        holder[0] = new ButtonWidget(0, 0, "Show Spawn Volume", TOGGLE_INACTIVE_COLOR, () ->
        {
            if (!row.hoverActive())
                return;
            boolean enabled = VolumeHighlightManager.toggleSpawnVolume(row.spawner().pos(), RadarClient.config.showSpawnerSpawnVolume);
            holder[0].setColor(enabled ? TOGGLE_ACTIVE_COLOR : TOGGLE_INACTIVE_COLOR);
        });
        return holder[0];
    }

    private ButtonWidget createMobCapToggleButton(ChildRow row)
    {
        final ButtonWidget[] holder = new ButtonWidget[1];
        holder[0] = new ButtonWidget(0, 0, "Show Mob Cap Volume", TOGGLE_INACTIVE_COLOR, () ->
        {
            if (!row.hoverActive())
                return;
            boolean enabled = VolumeHighlightManager.toggleMobCapVolume(row.spawner().pos(), RadarClient.config.showSpawnerMobCapVolume);
            holder[0].setColor(enabled ? TOGGLE_ACTIVE_SECONDARY_COLOR : TOGGLE_INACTIVE_COLOR);
        });
        return holder[0];
    }

    private int toggleBlockWidth(ChildRow row)
    {
        return TOGGLE_GAP + branchGlyphWidth + BRANCH_GAP
            + row.spawnToggle().getContentWidth()
            + TOGGLE_SPACING + row.mobCapToggle().getContentWidth();
    }

    private void renderToggleBranch(DrawContext context, TextRenderer textRenderer, ChildRow row,
                                    ButtonWidget child, int childWidth, int textY, boolean mirror)
    {
        ToggleBounds spawnBounds = renderToggleRow(
            context, textRenderer, row.spawnToggle(), child, childWidth, textY, mirror, true);

        ToggleBounds mobBounds = renderToggleRow(
            context, textRenderer, row.mobCapToggle(),
            child, childWidth, textY + row.button().getHeight() + TOGGLE_VERTICAL_GAP, mirror, false);

        row.setTreeBounds(
            Math.min(spawnBounds.minX(), mobBounds.minX()),
            Math.max(spawnBounds.maxX(), mobBounds.maxX()),
            Math.min(spawnBounds.minY(), mobBounds.minY()),
            Math.max(spawnBounds.maxY(), mobBounds.maxY())
        );
    }

    private ToggleBounds renderToggleRow(DrawContext context, TextRenderer textRenderer, ButtonWidget toggle,
                                 ButtonWidget child, int childWidth, int startY, boolean mirror, boolean useExpand)
    {
        String branch;
        if (mirror)
            branch = useExpand ? RIGHT_BRANCH_CONTINUE : RIGHT_BRANCH_END;
        else
            branch = useExpand ? LEFT_BRANCH_EXPAND : LEFT_BRANCH_END;

        int branchWidth = textRenderer.getWidth(branch);
        int branchX;
        if (!mirror)
            branchX = child.getX() + childWidth + TOGGLE_GAP;
        else
            branchX = child.getX() - TOGGLE_GAP - branchWidth;

        int branchY = startY + (toggle.getHeight() - textRenderer.fontHeight) / 2;
        context.drawText(textRenderer, branch, branchX, branchY, Colors.DARK_GRAY, false);

        int toggleX;
        if (!mirror)
            toggleX = branchX + branchWidth + BRANCH_GAP;
        else
            toggleX = branchX - BRANCH_GAP - toggle.getContentWidth();

        toggle.setX(toggleX);
        toggle.setY(startY);
        toggle.render(context);

        int minX = Math.min(branchX, toggleX);
        int maxX = Math.max(branchX + branchWidth, toggleX + toggle.getContentWidth());
        int minY = Math.min(branchY, startY);
        int maxY = Math.max(branchY + textRenderer.fontHeight, startY + toggle.getHeight());
        return new ToggleBounds(minX, maxX, minY, maxY);
    }

    private void syncToggleColors(ChildRow row)
    {
        BlockPos pos = row.spawner().pos();
        boolean spawnEnabled = VolumeHighlightManager.isSpawnVolumeEnabled(pos, RadarClient.config.showSpawnerSpawnVolume);
        boolean mobCapEnabled = VolumeHighlightManager.isMobCapVolumeEnabled(pos, RadarClient.config.showSpawnerMobCapVolume);
        row.spawnToggle().setColor(spawnEnabled ? TOGGLE_ACTIVE_COLOR : TOGGLE_INACTIVE_COLOR);
        row.mobCapToggle().setColor(mobCapEnabled ? TOGGLE_ACTIVE_SECONDARY_COLOR : TOGGLE_INACTIVE_COLOR);
    }

    private boolean isRowActive(ChildRow row)
    {
        return row.hoverActive();
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

    private static class ChildRow
    {
        private final ButtonWidget button;
        private final ItemStack iconStack;
        private final float iconSize;
        private final SpawnerInfo spawner;
        private ButtonWidget spawnToggle;
        private ButtonWidget mobCapToggle;
        private boolean hoverActive;
        private int treeMinX;
        private int treeMaxX;
        private int treeMinY;
        private int treeMaxY;

        ChildRow(ButtonWidget button, ItemStack iconStack, float iconSize, SpawnerInfo spawner)
        {
            this.button = button;
            this.iconStack = iconStack;
            this.iconSize = iconSize;
            this.spawner = spawner;
        }

        ButtonWidget button()
        {
            return button;
        }

        ItemStack iconStack()
        {
            return iconStack;
        }

        float iconSize()
        {
            return iconSize;
        }

        SpawnerInfo spawner()
        {
            return spawner;
        }

        void setToggles(ButtonWidget spawnToggle, ButtonWidget mobCapToggle)
        {
            this.spawnToggle = spawnToggle;
            this.mobCapToggle = mobCapToggle;
        }

        ButtonWidget spawnToggle()
        {
            return spawnToggle;
        }

        ButtonWidget mobCapToggle()
        {
            return mobCapToggle;
        }

        void setHoverActive(boolean active)
        {
            this.hoverActive = active;
        }

        boolean hoverActive()
        {
            return hoverActive;
        }

        void setTreeBounds(int minX, int maxX, int minY, int maxY)
        {
            this.treeMinX = minX;
            this.treeMaxX = maxX;
            this.treeMinY = minY;
            this.treeMaxY = maxY;
        }

        boolean isWithinTree(int mx, int my)
        {
            return mx >= treeMinX && mx <= treeMaxX && my >= treeMinY && my <= treeMaxY;
        }

        void clearTreeBounds()
        {
            treeMinX = treeMaxX = treeMinY = treeMaxY = 0;
        }
    }

    private record ToggleBounds(int minX, int maxX, int minY, int maxY) {}
}
