package cc.hachem;

import cc.hachem.config.ConfigManager;
import cc.hachem.config.ConfigSerializer;
import cc.hachem.core.*;
import cc.hachem.hud.HudRenderer;
import cc.hachem.hud.PanelWidget;
import cc.hachem.renderer.BlockHighlightRenderer;
import cc.hachem.renderer.TextRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
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

	public static ClientPlayerEntity getPlayer()
	{
		MinecraftClient client = MinecraftClient.getInstance();
		return client.player;
	}

	public static void generateClusters(ClientPlayerEntity source, int radius, String sorting)
	{
		BlockBank.scanForSpawners(source, radius, () -> generateClustersChild(source, sorting));
		RadarClient.LOGGER.debug("Scheduled cluster generation after scanning for spawners.");
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
		RadarClient.LOGGER.info("Generated {} clusters using sort type {}", clusters.size(), sortType);

		if (RadarClient.config.highlightAfterScan)
			ClusterManager.highlightAllClusters();
        PanelWidget.refresh();
	}

	public static void toggleCluster(ClientPlayerEntity source, String target)
	{
		List<SpawnerCluster> clusters = ClusterManager.getClusters();

		if (target.equals("all"))
		{
			if (clusters.isEmpty())
			{
				source.sendMessage(Text.translatable("chat.spawn_radar.no_clusters_to_toggle"), false);
				RadarClient.LOGGER.warn("Attempted to toggle all clusters but none exist.");
				return;
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
					return;
				}

				ClusterManager.toggleHighlightCluster(clusterId);
				RadarClient.LOGGER.info("Toggled highlight for cluster #{}", clusterId);
			}
			catch (NumberFormatException e)
			{
				source.sendMessage(Text.translatable("chat.spawn_radar.invalid_id_number"), false);
			}
		}
	}

	public static void reset(ClientPlayerEntity player)
	{
		LOGGER.info("Resetting RadarClient data...");
		int clustersBefore = ClusterManager.getClusters().size();
		int highlightsBefore = ClusterManager.getHighlights().size();

		ClusterManager.unhighlightAllClusters();
		ClusterManager.getClusters().clear();
		BlockBank.clear();

		player.sendMessage(Text.translatable("chat.spawn_radar.reset"), false);
		LOGGER.debug("Cleared {} clusters and {} highlights.", clustersBefore, highlightsBefore);
        PanelWidget.refresh();
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
					TextRenderer.renderBlockNametag(context, pos, label.toString());
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
					BlockHighlightRenderer.fillRegionMesh(context, region, clusterColor, config.regionHighlightOpacity / 100f);
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

		WorldRenderEvents.BEFORE_TRANSLUCENT.register(this::onRender);
		ClientPlayConnectionEvents.JOIN.register(((handler, sender, client) ->
		{
			ClusterManager.unhighlightAllClusters();
			ClusterManager.getClusters().clear();
			BlockBank.clear();
            LOGGER.info("Cleared block bank and cluster manager.");

            HudRenderer.build();
            LOGGER.info("Built HudRenderer widgets.");
        }));

		LOGGER.info("Initialized successfully.");
	}
}
