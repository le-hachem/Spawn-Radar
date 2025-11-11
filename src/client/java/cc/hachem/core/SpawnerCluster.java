package cc.hachem.core;

import java.util.*;
import java.util.stream.Collectors;

import cc.hachem.RadarClient;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public record SpawnerCluster(List<BlockPos> spawners, List<BlockPos> intersectionRegion)
{
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

    private static void sortClusterSpawnersByProximity(SpawnerCluster cluster, double px, double py, double pz, List<SpawnerCluster> clusters)
    {
        List<BlockPos> sortedSpawners = cluster.spawners().stream()
                                            .sorted(Comparator.comparingDouble(pos -> distanceSquared(pos, px, py, pz)))
                                            .collect(Collectors.toList());
        List<BlockPos> sortedIntersection = cluster.intersectionRegion().stream()
                                                .sorted(Comparator.comparingDouble(pos -> distanceSquared(pos, px, py, pz)))
                                                .collect(Collectors.toList());
        clusters.set(clusters.indexOf(cluster), new SpawnerCluster(sortedSpawners, sortedIntersection));
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

    private static List<BlockPos> computeIntersectionRegionWithExistingVolume(BlockPos other, List<BlockPos> existingVolume, double radius)
    {
        List<BlockPos> result = new ArrayList<>();
        for (BlockPos pos : existingVolume)
            if (inSphere(other, pos, radius))
                result.add(pos);
        return result;
    }

    public static List<SpawnerCluster> filterStrictSubsets(List<SpawnerCluster> clusters)
    {
        clusters.sort((c1, c2) -> Integer.compare(c2.spawners().size(), c1.spawners().size()));
        List<SpawnerCluster> filtered = new ArrayList<>();
        for (int i = 0; i < clusters.size(); i++)
        {
            SpawnerCluster current = clusters.get(i);
            boolean isSubset = false;
            for (int j = 0; j < clusters.size(); j++)
            {
                if (i == j) continue;
                SpawnerCluster other = clusters.get(j);
                if (other.spawners().size() <= current.spawners().size()) continue;
                if (new HashSet<>(other.spawners()).containsAll(current.spawners()))
                {
                    isSubset = true;
                    break;
                }
            }
            if (!isSubset) filtered.add(current);
        }
        return filtered;
    }

    public static void sortClustersByPlayerProximity(ClientPlayerEntity player, List<SpawnerCluster> clusters)
    {
        double px = player.getX(), py = player.getY(), pz = player.getZ();
        for (SpawnerCluster cluster : clusters)
            sortClusterSpawnersByProximity(cluster, px, py, pz, clusters);
        clusters.sort(Comparator.comparingDouble(c -> distanceSquared(c.spawners().getFirst(), px, py, pz)));
    }

    public static List<SpawnerCluster> findClusters(FabricClientCommandSource source, List<BlockPos> spawners, double activationRadius)
    {
        source.sendFeedback(Text.of("Generating radial clusters with incremental intersection check..."));
        long startTime = System.nanoTime();

        List<SpawnerCluster> clusters = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int n = spawners.size();

        for (int idx = 0; idx < n; idx++)
        {
            BlockPos center = spawners.get(idx);

            List<BlockPos> others = new ArrayList<>(spawners);
            others.remove(center);

            final long cx = center.getX(), cy = center.getY(), cz = center.getZ();
            others.sort(Comparator.comparingLong(p -> {
                long dx = p.getX() - cx, dy = p.getY() - cy, dz = p.getZ() - cz;
                return dx * dx + dy * dy + dz * dz;
            }));

            List<BlockPos> cluster = new ArrayList<>();
            cluster.add(center);
            List<BlockPos> currentIntersection = generateSphere(center, activationRadius);

            for (BlockPos other : others)
            {
                if (!spheresIntersect(center, other, activationRadius))
                    break;

                List<BlockPos> newIntersection = computeIntersectionRegionWithExistingVolume(other, currentIntersection, activationRadius);
                if (newIntersection.isEmpty()) break;

                cluster.add(other);
                currentIntersection = newIntersection;
            }

            if (cluster.size() <= 1) continue;

            cluster.sort(Comparator.comparingInt(BlockPos::getX)
                             .thenComparingInt(BlockPos::getY)
                             .thenComparingInt(BlockPos::getZ));

            String key = cluster.stream().map(p -> p.getX() + "," + p.getY() + "," + p.getZ())
                             .collect(Collectors.joining(";"));
            if (seen.contains(key)) continue;
            seen.add(key);

            clusters.add(new SpawnerCluster(cluster, currentIntersection));
        }

        for (BlockPos spawner : spawners)
            if (clusters.stream().noneMatch(c -> c.spawners().contains(spawner)))
                clusters.add(new SpawnerCluster(List.of(spawner), generateSphere(spawner, activationRadius)));

        clusters = filterStrictSubsets(clusters);

        long endTime = System.nanoTime();
        RadarClient.LOGGER.info("Radial incremental clustering finished: {} clusters, took {} s", clusters.size(),
            (endTime - startTime) / 1_000_000_000.0);

        return clusters;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder("SpawnerCluster[");
        for (BlockPos pos : spawners) sb.append(String.format("(%d,%d,%d), ", pos.getX(), pos.getY(), pos.getZ()));
        if (!spawners.isEmpty()) sb.setLength(sb.length() - 2);
        sb.append("]");
        return sb.toString();
    }
}