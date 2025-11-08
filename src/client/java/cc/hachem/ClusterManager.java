package cc.hachem;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class ClusterManager
{
    private static final List<BlockPos> activeHighlights = new ArrayList<>();
    private static List<SpawnerCluster> clusters = new ArrayList<>();
    private static int highlightedClusterId = -1;

    public static void setClusters(List<SpawnerCluster> list)
    {
        clusters = list;
    }

    public static List<SpawnerCluster> getClusters()
    {
        return clusters;
    }

    public static void highlightCluster(int clusterId)
    {
        if (highlightedClusterId == clusterId)
        {
            clearHighlights();
            highlightedClusterId = -1;
        } else
        {
            highlightedClusterId = clusterId;
            activeHighlights.clear();
            SpawnerCluster cluster = clusters.get(clusterId);
            activeHighlights.addAll(cluster.spawners());
        }
    }

    public static void clearHighlights()
    {
        activeHighlights.clear();
        highlightedClusterId = -1;
    }

    public static List<BlockPos> getHighlights()
    {
        return activeHighlights.isEmpty() ? BlockBank.getAll() : activeHighlights;
    }

    public static List<BlockPos> getHighlightedIntersectionRegion()
    {
        if (highlightedClusterId < 0 || highlightedClusterId >= clusters.size())
            return List.of();
        return clusters.get(highlightedClusterId).intersectionRegion();
    }
}
