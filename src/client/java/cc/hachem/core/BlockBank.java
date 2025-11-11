package cc.hachem.core;

import cc.hachem.RadarClient;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.block.Blocks;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class BlockBank
{
    private static final List<BlockPos> HIGHLIGHTED_BLOCKS = new CopyOnWriteArrayList<>();

    public static void scanForSpawners(FabricClientCommandSource source, int chunkRadius) {
        ClusterManager.getClusters().clear();
        BlockBank.clear();

        source.sendFeedback(Text.of("Searching for spawners..."));

        new Thread(() -> {
            BlockPos playerPos = source.getPlayer().getBlockPos();
            int playerChunkX = playerPos.getX() >> 4;
            int playerChunkZ = playerPos.getZ() >> 4;

            List<BlockPos> foundSpawners = Collections.synchronizedList(new ArrayList<>());

            int halfRadius = chunkRadius / 2;

            // Quadrants relative to player-centered coordinates
            int[][] quadrants = new int[][] {
                {-halfRadius, 0, -halfRadius, 0}, // NW
                {1, halfRadius, -halfRadius, 0},  // NE
                {-halfRadius, 0, 1, halfRadius},  // SW
                {1, halfRadius, 1, halfRadius}    // SE
            };

            List<Thread> workers = new ArrayList<>();

            for (int[] q : quadrants) {
                Thread t = new Thread(() -> {
                    // Spiral outward: compute Manhattan distance from player
                    List<int[]> chunkOffsets = new ArrayList<>();
                    for (int dx = q[0]; dx <= q[1]; dx++) {
                        for (int dz = q[2]; dz <= q[3]; dz++) {
                            chunkOffsets.add(new int[]{dx, dz});
                        }
                    }

                    // Sort chunks by distance from player (Manhattan distance)
                    chunkOffsets.sort((a, b) -> {
                        int distA = Math.abs(a[0]) + Math.abs(a[1]);
                        int distB = Math.abs(b[0]) + Math.abs(b[1]);
                        return Integer.compare(distA, distB);
                    });

                    for (int[] offset : chunkOffsets) {
                        int chunkX = playerChunkX + offset[0];
                        int chunkZ = playerChunkZ + offset[1];

                        if (!source.getWorld().isChunkLoaded(chunkX, chunkZ)) continue;

                        int baseX = chunkX << 4;
                        int baseZ = chunkZ << 4;

                        int minY = source.getWorld().getBottomY();
                        int maxY = source.getWorld().getHeight();

                        for (int y = minY; y < maxY; y++)
                            for (int x = 0; x < 16; x++)
                                for (int z = 0; z < 16; z++) {
                                    BlockPos pos = new BlockPos(baseX + x, y, baseZ + z);
                                    if (source.getWorld().getBlockState(pos).isOf(Blocks.SPAWNER)) {
                                        foundSpawners.add(pos);
                                        BlockBank.add(pos);
                                    }
                                }
                    }
                }, "SpawnerScanner-" + q[0] + "-" + q[2]);
                workers.add(t);
                t.start();
            }

            // Wait for all threads to finish
            for (Thread t : workers) {
                try {
                    t.join();
                } catch (InterruptedException e) {
                    RadarClient.LOGGER.error("Spawner scan thread interrupted", e);
                }
            }

            int spawnersFound = foundSpawners.size();
            source.getClient().execute(() -> {
                if (spawnersFound == 0)
                    source.sendFeedback(Text.of("No spawners found."));
                else
                    source.sendFeedback(Text.of("Found " + spawnersFound + " spawners."));
            });
        }).start();
    }

    public static void add(BlockPos pos)
    {
        if (!HIGHLIGHTED_BLOCKS.contains(pos))
            HIGHLIGHTED_BLOCKS.add(pos);
    }

    public static void clear()
    {
        HIGHLIGHTED_BLOCKS.clear();
    }

    public static List<BlockPos> getAll()
    {
        return Collections.unmodifiableList(HIGHLIGHTED_BLOCKS);
    }
}