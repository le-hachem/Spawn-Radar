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
        dispatcher.register(ClientCommandManager.literal("radar:scan")
            .executes(context -> executeGenerate(context, RadarClient.config.defaultSearchRadius))
            .then(ClientCommandManager.argument("sorting", StringArgumentType.word())
                .suggests((context, builder) ->
                {
                    builder.suggest("proximity");
                    builder.suggest("size");
                    return builder.buildFuture();
                })
                .executes(context -> executeGenerate(context, RadarClient.config.defaultSearchRadius))
                .then(ClientCommandManager.argument("radius", IntegerArgumentType.integer(1, 256))
                    .executes(context ->
                    {
                        int radius = IntegerArgumentType.getInteger(context, "radius");
                        return executeGenerate(context, radius);
                    })
                )
            )
        );

        dispatcher.register(ClientCommandManager.literal("radar:toggle")
            .then(ClientCommandManager.argument("target", StringArgumentType.word())
                .suggests((context, builder) ->
                {
                    builder.suggest("all");
                    for (int i = 1; i <= ClusterManager.getClusters().size(); i++)
                        builder.suggest(String.valueOf(i));
                    return builder.buildFuture();
                })
                .executes(CommandManager::toggleCluster)
            )
        );

        dispatcher.register(ClientCommandManager.literal("radar:info")
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
                        MutableText spawnerText = Text.literal(
                                "    - Spawner " + sid + " @ [" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]")
                                .styled(style -> style.withColor(Formatting.GREEN)
                                .withClickEvent(new ClickEvent.RunCommand(
                                        String.format("/tp %d %d %d", pos.getX(), pos.getY(), pos.getZ())))
                                .withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to teleport to this spawner"))));
                        context.getSource().getPlayer().sendMessage(spawnerText, false);
                        sid++;
                    }

                    return Command.SINGLE_SUCCESS;
                })
            )
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

        dispatcher.register(ClientCommandManager.literal("radar:help")
            .executes(context ->
            {
                FabricClientCommandSource source = context.getSource();

                // /radar:sca [sorting] [radius] - Scan for spawners and generate clusters.
                source.sendFeedback(Text.literal("")
                    .append(Text.literal("/radar").styled(s -> s.withColor(Formatting.WHITE)))
                    .append(Text.literal(":").styled(s -> s.withColor(Formatting.WHITE)))
                    .append(Text.literal("scan ").styled(s -> s.withColor(Formatting.WHITE)))
                    .append(Text.literal("[sorting] ").styled(s -> s.withColor(Formatting.GRAY).withItalic(true)))
                    .append(Text.literal("[radius]").styled(s -> s.withColor(Formatting.GRAY).withItalic(true)))
                    .append(Text.literal(" - Scan for spawners and generate clusters.").styled(s -> s.withColor(Formatting.GRAY)))
                );

                // /radar:toggle <id|all> - Toggle highlight for a cluster or all clusters.
                source.sendFeedback(Text.literal("")
                    .append(Text.literal("/radar").styled(s -> s.withColor(Formatting.WHITE)))
                    .append(Text.literal(":").styled(s -> s.withColor(Formatting.WHITE)))
                    .append(Text.literal("toggle ").styled(s -> s.withColor(Formatting.WHITE)))
                    .append(Text.literal("<id|all>").styled(s -> s.withColor(Formatting.GRAY)))
                    .append(Text.literal(" - Toggle highlight for a cluster or all clusters.").styled(s -> s.withColor(Formatting.GRAY)))
                );

                // /radar:info <id> - Show all spawners in a cluster with teleport options.
                source.sendFeedback(Text.literal("")
                    .append(Text.literal("/radar").styled(s -> s.withColor(Formatting.WHITE)))
                    .append(Text.literal(":").styled(s -> s.withColor(Formatting.WHITE)))
                    .append(Text.literal("info ").styled(s -> s.withColor(Formatting.WHITE)))
                    .append(Text.literal("<id>").styled(s -> s.withColor(Formatting.GRAY)))
                    .append(Text.literal(" - Show all spawners in a cluster with teleport options.").styled(s -> s.withColor(Formatting.GRAY)))
                );

                // /radar:reset - Reset all spawner and cluster data.
                source.sendFeedback(Text.literal("")
                     .append(Text.literal("/radar").styled(s -> s.withColor(Formatting.WHITE)))
                    .append(Text.literal(":").styled(s -> s.withColor(Formatting.WHITE)))
                    .append(Text.literal("reset").styled(s -> s.withColor(Formatting.WHITE)))
                    .append(Text.literal(" - Reset all spawner and cluster data.").styled(s -> s.withColor(Formatting.GRAY)))
                );

                // /radar:help - Show this help message.
                source.sendFeedback(Text.literal("")
                    .append(Text.literal("/radar").styled(s -> s.withColor(Formatting.WHITE)))
                    .append(Text.literal(":").styled(s -> s.withColor(Formatting.WHITE)))
                    .append(Text.literal("help").styled(s -> s.withColor(Formatting.WHITE)))
                    .append(Text.literal(" - Show this help message.").styled(s -> s.withColor(Formatting.GRAY)))
                );

                return Command.SINGLE_SUCCESS;
            })
        );
    }

    private static int toggleCluster(CommandContext<FabricClientCommandSource> context)
    {
        String target = StringArgumentType.getString(context, "target").toLowerCase();
        List<SpawnerCluster> clusters = ClusterManager.getClusters();

        if (target.equals("all"))
        {
            if (clusters.isEmpty())
            {
                context.getSource().sendFeedback(Text.literal("No clusters to toggle."));
                RadarClient.LOGGER.warn("Attempted to toggle all clusters but none exist.");
                return 0;
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
            int id = Integer.parseInt(target) - 1;
            try
            {
                if (id < 0 || id >= clusters.size())
                {
                    context.getSource().sendFeedback(Text.literal("Invalid cluster ID."));
                    RadarClient.LOGGER.warn("Attempted to toggle invalid cluster ID {}", id + 1);
                    return 0;
                }

                ClusterManager.toggleHighlightCluster(id);
                RadarClient.LOGGER.info("Toggled highlight for cluster #{}", id + 1);
            } catch (NumberFormatException e)
            {
                context.getSource().sendFeedback(Text.literal("Invalid cluster ID. Use a number or 'all'."));
                return 0;
            }

        }

        return Command.SINGLE_SUCCESS;
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
        String argument = null;
        try
        {
            argument = StringArgumentType.getString(context, "sorting").toLowerCase();
        } catch (Exception ignored) {}

        if ("proximity".equals(argument))
            sortType = SpawnerCluster.SortType.BY_PROXIMITY;
        else if ("size".equals(argument))
            sortType = SpawnerCluster.SortType.BY_SIZE;

        List<SpawnerCluster> clusters = SpawnerCluster.findClusters(source, spawners, 16.0, sortType);
        ClusterManager.setClusters(clusters);
        RadarClient.LOGGER.info("Generated {} clusters using sort type {}", clusters.size(), sortType);

        if (clusters.isEmpty())
        {
            source.sendFeedback(Text.literal("No clusters found."));
            return;
        }

        MutableText showAllButton = Text.literal("[Toggle All]").styled(style -> style
            .withColor(Formatting.GREEN)
            .withClickEvent(new ClickEvent.RunCommand("/radar:toggle all"))
            .withHoverEvent(new HoverEvent.ShowText(Text.literal("Toggle all clusters"))));
        source.getPlayer().sendMessage(showAllButton, false);

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
                .withClickEvent(new ClickEvent.RunCommand("/radar:toggle " + finalId))
                .withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to toggle this cluster"))));

            MutableText teleportButton = Text.literal("[Teleport]").styled(style -> style.withColor(Formatting.GOLD)
                .withClickEvent(new ClickEvent.RunCommand(String.format("/tp %.0f %.0f %.0f", cx, cy, cz)))
                .withHoverEvent(new HoverEvent.ShowText(Text.literal("Teleport to cluster center"))));

            MutableText showSpawnersButton = Text.literal("[Show Spawners]").styled(style -> style.withColor(Formatting.GREEN)
                .withClickEvent(new ClickEvent.RunCommand("/radar:info " + finalId))
                .withHoverEvent(new HoverEvent.ShowText(Text.literal("Show all spawners in this cluster"))));

            MutableText combined = clusterHeader.copy()
                .append(" ")
                .append(teleportButton)
                .append(" ")
                .append(showSpawnersButton);

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
