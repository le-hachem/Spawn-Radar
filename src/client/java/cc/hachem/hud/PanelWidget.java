package cc.hachem.hud;

import cc.hachem.RadarClient;
import cc.hachem.core.ClusterManager;
import cc.hachem.core.SpawnerCluster;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Colors;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class PanelWidget extends Widget
{
    public static class Pagination extends Widget
    {
        private final Runnable callback;
        private final String text;

        public Pagination(int x, int y, String text, Runnable callback)
        {
            MinecraftClient client = MinecraftClient.getInstance();
            TextRenderer textRenderer = client.textRenderer;

            this.text = text;
            this.callback = callback;

            this.x = x;
            this.y = y;
            this.width = textRenderer.getWidth(text)+10;
            this.height = (textRenderer.fontHeight+10);
        }

        @Override
        public void render(DrawContext context)
        {
            MinecraftClient client = MinecraftClient.getInstance();
            TextRenderer textRenderer = client.textRenderer;
            context.drawText(textRenderer, text, x, y, Colors.WHITE, true);
        }

        public void onMouseClick(int mx, int my, int mouseButton)
        {
            if (mouseButton != GLFW.GLFW_MOUSE_BUTTON_LEFT)
                return;
            if (!isMouseHover(mx, my))
                return;
            callback.run();
        }
    }

    public static List<Widget> clusterList = new ArrayList<>();
    private static Pagination previousPageWidget;
    private static Pagination nextPageWidget;

    private final static int elementCount = 5;
    private static int currentPage = 0;
    private static int pageCount = 0;

    public static PanelWidget instance;

    public PanelWidget(int x, int y)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;
        String placeholder = "[(xxx) Cluster#xxx]";

        this.x = x;
        this.y = y;
        this.width = textRenderer.getWidth(placeholder);
        this.height = (textRenderer.fontHeight+10)* elementCount;

        previousPageWidget = new Pagination(x, y - 15, "< Prev", PanelWidget::previousPage);
        nextPageWidget = new Pagination(x + 100, y - 15, "Next >", PanelWidget::nextPage);

        instance = this;
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

            int clusterSize = cluster.spawners().size();
            int clusterId = cluster.id();
            String label = String.format("[(%d) Cluster #%d]", clusterSize, clusterId);
            int labelWidth = textRenderer.getWidth(label);

            if (labelWidth > maxWidth)
                maxWidth = labelWidth;
        }

        int padding = 10;
        if (!clusterList.isEmpty())
        {
            int finalMaxWidth = maxWidth;
            clusterList.forEach(c -> c.setWidth(finalMaxWidth + padding));
        }

        PanelWidget panelWidget = PanelWidget.getInstance();
        panelWidget.width = maxWidth + padding;

        previousPageWidget.setX(panelWidget.x);
        previousPageWidget.setY(panelWidget.y - 15);

        nextPageWidget.setX(panelWidget.x + panelWidget.width - nextPageWidget.getWidth());
        nextPageWidget.setY(panelWidget.y - 15);

        pageCount = (int) Math.ceil((double) clusterList.size() / elementCount);
        currentPage = Math.min(currentPage, Math.max(pageCount - 1, 0));
    }


    public static void nextPage()
    {
        if (currentPage < pageCount - 1)
            currentPage++;
    }

    public static void previousPage()
    {
        if (currentPage > 0)
            currentPage--;
    }

    @Override
    public void onMouseClick(int mx, int my, int mouseButton)
    {
        previousPageWidget.onMouseClick(mx, my, mouseButton);
        nextPageWidget.onMouseClick(mx, my, mouseButton);

        for (Widget child : getVisiblePageElements())
            child.onMouseClick(mx, my, mouseButton);
    }

    @Override
    public void render(DrawContext context)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;

        if (pageCount > 1)
        {
            String pageText = String.format("Page %d / %d", currentPage + 1, pageCount);
            context.drawText(textRenderer, pageText, x, y - 30, Colors.LIGHT_GRAY, false);
        }

        if (currentPage > 0)
            previousPageWidget.render(context);
        if (currentPage < pageCount - 1)
            nextPageWidget.render(context);

        int elementY = y;
        for (Widget child : getVisiblePageElements())
        {
            child.setX(this.x);
            child.setY(elementY);
            child.setWidth(this.width);
            child.render(context);
            elementY += child.getHeight();
        }
    }

    public static PanelWidget getInstance()
    {
        return instance;
    }

    private static List<Widget> getVisiblePageElements()
    {
        int start = currentPage * elementCount;
        int end = Math.min(start + elementCount, clusterList.size());
        return clusterList.subList(start, end);
    }
}
