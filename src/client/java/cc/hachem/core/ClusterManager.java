package cc.hachem.core;

import net.minecraft.util.math.BlockPos;
import java.util.ArrayList;
import java.util.List;

public class ClusterManager
{
    private static final List<BlockPos> activeHighlights = new ArrayList<>();
    private static List<SpawnerCluster> clusters = new ArrayList<>();
    private static int highlightedClusterId = -1;

    public static List<Integer> getClusterIDAt(BlockPos pos)
    {
        List<Integer> ids = new ArrayList<>();
        List<SpawnerCluster> clusters = ClusterManager.getClusters();
        for (int i = 0; i < clusters.size(); i++)
            if (clusters.get(i).spawners().contains(pos))
                ids.add(i);
        return ids;
    }

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

    public static void highlightAllClusters()
    {
        activeHighlights.clear();
        for (SpawnerCluster cluster : clusters)
            activeHighlights.addAll(cluster.spawners());
        highlightedClusterId = -1;
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

    public static List<List<BlockPos>> getHighlightedIntersectionRegions()
    {
        List<List<BlockPos>> intersections = new ArrayList<>();

        if (highlightedClusterId >= 0 && highlightedClusterId < clusters.size())
            intersections.add(clusters.get(highlightedClusterId).intersectionRegion());
        else if (!activeHighlights.isEmpty())
            for (SpawnerCluster cluster : clusters)
                intersections.add(cluster.intersectionRegion());

        return intersections;
    }
}
