package cc.hachem;

import cc.hachem.config.ConfigManager;
import cc.hachem.config.ConfigSerializer;
import cc.hachem.core.BlockBank;
import cc.hachem.core.ClusterManager;
import cc.hachem.core.CommandManager;
import cc.hachem.core.KeyManager;
import cc.hachem.core.SpawnerCluster;
import cc.hachem.core.SpawnerInfo;
import cc.hachem.core.VolumeHighlightManager;
import cc.hachem.hud.HudRenderer;
import cc.hachem.hud.PanelWidget;
import cc.hachem.network.RadarHandshakePayload;
import cc.hachem.renderer.BlockHighlightRenderer;
import cc.hachem.renderer.BoxOutlineRenderer;
import cc.hachem.renderer.FloatingTextRenderer;
import cc.hachem.renderer.ItemTextureRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

public class RadarClient implements ClientModInitializer
{
    public static final String MOD_ID = "radar";
    public static Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    public static ConfigManager config;
    private static volatile boolean serverSupportsRadar = false;
    private static final double ACTIVATION_RADIUS = 16.0;

    public static ClientPlayerEntity getPlayer()
    {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player;
    }

    public static boolean generateClusters(ClientPlayerEntity source, int radius, String sorting)
    {
        if (!ensureServerEnabled(source))
            return false;

        BlockBank.scanForSpawners(source, radius, () -> generateClustersChild(source, sorting));
        RadarClient.LOGGER.debug("Scheduled cluster generation after scanning for spawners.");
        return true;
    }

    public static void generateClustersChild(ClientPlayerEntity source, String argument)
    {
        List<SpawnerInfo> spawners = BlockBank.getAll();
        if (!validateSpawnerResults(source, spawners))
            return;

        SpawnerCluster.SortType sortType = resolveSortType(argument);
        List<SpawnerCluster> clusters = SpawnerCluster.findClusters(source, spawners, ACTIVATION_RADIUS, sortType);
        persistClusterResults(clusters, sortType);
    }

    public static boolean toggleCluster(ClientPlayerEntity source, String target)
    {
        if (!ensureServerEnabled(source))
            return false;

        List<SpawnerCluster> clusters = ClusterManager.getClusters();
        if (target.equals("all"))
            return toggleAllClusters(source, clusters);
        return toggleSpecificCluster(source, clusters, target);
    }

    public static boolean reset(ClientPlayerEntity player)
    {
        if (!ensureServerEnabled(player))
            return false;

        LOGGER.info("Resetting RadarClient data...");
        int clustersBefore = ClusterManager.getClusters().size();
        int highlightsBefore = ClusterManager.getHighlights().size();

        clearClientState();

        player.sendMessage(Text.translatable("chat.spawn_radar.reset"), false);
        LOGGER.debug("Cleared {} clusters and {} highlights.", clustersBefore, highlightsBefore);
        return true;
    }

    private void onRender(WorldRenderContext context)
    {
        try
        {
            Set<Integer> highlightedIds = ClusterManager.getHighlightedClusterIds();
            renderHighlightedBlocks(context);
            renderHighlightedRegions(context, highlightedIds);
        }
        catch (Exception e)
        {
            LOGGER.error("Error during rendering: ", e);
        }
        finally
        {
            BlockHighlightRenderer.submit(MinecraftClient.getInstance());
            BoxOutlineRenderer.submit(MinecraftClient.getInstance());
        }
    }

    public void onInitializeClient()
    {
        initializeSubsystems();
        registerEventHandlers();
        LOGGER.info("Initialized successfully.");
    }

    private static boolean ensureServerEnabled(ClientPlayerEntity player)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.isIntegratedServerRunning() || serverSupportsRadar)
            return true;

        if (player != null)
            player.sendMessage(Text.translatable("chat.spawn_radar.disabled"), false);
        LOGGER.warn("Prevented Spawn Radar usage because the connected server does not have the mod installed.");
        return false;
    }

    private void initializeSubsystems()
    {
        LOGGER.info("Initializing RadarClient...");
        ConfigSerializer.load();
        LOGGER.info("Config file loaded.");
        CommandManager.init();
        LOGGER.info("CommandManager initialized.");
        KeyManager.init();
        LOGGER.info("KeyManager initialized.");
        HudRenderer.init();
        LOGGER.info("KeyManager initialized.");
        ItemTextureRenderer.init();
        LOGGER.info("ItemTextureRenderer initialized.");
    }

    private void registerEventHandlers()
    {
        WorldRenderEvents.END_MAIN.register(this::onRender);
        registerHandshakeReceiver();
        registerConnectionCallbacks();
    }

    private void registerHandshakeReceiver()
    {
        ClientPlayNetworking.registerGlobalReceiver(RadarHandshakePayload.ID, (payload, context) ->
            MinecraftClient.getInstance().execute(() ->
            {
                serverSupportsRadar = true;
                LOGGER.info("Spawn Radar features enabled on the current server.");
            })
        );
    }

    private void registerConnectionCallbacks()
    {
        ClientPlayConnectionEvents.INIT.register((handler, client) ->
            serverSupportsRadar = client.isIntegratedServerRunning()
        );

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> handleJoinEvent(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> serverSupportsRadar = false);
    }

    private static void handleJoinEvent(MinecraftClient client)
    {
        if (!client.isIntegratedServerRunning())
        {
            ClientPlayNetworking.send(RadarHandshakePayload.INSTANCE);
            LOGGER.debug("Requested Spawn Radar handshake from server.");
        }
        else
            serverSupportsRadar = true;

        clearClientState();
        LOGGER.info("Reset initial data.");

        HudRenderer.build();
        LOGGER.info("Built HudRenderer widgets.");
    }

    private static void clearClientState()
    {
        ClusterManager.unhighlightAllClusters();
        ClusterManager.getClusters().clear();
        BlockHighlightRenderer.clearRegionMeshCache();
        BlockBank.clear();
        VolumeHighlightManager.clear();
        PanelWidget.refresh();
    }

    private static boolean validateSpawnerResults(ClientPlayerEntity source, List<SpawnerInfo> spawners)
    {
        if (!spawners.isEmpty())
            return true;

        source.sendMessage(Text.translatable("chat.spawn_radar.none"), false);
        LOGGER.warn("No spawners found for cluster generation.");
        return false;
    }

    private static SpawnerCluster.SortType resolveSortType(String argument)
    {
        if ("proximity".equals(argument))
            return SpawnerCluster.SortType.BY_PROXIMITY;
        if ("size".equals(argument))
            return SpawnerCluster.SortType.BY_SIZE;
        return config.defaultSortType;
    }

    private static void persistClusterResults(List<SpawnerCluster> clusters, SpawnerCluster.SortType sortType)
    {
        ClusterManager.setClusters(clusters);
        BlockHighlightRenderer.clearRegionMeshCache();
        LOGGER.info("Generated {} clusters using sort type {}", clusters.size(), sortType);

        if (config.highlightAfterScan)
            ClusterManager.highlightAllClusters();
        PanelWidget.refresh();
    }

    private static boolean toggleAllClusters(ClientPlayerEntity source, List<SpawnerCluster> clusters)
    {
        if (clusters.isEmpty())
        {
            source.sendMessage(Text.translatable("chat.spawn_radar.no_clusters_to_toggle"), false);
            LOGGER.warn("Attempted to toggle all clusters but none exist.");
            return false;
        }

        boolean anyHighlighted = !ClusterManager.getHighlightedClusterIds().isEmpty();
        if (anyHighlighted)
        {
            ClusterManager.unhighlightAllClusters();
            LOGGER.info("Un-highlighted all {} clusters", clusters.size());
        }
        else
        {
            ClusterManager.highlightAllClusters();
            LOGGER.info("Highlighted all {} clusters", clusters.size());
        }
        return true;
    }

    private static boolean toggleSpecificCluster(ClientPlayerEntity source, List<SpawnerCluster> clusters, String target)
    {
        try
        {
            int clusterId = Integer.parseInt(target);
            if (clusters.stream().noneMatch(c -> c.id() == clusterId))
            {
                source.sendMessage(Text.translatable("chat.spawn_radar.invalid_id"), false);
                LOGGER.warn("Attempted to toggle invalid cluster ID {}", clusterId);
                return false;
            }

            ClusterManager.toggleHighlightCluster(clusterId);
            LOGGER.info("Toggled highlight for cluster #{}", clusterId);
            return true;
        }
        catch (NumberFormatException e)
        {
            source.sendMessage(Text.translatable("chat.spawn_radar.invalid_id_number"), false);
            return false;
        }
    }

    private void renderHighlightedBlocks(WorldRenderContext context)
    {
        boolean useOutline = config.useOutlineSpawnerHighlight;
        boolean defaultSpawnVolume = config.showSpawnerSpawnVolume;
        boolean defaultMobCapVolume = config.showSpawnerMobCapVolume;
        int outlineColor = config.spawnerOutlineColor;
        float outlineThickness = Math.max(0.05f, config.spawnerOutlineThickness);
        float alpha = config.spawnerHighlightOpacity / 100f;
        float spawnVolumeAlpha = clamp01(config.spawnVolumeOpacity / 100f);
        float mobCapVolumeAlpha = clamp01(config.mobCapVolumeOpacity / 100f);
        for (SpawnerInfo info : ClusterManager.getHighlights())
        {
            BlockPos pos = info.pos();
            if (useOutline)
            {
                BoxOutlineRenderer.draw(context, pos, outlineColor, alpha, outlineThickness);
                boolean spawnEnabled = VolumeHighlightManager.isSpawnVolumeEnabled(pos, defaultSpawnVolume);
                if (spawnEnabled && spawnVolumeAlpha > 0f)
                {
                    float volumeThickness = Math.max(0.05f, outlineThickness * 0.65f);
                    renderSpawnVolume(context, info, config.spawnVolumeColor, spawnVolumeAlpha, volumeThickness);
                }
                boolean mobEnabled = VolumeHighlightManager.isMobCapVolumeEnabled(pos, defaultMobCapVolume);
                if (mobEnabled && mobCapVolumeAlpha > 0f)
                {
                    float mobCapThickness = Math.max(0.05f, outlineThickness * 0.45f);
                    renderMobCapVolume(context, info, config.mobCapVolumeColor, mobCapVolumeAlpha, mobCapThickness);
                }
            }
            else
                BlockHighlightRenderer.draw(context, pos, config.spawnerHighlightColor, alpha);
            renderClusterLabel(context, pos);
        }
        renderManualVolumes(context, defaultSpawnVolume, defaultMobCapVolume);
    }

    private void renderClusterLabel(WorldRenderContext context, BlockPos pos)
    {
        List<Integer> ids = ClusterManager.getClusterIDAt(pos);
        if (ids.isEmpty())
            return;

        FloatingTextRenderer.renderBlockNametag(context, pos, buildClusterLabel(ids));
    }

    private static String buildClusterLabel(List<Integer> ids)
    {
        StringBuilder label = new StringBuilder();
        for (int i = 0; i < ids.size(); i++)
        {
            label.append("Cluster #").append(ids.get(i));
            if (i < ids.size() - 1)
                label.append("\n");
        }
        return label.toString();
    }

    private void renderHighlightedRegions(WorldRenderContext context, Set<Integer> highlightedIds)
    {
        for (SpawnerCluster cluster : ClusterManager.getClusters())
        {
            if (!highlightedIds.contains(cluster.id()))
                continue;

            int spawnerCount = cluster.spawners().size();
            if (spawnerCount < config.minimumSpawnersForRegion)
                continue;

            int clusterColor = ConfigManager.getClusterColor(spawnerCount);
            List<BlockPos> region = cluster.intersectionRegion();
            BlockHighlightRenderer.fillRegionMesh(context, cluster.id(), region, clusterColor, config.regionHighlightOpacity / 100f);
        }
    }

    private void renderSpawnVolume(WorldRenderContext context, SpawnerInfo info, int color, float alpha, float thickness)
    {
        SpawnVolume volume = computeSpawnVolume(info);
        if (volume == null)
            return;

        BoxOutlineRenderer.draw(
            context,
            volume.originX(),
            volume.originY(),
            volume.originZ(),
            volume.width(),
            volume.height(),
            volume.depth(),
            color,
            alpha,
            thickness);
    }

    private static SpawnVolume computeSpawnVolume(SpawnerInfo info)
    {
        BlockPos pos = info.pos();
        double centerX = pos.getX() + 0.5;
        double centerZ = pos.getZ() + 0.5;

        double baseHorizontalSpan = 8.0;
        double baseVerticalSpan = 3.0;
        double entityWidth = 0.0;
        double entityHeight = 0.0;
        EntityType<?> entityType = info.entityType();
        if (entityType != null)
        {
            EntityDimensions dims = entityType.getDimensions();
            entityWidth = Math.max(0.0, dims.width());
            entityHeight = Math.max(0.0, dims.height());
        }

        double horizontalSpan = entityWidth+8.0;
        double verticalSpan = entityHeight+2.0;
        double originY = pos.getY() - 1.0;
        double originX = centerX - horizontalSpan / 2.0;
        double originZ = centerZ - horizontalSpan / 2.0;

        return new SpawnVolume(originX, originY, originZ, horizontalSpan, verticalSpan, horizontalSpan);
    }

    private void renderManualVolumes(WorldRenderContext context, boolean defaultSpawnVolume, boolean defaultMobCapVolume)
    {
        float spawnAlpha = clamp01(config.spawnVolumeOpacity / 100f);
        float mobAlpha = clamp01(config.mobCapVolumeOpacity / 100f);

        if (!defaultSpawnVolume && spawnAlpha > 0f)
        {
            float spawnThickness = Math.max(0.05f, config.spawnerOutlineThickness * 0.65f);
            for (BlockPos pos : VolumeHighlightManager.getForcedSpawnShows())
            {
                SpawnerInfo info = resolveSpawner(pos);
                if (info != null)
                    renderSpawnVolume(context, info, config.spawnVolumeColor, spawnAlpha, spawnThickness);
            }
        }

        if (!defaultMobCapVolume && mobAlpha > 0f)
        {
            float mobThickness = Math.max(0.05f, config.spawnerOutlineThickness * 0.45f);
            for (BlockPos pos : VolumeHighlightManager.getForcedMobCapShows())
            {
                SpawnerInfo info = resolveSpawner(pos);
                if (info != null)
                    renderMobCapVolume(context, info, config.mobCapVolumeColor, mobAlpha, mobThickness);
            }
        }
    }

    private SpawnerInfo resolveSpawner(BlockPos pos)
    {
        SpawnerInfo info = BlockBank.get(pos);
        if (info != null)
            return info;

        for (SpawnerCluster cluster : ClusterManager.getClusters())
            for (SpawnerInfo candidate : cluster.spawners())
                if (candidate.pos().equals(pos))
                    return candidate;
        return null;
    }

    private void renderMobCapVolume(WorldRenderContext context, SpawnerInfo info, int color, float alpha, float thickness)
    {
        BlockPos pos = info.pos();
        double centerX = pos.getX() + 0.5;
        double centerY = pos.getY() + 0.5;
        double centerZ = pos.getZ() + 0.5;

        double span = 9.0;
        double originX = centerX - span / 2.0;
        double originY = centerY - span / 2.0;
        double originZ = centerZ - span / 2.0;

        BoxOutlineRenderer.draw(
            context,
            originX,
            originY,
            originZ,
            span,
            span,
            span,
            color,
            alpha,
            thickness);
    }

    private record SpawnVolume(double originX, double originY, double originZ,
                               double width, double height, double depth) {}

    private static float clamp01(float value)
    {
        return Math.max(0f, Math.min(1f, value));
    }
}