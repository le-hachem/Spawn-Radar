package cc.hachem.spawnradar.hud;

import cc.hachem.spawnradar.RadarClient;
import cc.hachem.spawnradar.config.ConfigManager;
import cc.hachem.spawnradar.core.ClusterManager;
import cc.hachem.spawnradar.core.SpawnerCluster;
import cc.hachem.spawnradar.core.SpawnerInfo;
import cc.hachem.spawnradar.core.SpawnerEfficiencyManager;
import cc.hachem.spawnradar.core.SpawnerMobCapStatusManager;
import cc.hachem.spawnradar.core.SpawnerLightLevelManager;
import cc.hachem.spawnradar.core.VolumeHighlightManager;
import cc.hachem.spawnradar.renderer.ItemTextureRenderer;
import cc.hachem.spawnradar.renderer.MobPuppetRenderer;import java.util.ArrayList;
import java.util.List;import net.minecraft.client.Minecraft;import net.minecraft.client.gui.Font;import net.minecraft.client.gui.GuiGraphics;import net.minecraft.client.gui.screens.ChatScreen;import net.minecraft.core.BlockPos;import net.minecraft.network.chat.Component;import net.minecraft.util.CommonColors;import net.minecraft.world.item.ItemStack;import net.minecraft.world.item.SpawnEggItem;

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
    private static final int TREE_HOVER_PADDING = 6;
    private static final int TOGGLE_INACTIVE_COLOR = CommonColors.LIGHTER_GRAY;
    private static final int EXPANDED_CLUSTER_GAP = 8;
    private static final String LEFT_BRANCH_EXPAND = "┏━━";
    private static final String LEFT_BRANCH_CONTINUE = "┠━━";
    private static final String LEFT_BRANCH_END = "┗━━";
    private static final String RIGHT_BRANCH_CONTINUE = "━━┫";
    private static final String RIGHT_BRANCH_END = "━━┛";
    private ConfigManager.HudHorizontalAlignment alignment = ConfigManager.HudHorizontalAlignment.LEFT;

    public ClusterListItemWidget(SpawnerCluster cluster, int x, int y, int width)
    {
        Minecraft client = Minecraft.getInstance();
        Font textRenderer = client.font;

        this.cluster = cluster;
        this.x = x;
        this.y = y;
        this.width = width;
        this.rowWidth = width;
        this.height = textRenderer.lineHeight + 5;
        float baseIconSize = textRenderer.lineHeight + 2;

        expandButton = new ButtonWidget(x, y, "+", CommonColors.WHITE, this::toggleExpanded);
        expandButton.setDecorated(false);

        baseLabel = Component.translatable("button.spawn_radar.cluster_label", cluster.spawners().size(), cluster.id()).getString();
        clusterButton = new ButtonWidget(
            x + expandButton.getWidth() + CLUSTER_GAP,
            y,
            baseLabel,
            CommonColors.LIGHT_GRAY,
            () -> ClusterManager.toggleHighlightCluster(cluster.id())
        );
        clusterButton.setDecorated(false);

        int leftBranchWidth = Math.max(textRenderer.width(LEFT_BRANCH_END), textRenderer.width(LEFT_BRANCH_CONTINUE));
        int rightBranchWidth = Math.max(textRenderer.width(RIGHT_BRANCH_END), textRenderer.width(RIGHT_BRANCH_CONTINUE));
        branchGlyphWidth = Math.max(leftBranchWidth, rightBranchWidth);

        requiredWidth = expandButton.getWidth() + CLUSTER_GAP + textRenderer.width("[*] " + baseLabel);

        int index = 1;
        for (SpawnerInfo spawner : cluster.spawners())
        {
            BlockPos pos = spawner.pos();
            String label = String.format("%d. (%d, %d, %d)", index++, pos.getX(), pos.getY(), pos.getZ());
            ButtonWidget childButton = new ButtonWidget(
                x + CHILD_INDENT,
                y,
                label,
                CommonColors.LIGHTER_GRAY,
                () -> {
                    if (client.player != null)
                        client.player.connection.sendCommand(
                            String.format("tp %d %d %d", pos.getX(), pos.getY(), pos.getZ())
                        );
                }
            );
            childButton.setDecorated(false);

            boolean knownMob = spawner != null && spawner.hasKnownMob();
            float iconSize = knownMob ? baseIconSize : 0f;
            ItemStack spawnEgg = knownMob ? createSpawnEggStack(spawner) : ItemStack.EMPTY;
            ChildRow row = new ChildRow(childButton, iconSize, spawner, spawnEgg);
            if (row.supportsVolumeToggles())
            {
                ButtonWidget spawnToggle = createSpawnToggleButton(row);
                ButtonWidget mobCapToggle = createMobCapToggleButton(row);
                ButtonWidget efficiencyToggle = createEfficiencyToggleButton(row);
                ButtonWidget mobCapStatusToggle = createMobCapStatusToggleButton(row);
                ButtonWidget lightLevelToggle = createLightLevelToggleButton(row);
                row.setSpawnToggle(spawnToggle);
                row.setMobCapToggle(mobCapToggle);
                row.setEfficiencyToggle(efficiencyToggle);
                row.setMobCapStatusToggle(mobCapStatusToggle);
                row.setLightLevelToggle(lightLevelToggle);
                syncToggleColors(row);
            }
            childRows.add(row);

            int iconSpace = row.hasMobIcon() ? (int) Math.ceil(iconSize) + ICON_GAP : 0;
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
                for (ButtonWidget toggle : row.activeToggles())
                    toggle.onMouseClick(mx, my, mouseButton);
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
                for (ButtonWidget toggle : row.activeToggles())
                    toggle.onMouseMove(mx, my);
                boolean hovered = row.button().isHovered() || row.isWithinTree(mx, my);
                row.setHoverActive(hovered);
            }
        }
    }

    @Override
    public void render(GuiGraphics context)
    {
        Font textRenderer = Minecraft.getInstance().font;
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
        return total + EXPANDED_CLUSTER_GAP;
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

    private void updateClusterRow(Font textRenderer)
    {
        expandButton.setText(expanded ? "-" : "+");
        expandButton.setY(y);
        boolean highlighted = ClusterManager.isHighlighted(cluster.id());

        String statusMarker = highlighted ? "[*]" : "[ ]";
        String clusterText = statusMarker + " " + baseLabel;
        clusterButton.setText(clusterText);
        clusterButton.setY(y);

        int accentColor = 0xFF000000 | ConfigManager.getClusterColor(cluster.spawners().size());
        clusterButton.setColor(highlighted ? accentColor : CommonColors.LIGHT_GRAY);
        int clusterTextWidth = textRenderer.width(clusterText);
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

    private void renderChildren(GuiGraphics context, Font textRenderer)
    {
        boolean mirror = alignment == ConfigManager.HudHorizontalAlignment.RIGHT;
        int childY = y + expandButton.getHeight() + CHILD_VERTICAL_GAP;

        for (int i = 0; i < childRows.size(); i++)
        {
            ChildRow row = childRows.get(i);
            ButtonWidget child = row.button();
            if (row.supportsVolumeToggles())
                syncToggleColors(row);
            boolean rowActive = isRowActive(row);
            int rowHeight = getRowHeight(row);
            String branch = getBranchGlyph(i == childRows.size() - 1, mirror);
            int branchWidth = textRenderer.width(branch);
            int childWidth = Math.max(child.getContentWidth(), 80);
            boolean hasIcon = row.hasMobIcon();
            int iconSpace = hasIcon ? (int) Math.ceil(row.iconSize()) + ICON_GAP : 0;
            int branchY = childY + (rowHeight - textRenderer.lineHeight) / 2;
            int textY = childY + (rowHeight - child.getHeight()) / 2;
            child.setY(textY);

            if (!mirror)
            {
                int branchX = expandButton.getX() + expandButton.getWidth() + CHILD_INDENT;
                int childX = branchX + branchWidth + BRANCH_GAP + iconSpace;
                child.setX(childX);
                context.drawString(textRenderer, branch, branchX, branchY, CommonColors.DARK_GRAY, false);
                if (hasIcon)
                    renderSpawnerIcon(context, row, childX - iconSpace, childY + (rowHeight - Math.round(row.iconSize())) / 2);
            }
            else
            {
                int branchX = expandButton.getX() - CHILD_INDENT - branchWidth;
                int childX = branchX - BRANCH_GAP - iconSpace - childWidth;
                child.setX(childX);
                context.drawString(textRenderer, branch, branchX, branchY, CommonColors.DARK_GRAY, false);
                if (hasIcon)
                    renderSpawnerIcon(context, row, childX + childWidth + ICON_GAP, childY + (rowHeight - Math.round(row.iconSize())) / 2);
            }

            child.setWidth(childWidth);
            child.render(context);

            if (rowActive && Minecraft.getInstance().screen instanceof ChatScreen && row.supportsVolumeToggles())
                renderToggleTree(context, textRenderer, row, child, childWidth, textY, mirror);
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
            holder[0].setColor(enabled ? (255 << 24) | RadarClient.config.spawnVolumeColor : TOGGLE_INACTIVE_COLOR);
        });
        holder[0].setDecorated(false);
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
            holder[0].setColor(enabled ? (255 << 24) | RadarClient.config.mobCapVolumeColor : TOGGLE_INACTIVE_COLOR);
        });
        holder[0].setDecorated(false);
        return holder[0];
    }

    private ButtonWidget createEfficiencyToggleButton(ChildRow row)
    {
        final ButtonWidget[] holder = new ButtonWidget[1];
        holder[0] = new ButtonWidget(0, 0, "Show Efficiency", TOGGLE_INACTIVE_COLOR, () ->
        {
            if (!row.hoverActive())
                return;
            boolean enabled = SpawnerEfficiencyManager.toggle(row.spawner().pos(), RadarClient.config.showSpawnerEfficiencyLabel);
            holder[0].setColor(enabled ? CommonColors.GREEN : TOGGLE_INACTIVE_COLOR);
        });
        holder[0].setDecorated(false);
        return holder[0];
    }

    private ButtonWidget createMobCapStatusToggleButton(ChildRow row)
    {
        final ButtonWidget[] holder = new ButtonWidget[1];
        holder[0] = new ButtonWidget(0, 0, "Show Mob Cap Status", TOGGLE_INACTIVE_COLOR, () ->
        {
            if (!row.hoverActive())
                return;
            boolean enabled = SpawnerMobCapStatusManager.toggle(row.spawner().pos(), RadarClient.config.showSpawnerMobCapStatus);
            holder[0].setColor(enabled ? CommonColors.HIGH_CONTRAST_DIAMOND : TOGGLE_INACTIVE_COLOR);
        });
        holder[0].setDecorated(false);
        return holder[0];
    }

    private ButtonWidget createLightLevelToggleButton(ChildRow row)
    {
        final ButtonWidget[] holder = new ButtonWidget[1];
        holder[0] = new ButtonWidget(0, 0, "Show Light Levels", TOGGLE_INACTIVE_COLOR, () ->
        {
            if (!row.hoverActive())
                return;
            boolean enabled = SpawnerLightLevelManager.toggle(row.spawner().pos(), RadarClient.config.showSpawnerLightLevels);
            holder[0].setColor(enabled ? CommonColors.YELLOW : TOGGLE_INACTIVE_COLOR);
        });
        holder[0].setDecorated(false);
        return holder[0];
    }

    private int toggleBlockWidth(ChildRow row)
    {
        if (!row.supportsVolumeToggles())
            return 0;
        int width = TOGGLE_GAP + branchGlyphWidth + BRANCH_GAP;
        boolean first = true;
        for (ButtonWidget toggle : row.activeToggles())
        {
            if (!first)
                width += TOGGLE_SPACING;
            width += toggle.getContentWidth();
            first = false;
        }
        return width;
    }

    private void renderToggleTree(GuiGraphics context, Font textRenderer, ChildRow row,
                                  ButtonWidget child, int childWidth, int textY, boolean mirror)
    {
        List<ButtonWidget> toggles = row.activeToggles();
        if (toggles.isEmpty())
        {
            row.clearTreeBounds();
            return;
        }

        ToggleBounds combinedBounds = null;
        int currentY = textY;
        for (int i = 0; i < toggles.size(); i++)
        {
            ButtonWidget toggle = toggles.get(i);
            ToggleBranchType branchType = ToggleBranchType.EXPAND;
            if (i == toggles.size() - 1)
                branchType = ToggleBranchType.END;
            else if (i > 0)
                branchType = ToggleBranchType.CONTINUE;

            ToggleBounds bounds = renderToggleRow(
                context, textRenderer, toggle, child, childWidth, currentY, mirror, branchType);

            combinedBounds = combinedBounds == null
                ? bounds
                : new ToggleBounds(
                    Math.min(combinedBounds.minX(), bounds.minX()),
                    Math.max(combinedBounds.maxX(), bounds.maxX()),
                    Math.min(combinedBounds.minY(), bounds.minY()),
                    Math.max(combinedBounds.maxY(), bounds.maxY())
                );

            currentY += toggle.getHeight() + TOGGLE_VERTICAL_GAP;
        }

        if (combinedBounds != null)
        {
            row.setTreeBounds(
                combinedBounds.minX() - TREE_HOVER_PADDING,
                combinedBounds.maxX() + TREE_HOVER_PADDING,
                combinedBounds.minY() - TREE_HOVER_PADDING,
                combinedBounds.maxY() + TREE_HOVER_PADDING
            );
        }
    }

    private ToggleBounds renderToggleRow(GuiGraphics context, Font textRenderer, ButtonWidget toggle,
                                 ButtonWidget child, int childWidth, int startY, boolean mirror, ToggleBranchType branchType)
    {
        String branch;
        if (mirror)
        {
            branch = branchType == ToggleBranchType.END ? RIGHT_BRANCH_END : RIGHT_BRANCH_CONTINUE;
        }
        else
        {
            branch = switch (branchType)
            {
                case EXPAND -> LEFT_BRANCH_EXPAND;
                case CONTINUE -> LEFT_BRANCH_CONTINUE;
                case END -> LEFT_BRANCH_END;
            };
        }

        int branchWidth = textRenderer.width(branch);
        int branchX;
        if (!mirror)
            branchX = child.getX() + childWidth + TOGGLE_GAP;
        else
            branchX = child.getX() - TOGGLE_GAP - branchWidth;

        int branchY = startY + (toggle.getHeight() - textRenderer.lineHeight) / 2;
        context.drawString(textRenderer, branch, branchX, branchY, CommonColors.DARK_GRAY, false);

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
        int maxY = Math.max(branchY + textRenderer.lineHeight, startY + toggle.getHeight());
        return new ToggleBounds(minX, maxX, minY, maxY);
    }

    private void syncToggleColors(ChildRow row)
    {
        if (!row.supportsVolumeToggles())
            return;
        BlockPos pos = row.spawner().pos();
        boolean spawnEnabled = VolumeHighlightManager.isSpawnVolumeEnabled(pos, RadarClient.config.showSpawnerSpawnVolume);
        boolean mobCapEnabled = VolumeHighlightManager.isMobCapVolumeEnabled(pos, RadarClient.config.showSpawnerMobCapVolume);
        if (row.spawnToggle() != null)
            row.spawnToggle().setColor(spawnEnabled ? (255 << 24) | RadarClient.config.spawnVolumeColor : TOGGLE_INACTIVE_COLOR);
        if (row.mobCapToggle() != null)
            row.mobCapToggle().setColor(mobCapEnabled ? (255 << 24) | RadarClient.config.mobCapVolumeColor : TOGGLE_INACTIVE_COLOR);
        if (row.efficiencyToggle() != null)
        {
            boolean efficiencyEnabled = SpawnerEfficiencyManager.isEnabled(pos, RadarClient.config.showSpawnerEfficiencyLabel);
            row.efficiencyToggle().setColor(efficiencyEnabled ? CommonColors.GREEN : TOGGLE_INACTIVE_COLOR);
        }
        if (row.mobCapStatusToggle() != null)
        {
            boolean statusEnabled = SpawnerMobCapStatusManager.isEnabled(pos, RadarClient.config.showSpawnerMobCapStatus);
            row.mobCapStatusToggle().setColor(statusEnabled ? CommonColors.HIGH_CONTRAST_DIAMOND : TOGGLE_INACTIVE_COLOR);
        }
        if (row.lightLevelToggle() != null)
        {
            boolean lightLevelsEnabled = SpawnerLightLevelManager.isEnabled(pos, RadarClient.config.showSpawnerLightLevels);
            row.lightLevelToggle().setColor(lightLevelsEnabled ? CommonColors.YELLOW : TOGGLE_INACTIVE_COLOR);
        }
    }

    private boolean isRowActive(ChildRow row)
    {
        return row.hoverActive() && row.supportsVolumeToggles();
    }

    private static void renderSpawnerIcon(GuiGraphics context, ChildRow row, int x, int y)
    {
        if (!row.hasMobIcon())
            return;

        SpawnerInfo spawner = row.spawner();
        if (spawner == null || spawner.entityType() == null)
            return;

        ConfigManager.SpawnerIconMode mode = resolveIconMode();
        if (mode == ConfigManager.SpawnerIconMode.SPAWN_EGG && !row.spawnEggStack().isEmpty())
            renderSpawnEgg(row.spawnEggStack(), x, y, row.iconSize());
        else
            MobPuppetRenderer.render(context, spawner.entityType(), x, y, row.iconSize());
    }

    private static ConfigManager.SpawnerIconMode resolveIconMode()
    {
        if (RadarClient.config == null || RadarClient.config.spawnerIconMode == null)
            return ConfigManager.DEFAULT.spawnerIconMode;
        return RadarClient.config.spawnerIconMode;
    }

    private static ItemStack createSpawnEggStack(SpawnerInfo spawner)
    {
        if (spawner == null || spawner.entityType() == null)
            return ItemStack.EMPTY;
        SpawnEggItem spawnEgg = SpawnEggItem.byId(spawner.entityType());
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
        private final float iconSize;
        private final SpawnerInfo spawner;
        private final ItemStack spawnEggStack;
        private final boolean hasMobIcon;
        private final boolean supportsVolumeToggles;
        private ButtonWidget spawnToggle;
        private ButtonWidget mobCapToggle;
        private ButtonWidget efficiencyToggle;
        private ButtonWidget mobCapStatusToggle;
        private ButtonWidget lightLevelToggle;
        private boolean hoverActive;
        private int treeMinX;
        private int treeMaxX;
        private int treeMinY;
        private int treeMaxY;

        ChildRow(ButtonWidget button, float iconSize, SpawnerInfo spawner, ItemStack spawnEggStack)
        {
            this.button = button;
            this.iconSize = iconSize;
            this.spawner = spawner;
            this.spawnEggStack = spawnEggStack == null ? ItemStack.EMPTY : spawnEggStack;
            this.hasMobIcon = iconSize > 0f && spawner != null && spawner.hasKnownMob();
            this.supportsVolumeToggles = hasMobIcon;
        }

        ButtonWidget button()
        {
            return button;
        }

        float iconSize()
        {
            return iconSize;
        }

        SpawnerInfo spawner()
        {
            return spawner;
        }

        ItemStack spawnEggStack()
        {
            return spawnEggStack;
        }

        boolean hasMobIcon()
        {
            return hasMobIcon;
        }

        void setSpawnToggle(ButtonWidget spawnToggle)
        {
            this.spawnToggle = spawnToggle;
        }

        void setMobCapToggle(ButtonWidget mobCapToggle)
        {
            this.mobCapToggle = mobCapToggle;
        }

        void setEfficiencyToggle(ButtonWidget efficiencyToggle)
        {
            this.efficiencyToggle = efficiencyToggle;
        }

        void setMobCapStatusToggle(ButtonWidget mobCapStatusToggle)
        {
            this.mobCapStatusToggle = mobCapStatusToggle;
        }

        void setLightLevelToggle(ButtonWidget lightLevelToggle)
        {
            this.lightLevelToggle = lightLevelToggle;
        }

        ButtonWidget spawnToggle()
        {
            return spawnToggle;
        }

        ButtonWidget mobCapToggle()
        {
            return mobCapToggle;
        }

        ButtonWidget efficiencyToggle()
        {
            return efficiencyToggle;
        }

        ButtonWidget mobCapStatusToggle()
        {
            return mobCapStatusToggle;
        }

        ButtonWidget lightLevelToggle()
        {
            return lightLevelToggle;
        }

        List<ButtonWidget> activeToggles()
        {
            List<ButtonWidget> toggles = new ArrayList<>();
            if (spawnToggle != null)
                toggles.add(spawnToggle);
            if (mobCapToggle != null)
                toggles.add(mobCapToggle);
            if (efficiencyToggle != null)
                toggles.add(efficiencyToggle);
            if (mobCapStatusToggle != null)
                toggles.add(mobCapStatusToggle);
            if (lightLevelToggle != null)
                toggles.add(lightLevelToggle);
            return toggles;
        }

        boolean supportsVolumeToggles()
        {
            return supportsVolumeToggles;
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
            if (!supportsVolumeToggles)
                return false;
            return mx >= treeMinX && mx <= treeMaxX && my >= treeMinY && my <= treeMaxY;
        }

        void clearTreeBounds()
        {
            treeMinX = treeMaxX = treeMinY = treeMaxY = 0;
        }
    }

    private enum ToggleBranchType
    {
        EXPAND,
        CONTINUE,
        END
    }

    private record ToggleBounds(int minX, int maxX, int minY, int maxY) {}
}
