package cc.hachem.spawnradar.core;

import cc.hachem.spawnradar.RadarClient;
import cc.hachem.spawnradar.guide.GuideBookManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Locale;

public class CommandManager
{
    private CommandManager() {}

    private static final String[] HELP_TOPICS = new String[] {"scan", "toggle", "info", "reset"};

    private static final SuggestionProvider<FabricClientCommandSource> SORTING_SUGGESTIONS = (context, builder) ->
    {
        builder.suggest("proximity", Text.translatable("command.spawn_radar.scan.suggestion.proximity"));
        builder.suggest("size", Text.translatable("command.spawn_radar.scan.suggestion.size"));
        return builder.buildFuture();
    };

    private static final SuggestionProvider<FabricClientCommandSource> TOGGLE_SUGGESTIONS = (context, builder) ->
    {
        builder.suggest("all", Text.translatable("command.spawn_radar.toggle.suggestion.all"));
        List<SpawnerCluster> clusters = ClusterManager.getClusters();
        for (int i = 1; i <= clusters.size(); i++)
            builder.suggest(String.valueOf(i));
        return builder.buildFuture();
    };

    private static final SuggestionProvider<FabricClientCommandSource> CLUSTER_ID_SUGGESTIONS = (context, builder) ->
    {
        List<SpawnerCluster> clusters = ClusterManager.getClusters();
        for (int i = 1; i <= clusters.size(); i++)
            builder.suggest(String.valueOf(i));
        return builder.buildFuture();
    };

    private static final SuggestionProvider<FabricClientCommandSource> SPAWNER_ID_SUGGESTIONS = (context, builder) ->
    {
        int clusterId;
        try
        {
            clusterId = IntegerArgumentType.getInteger(context, "id");
        }
        catch (IllegalArgumentException ignored)
        {
            return builder.buildFuture();
        }

        SpawnerCluster cluster = ClusterManager.getClusterById(clusterId);
        if (cluster == null)
            return builder.buildFuture();
        for (int i = 1; i <= cluster.spawners().size(); i++)
            builder.suggest(String.valueOf(i));
        return builder.buildFuture();
    };

    private static final SuggestionProvider<FabricClientCommandSource> HELP_TOPIC_SUGGESTIONS = (context, builder) ->
    {
        for (String topic : HELP_TOPICS)
            builder.suggest(topic);
        return builder.buildFuture();
    };

    public static void init()
    {
        ClientCommandRegistrationCallback.EVENT.register(CommandManager::registerCommands);
        RadarClient.LOGGER.info("CommandManager initialized and commands registered.");
    }

    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess registryAccess)
    {
        registerScanCommand(dispatcher);
        registerToggleCommand(dispatcher);
        registerInfoCommand(dispatcher);
        registerResetCommand(dispatcher);
        registerHelpCommand(dispatcher);
        registerGuideCommand(dispatcher);
    }

    private static void registerScanCommand(CommandDispatcher<FabricClientCommandSource> dispatcher)
    {
        dispatcher.register(ClientCommandManager.literal("radar:scan")
            .executes(context -> executeScan(context.getSource(), "", RadarClient.config.defaultSearchRadius, false))
            .then(ClientCommandManager.literal("manual")
                .executes(context -> executeScan(context.getSource(), "", RadarClient.config.defaultSearchRadius, true))
                .then(ClientCommandManager.argument("sorting", StringArgumentType.word())
                    .suggests(SORTING_SUGGESTIONS)
                    .executes(context -> executeScan(
                        context.getSource(),
                        StringArgumentType.getString(context, "sorting"),
                        RadarClient.config.defaultSearchRadius,
                        true
                    ))
                    .then(ClientCommandManager.argument("radius", IntegerArgumentType.integer(1, 256))
                        .executes(context -> executeScan(
                            context.getSource(),
                            StringArgumentType.getString(context, "sorting"),
                            IntegerArgumentType.getInteger(context, "radius"),
                            true
                        ))
                    )
                )
            )
            .then(ClientCommandManager.argument("sorting", StringArgumentType.word())
                .suggests(SORTING_SUGGESTIONS)
                .executes(context -> executeScan(
                    context.getSource(),
                    StringArgumentType.getString(context, "sorting"),
                    RadarClient.config.defaultSearchRadius,
                    false
                ))
                .then(ClientCommandManager.argument("radius", IntegerArgumentType.integer(1, 256))
                    .executes(context -> executeScan(
                        context.getSource(),
                        StringArgumentType.getString(context, "sorting"),
                        IntegerArgumentType.getInteger(context, "radius"),
                        false
                    ))
                )
            )
        );
    }

    private static void registerToggleCommand(CommandDispatcher<FabricClientCommandSource> dispatcher)
    {
        dispatcher.register(ClientCommandManager.literal("radar:toggle")
            .then(ClientCommandManager.argument("target", StringArgumentType.word())
                .suggests(TOGGLE_SUGGESTIONS)
                .executes(context ->
                {
                    String target = StringArgumentType.getString(context, "target").toLowerCase();
                    if (RadarClient.toggleCluster(context.getSource().getPlayer(), target))
                    {
                        context.getSource().sendFeedback(Text.translatable("chat.spawn_radar.toggle", target));
                        return Command.SINGLE_SUCCESS;
                    }
                    return 0;
                })
            )
        );
    }

    private static void registerInfoCommand(CommandDispatcher<FabricClientCommandSource> dispatcher)
    {
        dispatcher.register(ClientCommandManager.literal("radar:info")
            .executes(context -> showInfoUsage(context.getSource()))
            .then(ClientCommandManager.argument("id", IntegerArgumentType.integer(1))
                .suggests(CLUSTER_ID_SUGGESTIONS)
                .executes(context ->
                    showClusterInfo(
                        context.getSource(),
                        IntegerArgumentType.getInteger(context, "id")
                    )
                )
                .then(ClientCommandManager.argument("spawner", IntegerArgumentType.integer(1))
                    .suggests(SPAWNER_ID_SUGGESTIONS)
                    .executes(context ->
                        openSpawnerAdvisor(
                            context.getSource(),
                            IntegerArgumentType.getInteger(context, "id"),
                            IntegerArgumentType.getInteger(context, "spawner")
                        )
                    )
                )
            )
        );
    }

    private static void registerResetCommand(CommandDispatcher<FabricClientCommandSource> dispatcher)
    {
        dispatcher.register(ClientCommandManager.literal("radar:reset")
            .executes(context ->
            {
                if (RadarClient.reset(context.getSource().getPlayer()))
                {
                    RadarClient.LOGGER.info("Radar reset via command.");
                    context.getSource().sendFeedback(Text.translatable("chat.spawn_radar.reset"));
                    return Command.SINGLE_SUCCESS;
                }
                return 0;
            })
        );
    }

    private static void registerGuideCommand(CommandDispatcher<FabricClientCommandSource> dispatcher)
    {
        dispatcher.register(ClientCommandManager.literal("radar:guide")
            .executes(context ->
            {
                GuideBookManager.openGuide();
                return Command.SINGLE_SUCCESS;
            })
        );
    }

    private static void registerHelpCommand(CommandDispatcher<FabricClientCommandSource> dispatcher)
    {
        dispatcher.register(ClientCommandManager.literal("radar:help")
            .executes(context -> showHelp(context.getSource(), null))
            .then(ClientCommandManager.argument("command", StringArgumentType.word())
                .suggests(HELP_TOPIC_SUGGESTIONS)
                .executes(context -> showHelp(
                    context.getSource(),
                    StringArgumentType.getString(context, "command")
                ))
            )
        );
    }

    private static int showHelp(FabricClientCommandSource source, String topic)
    {
        if (topic == null || topic.isBlank())
        {
            source.sendFeedback(Text.literal("=== Spawn Radar ===").formatted(Formatting.GOLD));
            sendHelpBlock(source, "scan", false);
            sendHelpBlock(source, "toggle", false);
            sendHelpBlock(source, "info", false);
            sendHelpBlock(source, "reset", false);
            source.sendFeedback(Text.translatable("command.spawn_radar.help.misc").formatted(Formatting.GRAY));
            return Command.SINGLE_SUCCESS;
        }

        String normalized = topic.toLowerCase(Locale.ROOT);
        boolean handled = switch (normalized)
        {
            case "scan" -> { sendHelpBlock(source, "scan", true); yield true; }
            case "toggle" -> { sendHelpBlock(source, "toggle", true); yield true; }
            case "info" -> { sendHelpBlock(source, "info", true); yield true; }
            case "reset" -> { sendHelpBlock(source, "reset", true); yield true; }
            default -> false;
        };

        if (!handled)
        {
            source.sendFeedback(Text.translatable("command.spawn_radar.help.unknown", topic).formatted(Formatting.RED));
            source.sendFeedback(Text.translatable("command.spawn_radar.help.misc").formatted(Formatting.GRAY));
            return 0;
        }

        return Command.SINGLE_SUCCESS;
    }

    private static void sendHelpBlock(FabricClientCommandSource source, String key, boolean detailed)
    {
        String base = "command.spawn_radar.help." + key;
        source.sendFeedback(Text.translatable(base + ".title").formatted(Formatting.YELLOW));
        source.sendFeedback(Text.translatable(base));
        if (detailed)
            source.sendFeedback(Text.translatable(base + ".detail").formatted(Formatting.WHITE));
        source.sendFeedback(Text.translatable(base + ".usage").formatted(Formatting.GRAY));
        source.sendFeedback(Text.literal(""));
    }

    private static int executeScan(FabricClientCommandSource source, String sortingArg, int radius, boolean forceRescan)
    {
        String sorting = normalizeSorting(sortingArg);
        RadarClient.reset(source.getPlayer());
        if (RadarClient.generateClusters(source.getPlayer(), radius, sorting, forceRescan))
        {
            source.sendFeedback(Text.translatable("chat.spawn_radar.scan_started"));
            return Command.SINGLE_SUCCESS;
        }
        return 0;
    }

    private static int showClusterInfo(FabricClientCommandSource source, int id)
    {
        List<SpawnerCluster> clusters = ClusterManager.getClusters();
        if (clusters == null || clusters.isEmpty())
        {
            source.sendFeedback(Text.translatable("chat.spawn_radar.invalid_id").formatted(Formatting.RED));
            return 0;
        }

        if (id < 1)
        {
            source.sendFeedback(Text.translatable("chat.spawn_radar.invalid_id").formatted(Formatting.RED));
            return 0;
        }

        SpawnerCluster cluster = ClusterManager.getClusterById(id);
        if (cluster == null)
        {
            source.sendFeedback(Text.translatable("chat.spawn_radar.invalid_id").formatted(Formatting.RED));
            return 0;
        }
        sendClusterSummary(source, cluster);

        int sid = 1;
        for (SpawnerInfo spawner : cluster.spawners())
            sendSpawnerLine(source, sid++, spawner);

        return Command.SINGLE_SUCCESS;
    }

    private static int showInfoUsage(FabricClientCommandSource source)
    {
        sendHelpBlock(source, "info", true);
        return 0;
    }

    private static int openSpawnerAdvisor(FabricClientCommandSource source, int clusterId, int spawnerIndex)
    {
        List<SpawnerCluster> clusters = ClusterManager.getClusters();
        if (clusters == null || clusters.isEmpty())
        {
            source.sendFeedback(Text.translatable("chat.spawn_radar.invalid_id").formatted(Formatting.RED));
            return 0;
        }

        if (clusterId < 1)
        {
            source.sendFeedback(Text.translatable("chat.spawn_radar.invalid_id").formatted(Formatting.RED));
            return 0;
        }

        SpawnerCluster cluster = ClusterManager.getClusterById(clusterId);
        if (cluster == null)
        {
            source.sendFeedback(Text.translatable("chat.spawn_radar.invalid_id").formatted(Formatting.RED));
            return 0;
        }
        if (spawnerIndex < 1 || spawnerIndex > cluster.spawners().size())
        {
            source.sendFeedback(Text.translatable("chat.spawn_radar.invalid_spawner_id").formatted(Formatting.RED));
            return 0;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null)
        {
            source.sendFeedback(Text.translatable("chat.spawn_radar.info.no_efficiency_data").formatted(Formatting.RED));
            return 0;
        }

        SpawnerInfo spawner = cluster.spawners().get(spawnerIndex - 1);
        SpawnerEfficiencyAdvisor.AdviceResult result = SpawnerEfficiencyAdvisor.openAdviceBook(client.world, spawner);

        switch (result)
        {
            case OPENED ->
            {
                return Command.SINGLE_SUCCESS;
            }
            case NO_ADVICE ->
            {
                source.sendFeedback(Text.translatable("chat.spawn_radar.info.no_suggestions").formatted(Formatting.GRAY));
                return Command.SINGLE_SUCCESS;
            }
            case NO_DATA ->
            {
                source.sendFeedback(Text.translatable("chat.spawn_radar.info.no_efficiency_data").formatted(Formatting.RED));
                return 0;
            }
            default ->
            {
                return 0;
            }
        }
    }

    private static void sendClusterSummary(FabricClientCommandSource source, SpawnerCluster cluster)
    {
        source.sendFeedback(
            Text.translatable("command.spawn_radar.info.header", cluster.id(), cluster.spawners().size())
                .formatted(Formatting.GOLD)
        );

        boolean highlighted = ClusterManager.isHighlighted(cluster.id());
        String key = highlighted
            ? "command.spawn_radar.info.highlighted.on"
            : "command.spawn_radar.info.highlighted.off";
        source.sendFeedback(Text.translatable(key).formatted(highlighted ? Formatting.GREEN : Formatting.GRAY));

        MutableText actions = Text.translatable("command.spawn_radar.info.actions").formatted(Formatting.DARK_GRAY)
            .append(Text.literal(" "))
            .append(createCommandButton(
                "command.spawn_radar.info.toggle_label",
                "/radar:toggle " + cluster.id(),
                Formatting.AQUA,
                "command.spawn_radar.info.toggle_hover"
            ));

        source.sendFeedback(actions);
        source.sendFeedback(Text.literal(""));
    }

    private static void sendSpawnerLine(FabricClientCommandSource source, int index, SpawnerInfo spawner)
    {
        BlockPos pos = spawner.pos();
        MutableText line = Text.translatable("command.spawn_radar.info.spawner_line", index, spawner.mobName())
            .formatted(Formatting.GREEN)
            .append(createCoordinateComponent(pos))
            .append(Text.literal(" "))
            .append(createTeleportButton(pos));
        source.sendFeedback(line);
    }

    private static MutableText createCommandButton(String labelKey, String command, Formatting color, String hoverKey)
    {
        return Text.translatable(labelKey)
            .formatted(color)
            .styled(style -> style
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Text.translatable(hoverKey)))
            );
    }

    private static MutableText createTeleportButton(BlockPos pos)
    {
        String cmd = String.format("/tp %d %d %d", pos.getX(), pos.getY(), pos.getZ());
        return Text.translatable("command.spawn_radar.info.teleport_label")
            .formatted(Formatting.AQUA)
            .styled(style -> style
                .withClickEvent(new ClickEvent.RunCommand(cmd))
                .withHoverEvent(new HoverEvent.ShowText(Text.translatable("command.spawn_radar.teleport_hover")))
            );
    }

    private static MutableText createCoordinateComponent(BlockPos pos)
    {
        String coords = String.format("%d, %d, %d", pos.getX(), pos.getY(), pos.getZ());
        return Text.literal("[" + coords + "]")
            .formatted(Formatting.YELLOW)
            .styled(style -> style
                .withClickEvent(new ClickEvent.CopyToClipboard(coords))
                .withHoverEvent(new HoverEvent.ShowText(Text.translatable("command.spawn_radar.info.coords_hover")))
            );
    }

    private static String normalizeSorting(String value)
    {
        if (value == null)
            return "";
        return value.toLowerCase(Locale.ROOT);
    }
}
