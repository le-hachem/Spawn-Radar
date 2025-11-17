package cc.hachem.core;

import java.util.*;
import java.util.stream.Collectors;

import cc.hachem.RadarClient;
import cc.hachem.config.ConfigManager;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;

public record SpawnerCluster(int id, List<BlockPos> spawners, List<BlockPos> intersectionRegion)
{
    public enum SortType
    {
        NO_SORT("option.spawn_radar.sort_type.none"),
        BY_PROXIMITY("option.spawn_radar.sort_type.proximity"),
        BY_SIZE("option.spawn_radar.sort_type.size");

        private final String name;
        SortType(String name) { this.name = name; }
        public String toString() { return name; }
    }

    public static boolean spheresIntersect(BlockPos a, BlockPos b, double activationRadius)
    {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        double distanceSq = dx * dx + dy * dy + dz * dz;
        double combinedRadius = activationRadius * 2;
        return distanceSq <= combinedRadius * combinedRadius;
    }

    private static double distanceSquared(BlockPos pos, double px, double py, double pz)
    {
        double dx = pos.getX() - px;
        double dy = pos.getY() - py;
        double dz = pos.getZ() - pz;
        return dx * dx + dy * dy + dz * dz;
    }

    private static boolean inSphere(BlockPos center, BlockPos pos, double radius)
    {
        return distanceSquared(center, pos.getX(), pos.getY(), pos.getZ()) <= radius * radius;
    }

    private static List<BlockPos> generateSphere(BlockPos center, double radius)
    {
        List<BlockPos> volume = new ArrayList<>();
        int r = (int) Math.ceil(radius);
        for (int x = center.getX() - r; x <= center.getX() + r; x++)
            for (int y = center.getY() - r; y <= center.getY() + r; y++)
                for (int z = center.getZ() - r; z <= center.getZ() + r; z++)
                    if (inSphere(center, new BlockPos(x, y, z), radius))
                        volume.add(new BlockPos(x, y, z));
        return volume;
    }

    private static List<BlockPos> computeIntersectionRegion(BlockPos other, List<BlockPos> existingVolume, double radius)
    {
        List<BlockPos> result = new ArrayList<>();
        for (BlockPos pos : existingVolume)
            if (inSphere(other, pos, radius))
                result.add(pos);
        return result;
    }

    public static List<SpawnerCluster> filterSubsets(List<SpawnerCluster> clusters)
    {
        clusters.sort((c1, c2) -> Integer.compare(c2.spawners().size(), c1.spawners().size()));
        List<SpawnerCluster> filtered = new ArrayList<>();
        for (int i = 0; i < clusters.size(); i++)
        {
            SpawnerCluster current = clusters.get(i);
            boolean isSubset = false;

            for (int j = 0; j < clusters.size(); j++)
            {
                if (i == j)
                    continue;
                SpawnerCluster other = clusters.get(j);
                if (other.spawners().size() <= current.spawners().size())
                    continue;
                if (new HashSet<>(other.spawners()).containsAll(current.spawners()))
                {
                    isSubset = true;
                    break;
                }
            }

            if (!isSubset)
                filtered.add(current);
        }

        RadarClient.LOGGER.debug("Filtered clusters, {} remain after removing strict subsets.", filtered.size());
        return filtered;
    }

    public static void sortClustersByProximity(ClientPlayerEntity player, List<SpawnerCluster> clusters)
    {
        double px = player.getX(), py = player.getY(), pz = player.getZ();

        for (int i = 0; i < clusters.size(); i++)
        {
            SpawnerCluster cluster = clusters.get(i);
            List<BlockPos> sortedSpawners = cluster.spawners().stream()
                .sorted(Comparator.comparingDouble(pos -> distanceSquared(pos, px, py, pz)))
                .collect(Collectors.toList());

            List<BlockPos> sortedIntersection = cluster.intersectionRegion().stream()
                .sorted(Comparator.comparingDouble(pos -> distanceSquared(pos, px, py, pz)))
                .collect(Collectors.toList());

            clusters.set(i, new SpawnerCluster(cluster.id(), sortedSpawners, sortedIntersection));
        }

        clusters.sort(Comparator.comparingDouble(c -> distanceSquared(c.spawners().getFirst(), px, py, pz)));
        if (RadarClient.config.clusterProximitySortOrder == ConfigManager.SortOrder.ASCENDING)
            Collections.reverse(clusters);

        RadarClient.LOGGER.debug("Clusters sorted by proximity to player at ({}, {}, {}).", px, py, pz);
    }

    public static void sortClustersBySize(List<SpawnerCluster> clusters)
    {
        clusters.sort(Comparator.comparingInt(a -> a.spawners().size()));
        if (RadarClient.config.clusterSizeSortOrder == ConfigManager.SortOrder.ASCENDING)
            Collections.reverse(clusters);

        RadarClient.LOGGER.debug("Clusters sorted by size.");
    }

    public static List<SpawnerCluster> findClusters(ClientPlayerEntity player, List<BlockPos> spawners, double activationRadius, SortType sortType)
    {
        long startTime = System.nanoTime();
        List<SpawnerCluster> clusters = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int nextId = 1;

        for (BlockPos center : spawners)
        {
            ClusterCandidate candidate = buildCluster(center, spawners, activationRadius);
            if (candidate == null)
                continue;

            String key = clusterKey(candidate.members());
            if (!seen.add(key))
                continue;

            clusters.add(new SpawnerCluster(nextId++, candidate.members(), candidate.intersection()));
        }

        nextId = appendSingletonClusters(spawners, clusters, nextId, activationRadius);
        clusters = filterSubsets(clusters);

        long endTime = System.nanoTime();
        RadarClient.LOGGER.info("Radial incremental clustering finished: {} clusters, took {} s", clusters.size(),
            (endTime - startTime) / 1_000_000_000.0);

        switch (sortType)
        {
            case BY_PROXIMITY -> sortClustersByProximity(player, clusters);
            case BY_SIZE -> sortClustersBySize(clusters);
            case NO_SORT -> {}
        }

        return clusters;
    }

    @NotNull
    public String toString()
    {
        StringBuilder sb = new StringBuilder("SpawnerCluster #" + id + " [");
        for (BlockPos pos : spawners)
            sb.append(String.format("(%d,%d,%d), ", pos.getX(), pos.getY(), pos.getZ()));
        if (!spawners.isEmpty())
            sb.setLength(sb.length() - 2);
        sb.append("]");
        return sb.toString();
    }

    private static ClusterCandidate buildCluster(BlockPos center, List<BlockPos> spawners, double activationRadius)
    {
        List<BlockPos> orderedNeighbors = sortedNeighbors(center, spawners);

        List<BlockPos> cluster = new ArrayList<>();
        cluster.add(center);
        List<BlockPos> intersection = generateSphere(center, activationRadius);

        for (BlockPos other : orderedNeighbors)
        {
            if (!spheresIntersect(center, other, activationRadius))
                break;

            List<BlockPos> newIntersection = computeIntersectionRegion(other, intersection, activationRadius);
            if (newIntersection.isEmpty())
                break;

            cluster.add(other);
            intersection = newIntersection;
        }

        if (cluster.size() <= 1)
            return null;

        cluster.sort(Comparator.comparingInt(BlockPos::getX)
                          .thenComparingInt(BlockPos::getY)
                          .thenComparingInt(BlockPos::getZ));

        return new ClusterCandidate(cluster, intersection);
    }

    private static List<BlockPos> sortedNeighbors(BlockPos center, List<BlockPos> spawners)
    {
        List<BlockPos> others = new ArrayList<>(spawners);
        others.remove(center);

        final long cx = center.getX();
        final long cy = center.getY();
        final long cz = center.getZ();

        others.sort(Comparator.comparingLong(pos ->
        {
            long dx = pos.getX() - cx;
            long dy = pos.getY() - cy;
            long dz = pos.getZ() - cz;
            return dx * dx + dy * dy + dz * dz;
        }));

        return others;
    }

    private static int appendSingletonClusters(List<BlockPos> spawners, List<SpawnerCluster> clusters, int nextId, double activationRadius)
    {
        for (BlockPos spawner : spawners)
        {
            boolean alreadyIncluded = clusters.stream().anyMatch(c -> c.spawners().contains(spawner));
            if (alreadyIncluded)
                continue;

            clusters.add(new SpawnerCluster(nextId++, List.of(spawner), generateSphere(spawner, activationRadius)));
        }
        return nextId;
    }

    private static String clusterKey(List<BlockPos> cluster)
    {
        return cluster.stream()
            .map(pos -> pos.getX() + "," + pos.getY() + "," + pos.getZ())
            .collect(Collectors.joining(";"));
    }

    private record ClusterCandidate(List<BlockPos> members, List<BlockPos> intersection) {}
}
