package cc.hachem;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

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

    public static List<SpawnerCluster> findClusters(
        FabricClientCommandSource source,
        List<BlockPos> spawners,
        double activationRadius
    )
    {
        source.sendFeedback(Text.of("Generating clusters (up to size " + MAX_SUBSET_SIZE + "), this might take a minute."));

        long startTime = System.nanoTime(); // Start timer

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
                        continue; // only subsets of size k

                    List<BlockPos> subset = new ArrayList<>();
                    for (int i = 0; i < n; i++)
                    {
                        if ((mask & (1 << i)) != 0)
                            subset.add(spawners.get(i));
                    }

                    // Sort subset for canonical comparison
                    subset.sort((p1, p2) ->
                    {
                        int cmpX = Integer.compare(p1.getX(), p2.getX());
                        if (cmpX != 0) return cmpX;
                        int cmpY = Integer.compare(p1.getY(), p2.getY());
                        return Integer.compare(p1.getZ(), p2.getZ());
                    });

                    String key = subset.stream()
                                     .map(p -> p.getX() + "," + p.getY() + "," + p.getZ())
                                     .collect(Collectors.joining(";"));
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

        for (Future<List<SpawnerCluster>> future : futures)
            try
            {
                clusters.addAll(future.get());
            }
            catch (Exception e)
            {
                RadarClient.LOGGER.error(e.toString());
            }

        executor.shutdown();

        // Step 2: Filter strict subsets
        RadarClient.LOGGER.info("Step 2: Filtering strict subsets...");
        clusters.sort((c1, c2) -> Integer.compare(c2.spawners().size(), c1.spawners().size())); // largest first
        List<SpawnerCluster> filtered = new ArrayList<>();

        for (int i = 0; i < clusters.size(); i++)
        {
            SpawnerCluster current = clusters.get(i);
            boolean isSubset = false;

            for (int j = 0; j < clusters.size(); j++)
            {
                if (i == j) continue;
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

        // Step 3: Add singletons for any missing spawners
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
        for (SpawnerCluster cluster : filtered)
            RadarClient.LOGGER.info("Cluster ({} spawners): {}", cluster.spawners().size(), cluster.spawners());
        RadarClient.LOGGER.info("=====================================");

        long endTime = System.nanoTime();
        double elapsedSeconds = (endTime - startTime) / 1_000_000_000.0;
        RadarClient.LOGGER.info("Finished. Total clusters: {}. Clustering took {} seconds", filtered.size(), elapsedSeconds);

        return filtered;
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
        {
            for (int y = minY; y <= maxY; y++)
            {
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
                    if (insideAll) intersection.add(pos);
                }
            }
        }

        return intersection;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder("SpawnerCluster[");
        for (BlockPos pos : spawners)
            sb.append(String.format("(%d,%d,%d), ", pos.getX(), pos.getY(), pos.getZ()));
        if (!spawners.isEmpty())
            sb.setLength(sb.length() - 2);
        sb.append("]");
        return sb.toString();
    }
}
