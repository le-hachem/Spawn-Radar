package cc.hachem.core;

import cc.hachem.RadarClient;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public class CommandManager
{
	private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess registryAccess)
	{
		dispatcher.register(
			ClientCommandManager.literal("radar:scan")
				.executes(context -> {
					RadarClient.LOGGER.info("Executing radar:scan with default radius {}", RadarClient.config.defaultSearchRadius);
					return executeGenerate(context, RadarClient.config.defaultSearchRadius);
				})
				.then(ClientCommandManager.argument("sorting", StringArgumentType.word())
					.suggests((context, builder) ->
					{
						builder.suggest("proximity");
						builder.suggest("size");
						return builder.buildFuture();
					})
					.executes(context -> {
						RadarClient.LOGGER.info("Executing radar:scan with sorting argument '{}'", StringArgumentType.getString(context, "sorting"));
						return executeGenerate(context, RadarClient.config.defaultSearchRadius);
					})
					.then(ClientCommandManager.argument("radius", IntegerArgumentType.integer(1, 256))
						.executes(context ->
						{
							int radius = IntegerArgumentType.getInteger(context, "radius");
							RadarClient.LOGGER.info("Executing radar:scan with radius {}", radius);
							return executeGenerate(context, radius);
						})
					)
				)
		);

		dispatcher.register(ClientCommandManager.literal("radar:show_spawners_in_cluster")
			.then(ClientCommandManager.argument("id", IntegerArgumentType.integer(1))
				.executes(context ->
				{
					int id = IntegerArgumentType.getInteger(context, "id");
					List<SpawnerCluster> clusters = ClusterManager.getClusters();

					if (clusters == null || clusters.isEmpty() || id > clusters.size())
					{
						context.getSource().sendFeedback(Text.literal("Invalid cluster ID."));
						RadarClient.LOGGER.warn("Attempted to show spawners for invalid cluster ID {}", id);
						return 0;
					}

					RadarClient.LOGGER.info("Showing spawners for cluster #{}", id);
					SpawnerCluster cluster = clusters.get(id - 1);
					int sid = 1;
					for (BlockPos pos : cluster.spawners())
					{
						MutableText spawnerText = Text.literal("    - Spawner " + sid + " @ [" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]")
							.styled(style -> style.withColor(Formatting.GREEN)
								.withClickEvent(new ClickEvent.RunCommand(String.format("/tp %d %d %d", pos.getX(), pos.getY(), pos.getZ())))
								.withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to teleport to this spawner"))));
						context.getSource().getPlayer().sendMessage(spawnerText, false);
						sid++;
					}

					return Command.SINGLE_SUCCESS;
				})
			)
		);

		dispatcher.register(ClientCommandManager.literal("radar:highlight_cluster")
			.then(ClientCommandManager.argument("id", IntegerArgumentType.integer(0))
				.executes(context ->
				{
					int id = IntegerArgumentType.getInteger(context, "id") - 1;
					List<SpawnerCluster> clusters = ClusterManager.getClusters();
					if (id < 0 || id >= clusters.size())
					{
						context.getSource().sendFeedback(Text.literal("Invalid cluster ID."));
						RadarClient.LOGGER.warn("Attempted to highlight invalid cluster ID {}", id + 1);
						return 0;
					}

					ClusterManager.toggleHighlightCluster(id);
					RadarClient.LOGGER.info("Toggled highlight for cluster #{}", id + 1);
					return Command.SINGLE_SUCCESS;
				})
			)
		);

		dispatcher.register(ClientCommandManager.literal("radar:highlight_all_clusters")
			.executes(context ->
			{
				List<SpawnerCluster> clusters = ClusterManager.getClusters();
				if (clusters == null || clusters.isEmpty())
				{
					context.getSource().sendFeedback(Text.literal("No clusters to highlight."));
					RadarClient.LOGGER.warn("Attempted to highlight all clusters but none exist.");
					return 0;
				}

				ClusterManager.highlightAllClusters();
				RadarClient.LOGGER.info("Highlighted all {} clusters", clusters.size());
				return Command.SINGLE_SUCCESS;
			})
		);

		dispatcher.register(ClientCommandManager.literal("radar:clear_highlights")
			.executes(context ->
			{
				ClusterManager.clearHighlights();
				RadarClient.LOGGER.info("Cleared all cluster highlights via command.");
				return Command.SINGLE_SUCCESS;
			})
		);

		dispatcher.register(ClientCommandManager.literal("radar:reset")
			.executes(context ->
			{
				context.getSource().sendFeedback(Text.of("Reset all spawner and cluster banks."));
				RadarClient.reset();
				RadarClient.LOGGER.info("Radar reset via command.");
				return Command.SINGLE_SUCCESS;
			})
		);
	}

	private static int executeGenerate(CommandContext<FabricClientCommandSource> context, int radius)
	{
		BlockBank.scanForSpawners(context.getSource(), radius, () -> generateClusters(context));
		RadarClient.LOGGER.debug("Scheduled cluster generation after scanning for spawners.");
		return Command.SINGLE_SUCCESS;
	}

	private static void generateClusters(CommandContext<FabricClientCommandSource> context)
	{
		FabricClientCommandSource source = context.getSource();
		List<BlockPos> spawners = BlockBank.getAll();

		if (spawners.isEmpty())
		{
			source.sendFeedback(Text.literal("No spawners found in the scanned area."));
			RadarClient.LOGGER.warn("No spawners found for cluster generation.");
			return;
		}

		SpawnerCluster.SortType sortType = RadarClient.config.defaultSortType;
		switch (StringArgumentType.getString(context, "sorting").toLowerCase())
		{
			case "proximity" -> sortType = SpawnerCluster.SortType.BY_PROXIMITY;
			case "size" -> sortType = SpawnerCluster.SortType.BY_SIZE;
			default -> {}
		}

		List<SpawnerCluster> clusters = SpawnerCluster.findClusters(source, spawners, 16.0, sortType);
		ClusterManager.setClusters(clusters);
		RadarClient.LOGGER.info("Generated {} clusters using sort type {}", clusters.size(), sortType);

		if (clusters.isEmpty())
		{
			source.sendFeedback(Text.literal("No clusters found."));
			return;
		}

		MutableText showAllButton = Text.literal("[Show All]").styled(style -> style
			.withColor(Formatting.GREEN)
			.withClickEvent(new ClickEvent.RunCommand("/radar:highlight_all_clusters"))
			.withHoverEvent(new HoverEvent.ShowText(Text.literal("Highlight all clusters"))));

		MutableText hideAllButton = Text.literal("[Hide All]").styled(style -> style
			.withColor(Formatting.RED)
			.withClickEvent(new ClickEvent.RunCommand("/radar:clear_highlights"))
			.withHoverEvent(new HoverEvent.ShowText(Text.literal("Clear all highlights"))));

		source.getPlayer().sendMessage(showAllButton.append(" ").append(hideAllButton), false);

		int id = 1;
		for (SpawnerCluster cluster : clusters)
		{
			int finalId = id;

			double cx = cluster.spawners().stream().mapToDouble(BlockPos::getX).average().orElse(0);
			double cy = cluster.spawners().stream().mapToDouble(BlockPos::getY).average().orElse(0);
			double cz = cluster.spawners().stream().mapToDouble(BlockPos::getZ).average().orElse(0);

			MutableText clusterHeader = Text.literal("[(" + cluster.spawners().size() + ") Cluster #" + id + "]")
				.styled(style -> style.withColor(Formatting.AQUA)
					.withUnderline(true)
					.withClickEvent(new ClickEvent.RunCommand(String.format("/radar:highlight_cluster %d", finalId)))
					.withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to highlight this cluster"))));

			MutableText teleportButton = Text.literal("[Teleport]").styled(style -> style.withColor(Formatting.GOLD)
				.withClickEvent(new ClickEvent.RunCommand(String.format("/tp %.0f %.0f %.0f", cx, cy, cz)))
				.withHoverEvent(new HoverEvent.ShowText(Text.literal("Teleport to cluster center"))));

			MutableText showSpawnersButton = Text.literal("[Show Spawners]").styled(style -> style.withColor(Formatting.GREEN)
				.withClickEvent(new ClickEvent.RunCommand(String.format("/radar:show_spawners_in_cluster %d", finalId)))
				.withHoverEvent(new HoverEvent.ShowText(Text.literal("Show all spawners in this cluster"))));

			MutableText combined = clusterHeader.copy().append(" ").append(teleportButton).append(" ").append(showSpawnersButton);
			source.getPlayer().sendMessage(combined, false);
			RadarClient.LOGGER.debug("Displayed cluster #{} with {} spawners.", id, cluster.spawners().size());
			id++;
		}
	}

	public static void init()
	{
		ClientCommandRegistrationCallback.EVENT.register(CommandManager::registerCommands);
		RadarClient.LOGGER.info("CommandManager initialized and commands registered.");
	}
}
