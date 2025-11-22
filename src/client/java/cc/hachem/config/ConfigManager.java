package cc.hachem.config;

import cc.hachem.RadarClient;
import cc.hachem.core.SpawnerCluster;

import java.util.ArrayList;
import java.util.List;

public class ConfigManager
{
    private static final List<Integer> DEFAULT_CLUSTER_COLORS = List.of(
        0x00FFFF,
        0x00FF00,
        0xFFFF00,
        0xFF0000,
        0xFF00FF
    );

    public static final ConfigManager DEFAULT = new ConfigManager();

    public enum SortOrder
    {
        ASCENDING("option.spawn_radar.sort_order.ascending"),
        DESCENDING("option.spawn_radar.sort_order.descending");

        private final String name;
        SortOrder(String name) { this.name = name; }
        public String toString() { return name; }
    }

    public enum HudHorizontalAlignment
    {
        LEFT("option.spawn_radar.hud_alignment.left"),
        RIGHT("option.spawn_radar.hud_alignment.right");

        private final String name;
        HudHorizontalAlignment(String name) { this.name = name; }
        public String toString() { return name; }
    }

    public SpawnerCluster.SortType defaultSortType = SpawnerCluster.SortType.NO_SORT;
    public SortOrder clusterProximitySortOrder = SortOrder.ASCENDING;
    public SortOrder clusterSizeSortOrder = SortOrder.DESCENDING;

    public int spawnerHighlightColor = 0xFFFFFF;
    public int minimumSpawnersForRegion = 1;
    public int defaultSearchRadius = 64;

    public int spawnerHighlightOpacity = 50;
    public int regionHighlightOpacity = 30;
    public boolean useOutlineSpawnerHighlight = false;
    public int spawnerOutlineColor = 0xFFFFFF;
    public float spawnerOutlineThickness = 0.5f;

    public boolean highlightAfterScan = false;
    public boolean frustumCullingEnabled = true;

    public double verticalPanelOffset = 0.1;
    public int panelElementCount = 5;
    public HudHorizontalAlignment panelHorizontalAlignment = HudHorizontalAlignment.LEFT;

    public List<Integer> clusterColors = defaultClusterColors();

    public void normalize()
    {
        ensureColorPalette();
        ensureHudAlignment();
    }

    public void ensureColorPalette()
    {
        if (clusterColors == null)
        {
            resetClusterColors("missing");
            return;
        }

        clusterColors.removeIf(java.util.Objects::isNull);

        if (clusterColors.isEmpty())
        {
            resetClusterColors("empty");
            return;
        }

        int originalSize = clusterColors.size();
        while (clusterColors.size() < DEFAULT_CLUSTER_COLORS.size())
            clusterColors.add(DEFAULT_CLUSTER_COLORS.get(clusterColors.size()));

        if (clusterColors.size() != originalSize)
            RadarClient.LOGGER.warn("Cluster color palette had {} entries; padded to {}", originalSize, clusterColors.size());
    }

    private void resetClusterColors(String reason)
    {
        clusterColors = defaultClusterColors();
        RadarClient.LOGGER.warn("Cluster color palette {}; restored defaults.", reason);
    }

    private static List<Integer> defaultClusterColors()
    {
        return new ArrayList<>(DEFAULT_CLUSTER_COLORS);
    }

    public static int getClusterColor(int spawnerCount)
    {
        if (spawnerCount <= 0)
            return 0xFFFFFF;
        RadarClient.config.ensureColorPalette();

        if (spawnerCount <= RadarClient.config.clusterColors.size())
            return RadarClient.config.clusterColors.get(Math.max(0, spawnerCount - 1));
        return RadarClient.config.clusterColors.getLast();
    }

    public void ensureHudAlignment()
    {
        if (panelHorizontalAlignment == null)
            panelHorizontalAlignment = DEFAULT.panelHorizontalAlignment;
    }
}
