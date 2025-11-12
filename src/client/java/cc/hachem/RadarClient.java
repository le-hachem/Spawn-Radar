package cc.hachem;

import cc.hachem.config.ConfigManager;
import cc.hachem.config.ConfigSerializer;
import cc.hachem.core.*;
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

		if (clusters.isEmpty())
		{
			source.sendMessage(Text.translatable("chat.spawn_radar.no_clusters"), false);
			return;
		}

		MutableText showAllButton = Text.translatable("chat.spawn_radar.toggle_all")
			.styled(style -> style
				.withColor(Formatting.GREEN)
				.withClickEvent(new ClickEvent.RunCommand("/radar:toggle all"))
				.withHoverEvent(new HoverEvent.ShowText(Text.translatable("chat.spawn_radar.toggle_all_hover")))
			);
		source.sendMessage(showAllButton, false);

		int id = 1;
		for (SpawnerCluster cluster : clusters)
		{
			int finalId = id;
			double cx = cluster.spawners().stream().mapToDouble(BlockPos::getX).average().orElse(0);
			double cy = cluster.spawners().stream().mapToDouble(BlockPos::getY).average().orElse(0);
			double cz = cluster.spawners().stream().mapToDouble(BlockPos::getZ).average().orElse(0);

			MutableText clusterHeader = Text.translatable("chat.spawn_radar.cluster_header", cluster.spawners().size(), id)
				.styled(style -> style.withColor(Formatting.AQUA)
					.withUnderline(true)
					.withClickEvent(new ClickEvent.RunCommand("/radar:toggle " + finalId))
					.withHoverEvent(new HoverEvent.ShowText(Text.translatable("chat.spawn_radar.cluster_hover")))
				);

			MutableText teleportButton = Text.translatable("chat.spawn_radar.teleport")
				.styled(style -> style
					.withColor(Formatting.GOLD)
					.withClickEvent(new ClickEvent.RunCommand(String.format("/tp %.0f %.0f %.0f", cx, cy, cz)))
					.withHoverEvent(new HoverEvent.ShowText(Text.translatable("chat.spawn_radar.teleport_hover")))
				);

			MutableText showSpawnersButton = Text.translatable("chat.spawn_radar.show_spawners")
				.styled(style -> style
					.withColor(Formatting.GREEN)
					.withClickEvent(new ClickEvent.RunCommand("/radar:info " + finalId))
					.withHoverEvent(new HoverEvent.ShowText(Text.translatable("chat.spawn_radar.show_spawners_hover")))
				);

			MutableText combined = clusterHeader.copy()
				.append(" ")
				.append(teleportButton)
				.append(" ")
				.append(showSpawnersButton);

			source.sendMessage(combined, false);
			RadarClient.LOGGER.debug("Displayed cluster #{} with {} spawners.", id, cluster.spawners().size());
			id++;
		}

		if (RadarClient.config.highlightAfterScan)
			ClusterManager.highlightAllClusters();
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

			boolean anyHighlighted = !ClusterManager.getHighlightedClusters().isEmpty();
			if (anyHighlighted)
			{
				ClusterManager.unhighlightAllClusters();
				RadarClient.LOGGER.info("Un-highlighted all {} clusters", clusters.size());
			} else
			{
				ClusterManager.highlightAllClusters();
				RadarClient.LOGGER.info("Highlighted all {} clusters", clusters.size());
			}
		} else
		{
			try
			{
				int id = Integer.parseInt(target) - 1;
				if (id < 0 || id >= clusters.size())
				{
					source.sendMessage(Text.translatable("chat.spawn_radar.invalid_id"), false);
					RadarClient.LOGGER.warn("Attempted to toggle invalid cluster ID {}", id + 1);
					return;
				}

				ClusterManager.toggleHighlightCluster(id);
				RadarClient.LOGGER.info("Toggled highlight for cluster #{}", id + 1);
			} catch (NumberFormatException e)
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
	}

	private void onRender(WorldRenderContext context)
	{
		try
		{
			for (BlockPos pos : ClusterManager.getHighlights())
			{
				BlockHighlightRenderer.draw(context, pos, config.spawnerHighlightColor, config.spawnerHighlightOpacity/100f);

				List<Integer> ids = ClusterManager.getClusterIDAt(pos);
				if (!ids.isEmpty())
				{
					StringBuilder label = new StringBuilder();

					for (int id : ids)
					{
						label.append("Cluster #").append(id + 1);
						if (id != ids.getLast())
							label.append("\n");
					}
					TextRenderer.renderBlockNametag(context, pos, label.toString());
				}
			}

			for (ClusterManager.HighlightedCluster hc : ClusterManager.getHighlightedClusters())
			{
				int spawnerCount = hc.cluster().spawners().size();
				int clusterColor = ConfigManager.getClusterColor(spawnerCount);

				if (spawnerCount >= config.minimumSpawnersForRegion)
				{
					List<BlockPos> region = hc.cluster().intersectionRegion();
					BlockHighlightRenderer.fillRegionMesh(context, region, clusterColor, config.regionHighlightOpacity/100f);
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

		WorldRenderEvents.BEFORE_TRANSLUCENT.register(this::onRender);
		ClientPlayConnectionEvents.JOIN.register(((handler, sender, client) ->
		{
			ClusterManager.unhighlightAllClusters();
			ClusterManager.getClusters().clear();
			BlockBank.clear();
		}));

		LOGGER.info("Initialized successfully.");
	}
}
