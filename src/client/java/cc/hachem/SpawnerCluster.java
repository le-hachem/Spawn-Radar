package cc.hachem;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
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

    public static List<SpawnerCluster> findClusters(List<BlockPos> spawners, double activationRadius)
    {
        List<SpawnerCluster> clusters = new ArrayList<>();
        List<String> seenClusters = new ArrayList<>();

        for (int i = 0; i < spawners.size(); i++)
        {
            BlockPos a = spawners.get(i);
            List<BlockPos> cluster = new ArrayList<>();
            cluster.add(a);

            for (int j = 0; j < spawners.size(); j++)
            {
                if (i == j) continue;
                BlockPos b = spawners.get(j);
                if (spheresIntersect(a, b, activationRadius)) cluster.add(b);
            }

            boolean expanded;
            do
            {
                expanded = false;
                for (BlockPos s1 : new ArrayList<>(cluster))
                {
                    for (BlockPos s2 : spawners)
                    {
                        if (!cluster.contains(s2) && spheresIntersect(s1, s2, activationRadius))
                        {
                            cluster.add(s2);
                            expanded = true;
                        }
                    }
                }
            } while (expanded);

            cluster.sort((p1, p2) ->
            {
                int cmpX = Integer.compare(p1.getX(), p2.getX());
                if (cmpX != 0) return cmpX;
                int cmpY = Integer.compare(p1.getY(), p2.getY());
                if (cmpY != 0) return cmpY;
                return Integer.compare(p1.getZ(), p2.getZ());
            });

            String key = cluster.stream().map(p -> p.getX() + "," + p.getY() + "," + p.getZ())
                             .reduce((s1, s2) -> s1 + ";" + s2).orElse("");
            if (seenClusters.contains(key)) continue;
            seenClusters.add(key);

            List<BlockPos> intersection = computeIntersectionRegion(cluster, activationRadius);
            clusters.add(new SpawnerCluster(cluster, intersection));
        }

        return clusters;
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
