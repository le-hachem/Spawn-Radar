package cc.hachem.spawnradar.core;

import cc.hachem.spawnradar.RadarClient;
import net.minecraft.util.math.BlockPos;

import java.util.*;

public class ClusterManager
{
    private static final Set<Integer> highlightedClusterIds = new HashSet<>();
    private static List<SpawnerCluster> clusters = new ArrayList<>();
    private static final Set<Long> backgroundHighlightSpawners = Collections.synchronizedSet(new HashSet<>());

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

    public static SpawnerCluster getClusterById(int clusterId)
    {
        for (SpawnerCluster cluster : clusters)
            if (cluster.id() == clusterId)
                return cluster;
        return null;
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
        if (highlightedClusterIds.isEmpty())
            highlightAllClusters();
        else
            unhighlightAllClusters();
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

    public static void highlightCluster(int clusterId)
    {
        if (isValidClusterId(clusterId))
            highlightedClusterIds.add(clusterId);
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
        Set<Long> seen = new HashSet<>();
        List<SpawnerInfo> highlights = new ArrayList<>();
        highlights.addAll(getBackgroundHighlightInfos(seen));

        if (!highlightedClusterIds.isEmpty())
        {
            for (SpawnerCluster c : clusters)
                if (highlightedClusterIds.contains(c.id()))
                    addUnique(c.spawners(), highlights, seen);
            return highlights;
        }

        if (BlockBank.hasManualResults())
            addUnique(BlockBank.getAll(), highlights, seen);

        return highlights;
    }

    public static void addBackgroundHighlights(List<SpawnerInfo> spawners)
    {
        if (spawners == null || spawners.isEmpty() || !isBackgroundHighlightingEnabled())
            return;
        for (SpawnerInfo info : spawners)
            backgroundHighlightSpawners.add(info.pos().asLong());
    }

    public static void removeBackgroundHighlights(Set<Long> positions)
    {
        if (positions == null || positions.isEmpty())
            return;
        backgroundHighlightSpawners.removeIf(positions::contains);
    }

    public static void clearBackgroundHighlights()
    {
        backgroundHighlightSpawners.clear();
    }

    private static boolean isValidClusterId(int clusterId)
    {
        return clusters.stream().anyMatch(c -> c.id() == clusterId);
    }

    public static boolean isHighlighted(int clusterId)
    {
        return highlightedClusterIds.contains(clusterId);
    }

    private static List<SpawnerInfo> getBackgroundHighlightInfos(Set<Long> seen)
    {
        if (!isBackgroundHighlightingEnabled())
        {
            if (!backgroundHighlightSpawners.isEmpty())
                backgroundHighlightSpawners.clear();
            return Collections.emptyList();
        }

        List<SpawnerInfo> highlights = new ArrayList<>();
        synchronized (backgroundHighlightSpawners)
        {
            backgroundHighlightSpawners.removeIf(packed ->
            {
                SpawnerInfo info = BlockBank.get(BlockPos.fromLong(packed));
                if (info == null)
                    return true;
                if (seen.add(packed))
                    highlights.add(info);
                return false;
            });
        }

        return highlights;
    }

    private static void addUnique(List<SpawnerInfo> source, List<SpawnerInfo> target, Set<Long> seen)
    {
        for (SpawnerInfo info : source)
        {
            long key = info.pos().asLong();
            if (seen.add(key))
                target.add(info);
        }
    }

    private static boolean isBackgroundHighlightingEnabled()
    {
        return RadarClient.config != null && RadarClient.config.autoHighlightAlertedClusters;
    }
}
