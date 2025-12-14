package cc.hachem.spawnradar.core;

import java.util.*;
import java.util.stream.Collectors;import net.minecraft.client.player.LocalPlayer;import net.minecraft.core.BlockPos;import cc.hachem.spawnradar.RadarClient;
import cc.hachem.spawnradar.config.ConfigManager;import org.jetbrains.annotations.NotNull;

public record SpawnerCluster(int id, List<SpawnerInfo> spawners, List<BlockPos> intersectionRegion)
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
            Set<BlockPos> currentPositions = current.spawners().stream()
                .map(SpawnerInfo::pos)
                .collect(Collectors.toSet());

            for (int j = 0; j < clusters.size(); j++)
            {
                if (i == j)
                    continue;
                SpawnerCluster other = clusters.get(j);
                if (other.spawners().size() <= current.spawners().size())
                    continue;
                Set<BlockPos> otherPositions = other.spawners().stream()
                    .map(SpawnerInfo::pos)
                    .collect(Collectors.toSet());
                if (otherPositions.containsAll(currentPositions))
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

    public static void sortClustersByProximity(LocalPlayer player, List<SpawnerCluster> clusters)
    {
        double px = player.getX(), py = player.getY(), pz = player.getZ();

        for (int i = 0; i < clusters.size(); i++)
        {
            SpawnerCluster cluster = clusters.get(i);
            List<SpawnerInfo> sortedSpawners = cluster.spawners().stream()
                .sorted(Comparator.comparingDouble(info -> distanceSquared(info.pos(), px, py, pz)))
                .collect(Collectors.toList());

            List<BlockPos> sortedIntersection = cluster.intersectionRegion().stream()
                .sorted(Comparator.comparingDouble(pos -> distanceSquared(pos, px, py, pz)))
                .collect(Collectors.toList());

            clusters.set(i, new SpawnerCluster(cluster.id(), sortedSpawners, sortedIntersection));
        }

        clusters.sort(Comparator.comparingDouble(c -> distanceSquared(c.spawners().getFirst().pos(), px, py, pz)));
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

    public static List<SpawnerCluster> findClusters(LocalPlayer player, List<SpawnerInfo> spawners, double activationRadius, SortType sortType)
    {
        long startTime = System.nanoTime();
        List<SpawnerCluster> clusters = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int nextId = 1;

        for (SpawnerInfo center : spawners)
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
        for (SpawnerInfo info : spawners)
        {
            BlockPos pos = info.pos();
            sb.append(String.format("(%d,%d,%d), ", pos.getX(), pos.getY(), pos.getZ()));
        }
        if (!spawners.isEmpty())
            sb.setLength(sb.length() - 2);
        sb.append("]");
        return sb.toString();
    }

    private static ClusterCandidate buildCluster(SpawnerInfo center, List<SpawnerInfo> spawners, double activationRadius)
    {
        List<SpawnerInfo> orderedNeighbors = sortedNeighbors(center, spawners);

        List<SpawnerInfo> cluster = new ArrayList<>();
        cluster.add(center);
        List<BlockPos> intersection = generateSphere(center.pos(), activationRadius);

        for (SpawnerInfo other : orderedNeighbors)
        {
            BlockPos otherPos = other.pos();
            if (!spheresIntersect(center.pos(), otherPos, activationRadius))
                break;

            List<BlockPos> newIntersection = computeIntersectionRegion(otherPos, intersection, activationRadius);
            if (newIntersection.isEmpty())
                break;

            cluster.add(other);
            intersection = newIntersection;
        }

        if (cluster.size() <= 1)
            return null;

        cluster.sort(Comparator.<SpawnerInfo>comparingInt(info -> info.pos().getX())
                          .thenComparingInt(info -> info.pos().getY())
                          .thenComparingInt(info -> info.pos().getZ()));

        return new ClusterCandidate(cluster, intersection);
    }

    private static List<SpawnerInfo> sortedNeighbors(SpawnerInfo center, List<SpawnerInfo> spawners)
    {
        List<SpawnerInfo> others = new ArrayList<>(spawners);
        others.remove(center);

        final long cx = center.pos().getX();
        final long cy = center.pos().getY();
        final long cz = center.pos().getZ();

        others.sort(Comparator.comparingLong(info ->
        {
            BlockPos pos = info.pos();
            long dx = pos.getX() - cx;
            long dy = pos.getY() - cy;
            long dz = pos.getZ() - cz;
            return dx * dx + dy * dy + dz * dz;
        }));

        return others;
    }

    private static int appendSingletonClusters(List<SpawnerInfo> spawners, List<SpawnerCluster> clusters, int nextId, double activationRadius)
    {
        for (SpawnerInfo spawner : spawners)
        {
            boolean alreadyIncluded = clusters.stream()
                .anyMatch(c -> c.spawners().stream().anyMatch(info -> info.pos().equals(spawner.pos())));
            if (alreadyIncluded)
                continue;

            clusters.add(new SpawnerCluster(nextId++, List.of(spawner), generateSphere(spawner.pos(), activationRadius)));
        }
        return nextId;
    }

    private static String clusterKey(List<SpawnerInfo> cluster)
    {
        return cluster.stream()
            .map(info -> info.pos().getX() + "," + info.pos().getY() + "," + info.pos().getZ())
            .collect(Collectors.joining(";"));
    }

    private record ClusterCandidate(List<SpawnerInfo> members, List<BlockPos> intersection) {}
}
