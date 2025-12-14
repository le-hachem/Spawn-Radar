package cc.hachem.spawnradar.hud;

import cc.hachem.spawnradar.RadarClient;
import cc.hachem.spawnradar.config.ConfigManager;
import cc.hachem.spawnradar.core.ClusterManager;
import cc.hachem.spawnradar.core.SpawnerCluster;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;

public class PanelWidget extends Widget
{
    public static List<Widget> clusterList = new ArrayList<>();
    private static ButtonWidget previousPageWidget;
    private static ButtonWidget nextPageWidget;
    private static ButtonWidget toggleAllButton;
    private static ButtonWidget rescanButton;
    private static ButtonWidget resetButton;

    private static int elementCount = 5;
    private static int currentPage = 0;
    private static int pageCount = 0;

    public static PanelWidget instance;

    private static String pageText = "";
    private static int pageTextX = 0;
    private static int pageTextY = 0;
    private static final int paginationSpacing = 8;

    public PanelWidget(int x, int y)
    {
        Minecraft client = Minecraft.getInstance();
        Font textRenderer = client.font;
        String placeholder = "[(xxx) Cluster#xxx]";

        this.x = x;
        this.y = y;
        this.width = textRenderer.width(placeholder) + 10;
        this.height = (textRenderer.lineHeight + 6) * elementCount;

        initializeButtons(x, y, client);
        instance = this;
    }

    private void initializeButtons(int x, int y, Minecraft client)
    {
        previousPageWidget = new ButtonWidget(
            x, y - 20,
            Component.translatable("button.spawn_radar.prev_page").getString(),
            CommonColors.WHITE,
            PanelWidget::previousPage
        );

        nextPageWidget = new ButtonWidget(
            x + 100, y - 20,
            Component.translatable("button.spawn_radar.next_page").getString(),
            CommonColors.WHITE,
            PanelWidget::nextPage
        );

        toggleAllButton = new ButtonWidget(
            x, y - 40,
            Component.translatable("button.spawn_radar.toggle_all_off").getString(),
            CommonColors.GREEN,
            () ->
            {
                ClusterManager.toggleAllClusters();
                updateTopButtons();
            }
        );

        rescanButton = new ButtonWidget(
            x, y - 40,
            Component.translatable("button.spawn_radar.rescan").getString(),
            CommonColors.HIGH_CONTRAST_DIAMOND,
            PanelWidget::triggerRescan
        );

        resetButton = new ButtonWidget(
            x, y - 40,
            Component.translatable("button.spawn_radar.reset").getString(),
            CommonColors.SOFT_RED,
            () ->
            {
                if (client.player != null && RadarClient.reset(client.player))
                    updateTopButtons();
            }
        );

        toggleAllButton.setDecorated(false);
        rescanButton.setDecorated(false);
        resetButton.setDecorated(false);
    }

    public static PanelWidget getInstance()
    {
        return instance;
    }

    public static void refresh()
    {
        if (instance == null)
            return;

        rebuildClusterList();
        recalculatePagination();
        HudRenderer.updatePanelPosition();
    }

    public static void onPanelMoved()
    {
        if (instance == null)
            return;
        updatePages();
        updateTopButtons();
    }

    public static void setElementCount(int newCount)
    {
        if (newCount <= 0)
            return;
        elementCount = newCount;

        if (instance == null)
            return;

        recalculatePagination();
    }

    private static void updatePages()
    {
        Minecraft client = Minecraft.getInstance();
        Font textRenderer = client.font;

        PanelWidget panel = getInstance();

        pageText = String.format("%d/%d", currentPage + 1, pageCount);
        int pageTextWidth = textRenderer.width(pageText);
        int paginationY = panel.y - 20;
        int listWidth = Math.max(panel.width, 140);
        int leftEdge = panel.x;
        int rightEdge = panel.x + listWidth;
        ConfigManager.HudHorizontalAlignment alignment = getPanelAlignment();

        if (alignment == ConfigManager.HudHorizontalAlignment.RIGHT)
        {
            nextPageWidget.setX(rightEdge - nextPageWidget.getWidth());
            nextPageWidget.setY(paginationY);

            pageTextX = nextPageWidget.getX() - paginationSpacing - pageTextWidth;
            pageTextY = paginationY;

            previousPageWidget.setX(pageTextX - paginationSpacing - previousPageWidget.getWidth());
            previousPageWidget.setY(paginationY);
        }
        else
        {
            previousPageWidget.setX(leftEdge);
            previousPageWidget.setY(paginationY);

            pageTextX = previousPageWidget.getX() + previousPageWidget.getWidth() + paginationSpacing;
            pageTextY = paginationY;

            nextPageWidget.setX(pageTextX + pageTextWidth + paginationSpacing);
            nextPageWidget.setY(paginationY);
        }
    }

    private static void updateTopButtons()
    {
        boolean allHighlighted = ClusterManager.getHighlightedClusterIds().size() == ClusterManager.getClusters().size();

        toggleAllButton.setText(
            Component.translatable(allHighlighted ? "button.spawn_radar.toggle_all_off" : "button.spawn_radar.toggle_all_on").getString()
        );
        toggleAllButton.setColor(allHighlighted ? CommonColors.SOFT_YELLOW : CommonColors.GREEN);

        PanelWidget panel = getInstance();
        if (panel == null)
            return;

        int listWidth = Math.max(panel.width, 140);
        int baseY = previousPageWidget.getY() - 20;
        toggleAllButton.setY(baseY);
        rescanButton.setY(baseY);
        resetButton.setY(baseY);

        ConfigManager.HudHorizontalAlignment alignment = getPanelAlignment();
        if (alignment == ConfigManager.HudHorizontalAlignment.RIGHT)
            positionTopButtonsRight(panel, listWidth);
        else
            positionTopButtonsLeft(baseY);
    }

    public static void nextPage()
    {
        if (pageCount == 0) return;
        currentPage = (currentPage + 1) % pageCount;
        updatePages();
    }

    public static void previousPage()
    {
        if (pageCount == 0) return;
        currentPage = (currentPage - 1 + pageCount) % pageCount;
        updatePages();
    }

    @Override
    public void onMouseClick(int mx, int my, int mouseButton)
    {
        toggleAllButton.onMouseClick(mx, my, mouseButton);
        rescanButton.onMouseClick(mx, my, mouseButton);
        resetButton.onMouseClick(mx, my, mouseButton);

        previousPageWidget.onMouseClick(mx, my, mouseButton);
        nextPageWidget.onMouseClick(mx, my, mouseButton);

        getVisiblePageElements().forEach(child -> child.onMouseClick(mx, my, mouseButton));
    }

    @Override
    public void onMouseMove(int mx, int my)
    {
        toggleAllButton.onMouseMove(mx, my);
        rescanButton.onMouseMove(mx, my);
        resetButton.onMouseMove(mx, my);
        previousPageWidget.onMouseMove(mx, my);
        nextPageWidget.onMouseMove(mx, my);

        getVisiblePageElements().forEach(child -> child.onMouseMove(mx, my));
    }

    @Override
    public void render(GuiGraphics context)
    {
        if (!ClusterManager.getClusters().isEmpty())
        {
            toggleAllButton.render(context);
            rescanButton.render(context);
            resetButton.render(context);
        }

        Minecraft client = Minecraft.getInstance();
        Font textRenderer = client.font;

        int listWidth = Math.max(width, 140);
        int padding = 6;
        int frameX = Math.max(2, x - padding);
        int listX = frameX + padding;

        var panelAlignment = getPanelAlignment();
        var entryAlignment = panelAlignment == ConfigManager.HudHorizontalAlignment.RIGHT
                                 ? ConfigManager.HudHorizontalAlignment.RIGHT
                                 : ConfigManager.HudHorizontalAlignment.LEFT;

        int elementY = y;
        for (Widget child : getVisiblePageElements())
        {
            child.setX(listX);
            child.setY(elementY);
            child.setWidth(listWidth);
            if (child instanceof ClusterListItemWidget clusterItem)
                clusterItem.setAlignment(entryAlignment);
            child.render(context);
            elementY += child.getHeight() + 5;
        }

        if (pageCount >= 1)
        {
            previousPageWidget.render(context);
            nextPageWidget.render(context);
            context.drawString(textRenderer, pageText, pageTextX, pageTextY, CommonColors.GRAY, false);
        }
    }

    private static List<Widget> getVisiblePageElements()
    {
        int start = currentPage * elementCount;
        int end = Math.min(start + elementCount, clusterList.size());
        return clusterList.subList(start, end);
    }

    private static void triggerRescan()
    {
        var client = Minecraft.getInstance();
        if (client.player == null)
            return;
        int radius = RadarClient.config != null ? RadarClient.config.defaultSearchRadius : ConfigManager.DEFAULT.defaultSearchRadius;
        var sortType = RadarClient.config != null ? RadarClient.config.defaultSortType : ConfigManager.DEFAULT.defaultSortType;
        RadarClient.generateClusters(client.player, radius, mapSortType(sortType), false);
    }

    private static ConfigManager.HudHorizontalAlignment getPanelAlignment()
    {
        if (RadarClient.config == null || RadarClient.config.panelHorizontalAlignment == null)
            return ConfigManager.DEFAULT.panelHorizontalAlignment;
        return RadarClient.config.panelHorizontalAlignment;
    }

    private static void rebuildClusterList()
    {
        clusterList.clear();
        int minWidth = 140;
        int maxWidth = minWidth;

        for (SpawnerCluster cluster : ClusterManager.getClusters())
        {
            ClusterListItemWidget widget = new ClusterListItemWidget(cluster, 0, 0, 0);
            clusterList.add(widget);

            maxWidth = Math.max(maxWidth, widget.getRequiredWidth());
        }

        if (!clusterList.isEmpty())
        {
            int padding = 10;
            int finalMaxWidth = Math.max(minWidth, maxWidth + padding);
            clusterList.forEach(c -> c.setWidth(finalMaxWidth));
            getInstance().width = finalMaxWidth;
        }
    }

    private static void recalculatePagination()
    {
        pageCount = (int) Math.ceil((double) clusterList.size() / elementCount);
        currentPage = Math.min(currentPage, Math.max(pageCount - 1, 0));

        updatePages();
        updateTopButtons();
    }

    private static void positionTopButtonsRight(PanelWidget panel, int listWidth)
    {
        toggleAllButton.setWidth(toggleAllButton.getContentWidth());
        rescanButton.setWidth(rescanButton.getContentWidth());
        resetButton.setWidth(resetButton.getContentWidth());

        resetButton.setX(panel.x + listWidth - resetButton.getWidth());
        rescanButton.setX(resetButton.getX() - paginationSpacing - rescanButton.getWidth());
        toggleAllButton.setX(rescanButton.getX() - paginationSpacing - toggleAllButton.getWidth());
    }

    private static void positionTopButtonsLeft(int baseY)
    {
        int toggleX = previousPageWidget.getX();
        toggleAllButton.setWidth(toggleAllButton.getContentWidth());
        toggleAllButton.setX(toggleX);
        toggleAllButton.setY(baseY);

        int rescanX = toggleAllButton.getX() + toggleAllButton.getWidth() + paginationSpacing;
        rescanButton.setWidth(rescanButton.getContentWidth());
        rescanButton.setX(rescanX);
        rescanButton.setY(baseY);

        int resetX = rescanButton.getX() + rescanButton.getWidth() + paginationSpacing;
        resetButton.setWidth(resetButton.getContentWidth());
        resetButton.setX(resetX);
        resetButton.setY(baseY);
    }

    public static void dispose()
    {
        clusterList.clear();
        instance = null;
    }

    private static String mapSortType(SpawnerCluster.SortType type)
    {
        return switch (type)
        {
            case BY_PROXIMITY -> "proximity";
            case BY_SIZE -> "size";
            default -> "";
        };
    }

}