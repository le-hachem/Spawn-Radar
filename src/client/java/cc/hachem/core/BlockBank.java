package cc.hachem.core;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.block.Blocks;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class BlockBank
{
    private static final List<BlockPos> HIGHLIGHTED_BLOCKS = new CopyOnWriteArrayList<>();

    public static void scanForSpawners(FabricClientCommandSource source, int chunkRadius)
    {
        ClusterManager.getClusters().clear();
        BlockBank.clear();

        source.sendFeedback(Text.of("Searching for spawners..."));

        new Thread(() ->
        {
            BlockPos playerPos = source.getPlayer().getBlockPos();
            int playerChunkX = playerPos.getX() >> 4;
            int playerChunkZ = playerPos.getZ() >> 4;

            List<BlockPos> foundSpawners = new ArrayList<>();
            for (int dx = -chunkRadius; dx <= chunkRadius; dx++)
                for (int dz = -chunkRadius; dz <= chunkRadius; dz++)
                {
                    int chunkX = playerChunkX + dx;
                    int chunkZ = playerChunkZ + dz;

                    if (!source.getWorld().isChunkLoaded(chunkX, chunkZ))
                        continue;

                    int baseX = chunkX << 4;
                    int baseZ = chunkZ << 4;

                    int minY = source.getWorld().getBottomY();
                    int maxY = source.getWorld().getHeight();

                    for (int y = minY; y < maxY; y++)
                        for (int x = 0; x < 16; x++)
                            for (int z = 0; z < 16; z++)
                            {
                                BlockPos pos = new BlockPos(baseX + x, y, baseZ + z);
                                if (source.getWorld().getBlockState(pos).isOf(Blocks.SPAWNER))
                                {
                                    foundSpawners.add(pos);
                                    BlockBank.add(pos);
                                }
                            }
                }

            int spawnersFound = foundSpawners.size();
            source.getClient().execute(() ->
            {
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

    public static void remove(BlockPos pos)
    {
        HIGHLIGHTED_BLOCKS.remove(pos);
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