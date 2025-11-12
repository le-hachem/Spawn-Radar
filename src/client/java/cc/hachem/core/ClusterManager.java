package cc.hachem.core;

import cc.hachem.RadarClient;
import net.minecraft.util.math.BlockPos;

import java.util.*;

public class ClusterManager
{
    private static final Set<HighlightedCluster> highlightedClusters = new HashSet<>();
    private static List<SpawnerCluster> clusters = new ArrayList<>();

    public record HighlightedCluster(int id, SpawnerCluster cluster)
    {
        @Override
        public boolean equals(Object o)
        {
            if (this == o)
                return true;
            if (!(o instanceof HighlightedCluster other))
                return false;
            return id == other.id;
        }

        @Override
        public int hashCode()
        {
            return Integer.hashCode(id);
        }
    }

    public static void setClusters(List<SpawnerCluster> list)
    {
        clusters = list;
        highlightedClusters.clear();
        RadarClient.LOGGER.debug("Set {} clusters.", list.size());
    }

    public static List<SpawnerCluster> getClusters()
    {
        return clusters;
    }

    public static List<Integer> getClusterIDAt(BlockPos pos)
    {
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < clusters.size(); i++)
            if (clusters.get(i).spawners().contains(pos)) ids.add(i);
        return ids;
    }

    public static void toggleHighlightCluster(int clusterId)
    {
        if (!isValidClusterId(clusterId))
            return;

        HighlightedCluster hc = new HighlightedCluster(clusterId, clusters.get(clusterId));
        if (highlightedClusters.contains(hc))
        {
            highlightedClusters.remove(hc);
            RadarClient.LOGGER.info("Un-highlighted cluster #{}.", clusterId + 1);
        } else
        {
            highlightedClusters.add(hc);
            RadarClient.LOGGER.info("Highlighted cluster #{}.", clusterId + 1);
        }
    }

    public static void highlightAllClusters()
    {
        highlightedClusters.clear();
        for (int i = 0; i < clusters.size(); i++)
            highlightedClusters.add(new HighlightedCluster(i, clusters.get(i)));
        RadarClient.LOGGER.info("Highlighted all {} clusters.", clusters.size());
    }

    public static void unhighlightAllClusters()
    {
        highlightedClusters.clear();
        RadarClient.LOGGER.info("Un-highlighted all clusters.");
    }

    public static Set<HighlightedCluster> getHighlightedClusters()
    {
        return new HashSet<>(highlightedClusters);
    }

    public static List<BlockPos> getHighlights()
    {
        if (highlightedClusters.isEmpty())
            return BlockBank.getAll();

        List<BlockPos> activeHighlights = new ArrayList<>();
        for (HighlightedCluster hc : highlightedClusters)
            activeHighlights.addAll(hc.cluster.spawners());
        return activeHighlights;
    }

    private static boolean isValidClusterId(int clusterId)
    {
        return clusterId >= 0 && clusterId < clusters.size();
    }
}
