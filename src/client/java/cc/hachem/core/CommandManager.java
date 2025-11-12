package cc.hachem.core;

import cc.hachem.RadarClient;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
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
            .executes(context ->
            {
                RadarClient.generateClusters(context.getSource().getPlayer(), RadarClient.config.defaultSearchRadius, "");
                return Command.SINGLE_SUCCESS;
            })
            .then(ClientCommandManager.argument("sorting", StringArgumentType.word())
                .suggests((context, builder) ->
                {
                    builder.suggest("proximity");
                    builder.suggest("size");
                    return builder.buildFuture();
                })
                .executes(context ->
                {
                    String sorting = StringArgumentType.getString(context, "sorting").toLowerCase();
                    RadarClient.generateClusters(context.getSource().getPlayer(), RadarClient.config.defaultSearchRadius, sorting);
                    return Command.SINGLE_SUCCESS;
                })
                .then(ClientCommandManager.argument("radius", IntegerArgumentType.integer(1, 256))
                    .executes(context ->
                    {
                        int radius = IntegerArgumentType.getInteger(context, "radius");
                        String sorting = StringArgumentType.getString(context, "sorting").toLowerCase();
                        RadarClient.generateClusters(context.getSource().getPlayer(), radius, sorting);
                        return Command.SINGLE_SUCCESS;
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
                .executes((context) ->
                {
                    String target = StringArgumentType.getString(context, "target").toLowerCase();
                    RadarClient.toggleCluster(context.getSource().getPlayer(), target);
                    return Command.SINGLE_SUCCESS;
                })
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
                RadarClient.reset(context.getSource().getPlayer());
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



    public static void init()
    {
        ClientCommandRegistrationCallback.EVENT.register(CommandManager::registerCommands);
        RadarClient.LOGGER.info("CommandManager initialized and commands registered.");
    }
}
