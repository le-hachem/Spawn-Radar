package cc.hachem.config;

import cc.hachem.core.SpawnerCluster;

import java.util.ArrayList;
import java.util.List;

public class ConfigManager
{
    public enum SortOrder
    {
        ASCENDING("Ascending"),
        DESCENDING("Descending");

        private final String name;
        SortOrder(String name) { this.name = name; }
        public String getName() { return name; }
    }

    public static SpawnerCluster.SortType defaultSortType = SpawnerCluster.SortType.NO_SORT;
    public static SortOrder clusterProximitySortOrder = SortOrder.DESCENDING;
    public static SortOrder clusterSizeSortOrder = SortOrder.ASCENDING;

    public static int spawnerHighlightColor = 0xFFFFFF;
    public static int minimumSpawnersForRegion = 1;
    public static int defaultSearchRadius = 64;

    public static List<Integer> clusterColors = new ArrayList<>();
    static
    {
        clusterColors.add(0x00FFFF); // 1  spawner
        clusterColors.add(0x00FF00); // 2  spawners
        clusterColors.add(0xFFFF00); // 3  spawners
        clusterColors.add(0xFF0000); // 4  spawners
        clusterColors.add(0xFF00FF); // 5+ spawners
    }

    public static int getClusterColor(int spawnerCount)
    {
        if (spawnerCount <= 0)
            return 0xFFFFFF;
        if (spawnerCount <= clusterColors.size())
            return clusterColors.get(spawnerCount - 1);
        return clusterColors.getLast();
    }
}
