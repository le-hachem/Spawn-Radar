package cc.hachem.spawnradar.renderer;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class GreedyMesher
{

    public enum Face
    {
        POS_X,
        NEG_X,
        POS_Y,
        NEG_Y,
        POS_Z,
        NEG_Z
    }

    public record Quad(Face face, int x, int y, int z, int width, int height)
    {

        @Override @NotNull
        public String toString()
        {
            return MessageFormat.format("Quad'{'{0} @ ({1},{2},{3}) size={4}x{5}'}'",
                face, x, y, z, width, height);
        }
    }

    private GreedyMesher() { }

    public static List<Quad> mesh(Set<BlockPos> region)
    {
        List<Quad> quads = new ArrayList<>();
        if (region == null || region.isEmpty())
            return quads;

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (BlockPos position : region)
        {
            minX = Math.min(minX, position.getX());
            minY = Math.min(minY, position.getY());
            minZ = Math.min(minZ, position.getZ());
            maxX = Math.max(maxX, position.getX());
            maxY = Math.max(maxY, position.getY());
            maxZ = Math.max(maxZ, position.getZ());
        }

        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;

        boolean[][][] occ = new boolean[sizeX][sizeY][sizeZ];
        for (BlockPos p : region)
            occ[p.getX() - minX][p.getY() - minY][p.getZ() - minZ] = true;

        for (int x = 0; x < sizeX; x++)
        {
            boolean[][] mask = new boolean[sizeY][sizeZ];
            for (int y = 0; y < sizeY; y++)
                for (int z = 0; z < sizeZ; z++)
                {
                    boolean here = occ[x][y][z];
                    boolean neigh = (x + 1 < sizeX) && occ[x + 1][y][z];
                    mask[y][z] = here && !neigh;
                }

            int finalMinX = minX;
            int finalMinY = minY;
            int finalMinZ = minZ;
            int finalX = x;

            runGreedyOnMask(mask, (y, z, w, h) -> quads.add(new Quad(Face.POS_X, finalMinX + finalX, finalMinY + y, finalMinZ + z, w, h)));
        }

        for (int x = 0; x < sizeX; x++)
        {
            boolean[][] mask = new boolean[sizeY][sizeZ];
            for (int y = 0; y < sizeY; y++)
                for (int z = 0; z < sizeZ; z++)
                {
                    boolean here = occ[x][y][z];
                    boolean neigh = (x - 1 >= 0) && occ[x - 1][y][z];
                    mask[y][z] = here && !neigh;
                }

            int finalMinX1 = minX;
            int finalMinY1 = minY;
            int finalMinZ1 = minZ;
            int finalX = x;

            runGreedyOnMask(mask, (y, z, w, h) -> quads.add(new Quad(Face.NEG_X, finalMinX1 + finalX, finalMinY1 + y, finalMinZ1 + z, w, h)));
        }

        for (int y = 0; y < sizeY; y++)
        {
            boolean[][] mask = new boolean[sizeX][sizeZ];
            for (int x = 0; x < sizeX; x++)
                for (int z = 0; z < sizeZ; z++)
                {
                    boolean here = occ[x][y][z];
                    boolean neigh = (y + 1 < sizeY) && occ[x][y + 1][z];
                    mask[x][z] = here && !neigh;
                }

            int finalMinX2 = minX;
            int finalMinY2 = minY;
            int finalMinZ2 = minZ;
            int finalY = y;
            runGreedyOnMask(mask, (x0, z0, w, h) -> quads.add(new Quad(Face.POS_Y, finalMinX2 + x0, finalMinY2 + finalY, finalMinZ2 + z0, h, w)));
        }

        for (int y = 0; y < sizeY; y++)
        {
            boolean[][] mask = new boolean[sizeX][sizeZ];
            for (int x = 0; x < sizeX; x++)
                for (int z = 0; z < sizeZ; z++)
                {
                    boolean here = occ[x][y][z];
                    boolean neigh = (y - 1 >= 0) && occ[x][y - 1][z];
                    mask[x][z] = here && !neigh;
                }

            int finalMinX3 = minX;
            int finalMinY3 = minY;
            int finalMinZ3 = minZ;
            int finalY = y;

            runGreedyOnMask(mask, (x0, z0, w, h) -> quads.add(new Quad(Face.NEG_Y, finalMinX3 + x0, finalMinY3 + finalY, finalMinZ3 + z0, h, w)));
        }

        for (int z = 0; z < sizeZ; z++)
        {
            boolean[][] mask = new boolean[sizeY][sizeX];
            for (int y = 0; y < sizeY; y++)
                for (int x = 0; x < sizeX; x++)
                {
                    boolean here = occ[x][y][z];
                    boolean neigh = (z + 1 < sizeZ) && occ[x][y][z + 1];
                    mask[y][x] = here && !neigh;
                }

            int finalMinX4 = minX;
            int finalMinY4 = minY;
            int finalMinZ4 = minZ;
            int finalZ = z;

            runGreedyOnMask(mask, (y, x0, w, h) -> quads.add(new Quad(Face.POS_Z, finalMinX4 + x0, finalMinY4 + y, finalMinZ4 + finalZ, w, h)));
        }

        for (int z = 0; z < sizeZ; z++)
        {
            boolean[][] mask = new boolean[sizeY][sizeX];
            for (int y = 0; y < sizeY; y++)
                for (int x = 0; x < sizeX; x++)
                {
                    boolean here = occ[x][y][z];
                    boolean neigh = (z - 1 >= 0) && occ[x][y][z - 1];
                    mask[y][x] = here && !neigh;
                }

            int finalMinX5 = minX;
            int finalMinY5 = minY;
            int finalMinZ5 = minZ;
            int finalZ = z;
            runGreedyOnMask(mask, (y, x0, w, h) -> quads.add(new Quad(Face.NEG_Z, finalMinX5 + x0, finalMinY5 + y, finalMinZ5 + finalZ, w, h)));
        }

        return quads;
    }

    private interface QuadConsumer
    {
        void accept(int a, int b, int w, int h);
    }

    private static void runGreedyOnMask(boolean[][] mask, QuadConsumer consumer)
    {
        int rows = mask.length;
        if (rows == 0)
            return;
        int cols = mask[0].length;

        for (int row = 0; row < rows; row++)
        {
            for (int col = 0; col < cols; )
            {
                if (!mask[row][col])
                {
                    col++;
                    continue;
                }

                int width = 1;
                while (col + width < cols && mask[row][col + width]) width++;

                int height = 1;
                outer:
                while (row + height < rows)
                {
                    for (int k = 0; k < width; k++)
                        if (!mask[row + height][col + k])
                            break outer;
                    height++;
                }

                consumer.accept(row, col, width, height);

                for (int rr = row; rr < row + height; rr++)
                    for (int cc = col; cc < col + width; cc++)
                        mask[rr][cc] = false;
                col += width;
            }
        }
    }
}
