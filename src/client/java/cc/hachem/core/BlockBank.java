package cc.hachem.core;

import cc.hachem.RadarClient;
import cc.hachem.config.ConfigManager;
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
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class BlockBank
{
    private static final List<SpawnerInfo> SPAWNERS = new CopyOnWriteArrayList<>();
    private static volatile boolean manualDataReady = false;

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
        List<ChunkOffset> offsets = buildChunkOffsets(chunkRadius);
        int workerCount = resolveThreadCount(offsets.size());
        List<List<ChunkOffset>> batches = partitionOffsets(offsets, workerCount);

        List<Thread> workers = new ArrayList<>();
        for (int i = 0; i < batches.size(); i++)
        {
            List<ChunkOffset> batch = batches.get(i);
            if (batch.isEmpty())
                continue;
            Thread thread = new Thread(() ->
                scanChunkBatch(player, batch, playerChunkX, playerChunkZ, foundSpawners),
                "SpawnerScanner-" + i);
            workers.add(thread);
            thread.start();
        }
        RadarClient.LOGGER.debug("Spawned {} scan workers (requested {}) for {} chunk offsets.",
            workers.size(), workerCount, offsets.size());
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
        markManualDataReady();
        if (spawnersFound == 0)
            player.sendMessage(Text.translatable("chat.spawn_radar.none"), false);
        else
            player.sendMessage(Text.translatable("chat.spawn_radar.found", spawnersFound), false);

        if (callback != null)
            callback.run();
    }

    private static void scanChunkBatch(ClientPlayerEntity player, List<ChunkOffset> offsets,
                                       int playerChunkX, int playerChunkZ,
                                       List<SpawnerInfo> foundSpawners)
    {
        var world = player.getEntityWorld();
        RadarClient.LOGGER.debug("{} scanning {} chunk offsets.", Thread.currentThread().getName(), offsets.size());
        for (ChunkOffset offset : offsets)
            scanChunk(world, playerChunkX + offset.dx(), playerChunkZ + offset.dz(), foundSpawners);
        RadarClient.LOGGER.debug("{} finished scanning.", Thread.currentThread().getName());
    }

    private static List<ChunkOffset> buildChunkOffsets(int chunkRadius)
    {
        List<ChunkOffset> chunkOffsets = new ArrayList<>();
        int halfRadius = Math.max(0, chunkRadius / 2);
        for (int dx = -halfRadius; dx <= halfRadius; dx++)
            for (int dz = -halfRadius; dz <= halfRadius; dz++)
                chunkOffsets.add(new ChunkOffset(dx, dz));

        chunkOffsets.sort(Comparator.comparingInt(ChunkOffset::distance));
        return chunkOffsets;
    }

    private static List<List<ChunkOffset>> partitionOffsets(List<ChunkOffset> offsets, int partitions)
    {
        List<List<ChunkOffset>> batches = new ArrayList<>(partitions);
        if (partitions == 0)
        {
            batches.add(offsets);
            return batches;
        }

        int batchSize = (int) Math.ceil((double) offsets.size() / partitions);
        for (int i = 0; i < partitions; i++)
        {
            int start = i * batchSize;
            int end = Math.min(offsets.size(), start + batchSize);
            if (start >= end)
                batches.add(Collections.emptyList());
            else
                batches.add(new ArrayList<>(offsets.subList(start, end)));
        }
        return batches;
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

    private static int resolveThreadCount(int workSize)
    {
        int configured = RadarClient.config != null ? RadarClient.config.scanThreadCount : ConfigManager.DEFAULT.scanThreadCount;
        if (configured <= 1 || workSize <= 1)
            return 1;

        int desired = ensureEven(Math.min(16, configured));
        int limited = Math.min(desired, workSize);
        if ((limited & 1) != 0)
            limited = Math.max(2, limited - 1);
        return Math.max(2, limited);
    }

    private static int ensureEven(int value)
    {
        return (value & 1) == 0 ? value : value + 1;
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
        manualDataReady = false;
    }

    public static List<SpawnerInfo> getAll()
    {
        return Collections.unmodifiableList(SPAWNERS);
    }

    public static SpawnerInfo get(BlockPos pos)
    {
        for (SpawnerInfo info : SPAWNERS)
            if (info.pos().equals(pos))
                return info;
        return null;
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

    public static SpawnerInfo createSpawnerInfo(World world, BlockPos pos)
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

    public static void remove(BlockPos pos)
    {
        SPAWNERS.removeIf(info -> info.pos().equals(pos));
    }

    public static void removeAll(Set<BlockPos> positions)
    {
        if (positions == null || positions.isEmpty())
            return;
        SPAWNERS.removeIf(info -> positions.contains(info.pos()));
    }

    public static boolean hasCachedSpawners()
    {
        return !SPAWNERS.isEmpty();
    }

    public static void markManualDataReady()
    {
        manualDataReady = true;
    }

    public static boolean hasManualResults()
    {
        return manualDataReady;
    }

    public static List<SpawnerInfo> getWithinChunkRadius(BlockPos center, int chunkRadius)
    {
        List<SpawnerInfo> result = new ArrayList<>();
        int half = Math.max(0, chunkRadius / 2);
        int centerChunkX = center.getX() >> 4;
        int centerChunkZ = center.getZ() >> 4;
        int minChunkX = centerChunkX - half;
        int maxChunkX = centerChunkX + half;
        int minChunkZ = centerChunkZ - half;
        int maxChunkZ = centerChunkZ + half;

        for (SpawnerInfo info : SPAWNERS)
        {
            BlockPos pos = info.pos();
            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;
            if (chunkX >= minChunkX && chunkX <= maxChunkX && chunkZ >= minChunkZ && chunkZ <= maxChunkZ)
                result.add(info);
        }
        return result;
    }

    private static EntityType<?> resolveSpawnerEntityType(World world, BlockPos pos, MobSpawnerBlockEntity mobSpawner)
    {
        Entity renderedEntity = mobSpawner.getLogic().getRenderedEntity(world, pos);
        return renderedEntity != null ? renderedEntity.getType() : null;
    }
}
