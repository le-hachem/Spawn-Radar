package cc.hachem;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
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

    private static boolean inSphere(BlockPos center, BlockPos pos, double radius)
    {
        double dx = center.getX() - pos.getX();
        double dy = center.getY() - pos.getY();
        double dz = center.getZ() - pos.getZ();
        return dx * dx + dy * dy + dz * dz <= radius * radius;
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

        return filtered;
    }

    public static void sortClustersByPlayerProximity(ClientPlayerEntity player, List<SpawnerCluster> clusters)
    {
        final double px = player.getX();
        final double py = player.getY();
        final double pz = player.getZ();

        for (SpawnerCluster cluster : clusters)
        {
            List<BlockPos> sortedSpawners = cluster.spawners().stream().sorted(Comparator.comparingDouble(pos -> distanceSquared(pos, px, py, pz))).collect(Collectors.toList());
            List<BlockPos> sortedIntersection = cluster.intersectionRegion().stream().sorted(Comparator.comparingDouble(pos -> distanceSquared(pos, px, py, pz))).collect(Collectors.toList());

            clusters.set(clusters.indexOf(cluster), new SpawnerCluster(sortedSpawners, sortedIntersection));
        }

        clusters.sort(Comparator.comparingDouble(c -> distanceSquared(c.spawners().getFirst(), px, py, pz)));
    }

    private static double distanceSquared(BlockPos pos, double px, double py, double pz)
    {
        double dx = pos.getX() - px;
        double dy = pos.getY() - py;
        double dz = pos.getZ() - pz;
        return dx * dx + dy * dy + dz * dz;
    }

    public static List<SpawnerCluster> findClustersExhaustive(FabricClientCommandSource source, List<BlockPos> spawners, double activationRadius)
    {
        source.sendFeedback(Text.of("Generating clusters (up to size " + MAX_SUBSET_SIZE + "), this might take a minute."));

        long startTime = System.nanoTime();

        int n = spawners.size();
        Set<String> seen = Collections.synchronizedSet(new HashSet<>());
        List<SpawnerCluster> clusters = Collections.synchronizedList(new ArrayList<>());

        RadarClient.LOGGER.info("Step 1: Generating subsets in parallel (size ≤ {}) and computing intersections...", MAX_SUBSET_SIZE);

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
                    if (Integer.bitCount(mask) != k)
                        continue;

                    List<BlockPos> subset = new ArrayList<>();
                    for (int i = 0; i < n; i++)
                    {
                        if ((mask & (1 << i)) != 0)
                            subset.add(spawners.get(i));
                    }

                    subset.sort((p1, p2) ->
                    {
                        int cmpX = Integer.compare(p1.getX(), p2.getX());
                        if (cmpX != 0)
                            return cmpX;
                        int cmpY = Integer.compare(p1.getY(), p2.getY());
                        return Integer.compare(p1.getZ(), p2.getZ());
                    });

                    String key = subset.stream().map(p -> p.getX() + "," + p.getY() + "," + p.getZ()).collect(Collectors.joining(";"));
                    if (!seen.add(key))
                        continue;

                    if (++logCounter % 1000 == 0)
                        RadarClient.LOGGER.info("Processed {} subsets of size {} in thread...", logCounter, k);
                    RadarClient.LOGGER.info("Generated subset (size {}): {}", subset.size(), subset);

                    boolean shouldComputeIntersection = subset.size() == 1;
                    if (subset.size() > 1)
                    {
                        boolean anyInRange = false;
                        outer:
                        for (int i = 0; i < subset.size(); i++)
                            for (int j = i + 1; j < subset.size(); j++)
                                if (SpawnerCluster.spheresIntersect(subset.get(i), subset.get(j), activationRadius))
                                {
                                    anyInRange = true;
                                    break outer;
                                }

                        if (anyInRange)
                            shouldComputeIntersection = true;
                        else
                            RadarClient.LOGGER.info("Skipping intersection for subset {} because no spawners are in range.", subset);
                    }

                    if (shouldComputeIntersection)
                    {
                        List<BlockPos> intersection = SpawnerCluster.computeIntersectionRegion(subset, activationRadius);
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
        } catch (Exception e)
        {
            RadarClient.LOGGER.error(e.toString());
        }

        executor.shutdown();

        RadarClient.LOGGER.info("Step 2: Filtering strict subsets...");
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
                    RadarClient.LOGGER.info("Cluster {} is a subset of {} -> removing", current.spawners(), other.spawners());
                    break;
                }
            }

            if (!isSubset)
            {
                RadarClient.LOGGER.info("Keeping cluster: {}", current.spawners());
                filtered.add(current);
            }
        }

        RadarClient.LOGGER.info("Step 3: Adding singletons...");
        for (BlockPos spawner : spawners)
        {
            boolean inCluster = filtered.stream().anyMatch(c -> c.spawners().contains(spawner));
            if (!inCluster)
            {
                RadarClient.LOGGER.info("Adding singleton cluster for spawner: {}", spawner);
                filtered.add(new SpawnerCluster(List.of(spawner), List.of(spawner)));
            }
        }

        RadarClient.LOGGER.info("========== CLUSTER SUMMARY ==========");
        for (SpawnerCluster cluster : filtered) RadarClient.LOGGER.info("Cluster ({} spawners): {}", cluster.spawners().size(), cluster.spawners());
        RadarClient.LOGGER.info("=====================================");

        long endTime = System.nanoTime();
        double elapsedSeconds = (endTime - startTime) / 1_000_000_000.0;
        RadarClient.LOGGER.info("Finished. Total clusters: {}. Clustering took {} seconds", filtered.size(), elapsedSeconds);

        return filtered;
    }

    public static List<SpawnerCluster> findClustersRadialIncremental(FabricClientCommandSource source, List<BlockPos> spawners, double activationRadius)
    {
        source.sendFeedback(Text.of("Generating radial clusters with incremental intersection check..."));
        long startTime = System.nanoTime();

        List<SpawnerCluster> clusters = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int n = spawners.size();

        RadarClient.LOGGER.info("Starting radial incremental clustering with volume: {} spawners, activationRadius = {}", n, activationRadius);

        for (int idx = 0; idx < n; idx++)
        {
            BlockPos center = spawners.get(idx);

            List<BlockPos> others = new ArrayList<>(spawners);
            others.remove(center);

            final long cx = center.getX(), cy = center.getY(), cz = center.getZ();
            others.sort((p1, p2) ->
            {
                long dx1 = p1.getX() - cx, dy1 = p1.getY() - cy, dz1 = p1.getZ() - cz;
                long dx2 = p2.getX() - cx, dy2 = p2.getY() - cy, dz2 = p2.getZ() - cz;
                long d1 = dx1 * dx1 + dy1 * dy1 + dz1 * dz1;
                long d2 = dx2 * dx2 + dy2 * dy2 + dz2 * dz2;
                return Long.compare(d1, d2);
            });

            List<BlockPos> cluster = new ArrayList<>();
            cluster.add(center);

            List<BlockPos> currentIntersection = generateSphere(center, activationRadius);

            for (BlockPos other : others)
            {
                if (!SpawnerCluster.spheresIntersect(center, other, activationRadius))
                {
                    RadarClient.LOGGER.debug("Center {}: {} not in range, stopping search.", center, other);
                    break;
                }

                List<BlockPos> newIntersection = computeIntersectionRegionWithExistingVolume(other, currentIntersection, activationRadius);
                if (newIntersection.isEmpty())
                {
                    RadarClient.LOGGER.debug("Center {}: {} does not intersect current volume -> stopping", center, other);
                    break;
                }

                cluster.add(other);
                currentIntersection = newIntersection;
                RadarClient.LOGGER.debug("Center {}: added neighbor {} -> intersection size {}", center, other, currentIntersection.size());
            }

            if (cluster.size() <= 1)
                continue;

            cluster.sort((p1, p2) ->
            {
                int cmpX = Integer.compare(p1.getX(), p2.getX());
                if (cmpX != 0)
                    return cmpX;
                int cmpY = Integer.compare(p1.getY(), p2.getY());
                return Integer.compare(p1.getZ(), p2.getZ());
            });

            String key = cluster.stream().map(p -> p.getX() + "," + p.getY() + "," + p.getZ()).collect(Collectors.joining(";"));
            if (seen.contains(key))
                continue;
            seen.add(key);

            clusters.add(new SpawnerCluster(cluster, currentIntersection));
            RadarClient.LOGGER.info("Added cluster centered at {}: {} (volume size {})", center, cluster, currentIntersection.size());
        }

        for (BlockPos spawner : spawners)
        {
            boolean inAny = clusters.stream().anyMatch(c -> c.spawners().contains(spawner));
            if (!inAny)
                clusters.add(new SpawnerCluster(List.of(spawner), List.of(spawner)));
        }

        long endTime = System.nanoTime();
        double elapsedSeconds = (endTime - startTime) / 1_000_000_000.0;
        RadarClient.LOGGER.info("Radial incremental clustering finished: {} clusters, took {} s", clusters.size(), elapsedSeconds);

        return filterStrictSubsets(clusters);
    }

    private static List<BlockPos> generateSphere(BlockPos center, double radius)
    {
        List<BlockPos> volume = new ArrayList<>();
        int r = (int) Math.ceil(radius);
        for (int x = center.getX() - r; x <= center.getX() + r; x++) {
            for (int y = center.getY() - r; y <= center.getY() + r; y++) {
                for (int z = center.getZ() - r; z <= center.getZ() + r; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (SpawnerCluster.inSphere(center, pos, radius)) {
                        volume.add(pos);
                    }
                }
            }
        }
        return volume;
    }

    private static List<BlockPos> computeIntersectionRegionWithExistingVolume(BlockPos other, List<BlockPos> existingVolume, double radius)
    {
        List<BlockPos> result = new ArrayList<>();
        for (BlockPos pos : existingVolume)
            if (SpawnerCluster.inSphere(other, pos, radius))
                result.add(pos);
        return result;
    }

    private static List<BlockPos> computeIntersectionRegion(List<BlockPos> cluster, double radius)
    {
        List<BlockPos> intersection = new ArrayList<>();
        if (cluster.isEmpty())
            return intersection;

        int minX = cluster.stream().mapToInt(BlockPos::getX).min().getAsInt();
        int minY = cluster.stream().mapToInt(BlockPos::getY).min().getAsInt();
        int minZ = cluster.stream().mapToInt(BlockPos::getZ).min().getAsInt();
        int maxX = cluster.stream().mapToInt(BlockPos::getX).max().getAsInt();
        int maxY = cluster.stream().mapToInt(BlockPos::getY).max().getAsInt();
        int maxZ = cluster.stream().mapToInt(BlockPos::getZ).max().getAsInt();

        minX -= (int) radius;
        minY -= (int) radius;
        minZ -= (int) radius;
        maxX += (int) radius;
        maxY += (int) radius;
        maxZ += (int) radius;

        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++)
                {
                    BlockPos pos = new BlockPos(x, y, z);
                    boolean insideAll = true;
                    for (BlockPos s : cluster)
                    {
                        if (!inSphere(s, pos, radius))
                        {
                            insideAll = false;
                            break;
                        }
                    }
                    if (insideAll)
                        intersection.add(pos);
                }

        return intersection;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder("SpawnerCluster[");
        for (BlockPos pos : spawners) sb.append(String.format("(%d,%d,%d), ", pos.getX(), pos.getY(), pos.getZ()));
        if (!spawners.isEmpty())
            sb.setLength(sb.length() - 2);
        sb.append("]");
        return sb.toString();
    }
}
