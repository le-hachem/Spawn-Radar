package cc.hachem.core;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import cc.hachem.RadarClient;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public record SpawnerCluster(List<BlockPos> spawners, List<BlockPos> intersectionRegion)
{
    private static final int MAX_SUBSET_SIZE = 4;

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

    private static List<BlockPos> computeIntersectionRegion(List<BlockPos> cluster, double radius)
    {
        List<BlockPos> intersection = new ArrayList<>();
        if (cluster.isEmpty()) return intersection;

        int minX = cluster.stream().mapToInt(BlockPos::getX).min().getAsInt();
        int minY = cluster.stream().mapToInt(BlockPos::getY).min().getAsInt();
        int minZ = cluster.stream().mapToInt(BlockPos::getZ).min().getAsInt();
        int maxX = cluster.stream().mapToInt(BlockPos::getX).max().getAsInt();
        int maxY = cluster.stream().mapToInt(BlockPos::getY).max().getAsInt();
        int maxZ = cluster.stream().mapToInt(BlockPos::getZ).max().getAsInt();

        minX -= (int) radius; minY -= (int) radius; minZ -= (int) radius;
        maxX += (int) radius; maxY += (int) radius; maxZ += (int) radius;

        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++)
                {
                    BlockPos pos = new BlockPos(x, y, z);
                    boolean insideAll = true;
                    for (BlockPos s : cluster)
                        if (!inSphere(s, pos, radius))
                        {
                            insideAll = false;
                            break;
                        }
                    if (insideAll) intersection.add(pos);
                }

        return intersection;
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
        clusters.sort(Comparator.comparingDouble(c -> distanceSquared(c.spawners().get(0), px, py, pz)));
    }

    public static List<SpawnerCluster> findClustersExhaustive(FabricClientCommandSource source, List<BlockPos> spawners, double activationRadius)
    {
        source.sendFeedback(Text.of("Generating clusters (up to size " + MAX_SUBSET_SIZE + "), this might take a minute."));
        long startTime = System.nanoTime();

        int n = spawners.size();
        Set<String> seen = Collections.synchronizedSet(new HashSet<>());
        List<SpawnerCluster> clusters = Collections.synchronizedList(new ArrayList<>());

        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        List<Future<List<SpawnerCluster>>> futures = new ArrayList<>();

        for (int subsetSize = 1; subsetSize <= MAX_SUBSET_SIZE; subsetSize++)
        {
            final int k = subsetSize;
            futures.add(executor.submit(() ->
            {
                List<SpawnerCluster> sizeClusters = new ArrayList<>();
                int logCounter = 0;

                for (int mask = 1; mask < (1 << n); mask++)
                {
                    if (Integer.bitCount(mask) != k) continue;

                    List<BlockPos> subset = new ArrayList<>();
                    for (int i = 0; i < n; i++)
                        if ((mask & (1 << i)) != 0) subset.add(spawners.get(i));

                    subset.sort(Comparator.comparingInt(BlockPos::getX)
                                    .thenComparingInt(BlockPos::getY)
                                    .thenComparingInt(BlockPos::getZ));

                    String key = subset.stream().map(p -> p.getX() + "," + p.getY() + "," + p.getZ())
                                     .collect(Collectors.joining(";"));
                    if (!seen.add(key)) continue;

                    if (++logCounter % 1000 == 0)
                        RadarClient.LOGGER.info("Processed {} subsets of size {} in thread...", logCounter, k);
                    RadarClient.LOGGER.info("Generated subset (size {}): {}", subset.size(), subset);

                    boolean shouldComputeIntersection = subset.size() == 1;
                    if (subset.size() > 1)
                    {
                        outer:
                        for (int i = 0; i < subset.size(); i++)
                            for (int j = i + 1; j < subset.size(); j++)
                                if (spheresIntersect(subset.get(i), subset.get(j), activationRadius))
                                {
                                    shouldComputeIntersection = true;
                                    break outer;
                                }
                        if (!shouldComputeIntersection)
                            RadarClient.LOGGER.info("Skipping intersection for subset {} because no spawners are in range.", subset);
                    }

                    if (shouldComputeIntersection)
                    {
                        List<BlockPos> intersection = computeIntersectionRegion(subset, activationRadius);
                        RadarClient.LOGGER.info("Intersection size: {}", intersection.size());
                        if (!intersection.isEmpty())
                        {
                            RadarClient.LOGGER.info("Adding cluster: {}", subset);
                            sizeClusters.add(new SpawnerCluster(subset, intersection));
                        } else
                            RadarClient.LOGGER.info("Skipping cluster due to empty intersection: {}", subset);
                    }
                }

                return sizeClusters;
            }));
        }

        for (Future<List<SpawnerCluster>> future : futures) try
        {
            clusters.addAll(future.get());
        }
        catch (Exception e)
        {
            RadarClient.LOGGER.error(e.toString());
        }

        executor.shutdown();
        clusters = filterStrictSubsets(clusters);

        for (BlockPos spawner : spawners)
            if (clusters.stream().noneMatch(c -> c.spawners().contains(spawner)))
                clusters.add(new SpawnerCluster(List.of(spawner), List.of(spawner)));

        long endTime = System.nanoTime();
        RadarClient.LOGGER.info("Exhaustive clustering finished: {} clusters, took {} s", clusters.size(),
            (endTime - startTime) / 1_000_000_000.0);

        return clusters;
    }

    public static List<SpawnerCluster> findClustersRadialIncremental(FabricClientCommandSource source, List<BlockPos> spawners, double activationRadius)
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
                clusters.add(new SpawnerCluster(List.of(spawner), List.of(spawner)));

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