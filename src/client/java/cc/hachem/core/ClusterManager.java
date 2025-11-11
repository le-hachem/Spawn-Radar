package cc.hachem.core;

import net.minecraft.util.math.BlockPos;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ClusterManager
{
    private static final List<BlockPos> activeHighlights = new ArrayList<>();
    private static List<SpawnerCluster> clusters = new ArrayList<>();
    private static final Set<Integer> highlightedClusterIds = new HashSet<>();

    public static List<Integer> getClusterIDAt(BlockPos pos)
    {
        List<Integer> ids = new ArrayList<>();
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

    public static void toggleHighlightCluster(int clusterId)
    {
        if (highlightedClusterIds.contains(clusterId))
            highlightedClusterIds.remove(clusterId);
        else
            highlightedClusterIds.add(clusterId);

        updateActiveHighlights();
    }

    public static void highlightAllClusters()
    {
        highlightedClusterIds.clear();
        for (int i = 0; i < clusters.size(); i++)
            highlightedClusterIds.add(i);

        updateActiveHighlights();
    }

    public static void clearHighlights()
    {
        highlightedClusterIds.clear();
        updateActiveHighlights();
    }

    private static void updateActiveHighlights()
    {
        activeHighlights.clear();
        for (int id : highlightedClusterIds)
            if (id >= 0 && id < clusters.size())
                activeHighlights.addAll(clusters.get(id).spawners());
    }

    public static List<BlockPos> getHighlights()
    {
        return activeHighlights.isEmpty() ? BlockBank.getAll() : activeHighlights;
    }

    public static List<List<BlockPos>> getHighlightedIntersectionRegions()
    {
        List<List<BlockPos>> intersections = new ArrayList<>();
        if (!highlightedClusterIds.isEmpty())
        {
            for (int id : highlightedClusterIds)
                if (id >= 0 && id < clusters.size())
                    intersections.add(clusters.get(id).intersectionRegion());
        }
        else if (!activeHighlights.isEmpty())
            for (SpawnerCluster cluster : clusters)
                intersections.add(cluster.intersectionRegion());

        return intersections;
    }
}
