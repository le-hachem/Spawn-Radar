package cc.hachem.spawnradar.core;

import cc.hachem.spawnradar.RadarClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class ChunkProcessingManager
{
    private static final Map<Long, Set<Long>> chunkSpawners = new ConcurrentHashMap<>();
    private static final List<Set<Long>> activeAlerts = Collections.synchronizedList(new ArrayList<>());
    private static final List<Set<Long>> pendingAlertHighlights = Collections.synchronizedList(new ArrayList<>());
    private static final Set<Long> activeChunks = ConcurrentHashMap.newKeySet();
    private static int lastChunkX = Integer.MIN_VALUE;
    private static int lastChunkZ = Integer.MIN_VALUE;
    private static int lastViewDistance = -1;

    private ChunkProcessingManager() {}

    public static void init()
    {
        ClientChunkEvents.CHUNK_LOAD.register(ChunkProcessingManager::handleChunkLoad);
        ClientChunkEvents.CHUNK_UNLOAD.register(ChunkProcessingManager::handleChunkUnload);
        ClientTickEvents.END_CLIENT_TICK.register(client ->
        {
            if (shouldNotProcess())
            {
                lastChunkX = Integer.MIN_VALUE;
                lastChunkZ = Integer.MIN_VALUE;
                lastViewDistance = -1;
                return;
            }
            ClientPlayerEntity player = client.player;
            ClientWorld world = client.world;
            if (player == null || world == null)
                return;
            int chunkX = player.getChunkPos().x;
            int chunkZ = player.getChunkPos().z;
            int view = client.options.getViewDistance().getValue();
            if (chunkX != lastChunkX || chunkZ != lastChunkZ || view != lastViewDistance)
            {
                processLoadedChunks(world);
                lastChunkX = chunkX;
                lastChunkZ = chunkZ;
                lastViewDistance = view;
            }
        });
    }

    public static void clear()
    {
        chunkSpawners.clear();
        activeChunks.clear();
        activeAlerts.clear();
        pendingAlertHighlights.clear();
        lastChunkX = Integer.MIN_VALUE;
        lastChunkZ = Integer.MIN_VALUE;
        lastViewDistance = -1;
    }

    public static void processLoadedChunks(ClientWorld world)
    {
        if (shouldNotProcess() || world == null)
            return;
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null)
            return;

        int viewDistance = client.options.getViewDistance().getValue();
        int centerChunkX = player.getChunkPos().x;
        int centerChunkZ = player.getChunkPos().z;
        var chunkManager = world.getChunkManager();

        Set<Long> desired = new HashSet<>();
        for (int dx = -viewDistance; dx <= viewDistance; dx++)
            for (int dz = -viewDistance; dz <= viewDistance; dz++)
            {
                int chunkX = centerChunkX + dx;
                int chunkZ = centerChunkZ + dz;
                long key = ChunkPos.toLong(chunkX, chunkZ);
                desired.add(key);
                if (activeChunks.add(key))
                {
                    WorldChunk chunk = chunkManager.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                    if (chunk != null)
                        handleChunkLoad(world, chunk);
                }
            }

        Set<Long> toRemove = new HashSet<>(activeChunks);
        toRemove.removeAll(desired);
        for (Long key : toRemove)
        {
            removeChunkEntries(key);
            activeChunks.remove(key);
        }
    }

    public static void rescanCurrentWorld()
    {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        if (world == null)
            return;
        processLoadedChunks(world);
    }

    private static void handleChunkLoad(ClientWorld world, WorldChunk chunk)
    {
        if (shouldNotProcess())
            return;
        if (world == null || chunk == null)
            return;

        long key = chunk.getPos().toLong();
        removeChunkEntries(key);
        List<SpawnerInfo> spawners = extractSpawners(world, chunk);
        if (spawners.isEmpty())
            return;

        chunkSpawners.put(key, spawners.stream()
            .map(info -> info.pos().asLong())
            .collect(Collectors.toCollection(HashSet::new)));
        activeChunks.add(key);
        for (SpawnerInfo info : spawners)
            BlockBank.add(info);

        evaluateClusters(spawners);
    }

    private static void handleChunkUnload(ClientWorld world, WorldChunk chunk)
    {
        if (chunk == null)
            return;
        long key = chunk.getPos().toLong();
        removeChunkEntries(key);
        activeChunks.remove(key);
    }

    private static void removeChunkEntries(long key)
    {
        Set<Long> removed = chunkSpawners.remove(key);
        if (removed == null || removed.isEmpty())
            return;
        Set<BlockPos> positions = removed.stream().map(BlockPos::fromLong).collect(Collectors.toSet());
        BlockBank.removeAll(positions);
        ClusterManager.removeBackgroundHighlights(removed);
        invalidateAlerts(removed);
    }

    private static void invalidateAlerts(Set<Long> removedPositions)
    {
        if (removedPositions == null || removedPositions.isEmpty())
            return;
        pruneAlertSets(activeAlerts, removedPositions);
        pruneAlertSets(pendingAlertHighlights, removedPositions);
    }

    private static void pruneAlertSets(List<Set<Long>> source, Set<Long> removedPositions)
    {
        synchronized (source)
        {
            source.removeIf(cluster -> intersects(cluster, removedPositions));
        }
    }

    private static boolean intersects(Set<Long> alert, Set<Long> positions)
    {
        for (Long value : positions)
            if (alert.contains(value))
                return true;
        return false;
    }

    private static boolean shouldNotProcess()
    {
        return RadarClient.config == null || !RadarClient.config.processChunksOnGeneration;
    }

    private static List<SpawnerInfo> extractSpawners(ClientWorld world, WorldChunk chunk)
    {
        if (chunk.getBlockEntities().isEmpty())
            return Collections.emptyList();

        List<SpawnerInfo> found = new ArrayList<>();
        chunk.getBlockEntities().forEach((pos, blockEntity) ->
        {
            if (blockEntity instanceof MobSpawnerBlockEntity)
            {
                SpawnerInfo info = BlockBank.createSpawnerInfo(world, pos);
                found.add(info);
            }
        });
        return found;
    }

    private static void evaluateClusters(List<SpawnerInfo> newlyFound)
    {
        if (newlyFound.isEmpty())
            return;

        var client = MinecraftClient.getInstance();
        var player = client.player;
        var config = RadarClient.config;
        if (player == null || config == null)
            return;

        int threshold = Math.max(2, config.backgroundClusterAlertThreshold);
        double proximity = Math.max(8, config.backgroundClusterProximity);
        double proximitySq = proximity * proximity;

        List<SpawnerInfo> allSpawners = BlockBank.getAll();
        double activationRadius = RadarClient.getActivationRadius();
        for (SpawnerInfo origin : newlyFound)
        {
            List<SpawnerInfo> nearby = new ArrayList<>();
            for (SpawnerInfo info : allSpawners)
                if (info.pos().getSquaredDistance(origin.pos()) <= proximitySq)
                    nearby.add(info);

            if (nearby.size() < threshold)
                continue;

            List<SpawnerCluster> clusters = SpawnerCluster.findClusters(
                player,
                nearby,
                activationRadius,
                SpawnerCluster.SortType.NO_SORT
            );

            for (SpawnerCluster cluster : clusters)
            {
                if (cluster.spawners().size() < threshold)
                    continue;
                if (!isClusterWithinRange(cluster, origin.pos(), proximitySq))
                    continue;
                if (!shouldAlert(cluster))
                    continue;
                if (config.autoHighlightAlertedClusters)
                    ClusterManager.addBackgroundHighlights(cluster.spawners());
                sendAlert(cluster, player);
            }
        }
    }

    private static boolean shouldAlert(SpawnerCluster cluster)
    {
        Set<Long> current = cluster.spawners().stream()
            .map(info -> info.pos().asLong())
            .collect(Collectors.toSet());

        synchronized (activeAlerts)
        {
            for (Set<Long> previous : activeAlerts)
                if (!Collections.disjoint(previous, current))
                    return false;
            activeAlerts.add(current);
        }

        synchronized (pendingAlertHighlights)
        {
            pendingAlertHighlights.add(new HashSet<>(current));
        }
        return true;
    }

    public static boolean consumeAlertForCluster(SpawnerCluster cluster)
    {
        Set<Long> current = clusterToSet(cluster);
        synchronized (pendingAlertHighlights)
        {
            java.util.Iterator<Set<Long>> iterator = pendingAlertHighlights.iterator();
            while (iterator.hasNext())
            {
                Set<Long> pending = iterator.next();
                if (!Collections.disjoint(pending, current))
                {
                    iterator.remove();
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isClusterWithinRange(SpawnerCluster cluster, BlockPos origin, double proximitySq)
    {
        for (SpawnerInfo info : cluster.spawners())
            if (info.pos().getSquaredDistance(origin) <= proximitySq)
                return true;
        return false;
    }

    private static void sendAlert(SpawnerCluster cluster, ClientPlayerEntity player)
    {
        MutableText alert = Text.translatable(
            "chat.spawn_radar.cluster_alert",
            cluster.spawners().size()
        ).formatted(Formatting.LIGHT_PURPLE);
        player.sendMessage(alert, false);
    }

    private static Set<Long> clusterToSet(SpawnerCluster cluster)
    {
        Set<Long> ids = new HashSet<>();
        for (SpawnerInfo info : cluster.spawners())
            ids.add(info.pos().asLong());
        return ids;
    }
}

