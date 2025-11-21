package cc.hachem.core;

import cc.hachem.RadarClient;
import net.minecraft.util.math.BlockPos;

import java.util.*;

public class ClusterManager
{
    private static final Set<Integer> highlightedClusterIds = new HashSet<>();
    private static List<SpawnerCluster> clusters = new ArrayList<>();

    private ClusterManager() {}

    public static void setClusters(List<SpawnerCluster> list)
    {
        clusters = list;
        highlightedClusterIds.clear();
        RadarClient.LOGGER.debug("Set {} clusters.", list.size());
    }

    public static List<SpawnerCluster> getClusters()
    {
        return clusters;
    }

    public static List<Integer> getClusterIDAt(BlockPos pos)
    {
        List<Integer> ids = new ArrayList<>();
        for (SpawnerCluster c : clusters)
            if (c.spawners().stream().anyMatch(info -> info.pos().equals(pos))) ids.add(c.id());
        return ids;
    }

    public static void toggleAllClusters()
    {
        if (highlightedClusterIds.size() == clusters.size())
        {
            unhighlightAllClusters();
            RadarClient.LOGGER.info("Toggled all clusters: now all un-highlighted.");
        }
        else
        {
            highlightAllClusters();
            RadarClient.LOGGER.info("Toggled all clusters: now all highlighted.");
        }
    }

    public static void toggleHighlightCluster(int clusterId)
    {
        if (!isValidClusterId(clusterId))
            return;

        if (highlightedClusterIds.contains(clusterId))
        {
            highlightedClusterIds.remove(clusterId);
            RadarClient.LOGGER.info("Un-highlighted cluster #{}.", clusterId + 1);
        }
        else
        {
            highlightedClusterIds.add(clusterId);
            RadarClient.LOGGER.info("Highlighted cluster #{}.", clusterId + 1);
        }
    }

    public static void highlightAllClusters()
    {
        highlightedClusterIds.clear();
        for (SpawnerCluster c : clusters)
            highlightedClusterIds.add(c.id());
        RadarClient.LOGGER.info("Highlighted all {} clusters.", clusters.size());
    }

    public static void unhighlightAllClusters()
    {
        highlightedClusterIds.clear();
        RadarClient.LOGGER.info("Un-highlighted all clusters.");
    }

    public static Set<Integer> getHighlightedClusterIds()
    {
        return new HashSet<>(highlightedClusterIds);
    }

    public static List<SpawnerInfo> getHighlights()
    {
        if (highlightedClusterIds.isEmpty())
            return BlockBank.getAll();

        List<SpawnerInfo> activeHighlights = new ArrayList<>();
        for (SpawnerCluster c : clusters)
            if (highlightedClusterIds.contains(c.id()))
                activeHighlights.addAll(c.spawners());
        return activeHighlights;
    }

    private static boolean isValidClusterId(int clusterId)
    {
        return clusters.stream().anyMatch(c -> c.id() == clusterId);
    }

    public static boolean isHighlighted(int clusterId)
    {
        return highlightedClusterIds.contains(clusterId);
    }
}
