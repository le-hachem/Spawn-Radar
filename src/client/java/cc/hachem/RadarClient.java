package cc.hachem;

import cc.hachem.config.ConfigManager;
import cc.hachem.config.ConfigSerializer;
import cc.hachem.core.BlockBank;
import cc.hachem.core.ClusterManager;
import cc.hachem.core.CommandManager;
import cc.hachem.core.KeyManager;
import cc.hachem.core.SpawnerCluster;
import cc.hachem.hud.HudRenderer;
import cc.hachem.hud.PanelWidget;
import cc.hachem.network.RadarHandshakePayload;
import cc.hachem.renderer.BlockHighlightRenderer;
import cc.hachem.renderer.FloatingTextRenderer;
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

import java.util.List;
import java.util.Set;

public class RadarClient implements ClientModInitializer
{
    public static final String MOD_ID = "radar";
    public static Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static ConfigManager config;
    private static volatile boolean serverSupportsRadar = false;

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
        List<BlockPos> spawners = BlockBank.getAll();

        if (spawners.isEmpty())
        {
            source.sendMessage(Text.translatable("chat.spawn_radar.none"), false);
            RadarClient.LOGGER.warn("No spawners found for cluster generation.");
            return;
        }

        SpawnerCluster.SortType sortType = RadarClient.config.defaultSortType;
        if ("proximity".equals(argument))
            sortType = SpawnerCluster.SortType.BY_PROXIMITY;
        else if ("size".equals(argument))
            sortType = SpawnerCluster.SortType.BY_SIZE;

        List<SpawnerCluster> clusters = SpawnerCluster.findClusters(source, spawners, 16.0, sortType);
        ClusterManager.setClusters(clusters);
        BlockHighlightRenderer.clearRegionMeshCache();
        RadarClient.LOGGER.info("Generated {} clusters using sort type {}", clusters.size(), sortType);

        if (RadarClient.config.highlightAfterScan)
            ClusterManager.highlightAllClusters();
        PanelWidget.refresh();
    }

    public static boolean toggleCluster(ClientPlayerEntity source, String target)
    {
        if (!ensureServerEnabled(source))
            return false;

        List<SpawnerCluster> clusters = ClusterManager.getClusters();

        if (target.equals("all"))
        {
            if (clusters.isEmpty())
            {
                source.sendMessage(Text.translatable("chat.spawn_radar.no_clusters_to_toggle"), false);
                RadarClient.LOGGER.warn("Attempted to toggle all clusters but none exist.");
                return false;
            }

            boolean anyHighlighted = !ClusterManager.getHighlightedClusterIds().isEmpty();
            if (anyHighlighted)
            {
                ClusterManager.unhighlightAllClusters();
                RadarClient.LOGGER.info("Un-highlighted all {} clusters", clusters.size());
            } else
            {
                ClusterManager.highlightAllClusters();
                RadarClient.LOGGER.info("Highlighted all {} clusters", clusters.size());
            }
        }
        else
        {
            try
            {
                int clusterId = Integer.parseInt(target);
                if (clusters.stream().noneMatch(c -> c.id() == clusterId))
                {
                    source.sendMessage(Text.translatable("chat.spawn_radar.invalid_id"), false);
                    RadarClient.LOGGER.warn("Attempted to toggle invalid cluster ID {}", clusterId);
                    return false;
                }

                ClusterManager.toggleHighlightCluster(clusterId);
                RadarClient.LOGGER.info("Toggled highlight for cluster #{}", clusterId);
            }
            catch (NumberFormatException e)
            {
                source.sendMessage(Text.translatable("chat.spawn_radar.invalid_id_number"), false);
                return false;
            }
        }
        return true;
    }

    public static boolean reset(ClientPlayerEntity player)
    {
        if (!ensureServerEnabled(player))
            return false;

        LOGGER.info("Resetting RadarClient data...");
        int clustersBefore = ClusterManager.getClusters().size();
        int highlightsBefore = ClusterManager.getHighlights().size();

        ClusterManager.unhighlightAllClusters();
        ClusterManager.getClusters().clear();
        BlockHighlightRenderer.clearRegionMeshCache();
        BlockBank.clear();

        player.sendMessage(Text.translatable("chat.spawn_radar.reset"), false);
        LOGGER.debug("Cleared {} clusters and {} highlights.", clustersBefore, highlightsBefore);
        PanelWidget.refresh();
        return true;
    }

    private void onRender(WorldRenderContext context)
    {
        try
        {
            Set<Integer> highlightedIds = ClusterManager.getHighlightedClusterIds();

            for (BlockPos pos : ClusterManager.getHighlights())
            {
                BlockHighlightRenderer.draw(context, pos, config.spawnerHighlightColor, config.spawnerHighlightOpacity / 100f);

                List<Integer> ids = ClusterManager.getClusterIDAt(pos);
                if (!ids.isEmpty())
                {
                    StringBuilder label = new StringBuilder();
                    for (int id : ids)
                    {
                        label.append("Cluster #").append(id);
                        if (id != ids.getLast())
                            label.append("\n");
                    }

                    FloatingTextRenderer.renderBlockNametag(context, pos, label.toString());
                }
            }

            for (SpawnerCluster cluster : ClusterManager.getClusters())
            {
                if (!highlightedIds.contains(cluster.id()))
                    continue;

                int spawnerCount = cluster.spawners().size();
                int clusterColor = ConfigManager.getClusterColor(spawnerCount);

                if (spawnerCount >= config.minimumSpawnersForRegion)
                {
                    List<BlockPos> region = cluster.intersectionRegion();
                    BlockHighlightRenderer.fillRegionMesh(context, cluster.id(), region, clusterColor, config.regionHighlightOpacity / 100f);
                }
            }

            BlockHighlightRenderer.submit(MinecraftClient.getInstance());
        }
        catch (Exception e)
        {
            LOGGER.error("Error during rendering: ", e);
        }
    }

    @Override
    public void onInitializeClient()
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

        WorldRenderEvents.END_MAIN.register(this::onRender);
        ClientPlayNetworking.registerGlobalReceiver(RadarHandshakePayload.ID, (payload, context) ->
            MinecraftClient.getInstance().execute(() -> {
                serverSupportsRadar = true;
                LOGGER.info("Spawn Radar features enabled on the current server.");
            })
        );
        ClientPlayConnectionEvents.INIT.register((handler, client) ->
            serverSupportsRadar = client.isIntegratedServerRunning()
        );

        ClientPlayConnectionEvents.JOIN.register(((handler, sender, client) ->
        {
            if (!client.isIntegratedServerRunning())
            {
                ClientPlayNetworking.send(RadarHandshakePayload.INSTANCE);
                LOGGER.debug("Requested Spawn Radar handshake from server.");
            }
            else
                serverSupportsRadar = true;

            ClusterManager.unhighlightAllClusters();
            ClusterManager.getClusters().clear();
            BlockHighlightRenderer.clearRegionMeshCache();
            BlockBank.clear();
            PanelWidget.refresh();
            LOGGER.info("Reset initial data.");

            HudRenderer.build();
            LOGGER.info("Built HudRenderer widgets.");
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> serverSupportsRadar = false);

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
}