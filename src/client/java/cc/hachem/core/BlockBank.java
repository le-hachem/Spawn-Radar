package cc.hachem.core;

import cc.hachem.RadarClient;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class BlockBank
{
    private static final List<BlockPos> HIGHLIGHTED_BLOCKS = new CopyOnWriteArrayList<>();

    public static void scanForSpawners(ClientPlayerEntity player, int chunkRadius, Runnable callback)
    {
        RadarClient.reset(player);
        player.sendMessage(Text.translatable("chat.spawn_radar.searching"), false);

        new Thread(() ->
        {
            long startTime = System.currentTimeMillis();
            BlockPos playerPos = player.getBlockPos();
            int playerChunkX = playerPos.getX() >> 4;
            int playerChunkZ = playerPos.getZ() >> 4;

            List<BlockPos> foundSpawners = Collections.synchronizedList(new ArrayList<>());

            int halfRadius = chunkRadius / 2;

            int[][] quadrants = new int[][]
                {
                    { -halfRadius, 0, -halfRadius, 0 }, // NW
                    { 1, halfRadius, -halfRadius, 0  },  // NE
                    { -halfRadius, 0, 1, halfRadius  },  // SW
                    { 1, halfRadius, 1, halfRadius   }    // SE
                };

            List<Thread> workers = new ArrayList<>();

            for (int[] quadrant : quadrants)
            {
                Thread thread = new Thread(() ->
                {
                    RadarClient.LOGGER.debug("Thread {} scanning quadrant X[{}..{}], Z[{}..{}].",
                        Thread.currentThread().getName(), quadrant[0], quadrant[1], quadrant[2], quadrant[3]);

                    List<int[]> chunkOffsets = new ArrayList<>();
                    for (int dx = quadrant[0]; dx <= quadrant[1]; dx++)
                        for (int dz = quadrant[2]; dz <= quadrant[3]; dz++)
                            chunkOffsets.add(new int[]{dx, dz});

                    chunkOffsets.sort(Comparator.comparingInt(a -> Math.abs(a[0]) + Math.abs(a[1])));

                    for (int[] offset : chunkOffsets)
                    {
                        int chunkX = playerChunkX + offset[0];
                        int chunkZ = playerChunkZ + offset[1];

                        if (!player.getEntityWorld().isChunkLoaded(chunkX, chunkZ))
                            continue;

                        int baseX = chunkX << 4;
                        int baseZ = chunkZ << 4;

                        int minY = player.getEntityWorld().getBottomY();
                        int maxY = player.getEntityWorld().getHeight();

                        for (int y = minY; y < maxY; y++)
                            for (int x = 0; x < 16; x++)
                                for (int z = 0; z < 16; z++)
                                {
                                    BlockPos pos = new BlockPos(baseX + x, y, baseZ + z);
                                    if (player.getEntityWorld().getBlockState(pos).isOf(Blocks.SPAWNER))
                                    {
                                        foundSpawners.add(pos);
                                        BlockBank.add(pos);
                                        RadarClient.LOGGER.trace("Found spawner at {}", pos);
                                    }
                                }
                    }

                    RadarClient.LOGGER.debug("Thread {} finished scanning.", Thread.currentThread().getName());
                }, "SpawnerScanner-" + quadrant[0] + "-" + quadrant[2]);
                workers.add(thread);
                thread.start();
            }

            for (Thread thread : workers) try
            {
                thread.join();
            }
            catch (InterruptedException e)
            {
                RadarClient.LOGGER.error("Spawner scan thread interrupted", e);
            }

            int spawnersFound = foundSpawners.size();
            long elapsed = System.currentTimeMillis() - startTime;
            RadarClient.LOGGER.info("Spawner scan completed in {} ms. Found {} spawners.", elapsed, spawnersFound);

            MinecraftClient.getInstance().execute(() ->
            {
                if (spawnersFound == 0)
                    player.sendMessage(Text.translatable("chat.spawn_radar.none"), false);
                else
                    player.sendMessage(Text.translatable("chat.spawn_radar.found", spawnersFound), false);

                if (callback != null)
                    callback.run();
            });

        }).start();
    }


    public static void add(BlockPos pos)
    {
        if (!HIGHLIGHTED_BLOCKS.contains(pos))
        {
            HIGHLIGHTED_BLOCKS.add(pos);
            RadarClient.LOGGER.trace("Added spawner to highlight list: {}", pos);
        }
    }

    public static void clear()
    {
        int count = HIGHLIGHTED_BLOCKS.size();
        HIGHLIGHTED_BLOCKS.clear();
        RadarClient.LOGGER.debug("Cleared {} highlighted blocks.", count);
    }

    public static List<BlockPos> getAll()
    {
        return Collections.unmodifiableList(HIGHLIGHTED_BLOCKS);
    }
}
