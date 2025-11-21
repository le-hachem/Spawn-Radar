package cc.hachem.core;

import cc.hachem.RadarClient;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class BlockBank
{
    private static final List<SpawnerInfo> SPAWNERS = new CopyOnWriteArrayList<>();

    private BlockBank() {}

    public static void scanForSpawners(ClientPlayerEntity player, int chunkRadius, Runnable callback)
    {
        RadarClient.reset(player);
        player.sendMessage(Text.translatable("chat.spawn_radar.searching"), false);

        new Thread(() -> performScan(player, chunkRadius, callback)).start();
    }

    private static void performScan(ClientPlayerEntity player, int chunkRadius, Runnable callback)
    {
        long startTime = System.currentTimeMillis();
        BlockPos playerPos = player.getBlockPos();
        int playerChunkX = playerPos.getX() >> 4;
        int playerChunkZ = playerPos.getZ() >> 4;

        List<SpawnerInfo> foundSpawners = Collections.synchronizedList(new ArrayList<>());
        List<Thread> workers = startWorkers(player, chunkRadius, playerChunkX, playerChunkZ, foundSpawners);

        waitForWorkers(workers);

        int spawnersFound = foundSpawners.size();
        long elapsed = System.currentTimeMillis() - startTime;
        RadarClient.LOGGER.info("Spawner scan completed in {} ms. Found {} spawners.", elapsed, spawnersFound);

        MinecraftClient.getInstance().execute(() -> notifyPlayer(player, callback, spawnersFound));
    }

    private static List<Thread> startWorkers(ClientPlayerEntity player, int chunkRadius,
                                             int playerChunkX, int playerChunkZ,
                                             List<SpawnerInfo> foundSpawners)
    {
        List<Thread> workers = new ArrayList<>();
        for (Quadrant quadrant : createQuadrants(chunkRadius))
        {
            Thread thread = new Thread(() ->
                scanQuadrant(player, quadrant, playerChunkX, playerChunkZ, foundSpawners),
                quadrant.threadName());
            workers.add(thread);
            thread.start();
        }
        return workers;
    }

    private static void waitForWorkers(List<Thread> workers)
    {
        for (Thread thread : workers)
        {
            try
            {
                thread.join();
            }
            catch (InterruptedException e)
            {
                RadarClient.LOGGER.error("Spawner scan thread interrupted", e);
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void notifyPlayer(ClientPlayerEntity player, Runnable callback, int spawnersFound)
    {
        if (spawnersFound == 0)
            player.sendMessage(Text.translatable("chat.spawn_radar.none"), false);
        else
            player.sendMessage(Text.translatable("chat.spawn_radar.found", spawnersFound), false);

        if (callback != null)
            callback.run();
    }

    private static void scanQuadrant(ClientPlayerEntity player, Quadrant quadrant,
                                     int playerChunkX, int playerChunkZ,
                                     List<SpawnerInfo> foundSpawners)
    {
        RadarClient.LOGGER.debug("Thread {} scanning quadrant X[{}..{}], Z[{}..{}].",
            Thread.currentThread().getName(),
            quadrant.minChunkX(), quadrant.maxChunkX(),
            quadrant.minChunkZ(), quadrant.maxChunkZ());

        var world = player.getEntityWorld();
        List<ChunkOffset> chunkOffsets = buildChunkOffsets(quadrant);

        for (ChunkOffset offset : chunkOffsets)
            scanChunk(world, playerChunkX + offset.dx(), playerChunkZ + offset.dz(), foundSpawners);

        RadarClient.LOGGER.debug("Thread {} finished scanning.", Thread.currentThread().getName());
    }

    private static List<ChunkOffset> buildChunkOffsets(Quadrant quadrant)
    {
        List<ChunkOffset> chunkOffsets = new ArrayList<>();
        for (int dx = quadrant.minChunkX(); dx <= quadrant.maxChunkX(); dx++)
            for (int dz = quadrant.minChunkZ(); dz <= quadrant.maxChunkZ(); dz++)
                chunkOffsets.add(new ChunkOffset(dx, dz));

        chunkOffsets.sort(Comparator.comparingInt(ChunkOffset::distance));
        return chunkOffsets;
    }

    private static void scanChunk(World world, int chunkX, int chunkZ, List<SpawnerInfo> foundSpawners)
    {
        if (!world.isChunkLoaded(chunkX, chunkZ))
            return;

        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;

        int minY = world.getBottomY();
        int maxY = world.getHeight();

        for (int y = minY; y < maxY; y++)
            for (int x = 0; x < 16; x++)
                for (int z = 0; z < 16; z++)
                {
                    BlockPos pos = new BlockPos(baseX + x, y, baseZ + z);
                    if (world.getBlockState(pos).isOf(Blocks.SPAWNER))
                    {
                        SpawnerInfo info = createSpawnerInfo(world, pos);
                        foundSpawners.add(info);
                        BlockBank.add(info);
                        RadarClient.LOGGER.trace("Found {} spawner at {}", info.mobName(), pos);
                    }
                }
    }

    private static Quadrant[] createQuadrants(int chunkRadius)
    {
        int halfRadius = chunkRadius / 2;
        return new Quadrant[]
        {
            new Quadrant(-halfRadius, 0, -halfRadius, 0),
            new Quadrant(1, halfRadius, -halfRadius, 0),
            new Quadrant(-halfRadius, 0, 1, halfRadius),
            new Quadrant(1, halfRadius, 1, halfRadius)
        };
    }

    private record Quadrant(int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ)
    {
        String threadName()
        {
            return "SpawnerScanner-" + minChunkX + "-" + minChunkZ;
        }
    }

    private record ChunkOffset(int dx, int dz)
    {
        int distance()
        {
            return Math.abs(dx) + Math.abs(dz);
        }
    }

    public static void add(SpawnerInfo info)
    {
        int index = indexOf(info.pos());
        if (index >= 0)
            SPAWNERS.set(index, info);
        else
            SPAWNERS.add(info);
        RadarClient.LOGGER.trace("Added {} spawner to highlight list: {}", info.mobName(), info.pos());
    }

    public static void clear()
    {
        int count = SPAWNERS.size();
        SPAWNERS.clear();
        RadarClient.LOGGER.debug("Cleared {} highlighted spawners.", count);
    }

    public static List<SpawnerInfo> getAll()
    {
        return Collections.unmodifiableList(SPAWNERS);
    }

    private static int indexOf(BlockPos pos)
    {
        for (int i = 0; i < SPAWNERS.size(); i++)
        {
            if (SPAWNERS.get(i).pos().equals(pos))
                return i;
        }
        return -1;
    }

    private static SpawnerInfo createSpawnerInfo(World world, BlockPos pos)
    {
        EntityType<?> entityType = null;
        try
        {
            var blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof MobSpawnerBlockEntity mobSpawner)
                entityType = resolveSpawnerEntityType(world, pos, mobSpawner);
        }
        catch (Exception e)
        {
            RadarClient.LOGGER.debug("Unable to resolve mob type for spawner at {}", pos, e);
        }
        return new SpawnerInfo(pos, entityType);
    }

    private static EntityType<?> resolveSpawnerEntityType(World world, BlockPos pos, MobSpawnerBlockEntity mobSpawner)
    {
        Entity renderedEntity = mobSpawner.getLogic().getRenderedEntity(world, pos);
        return renderedEntity != null ? renderedEntity.getType() : null;
    }
}
