package cc.hachem.core;

import cc.hachem.RadarClient;
import net.minecraft.util.math.BlockPos;
import java.util.*;

public class ClusterManager
{
    private static final Set<HighlightedCluster> highlightedClusters = new HashSet<>();
    private static final List<BlockPos> activeHighlights = new ArrayList<>();
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
            if (clusters.get(i).spawners().contains(pos))
                ids.add(i);
        return ids;
    }

    public static void toggleHighlightCluster(int clusterId)
    {
        if (clusterId < 0 || clusterId >= clusters.size())
            return;

        HighlightedCluster hc = new HighlightedCluster(clusterId, clusters.get(clusterId));
        if (highlightedClusters.contains(hc))
        {
            highlightedClusters.remove(hc);
            RadarClient.LOGGER.info("Un-highlighted cluster #{}.", clusterId + 1);
        }
        else
        {
            highlightedClusters.add(hc);
            RadarClient.LOGGER.info("Highlighted cluster #{}.", clusterId + 1);
        }

        updateActiveHighlights();
    }

    public static void highlightAllClusters()
    {
        highlightedClusters.clear();
        for (int i = 0; i < clusters.size(); i++)
            highlightedClusters.add(new HighlightedCluster(i, clusters.get(i)));

        updateActiveHighlights();
        RadarClient.LOGGER.info("Highlighted all {} clusters.", clusters.size());
    }

    public static void clearHighlights()
    {
        highlightedClusters.clear();
        updateActiveHighlights();
        RadarClient.LOGGER.info("Cleared all highlighted clusters.");
    }

    private static void updateActiveHighlights()
    {
        activeHighlights.clear();
        for (HighlightedCluster hc : highlightedClusters)
            activeHighlights.addAll(hc.cluster.spawners());

        RadarClient.LOGGER.debug("Active highlights updated: {} blocks highlighted.", activeHighlights.size());
    }

    public static List<BlockPos> getHighlights()
    {
        return activeHighlights.isEmpty() ? BlockBank.getAll() : activeHighlights;
    }

    public static Set<HighlightedCluster> getHighlightedClusters()
    {
        return new HashSet<>(highlightedClusters);
    }
}
