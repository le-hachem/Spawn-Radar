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
        ASCENDING("Ascending"),
        DESCENDING("Descending");

        private final String name;
        SortOrder(String name) { this.name = name; }
        public String getName() { return name; }
    }

    public SpawnerCluster.SortType defaultSortType = SpawnerCluster.SortType.NO_SORT;
    public SortOrder clusterProximitySortOrder = SortOrder.DESCENDING;
    public SortOrder clusterSizeSortOrder = SortOrder.ASCENDING;

    public int spawnerHighlightColor = 0xFFFFFF;
    public int minimumSpawnersForRegion = 1;
    public int defaultSearchRadius = 64;

    public int spawnerHighlightOpacity = 50;
    public int regionHighlightOpacity = 30;

    public List<Integer> clusterColors = new ArrayList<>(
        List.of(0x00FFFF,
                0x00FF00,
                0xFFFF00,
                0xFF0000,
                0xFF00FF));

    public static int getClusterColor(int spawnerCount)
    {
        if (spawnerCount <= 0)
            return 0xFFFFFF;
        if (spawnerCount <= RadarClient.config.clusterColors.size())
            return RadarClient.config.clusterColors.get(spawnerCount - 1);
        return RadarClient.config.clusterColors.getLast();
    }
}
