package cc.hachem.core;

import cc.hachem.RadarClient;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.List;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

public class CommandManager
{
	private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess registryAccess)
	{
		dispatcher.register(ClientCommandManager.literal("locate_spawners")
			.executes(context ->
			{
				BlockBank.scanForSpawners(context.getSource(), RadarClient.DEFAULT_SCAN_RADIUS);
				return Command.SINGLE_SUCCESS;
			})
			.then(ClientCommandManager.argument("radius", IntegerArgumentType.integer(1, 256)).executes(context ->
			{
				int radius = IntegerArgumentType.getInteger(context, "radius");
				BlockBank.scanForSpawners(context.getSource(), radius);
				return Command.SINGLE_SUCCESS;
			})));

		dispatcher.register(ClientCommandManager.literal("generate_clusters")
			.executes(context -> {
				List<BlockPos> spawners = BlockBank.getAll();
				if (spawners.isEmpty())
				{
					context.getSource().sendFeedback(Text.literal("No spawners in BlockBank."));
					return 0;
				}

				List<SpawnerCluster> clusters = SpawnerCluster.findClusters(context.getSource(), spawners, 16.0);
				SpawnerCluster.sortClustersByPlayerProximity(context.getSource().getPlayer(), clusters);
				ClusterManager.setClusters(clusters);

				if (clusters.isEmpty())
				{
					context.getSource().sendFeedback(Text.literal("No clusters found."));
					return 0;
				}

				MutableText showAllButton = Text.literal("[Show All]").styled(style -> style
					.withColor(Formatting.GREEN)
					.withClickEvent(new ClickEvent.RunCommand("/highlight_all_clusters"))
					.withHoverEvent(new HoverEvent.ShowText(Text.literal("Highlight all clusters"))));

				MutableText hideAllButton = Text.literal("[Hide All]").styled(style -> style
					.withColor(Formatting.RED)
					.withClickEvent(new ClickEvent.RunCommand("/clear_highlights"))
					.withHoverEvent(new HoverEvent.ShowText(Text.literal("Clear all highlights"))));

				context.getSource().getPlayer().sendMessage(showAllButton.append(" ").append(hideAllButton), false);

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
							.withClickEvent(new ClickEvent.RunCommand(String.format("/highlight_cluster %d", finalId)))
							.withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to highlight this cluster"))));

					MutableText teleportButton = Text.literal("[Teleport]").styled(style -> style.withColor(Formatting.GOLD)
						.withClickEvent(new ClickEvent.RunCommand(String.format("/tp %.0f %.0f %.0f", cx, cy, cz)))
						.withHoverEvent(new HoverEvent.ShowText(Text.literal("Teleport to cluster center"))));

					MutableText showSpawnersButton = Text.literal("[Show Spawners]").styled(style -> style.withColor(Formatting.GREEN)
						.withClickEvent(new ClickEvent.RunCommand(String.format("/show_cluster_spawners %d", finalId)))
						.withHoverEvent(new HoverEvent.ShowText(Text.literal("Show all spawners in this cluster"))));

					MutableText combined = clusterHeader.copy().append(" ").append(teleportButton).append(" ").append(showSpawnersButton);
					context.getSource().getPlayer().sendMessage(combined, false);
					id++;
				}

				return Command.SINGLE_SUCCESS;
			}));

		dispatcher.register(ClientCommandManager.literal("show_cluster_spawners")
			.then(ClientCommandManager.argument("id", IntegerArgumentType.integer(1))
			.executes(context ->
			{
			    int id = IntegerArgumentType.getInteger(context, "id");
			    List<SpawnerCluster> clusters = ClusterManager.getClusters();

			    if (clusters == null || clusters.isEmpty() || id > clusters.size())
			    {
			    	context.getSource().sendFeedback(Text.literal("Invalid cluster ID."));
			    	return 0;
			    }

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
		    })));

		dispatcher.register(ClientCommandManager.literal("highlight_cluster")
			.then(ClientCommandManager.argument("id", IntegerArgumentType.integer(0))
			.executes(context ->
			{
			    int id = IntegerArgumentType.getInteger(context, "id") - 1;
			    List<SpawnerCluster> clusters = ClusterManager.getClusters();
			    if (id < 0 || id >= clusters.size())
				{
			    	context.getSource().sendFeedback(Text.literal("Invalid cluster ID."));
			    	return 0;
			    }

			    ClusterManager.highlightCluster(id);
			    return Command.SINGLE_SUCCESS;
			})));

		dispatcher.register(ClientCommandManager.literal("highlight_all_clusters")
			.executes(context -> {
				List<SpawnerCluster> clusters = ClusterManager.getClusters();
				if (clusters == null || clusters.isEmpty())
				{
					context.getSource().sendFeedback(Text.literal("No clusters to highlight."));
					return 0;
				}
				ClusterManager.highlightAllClusters();
				return Command.SINGLE_SUCCESS;
			}));

		dispatcher.register(ClientCommandManager.literal("clear_highlights")
			.executes(context ->
			{
				ClusterManager.clearHighlights();
				return Command.SINGLE_SUCCESS;
			}));

		dispatcher.register(ClientCommandManager.literal("reset_spawners")
			.executes(context ->
			{
				RadarClient.reset();
				return Command.SINGLE_SUCCESS;
			}));
	}

	public static void init()
	{
		ClientCommandRegistrationCallback.EVENT.register(CommandManager::registerCommands);
	}
}
