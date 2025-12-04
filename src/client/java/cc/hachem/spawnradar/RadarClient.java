package cc.hachem.spawnradar;

import cc.hachem.spawnradar.config.ConfigManager;
import cc.hachem.spawnradar.config.ConfigSerializer;
import cc.hachem.spawnradar.core.BlockBank;
import cc.hachem.spawnradar.core.ChunkProcessingManager;
import cc.hachem.spawnradar.core.ClusterManager;
import cc.hachem.spawnradar.core.CommandManager;
import cc.hachem.spawnradar.guide.GuideBookManager;
import cc.hachem.spawnradar.core.KeyManager;
import cc.hachem.spawnradar.core.SpawnerCluster;
import cc.hachem.spawnradar.core.SpawnerInfo;
import cc.hachem.spawnradar.core.SpawnVolumeHelper;
import cc.hachem.spawnradar.core.SpawnerEfficiencyManager;
import cc.hachem.spawnradar.core.SpawnerMobCapStatusManager;
import cc.hachem.spawnradar.core.SpawnerLightLevelManager;
import cc.hachem.spawnradar.core.VolumeHighlightManager;
import cc.hachem.spawnradar.hud.HudRenderer;
import cc.hachem.spawnradar.hud.PanelWidget;
import cc.hachem.spawnradar.network.RadarHandshakePayload;
import cc.hachem.spawnradar.renderer.BlockHighlightRenderer;
import cc.hachem.spawnradar.renderer.BoxOutlineRenderer;
import cc.hachem.spawnradar.renderer.FloatingTextRenderer;
import cc.hachem.spawnradar.renderer.ItemTextureRenderer;
import cc.hachem.spawnradar.renderer.MobPuppetRenderer;
import cc.hachem.spawnradar.core.SpawnerEfficiencyAdvisor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RadarClient implements ClientModInitializer
{

    public static final String MOD_ID = "radar";
    public static Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    public static ConfigManager config;
    private static volatile boolean serverSupportsRadar = false;
    private static final double ACTIVATION_RADIUS = 16.0;
    private static volatile boolean welcomeMessagePending = false;
    private static volatile boolean welcomeMessageSent = false;
    private static String cachedVersionString;

    public static ClientPlayerEntity getPlayer()
    {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player;
    }

    public static boolean generateClusters(ClientPlayerEntity source, int radius,
                                           String sorting, boolean forceRescan)
    {
        if (ensureServerDisabled(source))
            return false;

        if (!forceRescan && config.useCachedSpawnersForScan && BlockBank.hasCachedSpawners())
        {
            List<SpawnerInfo> cached = BlockBank.getWithinChunkRadius(source.getBlockPos(), radius);
            if (!cached.isEmpty())
            {
                List<SpawnerInfo> snapshot = new ArrayList<>(cached);
                MinecraftClient.getInstance().execute(() -> runClusterPipeline(source, snapshot, sorting));
                LOGGER.debug("Used cached spawner data for cluster generation ({} entries).", snapshot.size());
                return true;
            }
        }

        BlockBank.scanForSpawners(source, radius, () -> generateClustersChild(source, sorting));
        RadarClient.LOGGER.debug("Scheduled cluster generation after scanning for spawners.");
        return true;
    }

    public static void generateClustersChild(ClientPlayerEntity source, String argument)
    {
        List<SpawnerInfo> spawners = new ArrayList<>(BlockBank.getAll());
        runClusterPipeline(source, spawners, argument);
    }

    private static void runClusterPipeline(ClientPlayerEntity source, List<SpawnerInfo> spawners, String argument)
    {
        if (!validateSpawnerResults(source, spawners))
            return;

        BlockBank.markManualDataReady();
        SpawnerCluster.SortType sortType = resolveSortType(argument);
        List<SpawnerCluster> clusters = SpawnerCluster.findClusters(source, spawners, ACTIVATION_RADIUS, sortType);
        persistClusterResults(clusters, sortType);
    }

    public static boolean toggleCluster(ClientPlayerEntity source,
                                        String target)
    {
        if (ensureServerDisabled(source)) return false;

        List<SpawnerCluster> clusters = ClusterManager.getClusters();
        if (target.equals("all")) return toggleAllClusters(source, clusters);
        return toggleSpecificCluster(source, clusters, target);
    }

    public static boolean reset(ClientPlayerEntity player)
    {
        if (ensureServerDisabled(player)) return false;

        LOGGER.info("Resetting RadarClient data...");
        int clustersBefore = ClusterManager.getClusters().size();
        int highlightsBefore = ClusterManager.getHighlights().size();

        clearClientState();

        LOGGER.debug(
            "Cleared {} clusters and {} highlights.",
            clustersBefore,
            highlightsBefore
        );
        return true;
    }

    private void onRender(WorldRenderContext context)
    {
        try
        {
            Set<Integer> highlightedIds =
                ClusterManager.getHighlightedClusterIds();
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

    private static boolean ensureServerDisabled(ClientPlayerEntity player)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        if (
            client.isIntegratedServerRunning() || serverSupportsRadar
        ) return false;

        if (player != null) player.sendMessage(
            Text.translatable("chat.spawn_radar.disabled"),
            false
        );
        LOGGER.warn(
            "Prevented Spawn Radar usage because the connected server does not have the mod installed."
        );
        return true;
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
        LOGGER.info("HudRenderer initialized.");
        GuideBookManager.init();
        LOGGER.info("GuideBookManager initialized.");
        ChunkProcessingManager.init();
        LOGGER.info("ChunkProcessingManager initialized.");
        ItemTextureRenderer.init();
        LOGGER.info("ItemTextureRenderer initialized.");
        SpawnerEfficiencyAdvisor.init();
        LOGGER.info("SpawnerEfficiencyAdvisor initialized.");
    }

    private void registerEventHandlers()
    {
        WorldRenderEvents.END_MAIN.register(this::onRender);
        registerHandshakeReceiver();
        registerConnectionCallbacks();
        ClientTickEvents.END_CLIENT_TICK.register(RadarClient::handleClientTick);
    }

    private void registerHandshakeReceiver()
    {
        ClientPlayNetworking.registerGlobalReceiver(
            RadarHandshakePayload.ID,
            (payload, context) ->
                MinecraftClient.getInstance().execute(() ->
                {
                    serverSupportsRadar = true;
                    LOGGER.info(
                        "Spawn Radar features enabled on the current server."
                    );
                    scheduleWelcomeMessage(MinecraftClient.getInstance());
                })
        );
    }

    private void registerConnectionCallbacks()
    {
        ClientPlayConnectionEvents.INIT.register((handler, client) ->
            serverSupportsRadar = client.isIntegratedServerRunning()
        );

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
            handleJoinEvent(client)
        );
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
        {
            serverSupportsRadar = false;
            MobPuppetRenderer.clearCache();
            ChunkProcessingManager.clear();
            resetWelcomeMessageState();
        });
    }

    private static void handleJoinEvent(MinecraftClient client)
    {
        resetWelcomeMessageState();
        if (!client.isIntegratedServerRunning())
        {
            ClientPlayNetworking.send(RadarHandshakePayload.INSTANCE);
            LOGGER.debug("Requested Spawn Radar handshake from server.");
        }
        else
        {
            serverSupportsRadar = true;
            scheduleWelcomeMessage(client);
        }

        clearClientState();
        LOGGER.info("Reset initial data.");

        HudRenderer.build();
        LOGGER.info("Built HudRenderer widgets.");
        ChunkProcessingManager.processLoadedChunks(client.world);
    }

    private static void clearClientState()
    {
        ClusterManager.unhighlightAllClusters();
        ClusterManager.clearBackgroundHighlights();
        ClusterManager.getClusters().clear();
        BlockHighlightRenderer.clearRegionMeshCache();
        BoxOutlineRenderer.clearMeshCache();
        BlockBank.clear();
        VolumeHighlightManager.clear();
        SpawnerEfficiencyManager.clear();
        SpawnerMobCapStatusManager.clear();
        SpawnerLightLevelManager.clear();
        MobPuppetRenderer.clearCache();
        ChunkProcessingManager.clear();
        PanelWidget.refresh();
    }

    private static boolean validateSpawnerResults(ClientPlayerEntity source,
                                                  List<SpawnerInfo> spawners)
    {
        if (!spawners.isEmpty()) return true;

        source.sendMessage(Text.translatable("chat.spawn_radar.none"), false);
        LOGGER.warn("No spawners found for cluster generation.");
        return false;
    }

    private static SpawnerCluster.SortType resolveSortType(String argument)
    {
        if (
            "proximity".equals(argument)
        ) return SpawnerCluster.SortType.BY_PROXIMITY;
        if ("size".equals(argument)) return SpawnerCluster.SortType.BY_SIZE;
        return config.defaultSortType;
    }

    private static void persistClusterResults(List<SpawnerCluster> clusters,
                                              SpawnerCluster.SortType sortType)
    {
        ClusterManager.setClusters(clusters);
        BlockHighlightRenderer.clearRegionMeshCache();
        BoxOutlineRenderer.clearMeshCache();
        LOGGER.info(
            "Generated {} clusters using sort type {}",
            clusters.size(),
            sortType
        );

        if (config.autoHighlightAlertedClusters)
        {
            int alertThreshold = Math.max(2, config.backgroundClusterAlertThreshold);
            for (SpawnerCluster cluster : clusters)
            {
                if (cluster.spawners().size() < alertThreshold) continue;
                if (
                    ChunkProcessingManager.consumeAlertForCluster(cluster)
                ) ClusterManager.highlightCluster(cluster.id());
            }
        }

        if (config.highlightAfterScan) ClusterManager.highlightAllClusters();
        PanelWidget.refresh();
    }

    private static boolean toggleAllClusters(ClientPlayerEntity source,
                                             List<SpawnerCluster> clusters)
    {
        if (clusters.isEmpty())
        {
            source.sendMessage(
                Text.translatable("chat.spawn_radar.no_clusters_to_toggle"),
                false
            );
            LOGGER.warn("Attempted to toggle all clusters but none exist.");
            return false;
        }

        boolean anyHighlighted =
            !ClusterManager.getHighlightedClusterIds().isEmpty();
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

    private static boolean toggleSpecificCluster(ClientPlayerEntity source,
                                                 List<SpawnerCluster> clusters,
                                                 String target)
    {
        try
        {
            int clusterId = Integer.parseInt(target);
            if (clusters.stream().noneMatch(c -> c.id() == clusterId))
            {
                source.sendMessage(
                    Text.translatable("chat.spawn_radar.invalid_id"),
                    false
                );
                LOGGER.warn(
                    "Attempted to toggle invalid cluster ID {}",
                    clusterId
                );
                return false;
            }

            ClusterManager.toggleHighlightCluster(clusterId);
            LOGGER.info("Toggled highlight for cluster #{}", clusterId);
            return true;
        }
        catch (NumberFormatException e)
        {
            source.sendMessage(
                Text.translatable("chat.spawn_radar.invalid_id_number"),
                false
            );
            return false;
        }
    }

    private void renderHighlightedBlocks(WorldRenderContext context)
    {
        boolean useOutline = config.useOutlineSpawnerHighlight;
        boolean defaultSpawnVolume = config.showSpawnerSpawnVolume;
        boolean defaultMobCapVolume = config.showSpawnerMobCapVolume;
        boolean defaultEfficiencyLabel = config.showSpawnerEfficiencyLabel;
        boolean defaultMobCapStatus = config.showSpawnerMobCapStatus;
        boolean defaultLightLevels = config.showSpawnerLightLevels;
        int outlineColor = config.spawnerOutlineColor;
        float outlineThickness = Math.max(
            0.05f,
            config.spawnerOutlineThickness
        );
        float alpha = config.spawnerHighlightOpacity / 100f;
        float spawnVolumeAlpha = clamp01(config.spawnVolumeOpacity / 100f);
        float mobCapVolumeAlpha = clamp01(config.mobCapVolumeOpacity / 100f);
        Set<BlockPos> highlightedPositions = new HashSet<>();
        for (SpawnerInfo info : ClusterManager.getHighlights())
        {
            BlockPos pos = info.pos();
            highlightedPositions.add(pos);
            if (useOutline)
            {
                BoxOutlineRenderer.draw(
                    context,
                    pos,
                    outlineColor,
                    alpha,
                    outlineThickness
                );
                boolean spawnEnabled =
                    VolumeHighlightManager.isSpawnVolumeEnabled(
                        pos,
                        defaultSpawnVolume
                    );
                if (spawnEnabled && spawnVolumeAlpha > 0f)
                {
                    float volumeThickness = Math.max(
                        0.05f,
                        outlineThickness * 0.65f
                    );
                    renderSpawnVolume(
                        context,
                        info,
                        config.spawnVolumeColor,
                        spawnVolumeAlpha,
                        volumeThickness
                    );
                }
                boolean mobEnabled =
                    VolumeHighlightManager.isMobCapVolumeEnabled(
                        pos,
                        defaultMobCapVolume
                    );
                if (mobEnabled && mobCapVolumeAlpha > 0f)
                {
                    float mobCapThickness = Math.max(
                        0.05f,
                        outlineThickness * 0.45f
                    );
                    renderMobCapVolume(
                        context,
                        info,
                        config.mobCapVolumeColor,
                        mobCapVolumeAlpha,
                        mobCapThickness
                    );
                }
            } else BlockHighlightRenderer.draw(
                context,
                pos,
                config.spawnerHighlightColor,
                alpha
            );
            renderLightLevelOverlay(context, info, defaultLightLevels);
            renderSpawnerLabel(context, info, defaultEfficiencyLabel, defaultMobCapStatus);
        }
        renderManualVolumes(context, defaultSpawnVolume, defaultMobCapVolume);
        renderForcedLightLevelOverlays(context, defaultLightLevels, highlightedPositions);
        renderForcedEfficiencyLabels(context, defaultEfficiencyLabel, defaultMobCapStatus, highlightedPositions);
        renderForcedMobCapStatusLabels(context, defaultEfficiencyLabel, defaultMobCapStatus, highlightedPositions);
    }

    private void renderSpawnerLabel(WorldRenderContext context,
                                    SpawnerInfo info,
                                    boolean defaultEfficiencyLabel,
                                    boolean defaultMobCapStatus)
    {
        BlockPos pos = info.pos();
        List<Integer> ids = ClusterManager.getClusterIDAt(pos);
        var world = MinecraftClient.getInstance().world;
        Map<Integer, Double> clusterEfficiencies = ids.isEmpty() || world == null
            ? Collections.emptyMap()
            : computeClusterEfficiencyMap(world, ids);

        boolean efficiencyEnabled = SpawnerEfficiencyManager.isEnabled(pos, defaultEfficiencyLabel);
        boolean mobCapLabelEnabled = SpawnerMobCapStatusManager.isEnabled(pos, defaultMobCapStatus);

        String efficiencyLabel = null;
        if (efficiencyEnabled && world != null)
        {
            var result = SpawnerEfficiencyManager.evaluate(world, info);
            if (result != null)
            {
                efficiencyLabel = Text.translatable(
                    "text.spawn_radar.efficiency",
                    SpawnerEfficiencyManager.formatPercentage(result.overall())
                ).getString();
            }
        }

        String mobCapLabel = null;
        if (mobCapLabelEnabled && world != null)
        {
            var status = SpawnerEfficiencyManager.computeMobCapStatus(world, info);
            mobCapLabel = Text.translatable(
                "text.spawn_radar.mob_cap_status",
                status.formatted()
            ).getString();
        }

        if (ids.isEmpty() && efficiencyLabel == null && mobCapLabel == null)
            return;

        StringBuilder label = new StringBuilder();
        if (!ids.isEmpty())
            label.append(buildClusterLabel(ids, clusterEfficiencies));

        if (efficiencyLabel != null)
        {
            if (!label.isEmpty())
                label.append("\n");
            label.append(efficiencyLabel);
        }

        if (mobCapLabel != null)
        {
            if (!label.isEmpty())
                label.append("\n");
            label.append(mobCapLabel);
        }

        FloatingTextRenderer.renderBlockNametag(context, pos, label.toString());
    }

    private static String buildClusterLabel(List<Integer> ids,
                                            Map<Integer, Double> efficiencyByCluster)
    {
        StringBuilder label = new StringBuilder();
        for (int i = 0; i < ids.size(); i++)
        {
            int clusterId = ids.get(i);
            label.append("Cluster #").append(clusterId);
            Double avgEfficiency = efficiencyByCluster.get(clusterId);
            if (avgEfficiency != null)
                label.append(" (Avg ").append(SpawnerEfficiencyManager.formatPercentage(avgEfficiency)).append("%)");
            if (i < ids.size() - 1) label.append("\n");
        }
        return label.toString();
    }

    private static Map<Integer, Double> computeClusterEfficiencyMap(net.minecraft.world.World world,
                                                                    List<Integer> ids)
    {
        if (world == null || ids == null || ids.isEmpty())
            return Collections.emptyMap();

        Map<Integer, Double> averages = new LinkedHashMap<>();
        for (Integer id : ids)
        {
            SpawnerCluster cluster = ClusterManager.getClusterById(id);
            if (cluster == null || cluster.spawners().size() < 2)
                continue;
            double value = SpawnerEfficiencyManager.computeClusterEfficiency(world, cluster);
            if (value >= 0d)
                averages.put(id, value);
        }
        return averages.isEmpty() ? Collections.emptyMap() : averages;
    }

    private void renderHighlightedRegions(WorldRenderContext context,
                                          Set<Integer> highlightedIds)
    {
        for (SpawnerCluster cluster : ClusterManager.getClusters())
        {
            if (!highlightedIds.contains(cluster.id())) continue;

            int spawnerCount = cluster.spawners().size();
            if (spawnerCount < config.minimumSpawnersForRegion) continue;

            int clusterColor = ConfigManager.getClusterColor(spawnerCount);
            List<BlockPos> region = cluster.intersectionRegion();
            BlockHighlightRenderer.fillRegionMesh(
                context,
                cluster.id(),
                region,
                clusterColor,
                config.regionHighlightOpacity / 100f
            );
        }
    }

    private void renderSpawnVolume(WorldRenderContext context,
                                   SpawnerInfo info,
                                   int color,
                                   float alpha,
                                   float thickness)
    {
        SpawnVolumeHelper.SpawnVolume volume = SpawnVolumeHelper.compute(info);
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
            thickness
        );
    }

    private void renderManualVolumes(WorldRenderContext context,
                                     boolean defaultSpawnVolume,
                                     boolean defaultMobCapVolume)
    {
        float spawnAlpha = clamp01(config.spawnVolumeOpacity / 100f);
        float mobAlpha = clamp01(config.mobCapVolumeOpacity / 100f);

        if (!defaultSpawnVolume && spawnAlpha > 0f)
        {
            float spawnThickness = Math.max(
                0.05f,
                config.spawnerOutlineThickness * 0.65f
            );
            for (BlockPos pos : VolumeHighlightManager.getForcedSpawnShows())
            {
                SpawnerInfo info = resolveSpawner(pos);
                if (info != null) renderSpawnVolume(
                    context,
                    info,
                    config.spawnVolumeColor,
                    spawnAlpha,
                    spawnThickness
                );
            }
        }

        if (!defaultMobCapVolume && mobAlpha > 0f)
        {
            float mobThickness = Math.max(
                0.05f,
                config.spawnerOutlineThickness * 0.45f
            );
            for (BlockPos pos : VolumeHighlightManager.getForcedMobCapShows())
            {
                SpawnerInfo info = resolveSpawner(pos);
                if (info != null) renderMobCapVolume(
                    context,
                    info,
                    config.mobCapVolumeColor,
                    mobAlpha,
                    mobThickness
                );
            }
        }
    }

    private void renderForcedLightLevelOverlays(WorldRenderContext context,
                                                boolean defaultLightLevels,
                                                Set<BlockPos> highlightedPositions)
    {
        if (defaultLightLevels)
            return;

        for (BlockPos pos : SpawnerLightLevelManager.getForcedShows())
        {
            if (highlightedPositions.contains(pos))
                continue;
            SpawnerInfo info = resolveSpawner(pos);
            if (info != null)
                renderLightLevelOverlay(context, info, false);
        }
    }

    private void renderForcedEfficiencyLabels(WorldRenderContext context,
                                              boolean defaultEfficiencyLabel,
                                              boolean defaultMobCapStatus,
                                              Set<BlockPos> highlightedPositions)
    {
        if (defaultEfficiencyLabel)
            return;

        for (BlockPos pos : SpawnerEfficiencyManager.getForcedShows())
        {
            if (highlightedPositions.contains(pos))
                continue;
            SpawnerInfo info = resolveSpawner(pos);
            if (info != null)
                renderSpawnerLabel(context, info, false, defaultMobCapStatus);
        }
    }

    private void renderForcedMobCapStatusLabels(WorldRenderContext context,
                                                boolean defaultEfficiencyLabel,
                                                boolean defaultMobCapStatus,
                                                Set<BlockPos> highlightedPositions)
    {
        if (defaultMobCapStatus)
            return;

        for (BlockPos pos : SpawnerMobCapStatusManager.getForcedShows())
        {
            if (highlightedPositions.contains(pos))
                continue;
            SpawnerInfo info = resolveSpawner(pos);
            if (info != null)
                renderSpawnerLabel(context, info, defaultEfficiencyLabel, false);
        }
    }

    private SpawnerInfo resolveSpawner(BlockPos pos)
    {
        SpawnerInfo info = BlockBank.get(pos);
        if (info != null) return info;

        for (SpawnerCluster cluster : ClusterManager.getClusters())
            for (SpawnerInfo candidate : cluster.spawners())
                if (candidate.pos().equals(pos)) return candidate;
        return null;
    }

    private void renderMobCapVolume(WorldRenderContext context,
                                    SpawnerInfo info,
                                    int color,
                                    float alpha,
                                    float thickness)
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
            thickness
        );
    }

    private void renderLightLevelOverlay(WorldRenderContext context,
                                         SpawnerInfo info,
                                         boolean defaultLightLevels)
    {
        if (!SpawnerLightLevelManager.isEnabled(info.pos(), defaultLightLevels))
            return;

        MinecraftClient client = MinecraftClient.getInstance();
        World world = client.world;
        if (world == null)
            return;

        SpawnVolumeHelper.SpawnVolume volume = SpawnVolumeHelper.compute(info);
        if (volume == null)
            return;

        SpawnVolumeHelper.VolumeBounds bounds = SpawnVolumeHelper.computeBlockBounds(world, volume);
        if (bounds == null)
            return;

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++)
        {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++)
            {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++)
                {
                    mutable.set(x, y, z);
                    int lightLevel = computeCombinedLightLevel(world, mutable);
                    int color = selectLightLevelColor(lightLevel);
                    FloatingTextRenderer.render(
                        context,
                        mutable,
                        Integer.toString(lightLevel),
                        0.0125f,
                        color,
                        0.5f,
                        0.55f,
                        0.5f
                    );
                }
            }
        }
    }

    private static int computeCombinedLightLevel(World world, BlockPos pos)
    {
        int blockLight = world.getLightLevel(LightType.BLOCK, pos);
        int skyLight = world.getLightLevel(LightType.SKY, pos);
        return Math.min(15, Math.max(blockLight, skyLight));
    }

    private static int selectLightLevelColor(int lightLevel)
    {
        if (lightLevel <= 0)
            return 0xFF4CAF50;
        if (lightLevel <= 7)
            return 0xFFFFC107;
        return 0xFFF44336;
    }

    private static float clamp01(float value)
    {
        return Math.max(0f, Math.min(1f, value));
    }

    private static void handleClientTick(MinecraftClient client)
    {
        if (!welcomeMessagePending)
            return;
        if (isWelcomeMessageDisabled())
        {
            welcomeMessagePending = false;
            return;
        }
        if (welcomeMessageSent)
            return;
        if (!client.isIntegratedServerRunning() && !serverSupportsRadar)
            return;
        ClientPlayerEntity player = client.player;
        if (player == null)
            return;
        welcomeMessagePending = false;
        welcomeMessageSent = true;
        player.sendMessage(Text.translatable("chat.spawn_radar.welcome", getVersionString()), false);
    }

    private static void scheduleWelcomeMessage(MinecraftClient client)
    {
        if (isWelcomeMessageDisabled() || welcomeMessageSent)
            return;
        welcomeMessagePending = true;
        if (client != null && client.player != null)
            handleClientTick(client);
    }

    private static void resetWelcomeMessageState()
    {
        welcomeMessagePending = false;
        welcomeMessageSent = false;
    }

    private static boolean isWelcomeMessageDisabled()
    {
        return RadarClient.config == null || !Boolean.TRUE.equals(RadarClient.config.showWelcomeMessage);
    }

    private static String getVersionString()
    {
        if (cachedVersionString == null)
        {
            cachedVersionString = FabricLoader.getInstance()
                .getModContainer(RadarMod.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElseGet(() ->
                    RadarClient.class.getPackage() != null && RadarClient.class.getPackage().getImplementationVersion() != null
                        ? RadarClient.class.getPackage().getImplementationVersion()
                        : "dev");
        }
        return cachedVersionString;
    }

    public static double getActivationRadius()
    {
        return ACTIVATION_RADIUS;
    }
}
