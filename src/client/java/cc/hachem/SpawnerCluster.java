package cc.hachem;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

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

    private static boolean inSphere(BlockPos center, BlockPos pos, double radius)
    {
        double dx = center.getX() - pos.getX();
        double dy = center.getY() - pos.getY();
        double dz = center.getZ() - pos.getZ();
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    public static List<SpawnerCluster> findClusters(FabricClientCommandSource source, List<BlockPos> spawners, double activationRadius)
    {
        source.sendFeedback(Text.of("Generating clusters, this might take a minute."));
        int n = spawners.size();
        List<SpawnerCluster> clusters = new ArrayList<>();
        RadarClient.LOGGER.info("=== CLUSTER GENERATION ==");
        RadarClient.LOGGER.info("Step 0: Precomputing pairwise in-range...");
        boolean[][] inRange = new boolean[n][n];
        for (int i = 0; i < n; i++)
        {
            for (int j = i + 1; j < n; j++)
            {
                inRange[i][j] = spheresIntersect(spawners.get (i), spawners.get (j), activationRadius);
                inRange[j][i] = inRange[i][j];
            }
        }
        RadarClient.LOGGER.info("Step 1: Generating subsets and computing intersections...");
        for (int mask = 1; mask < (1 << n); mask++)
        {
            List<BlockPos> subset = new ArrayList<>();
            List<Integer> indices = new ArrayList<>();
            for (int i = 0; i < n; i++)
            {
                if ((mask & (1 << i)) != 0)
                {
                    subset.add(spawners.get(i));
                    indices.add(i);
                }
            }
            boolean canIntersect = true;
            for (int i = 0; i < indices.size(); i++)
            {
                for (int j = i + 1; j < indices.size (); j++)
                {
                    if (!inRange[indices.get(i)][indices.get(j)])
                    {
                        canIntersect = false;
                        break;
                    }
                }
                if (!canIntersect)
                    break;
            }
            if (!canIntersect)
            {
                RadarClient.LOGGER.info("Skipping subset (pairwise not in range): {}", subset);
                continue;
            }
            subset.sort ((p1, p2) ->
            {
                int cmpX = Integer.compare (p1.getX (), p2.getX ());
                if (cmpX != 0)
                    return cmpX;
                int cmpY = Integer.compare (p1.getY (), p2.getY ());
                return Integer.compare (p1.getZ (), p2.getZ ());
            });
            List<BlockPos> intersection = computeIntersectionRegion (subset, activationRadius);
            if (!intersection.isEmpty())
            {
                RadarClient.LOGGER.info("Adding cluster: {} (intersection size: {})", subset, intersection.size());
                clusters.add (new SpawnerCluster (subset, intersection));
            }
            else
                RadarClient.LOGGER.info("Skipping subset (empty intersection): {}", subset);
        }
        RadarClient.LOGGER.info ("Step 2: Filtering strict subsets...");
        clusters.sort((c1, c2) -> Integer.compare (c2.spawners().size(), c1.spawners().size()));
        List<SpawnerCluster> filtered = new ArrayList<>();
        for (int i = 0; i < clusters.size(); i++)
        {
            SpawnerCluster current = clusters.get(i);
            boolean isSubset = false;
            for (int j = 0; j < clusters.size(); j++)
            {
                if (i == j)
                    continue;
                SpawnerCluster other = clusters.get (j);
                if (other.spawners().size() <= current.spawners().size())
                    continue;
                if (new HashSet<>(other.spawners()).containsAll(current.spawners ()))
                {
                    isSubset = true;
                    RadarClient.LOGGER.info("Removing subset: {} is contained in {}", current.spawners (), other.spawners ());
                    break;
                }
            }
            if (!isSubset)
                filtered.add (current);
        }
        RadarClient.LOGGER.info ("Step 3: Adding singletons...");
        for (BlockPos spawner : spawners)
        {
            boolean inCluster = filtered.stream ().anyMatch( c -> c.spawners ().contains (spawner));
            if (!inCluster)
            {
                RadarClient.LOGGER.info("Adding singleton: {}", spawner);
                filtered.add(
                    new SpawnerCluster(List.of (spawner), List.of (spawner)));
            }
        }
        RadarClient.LOGGER.info("Finished. Total clusters: {}", filtered.size ());
        source.sendFeedback(Text.of("done."));
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
