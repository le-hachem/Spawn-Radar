package cc.hachem.core;

import cc.hachem.RadarClient;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;

import java.util.List;

public class ChunkSnapshot {
    private final int chunkX, chunkZ;
    private final int minY, maxY;
    private final BlockState[][][] blocks; // [x][y-minY][z]

    public ChunkSnapshot(Chunk chunk) {
        this.chunkX = chunk.getPos().x;
        this.chunkZ = chunk.getPos().z;
        this.minY = chunk.getBottomY();
        this.maxY = chunk.getHeight();
        this.blocks = new BlockState[16][maxY - minY][16];

        // Copy block states safely on main thread
        for (int y = minY; y < maxY; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    blocks[x][y - minY][z] = chunk.getBlockState(new BlockPos(x, y, z));
                }
            }
        }
    }

    public int getChunkX() { return chunkX; }
    public int getChunkZ() { return chunkZ; }

    public void scanForSpawners(List<BlockPos> output, int threadIdx) {
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;

        for (int y = minY; y < maxY; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    if (blocks[x][y - minY][z].isOf(Blocks.SPAWNER)) {
                        BlockPos pos = new BlockPos(baseX + x, y, baseZ + z);
                        output.add(pos);
                        BlockBank.add(pos);
                        RadarClient.LOGGER.info("[Radar][Thread {}] Spawner found at {}", threadIdx, pos);
                    }
                }
            }
        }
    }
}