package cc.hachem.config;

import cc.hachem.RadarClient;
import cc.hachem.core.SpawnerCluster;

import java.util.ArrayList;
import java.util.List;

public class ConfigManager
{
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

    public boolean highlightAfterScan = false;

    public double verticalPanelOffset = 0.1;
    public int panelElementCount = 5;
    public HudHorizontalAlignment panelHorizontalAlignment = HudHorizontalAlignment.LEFT;

    public List<Integer> clusterColors = new ArrayList<>(
        List.of(0x00FFFF,
                0x00FF00,
                0xFFFF00,
                0xFF0000,
                0xFF00FF));

    public void ensureColorPalette()
    {
        if (clusterColors == null)
        {
            clusterColors = new ArrayList<>(DEFAULT.clusterColors);
            RadarClient.LOGGER.warn("Cluster color palette missing, restored defaults.");
            return;
        }

        clusterColors.removeIf(java.util.Objects::isNull);

        if (clusterColors.isEmpty())
        {
            clusterColors.addAll(DEFAULT.clusterColors);
            RadarClient.LOGGER.warn("Cluster color palette empty, restored defaults.");
            return;
        }

        int originalSize = clusterColors.size();
        while (clusterColors.size() < DEFAULT.clusterColors.size())
            clusterColors.add(DEFAULT.clusterColors.get(clusterColors.size()));

        if (clusterColors.size() != originalSize)
            RadarClient.LOGGER.warn("Cluster color palette had {} entries; padded to {}", originalSize, clusterColors.size());
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
