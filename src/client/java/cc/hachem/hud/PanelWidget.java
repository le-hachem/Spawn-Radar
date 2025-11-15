package cc.hachem.hud;

import cc.hachem.RadarClient;
import cc.hachem.core.ClusterManager;
import cc.hachem.core.SpawnerCluster;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;

import java.util.ArrayList;
import java.util.List;

public class PanelWidget extends Widget
{
    public static List<Widget> clusterList = new ArrayList<>();
    private static ButtonWidget previousPageWidget;
    private static ButtonWidget nextPageWidget;
    private static ButtonWidget toggleAllButton;
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
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;
        String placeholder = "[(xxx) Cluster#xxx]";

        this.x = x;
        this.y = y;
        this.width = textRenderer.getWidth(placeholder) + 10;
        this.height = (textRenderer.fontHeight + 6) * elementCount;

        previousPageWidget = new ButtonWidget(
            x, y - 20,
            Text.translatable("button.spawn_radar.prev_page").getString(),
            Colors.WHITE,
            PanelWidget::previousPage
        );

        nextPageWidget = new ButtonWidget(
            x + 100, y - 20,
            Text.translatable("button.spawn_radar.next_page").getString(),
            Colors.WHITE,
            PanelWidget::nextPage
        );

        toggleAllButton = new ButtonWidget(
            x, y - 40,
            Text.translatable("button.spawn_radar.toggle_all_off").getString(),
            Colors.GREEN,
            () ->
            {
                ClusterManager.toggleAllClusters();
                updateTopButtons();
            }
        );

        resetButton = new ButtonWidget(
            x, y - 40,
            Text.translatable("button.spawn_radar.reset").getString(),
            Colors.LIGHT_RED,
            () ->
            {
                if (client.player != null)
                    RadarClient.reset(client.player);
                updateTopButtons();
            }
        );

        toggleAllButton.setDecorated(false);
        resetButton.setDecorated(false);

        instance = this;
    }

    public static PanelWidget getInstance()
    {
        return instance;
    }

    public static void refresh()
    {
        if (instance == null)
            return;

        clusterList.clear();
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

        int maxWidth = 0;

        for (SpawnerCluster cluster : ClusterManager.getClusters())
        {
            ClusterListItemWidget widget = new ClusterListItemWidget(cluster, 0, 0, 0);
            clusterList.add(widget);

            String label = Text.translatable("button.spawn_radar.cluster_label", cluster.spawners().size(), cluster.id()).getString();
            int labelWidth = textRenderer.getWidth(label);
            if (labelWidth > maxWidth)
                maxWidth = labelWidth;
        }

        int padding = 10;

        if (!clusterList.isEmpty())
        {
            int finalMaxWidth = maxWidth + padding;
            clusterList.forEach(c -> c.setWidth(finalMaxWidth));
            getInstance().width = finalMaxWidth;
        }

        pageCount = (int) Math.ceil((double) clusterList.size() / elementCount);
        currentPage = Math.min(currentPage, Math.max(pageCount - 1, 0));

        updatePages();
        updateTopButtons();
    }

    public static void updateButtonPositions()
    {
        previousPageWidget.setX(instance.getX());
        previousPageWidget.setY(instance.getY() - 20);

        nextPageWidget.setX(previousPageWidget.getX() + previousPageWidget.getWidth() + 50);
        nextPageWidget.setY(previousPageWidget.getY());

        toggleAllButton.setX(previousPageWidget.getX());
        toggleAllButton.setY(previousPageWidget.getY() - 20);

        resetButton.setX(toggleAllButton.getX() + toggleAllButton.getWidth());
        resetButton.setY(toggleAllButton.getY());
    }

    public static void setElementCount(int newCount)
    {
        if (newCount <= 0) return;
        elementCount = newCount;

        pageCount = (int) Math.ceil((double) clusterList.size() / elementCount);
        currentPage = Math.min(currentPage, Math.max(pageCount - 1, 0));

        updatePages();
        updateTopButtons();
    }

    private static void updatePages()
    {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;

        PanelWidget panel = getInstance();

        pageText = String.format("%d/%d", currentPage + 1, pageCount);
        int pageTextWidth = textRenderer.getWidth(pageText);
        int paginationY = panel.y - 20;

        previousPageWidget.setX(panel.x);
        previousPageWidget.setY(paginationY);

        pageTextX = previousPageWidget.getX() + previousPageWidget.getWidth() + paginationSpacing;
        pageTextY = paginationY;

        nextPageWidget.setX(pageTextX + pageTextWidth + paginationSpacing);
        nextPageWidget.setY(paginationY);

        updateTopButtons();
    }

    private static void updateTopButtons()
    {
        boolean allHighlighted = ClusterManager.getHighlightedClusterIds().size() == ClusterManager.getClusters().size();

        toggleAllButton.setText(
            Text.translatable(allHighlighted ? "button.spawn_radar.toggle_all_off" : "button.spawn_radar.toggle_all_on").getString()
        );
        toggleAllButton.setColor(allHighlighted ? Colors.LIGHT_YELLOW : Colors.GREEN);

        int totalWidth = (nextPageWidget.getX() + nextPageWidget.getWidth()) - previousPageWidget.getX();
        int buttonWidth = totalWidth / 2;

        toggleAllButton.setWidth(buttonWidth);
        resetButton.setWidth(buttonWidth);

        toggleAllButton.setX(previousPageWidget.getX());
        toggleAllButton.setY(previousPageWidget.getY() - 20);

        resetButton.setX(toggleAllButton.getX() + toggleAllButton.getWidth());
        resetButton.setY(toggleAllButton.getY());
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
        resetButton.onMouseClick(mx, my, mouseButton);

        previousPageWidget.onMouseClick(mx, my, mouseButton);
        nextPageWidget.onMouseClick(mx, my, mouseButton);

        getVisiblePageElements().forEach(child -> child.onMouseClick(mx, my, mouseButton));
    }

    @Override
    public void onMouseMove(int mx, int my)
    {
        toggleAllButton.onMouseMove(mx, my);
        resetButton.onMouseMove(mx, my);
        previousPageWidget.onMouseMove(mx, my);
        nextPageWidget.onMouseMove(mx, my);

        getVisiblePageElements().forEach(child -> child.onMouseMove(mx, my));
    }

    @Override
    public void render(DrawContext context)
    {
        if (!getVisiblePageElements().isEmpty())
        {
            toggleAllButton.render(context);
            resetButton.render(context);
        }

        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;

        int listWidth = Math.max(width, 140);
        int padding = 6;
        int frameX = Math.max(2, x - padding);
        int listX = frameX + padding;

        int elementY = y;
        for (Widget child : getVisiblePageElements())
        {
            child.setX(listX);
            child.setY(elementY);
            child.setWidth(listWidth);
            child.render(context);
            elementY += child.getHeight() + 5;
        }

        if (pageCount >= 1)
        {
            previousPageWidget.render(context);
            nextPageWidget.render(context);
            context.drawText(textRenderer, pageText, pageTextX, pageTextY, Colors.GRAY, false);
        }
    }

    private static List<Widget> getVisiblePageElements()
    {
        int start = currentPage * elementCount;
        int end = Math.min(start + elementCount, clusterList.size());
        return clusterList.subList(start, end);
    }
}