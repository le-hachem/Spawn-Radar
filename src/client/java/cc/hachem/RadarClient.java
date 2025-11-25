package cc.hachem;

import cc.hachem.config.ConfigManager;
import cc.hachem.config.ConfigSerializer;
import cc.hachem.core.BlockBank;
import cc.hachem.core.ChunkProcessingManager;
import cc.hachem.core.ClusterManager;
import cc.hachem.core.CommandManager;
import cc.hachem.guide.GuideBookManager;
import cc.hachem.core.KeyManager;
import cc.hachem.core.SpawnerCluster;
import cc.hachem.core.SpawnerInfo;
import cc.hachem.core.SpawnVolumeHelper;
import cc.hachem.core.SpawnerEfficiencyAdvisor;
import cc.hachem.core.SpawnerEfficiencyManager;
import cc.hachem.core.SpawnerMobCapStatusManager;
import cc.hachem.core.VolumeHighlightManager;
import cc.hachem.hud.HudRenderer;
import cc.hachem.hud.PanelWidget;
import cc.hachem.network.RadarHandshakePayload;
import cc.hachem.renderer.BlockHighlightRenderer;
import cc.hachem.renderer.BoxOutlineRenderer;
import cc.hachem.renderer.FloatingTextRenderer;
import cc.hachem.renderer.ItemTextureRenderer;
import cc.hachem.renderer.MobPuppetRenderer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RadarClient implements ClientModInitializer {

    public static final String MOD_ID = "radar";
    public static Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    public static ConfigManager config;
    private static volatile boolean serverSupportsRadar = false;
    private static final double ACTIVATION_RADIUS = 16.0;

    public static ClientPlayerEntity getPlayer() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player;
    }

    public static boolean generateClusters(
        ClientPlayerEntity source,
        int radius,
        String sorting
    ) {
        return generateClusters(source, radius, sorting, false);
    }

    public static boolean generateClusters(
        ClientPlayerEntity source,
        int radius,
        String sorting,
        boolean forceRescan
    ) {
        if (!ensureServerEnabled(source)) return false;

        if (
            !forceRescan &&
            config.useCachedSpawnersForScan &&
            BlockBank.hasCachedSpawners()
        ) {
            List<SpawnerInfo> cached = BlockBank.getWithinChunkRadius(
                source.getBlockPos(),
                radius
            );
            if (!cached.isEmpty()) {
                List<SpawnerInfo> snapshot = new ArrayList<>(cached);
                MinecraftClient.getInstance().execute(() ->
                    runClusterPipeline(source, snapshot, sorting)
                );
                LOGGER.debug(
                    "Used cached spawner data for cluster generation ({} entries).",
                    snapshot.size()
                );
                return true;
            }
        }

        BlockBank.scanForSpawners(source, radius, () ->
            generateClustersChild(source, sorting)
        );
        RadarClient.LOGGER.debug(
            "Scheduled cluster generation after scanning for spawners."
        );
        return true;
    }

    public static void generateClustersChild(
        ClientPlayerEntity source,
        String argument
    ) {
        List<SpawnerInfo> spawners = new ArrayList<>(BlockBank.getAll());
        runClusterPipeline(source, spawners, argument);
    }

    private static void runClusterPipeline(
        ClientPlayerEntity source,
        List<SpawnerInfo> spawners,
        String argument
    ) {
        if (!validateSpawnerResults(source, spawners)) return;

        BlockBank.markManualDataReady();
        SpawnerCluster.SortType sortType = resolveSortType(argument);
        List<SpawnerCluster> clusters = SpawnerCluster.findClusters(
            source,
            spawners,
            ACTIVATION_RADIUS,
            sortType
        );
        persistClusterResults(clusters, sortType);
    }

    public static boolean toggleCluster(
        ClientPlayerEntity source,
        String target
    ) {
        if (!ensureServerEnabled(source)) return false;

        List<SpawnerCluster> clusters = ClusterManager.getClusters();
        if (target.equals("all")) return toggleAllClusters(source, clusters);
        return toggleSpecificCluster(source, clusters, target);
    }

    public static boolean reset(ClientPlayerEntity player) {
        if (!ensureServerEnabled(player)) return false;

        LOGGER.info("Resetting RadarClient data...");
        int clustersBefore = ClusterManager.getClusters().size();
        int highlightsBefore = ClusterManager.getHighlights().size();

        clearClientState();

        player.sendMessage(Text.translatable("chat.spawn_radar.reset"), false);
        LOGGER.debug(
            "Cleared {} clusters and {} highlights.",
            clustersBefore,
            highlightsBefore
        );
        return true;
    }

    private void onRender(WorldRenderContext context) {
        try {
            Set<Integer> highlightedIds =
                ClusterManager.getHighlightedClusterIds();
            renderHighlightedBlocks(context);
            renderHighlightedRegions(context, highlightedIds);
        } catch (Exception e) {
            LOGGER.error("Error during rendering: ", e);
        } finally {
            BlockHighlightRenderer.submit(MinecraftClient.getInstance());
            BoxOutlineRenderer.submit(MinecraftClient.getInstance());
        }
    }

    public void onInitializeClient() {
        initializeSubsystems();
        registerEventHandlers();
        LOGGER.info("Initialized successfully.");
    }

    private static boolean ensureServerEnabled(ClientPlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (
            client.isIntegratedServerRunning() || serverSupportsRadar
        ) return true;

        if (player != null) player.sendMessage(
            Text.translatable("chat.spawn_radar.disabled"),
            false
        );
        LOGGER.warn(
            "Prevented Spawn Radar usage because the connected server does not have the mod installed."
        );
        return false;
    }

    private void initializeSubsystems() {
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

    private void registerEventHandlers() {
        WorldRenderEvents.END_MAIN.register(this::onRender);
        registerHandshakeReceiver();
        registerConnectionCallbacks();
    }

    private void registerHandshakeReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(
            RadarHandshakePayload.ID,
            (payload, context) ->
                MinecraftClient.getInstance().execute(() -> {
                serverSupportsRadar = true;
                    LOGGER.info(
                        "Spawn Radar features enabled on the current server."
                    );
            })
        );
    }

    private void registerConnectionCallbacks() {
        ClientPlayConnectionEvents.INIT.register((handler, client) ->
            serverSupportsRadar = client.isIntegratedServerRunning()
        );

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
            handleJoinEvent(client)
        );
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            serverSupportsRadar = false;
            MobPuppetRenderer.clearCache();
            ChunkProcessingManager.clear();
        });
    }

    private static void handleJoinEvent(MinecraftClient client) {
        if (!client.isIntegratedServerRunning()) {
            ClientPlayNetworking.send(RadarHandshakePayload.INSTANCE);
            LOGGER.debug("Requested Spawn Radar handshake from server.");
        } else serverSupportsRadar = true;

        clearClientState();
        LOGGER.info("Reset initial data.");

        HudRenderer.build();
        LOGGER.info("Built HudRenderer widgets.");
        ChunkProcessingManager.processLoadedChunks(client.world);
    }

    private static void clearClientState() {
        ClusterManager.unhighlightAllClusters();
        ClusterManager.clearBackgroundHighlights();
        ClusterManager.getClusters().clear();
        BlockHighlightRenderer.clearRegionMeshCache();
        BoxOutlineRenderer.clearMeshCache();
        BlockBank.clear();
        VolumeHighlightManager.clear();
        SpawnerEfficiencyManager.clear();
        SpawnerMobCapStatusManager.clear();
        MobPuppetRenderer.clearCache();
        ChunkProcessingManager.clear();
        PanelWidget.refresh();
    }

    private static boolean validateSpawnerResults(
        ClientPlayerEntity source,
        List<SpawnerInfo> spawners
    ) {
        if (!spawners.isEmpty()) return true;

        source.sendMessage(Text.translatable("chat.spawn_radar.none"), false);
        LOGGER.warn("No spawners found for cluster generation.");
        return false;
    }

    private static SpawnerCluster.SortType resolveSortType(String argument) {
        if (
            "proximity".equals(argument)
        ) return SpawnerCluster.SortType.BY_PROXIMITY;
        if ("size".equals(argument)) return SpawnerCluster.SortType.BY_SIZE;
        return config.defaultSortType;
    }

    private static void persistClusterResults(
        List<SpawnerCluster> clusters,
        SpawnerCluster.SortType sortType
    ) {
        ClusterManager.setClusters(clusters);
        BlockHighlightRenderer.clearRegionMeshCache();
        BoxOutlineRenderer.clearMeshCache();
        LOGGER.info(
            "Generated {} clusters using sort type {}",
            clusters.size(),
            sortType
        );

        if (config.autoHighlightAlertedClusters) {
            int alertThreshold = Math.max(2, config.backgroundClusterAlertThreshold);
            for (SpawnerCluster cluster : clusters) {
                if (cluster.spawners().size() < alertThreshold) continue;
                if (
                    ChunkProcessingManager.consumeAlertForCluster(cluster)
                ) ClusterManager.highlightCluster(cluster.id());
            }
        }

        if (config.highlightAfterScan) ClusterManager.highlightAllClusters();
        PanelWidget.refresh();
    }

    private static boolean toggleAllClusters(
        ClientPlayerEntity source,
        List<SpawnerCluster> clusters
    ) {
        if (clusters.isEmpty()) {
            source.sendMessage(
                Text.translatable("chat.spawn_radar.no_clusters_to_toggle"),
                false
            );
            LOGGER.warn("Attempted to toggle all clusters but none exist.");
            return false;
        }

        boolean anyHighlighted =
            !ClusterManager.getHighlightedClusterIds().isEmpty();
        if (anyHighlighted) {
            ClusterManager.unhighlightAllClusters();
            LOGGER.info("Un-highlighted all {} clusters", clusters.size());
        } else {
            ClusterManager.highlightAllClusters();
            LOGGER.info("Highlighted all {} clusters", clusters.size());
        }
        return true;
    }

    private static boolean toggleSpecificCluster(
        ClientPlayerEntity source,
        List<SpawnerCluster> clusters,
        String target
    ) {
        try {
            int clusterId = Integer.parseInt(target);
            if (clusters.stream().noneMatch(c -> c.id() == clusterId)) {
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
        } catch (NumberFormatException e) {
            source.sendMessage(
                Text.translatable("chat.spawn_radar.invalid_id_number"),
                false
            );
            return false;
        }
    }

    private void renderHighlightedBlocks(WorldRenderContext context) {
        boolean useOutline = config.useOutlineSpawnerHighlight;
        boolean defaultSpawnVolume = config.showSpawnerSpawnVolume;
        boolean defaultMobCapVolume = config.showSpawnerMobCapVolume;
        boolean defaultEfficiencyLabel = config.showSpawnerEfficiencyLabel;
        boolean defaultMobCapStatus = config.showSpawnerMobCapStatus;
        int outlineColor = config.spawnerOutlineColor;
        float outlineThickness = Math.max(
            0.05f,
            config.spawnerOutlineThickness
        );
        float alpha = config.spawnerHighlightOpacity / 100f;
        float spawnVolumeAlpha = clamp01(config.spawnVolumeOpacity / 100f);
        float mobCapVolumeAlpha = clamp01(config.mobCapVolumeOpacity / 100f);
        Set<BlockPos> highlightedPositions = new HashSet<>();
        for (SpawnerInfo info : ClusterManager.getHighlights()) {
            BlockPos pos = info.pos();
            highlightedPositions.add(pos);
            if (useOutline) {
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
                if (spawnEnabled && spawnVolumeAlpha > 0f) {
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
                if (mobEnabled && mobCapVolumeAlpha > 0f) {
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
            renderSpawnerLabel(context, info, defaultEfficiencyLabel, defaultMobCapStatus);
        }
        renderManualVolumes(context, defaultSpawnVolume, defaultMobCapVolume);
        renderForcedEfficiencyLabels(context, defaultEfficiencyLabel, defaultMobCapStatus, highlightedPositions);
        renderForcedMobCapStatusLabels(context, defaultEfficiencyLabel, defaultMobCapStatus, highlightedPositions);
    }

    private void renderSpawnerLabel(WorldRenderContext context, SpawnerInfo info,
                                    boolean defaultEfficiencyLabel, boolean defaultMobCapStatus) {
        BlockPos pos = info.pos();
        List<Integer> ids = ClusterManager.getClusterIDAt(pos);
        var world = MinecraftClient.getInstance().world;
        Map<Integer, Double> clusterEfficiencies = ids == null || ids.isEmpty() || world == null
            ? Collections.emptyMap()
            : computeClusterEfficiencyMap(world, ids);

        boolean efficiencyEnabled = SpawnerEfficiencyManager.isEnabled(pos, defaultEfficiencyLabel);
        boolean mobCapLabelEnabled = SpawnerMobCapStatusManager.isEnabled(pos, defaultMobCapStatus);

        String efficiencyLabel = null;
        if (efficiencyEnabled && world != null) {
            var result = SpawnerEfficiencyManager.evaluate(world, info);
            if (result != null) {
                efficiencyLabel = Text.translatable(
                    "text.spawn_radar.efficiency",
                    String.format(Locale.ROOT, "%.0f", result.overall())
                ).getString();
            }
        }

        String mobCapLabel = null;
        if (mobCapLabelEnabled && world != null) {
            var status = SpawnerEfficiencyManager.computeMobCapStatus(world, info);
            if (status != null) {
                mobCapLabel = Text.translatable(
                    "text.spawn_radar.mob_cap_status",
                    status.formatted()
                ).getString();
            }
        }

        if ((ids == null || ids.isEmpty()) && efficiencyLabel == null && mobCapLabel == null)
            return;

        StringBuilder label = new StringBuilder();
        if (ids != null && !ids.isEmpty())
            label.append(buildClusterLabel(ids, clusterEfficiencies));

        if (efficiencyLabel != null) {
            if (!label.isEmpty())
                label.append("\n");
            label.append(efficiencyLabel);
        }

        if (mobCapLabel != null) {
            if (!label.isEmpty())
                label.append("\n");
            label.append(mobCapLabel);
        }

        FloatingTextRenderer.renderBlockNametag(context, pos, label.toString());
    }

    private static String buildClusterLabel(List<Integer> ids, Map<Integer, Double> efficiencyByCluster) {
        StringBuilder label = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            int clusterId = ids.get(i);
            label.append("Cluster #").append(clusterId);
            Double avgEfficiency = efficiencyByCluster.get(clusterId);
            if (avgEfficiency != null)
                label.append(" (Avg ").append(String.format(Locale.ROOT, "%.0f%%", avgEfficiency)).append(")");
            if (i < ids.size() - 1) label.append("\n");
        }
        return label.toString();
    }

    private static Map<Integer, Double> computeClusterEfficiencyMap(net.minecraft.world.World world, List<Integer> ids) {
        if (world == null || ids == null || ids.isEmpty())
            return Collections.emptyMap();

        Map<Integer, Double> averages = new LinkedHashMap<>();
        for (Integer id : ids) {
            SpawnerCluster cluster = ClusterManager.getClusterById(id);
            if (cluster == null || cluster.spawners().size() < 2)
                continue;
            double value = SpawnerEfficiencyManager.computeClusterEfficiency(world, cluster);
            if (value >= 0d)
                averages.put(id, value);
        }
        return averages.isEmpty() ? Collections.emptyMap() : averages;
    }

    private void renderHighlightedRegions(
        WorldRenderContext context,
        Set<Integer> highlightedIds
    ) {
        for (SpawnerCluster cluster : ClusterManager.getClusters()) {
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

    private void renderSpawnVolume(
        WorldRenderContext context,
        SpawnerInfo info,
        int color,
        float alpha,
        float thickness
    ) {
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

    private void renderManualVolumes(
        WorldRenderContext context,
        boolean defaultSpawnVolume,
        boolean defaultMobCapVolume
    ) {
        float spawnAlpha = clamp01(config.spawnVolumeOpacity / 100f);
        float mobAlpha = clamp01(config.mobCapVolumeOpacity / 100f);

        if (!defaultSpawnVolume && spawnAlpha > 0f) {
            float spawnThickness = Math.max(
                0.05f,
                config.spawnerOutlineThickness * 0.65f
            );
            for (BlockPos pos : VolumeHighlightManager.getForcedSpawnShows()) {
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

        if (!defaultMobCapVolume && mobAlpha > 0f) {
            float mobThickness = Math.max(
                0.05f,
                config.spawnerOutlineThickness * 0.45f
            );
            for (BlockPos pos : VolumeHighlightManager.getForcedMobCapShows()) {
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

    private void renderForcedEfficiencyLabels(
        WorldRenderContext context,
        boolean defaultEfficiencyLabel,
        boolean defaultMobCapStatus,
        Set<BlockPos> highlightedPositions
    ) {
        if (defaultEfficiencyLabel)
            return;

        for (BlockPos pos : SpawnerEfficiencyManager.getForcedShows()) {
            if (highlightedPositions.contains(pos))
                continue;
            SpawnerInfo info = resolveSpawner(pos);
            if (info != null)
                renderSpawnerLabel(context, info, false, defaultMobCapStatus);
        }
    }

    private void renderForcedMobCapStatusLabels(
        WorldRenderContext context,
        boolean defaultEfficiencyLabel,
        boolean defaultMobCapStatus,
        Set<BlockPos> highlightedPositions
    ) {
        if (defaultMobCapStatus)
            return;

        for (BlockPos pos : SpawnerMobCapStatusManager.getForcedShows()) {
            if (highlightedPositions.contains(pos))
                continue;
            SpawnerInfo info = resolveSpawner(pos);
            if (info != null)
                renderSpawnerLabel(context, info, defaultEfficiencyLabel, false);
        }
    }

    private SpawnerInfo resolveSpawner(BlockPos pos) {
        SpawnerInfo info = BlockBank.get(pos);
        if (info != null) return info;

        for (SpawnerCluster cluster : ClusterManager.getClusters())
            for (SpawnerInfo candidate : cluster.spawners())
                if (candidate.pos().equals(pos)) return candidate;
        return null;
    }

    private void renderMobCapVolume(
        WorldRenderContext context,
        SpawnerInfo info,
        int color,
        float alpha,
        float thickness
    ) {
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

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    public static double getActivationRadius() {
        return ACTIVATION_RADIUS;
    }
}
