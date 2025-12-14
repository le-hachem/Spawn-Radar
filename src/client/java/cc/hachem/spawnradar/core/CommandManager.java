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
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import java.util.List;
import java.util.Locale;

public class CommandManager
{
    private CommandManager() {}

    private static final String[] HELP_TOPICS = new String[] {"scan", "toggle", "info", "reset"};

    private static final SuggestionProvider<FabricClientCommandSource> SORTING_SUGGESTIONS = (context, builder) ->
    {
        builder.suggest("proximity", Component.translatable("command.spawn_radar.scan.suggestion.proximity"));
        builder.suggest("size", Component.translatable("command.spawn_radar.scan.suggestion.size"));
        return builder.buildFuture();
    };

    private static final SuggestionProvider<FabricClientCommandSource> TOGGLE_SUGGESTIONS = (context, builder) ->
    {
        builder.suggest("all", Component.translatable("command.spawn_radar.toggle.suggestion.all"));
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

    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext registryAccess)
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
                        context.getSource().sendFeedback(Component.translatable("chat.spawn_radar.toggle", target));
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
                    context.getSource().sendFeedback(Component.translatable("chat.spawn_radar.reset"));
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
            source.sendFeedback(Component.literal("=== Spawn Radar ===").withStyle(ChatFormatting.GOLD));
            sendHelpBlock(source, "scan", false);
            sendHelpBlock(source, "toggle", false);
            sendHelpBlock(source, "info", false);
            sendHelpBlock(source, "reset", false);
            source.sendFeedback(Component.translatable("command.spawn_radar.help.misc").withStyle(ChatFormatting.GRAY));
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
            source.sendFeedback(Component.translatable("command.spawn_radar.help.unknown", topic).withStyle(ChatFormatting.RED));
            source.sendFeedback(Component.translatable("command.spawn_radar.help.misc").withStyle(ChatFormatting.GRAY));
            return 0;
        }

        return Command.SINGLE_SUCCESS;
    }

    private static void sendHelpBlock(FabricClientCommandSource source, String key, boolean detailed)
    {
        String base = "command.spawn_radar.help." + key;
        source.sendFeedback(Component.translatable(base + ".title").withStyle(ChatFormatting.YELLOW));
        source.sendFeedback(Component.translatable(base));
        if (detailed)
            source.sendFeedback(Component.translatable(base + ".detail").withStyle(ChatFormatting.WHITE));
        source.sendFeedback(Component.translatable(base + ".usage").withStyle(ChatFormatting.GRAY));
        source.sendFeedback(Component.literal(""));
    }

    private static int executeScan(FabricClientCommandSource source, String sortingArg, int radius, boolean forceRescan)
    {
        String sorting = normalizeSorting(sortingArg);
        RadarClient.reset(source.getPlayer());
        if (RadarClient.generateClusters(source.getPlayer(), radius, sorting, forceRescan))
        {
            source.sendFeedback(Component.translatable("chat.spawn_radar.scan_started"));
            return Command.SINGLE_SUCCESS;
        }
        return 0;
    }

    private static int showClusterInfo(FabricClientCommandSource source, int id)
    {
        List<SpawnerCluster> clusters = ClusterManager.getClusters();
        if (clusters == null || clusters.isEmpty())
        {
            source.sendFeedback(Component.translatable("chat.spawn_radar.invalid_id").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (id < 1)
        {
            source.sendFeedback(Component.translatable("chat.spawn_radar.invalid_id").withStyle(ChatFormatting.RED));
            return 0;
        }

        SpawnerCluster cluster = ClusterManager.getClusterById(id);
        if (cluster == null)
        {
            source.sendFeedback(Component.translatable("chat.spawn_radar.invalid_id").withStyle(ChatFormatting.RED));
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
            source.sendFeedback(Component.translatable("chat.spawn_radar.invalid_id").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (clusterId < 1)
        {
            source.sendFeedback(Component.translatable("chat.spawn_radar.invalid_id").withStyle(ChatFormatting.RED));
            return 0;
        }

        SpawnerCluster cluster = ClusterManager.getClusterById(clusterId);
        if (cluster == null)
        {
            source.sendFeedback(Component.translatable("chat.spawn_radar.invalid_id").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (spawnerIndex < 1 || spawnerIndex > cluster.spawners().size())
        {
            source.sendFeedback(Component.translatable("chat.spawn_radar.invalid_spawner_id").withStyle(ChatFormatting.RED));
            return 0;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null)
        {
            source.sendFeedback(Component.translatable("chat.spawn_radar.info.no_efficiency_data").withStyle(ChatFormatting.RED));
            return 0;
        }

        SpawnerInfo spawner = cluster.spawners().get(spawnerIndex - 1);
        SpawnerEfficiencyAdvisor.AdviceResult result = SpawnerEfficiencyAdvisor.openAdviceBook(client.level, spawner);

        switch (result)
        {
            case OPENED ->
            {
                return Command.SINGLE_SUCCESS;
            }
            case NO_ADVICE ->
            {
                source.sendFeedback(Component.translatable("chat.spawn_radar.info.no_suggestions").withStyle(ChatFormatting.GRAY));
                return Command.SINGLE_SUCCESS;
            }
            case NO_DATA ->
            {
                source.sendFeedback(Component.translatable("chat.spawn_radar.info.no_efficiency_data").withStyle(ChatFormatting.RED));
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
            Component.translatable("command.spawn_radar.info.header", cluster.id(), cluster.spawners().size())
                .withStyle(ChatFormatting.GOLD)
        );

        boolean highlighted = ClusterManager.isHighlighted(cluster.id());
        String key = highlighted
            ? "command.spawn_radar.info.highlighted.on"
            : "command.spawn_radar.info.highlighted.off";
        source.sendFeedback(Component.translatable(key).withStyle(highlighted ? ChatFormatting.GREEN : ChatFormatting.GRAY));

        MutableComponent actions = Component.translatable("command.spawn_radar.info.actions").withStyle(ChatFormatting.DARK_GRAY)
            .append(Component.literal(" "))
            .append(createCommandButton(
                "command.spawn_radar.info.toggle_label",
                "/radar:toggle " + cluster.id(),
                ChatFormatting.AQUA,
                "command.spawn_radar.info.toggle_hover"
            ));

        source.sendFeedback(actions);
        source.sendFeedback(Component.literal(""));
    }

    private static void sendSpawnerLine(FabricClientCommandSource source, int index, SpawnerInfo spawner)
    {
        BlockPos pos = spawner.pos();
        MutableComponent line = Component.translatable("command.spawn_radar.info.spawner_line", index, spawner.mobName())
            .withStyle(ChatFormatting.GREEN)
            .append(createCoordinateComponent(pos))
            .append(Component.literal(" "))
            .append(createTeleportButton(pos));
        source.sendFeedback(line);
    }

    private static MutableComponent createCommandButton(String labelKey, String command, ChatFormatting color, String hoverKey)
    {
        return Component.translatable(labelKey)
            .withStyle(color)
            .withStyle(style -> style
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.translatable(hoverKey)))
            );
    }

    private static MutableComponent createTeleportButton(BlockPos pos)
    {
        String cmd = String.format("/tp %d %d %d", pos.getX(), pos.getY(), pos.getZ());
        return Component.translatable("command.spawn_radar.info.teleport_label")
            .withStyle(ChatFormatting.AQUA)
            .withStyle(style -> style
                .withClickEvent(new ClickEvent.RunCommand(cmd))
                .withHoverEvent(new HoverEvent.ShowText(Component.translatable("command.spawn_radar.teleport_hover")))
            );
    }

    private static MutableComponent createCoordinateComponent(BlockPos pos)
    {
        String coords = String.format("%d, %d, %d", pos.getX(), pos.getY(), pos.getZ());
        return Component.literal("[" + coords + "]")
            .withStyle(ChatFormatting.YELLOW)
            .withStyle(style -> style
                .withClickEvent(new ClickEvent.CopyToClipboard(coords))
                .withHoverEvent(new HoverEvent.ShowText(Component.translatable("command.spawn_radar.info.coords_hover")))
            );
    }

    private static String normalizeSorting(String value)
    {
        if (value == null)
            return "";
        return value.toLowerCase(Locale.ROOT);
    }
}
