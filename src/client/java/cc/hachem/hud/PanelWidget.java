package cc.hachem.hud;

import cc.hachem.RadarClient;
import cc.hachem.core.ClusterManager;
import cc.hachem.core.SpawnerCluster;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
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

    private final static int elementCount = 5;
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

        previousPageWidget = new ButtonWidget(x, y - 20, "< Prev", Colors.WHITE, PanelWidget::previousPage);
        nextPageWidget = new ButtonWidget(x + 100, y - 20, "Next >", Colors.WHITE, PanelWidget::nextPage);

        toggleAllButton = new ButtonWidget(x, y - 40, "[All OFF]", Colors.GREEN, () ->
        {
            ClusterManager.toggleAllClusters();
            updateTopButtons();
        });

        resetButton = new ButtonWidget(x, y - 40, "[Reset]", Colors.LIGHT_RED, () ->
        {
            if (client.player != null)
                RadarClient.reset(client.player);
            updateTopButtons();
        });

        instance = this;
    }

    public static PanelWidget getInstance()
    {
        return instance;
    }

    public static void refresh()
    {
        clusterList.clear();
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;

        int maxWidth = 0;

        for (SpawnerCluster cluster : ClusterManager.getClusters())
        {
            ClusterListItemWidget widget = new ClusterListItemWidget(cluster, 0, 0, 0);
            clusterList.add(widget);

            String label = String.format("[(%d) Cluster #%d]", cluster.spawners().size(), cluster.id());
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

    private static void updatePages()
    {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;

        if (pageCount > 1)
        {
            PanelWidget panel = getInstance();

            pageText = String.format("%d/%d", currentPage + 1, pageCount);
            int pageTextWidth = textRenderer.getWidth(pageText);
            int paginationY = panel.y - 20;

            previousPageWidget.setX(panel.x);
            previousPageWidget.setY(paginationY);

            pageTextX = previousPageWidget.getX() + previousPageWidget.getWidth()+paginationSpacing;
            pageTextY = paginationY;

            nextPageWidget.setX(pageTextX + pageTextWidth + paginationSpacing);
            nextPageWidget.setY(paginationY);
        }

        updateTopButtons();
    }

    private static void updateTopButtons()
    {
        boolean allHighlighted = ClusterManager.getHighlightedClusterIds().size() == ClusterManager.getClusters().size();
        toggleAllButton.setText(allHighlighted ? "[All OFF]" : "[All ON]");

        if (allHighlighted)
            toggleAllButton.setColor(Colors.LIGHT_YELLOW);
        else
            toggleAllButton.setColor(Colors.GREEN);

        int totalWidth = (nextPageWidget.getX() + nextPageWidget.getWidth()) - previousPageWidget.getX();
        int buttonWidth = totalWidth / 2;

        toggleAllButton.setWidth(buttonWidth);
        resetButton.setWidth(buttonWidth);

        toggleAllButton.setX(previousPageWidget.getX());
        toggleAllButton.setY(previousPageWidget.getY() - 20);

        resetButton.setX(nextPageWidget.getX() + nextPageWidget.getWidth() - resetButton.getWidth()+10);
        resetButton.setY(previousPageWidget.getY() - 20);
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

        for (Widget child : getVisiblePageElements())
            child.onMouseClick(mx, my, mouseButton);
    }

    @Override
    public void render(DrawContext context)
    {
        if (!getVisiblePageElements().isEmpty())
        {
            toggleAllButton.render(context);
            resetButton.render(context);
        }

        int leftX = previousPageWidget.getX() + previousPageWidget.getWidth();
        int rightX = nextPageWidget.getX();
        int centerX = leftX + (rightX - leftX) / 2;

        int maxChildWidth = 0;
        for (Widget child : getVisiblePageElements())
            if (child.getWidth() > maxChildWidth && child instanceof ClusterListItemWidget)
                maxChildWidth = child.getWidth();

        int elementX = centerX - maxChildWidth / 2;
        int elementY = y;

        for (Widget child : getVisiblePageElements())
        {
            child.setX(elementX);
            child.setY(elementY);
            child.setWidth(maxChildWidth);
            child.render(context);
            elementY += child.getHeight()+5;
        }

        if (pageCount > 1)
        {
            previousPageWidget.render(context);
            nextPageWidget.render(context);

            if (!pageText.isEmpty())
            {
                MinecraftClient client = MinecraftClient.getInstance();
                TextRenderer textRenderer = client.textRenderer;
                context.drawText(textRenderer, pageText, pageTextX, pageTextY, Colors.GRAY, false);
            }
        }
    }

    private static List<Widget> getVisiblePageElements()
    {
        int start = currentPage * elementCount;
        int end = Math.min(start + elementCount, clusterList.size());
        return clusterList.subList(start, end);
    }
}
