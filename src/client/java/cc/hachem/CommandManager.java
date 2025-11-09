package cc.hachem;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CommandManager
{
	@Nullable
	private static BlockHitResult getTargetedBlock()
	{
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.crosshairTarget instanceof BlockHitResult hit)
			return hit;
		return null;
	}

	private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess registryAccess)
	{
		dispatcher.register(ClientCommandManager.literal("locate_spawners")
			.executes(context ->
			{
				RadarClient.scanForSpawners(context.getSource(), RadarClient.DEFAULT_SCAN_RADIUS);
				return Command.SINGLE_SUCCESS;
			})
			.then(ClientCommandManager.argument("radius", IntegerArgumentType.integer(1, 256))
				.executes(context ->
				{
					int radius = IntegerArgumentType.getInteger(context, "radius");
					RadarClient.scanForSpawners(context.getSource(), radius);
					return Command.SINGLE_SUCCESS;
				})
			)
		);

		dispatcher.register(ClientCommandManager.literal("generate_clusters")
			.executes(context ->
			{
				List<BlockPos> spawners = BlockBank.getAll();
				if (spawners.isEmpty())
				{
					context.getSource().sendFeedback(Text.literal("No spawners in BlockBank."));
					return 0;
				}

				List<SpawnerCluster> clusters = SpawnerCluster.findClusters(context.getSource(), spawners, 16.0);
				ClusterManager.setClusters(clusters);

				if (clusters.isEmpty())
				{
					context.getSource().sendFeedback(Text.literal("No clusters found."));
					return 0;
				}

				int id = 1;
				MinecraftClient client = MinecraftClient.getInstance();

				for (SpawnerCluster cluster : clusters)
				{
					int finalId = id;
					BlockPos firstSpawner = cluster.spawners().getFirst();
					MutableText clusterHeader = Text.literal("[Cluster #" + id + "]")
							.styled(style -> style
									.withColor(Formatting.AQUA)
									.withUnderline(true)
									.withClickEvent(new ClickEvent.RunCommand(
										String.format("/highlight_cluster %d", finalId)
									))
									.withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to highlight this cluster")))
					);

                    context.getSource().getPlayer().sendMessage(clusterHeader, false);

					int sid = 1;
					for (BlockPos pos : cluster.spawners())
					{
						MutableText spawnerText = Text.literal("    - Spawner " + sid + " @ [" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]")
							.styled(style -> style
									.withColor(Formatting.GREEN)
									.withClickEvent(new ClickEvent.RunCommand(
								   		String.format("/tp %d %d %d", pos.getX(), pos.getY(), pos.getZ())
									))
									.withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to teleport to this spawner")))
							);

						context.getSource().getPlayer().sendMessage(spawnerText, false);
						sid++;
					}

					id++;
				}

				return Command.SINGLE_SUCCESS;
			})
		);

		dispatcher.register(ClientCommandManager.literal("highlight_cluster")
			.then(ClientCommandManager.argument("id", IntegerArgumentType.integer(0))
			.executes(context ->
			{
				int id = IntegerArgumentType.getInteger(context, "id")-1;
			  	List<SpawnerCluster> clusters = ClusterManager.getClusters();
			  	if (id < 0 || id >= clusters.size())
				{
				  	context.getSource().sendFeedback(Text.literal("Invalid cluster ID."));
				  	return 0;
			  	}

			  	ClusterManager.highlightCluster(id);
				context.getSource().sendFeedback(Text.literal(String.format("Intersection size: %d", ClusterManager.getHighlightedIntersectionRegion().size())));
			  	context.getSource().sendFeedback(Text.literal("Toggled highlight for Cluster #" + id+1));
			  	return Command.SINGLE_SUCCESS;
			}))
		);

		dispatcher.register(ClientCommandManager.literal("clear_highlights")
			.executes(context ->
			{
				ClusterManager.clearHighlights();
				return Command.SINGLE_SUCCESS;
			})
		);

		dispatcher.register(ClientCommandManager.literal("reset_spawners")
			.executes(context ->
			{
				ClusterManager.clearHighlights();
				ClusterManager.getClusters().clear();
				BlockBank.clear();
				return Command.SINGLE_SUCCESS;
			})
		);
	}

	public static void init()
	{
		ClientCommandRegistrationCallback.EVENT.register(CommandManager::registerCommands);
	}
}
