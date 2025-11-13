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
        // /radar:scan
        dispatcher.register(ClientCommandManager.literal("radar:scan")
            .executes(context ->
            {
                RadarClient.generateClusters(context.getSource().getPlayer(), RadarClient.config.defaultSearchRadius, "");
                context.getSource().sendFeedback(Text.translatable("chat.spawn_radar.scan_started"));
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
                    context.getSource().sendFeedback(Text.translatable("chat.spawn_radar.scan_started"));
                    return Command.SINGLE_SUCCESS;
                })
                .then(ClientCommandManager.argument("radius", IntegerArgumentType.integer(1, 256))
                    .executes(context ->
                    {
                        int radius = IntegerArgumentType.getInteger(context, "radius");
                        String sorting = StringArgumentType.getString(context, "sorting").toLowerCase();
                        RadarClient.generateClusters(context.getSource().getPlayer(), radius, sorting);
                        context.getSource().sendFeedback(Text.translatable("chat.spawn_radar.scan_started"));
                        return Command.SINGLE_SUCCESS;
                    })
                )
            )
        );

        // /radar:toggle
        dispatcher.register(ClientCommandManager.literal("radar:toggle")
            .then(ClientCommandManager.argument("target", StringArgumentType.word())
                .suggests((context, builder) ->
                {
                    builder.suggest("all");
                    for (int i = 1; i <= ClusterManager.getClusters().size(); i++)
                        builder.suggest(String.valueOf(i));
                    return builder.buildFuture();
                })
                .executes(context ->
                {
                    String target = StringArgumentType.getString(context, "target").toLowerCase();
                    RadarClient.toggleCluster(context.getSource().getPlayer(), target);
                    context.getSource().sendFeedback(Text.translatable("chat.spawn_radar.toggle", target));
                    return Command.SINGLE_SUCCESS;
                })
            )
        );

        // /radar:info
        dispatcher.register(ClientCommandManager.literal("radar:info")
            .then(ClientCommandManager.argument("id", IntegerArgumentType.integer(1))
                .executes(context ->
                {
                    int id = IntegerArgumentType.getInteger(context, "id");
                    List<SpawnerCluster> clusters = ClusterManager.getClusters();
                    if (clusters == null || clusters.isEmpty() || id > clusters.size())
                    {
                        context.getSource().sendFeedback(Text.translatable("chat.spawn_radar.invalid_id"));
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
                                .withHoverEvent(new HoverEvent.ShowText(Text.translatable("command.spawn_radar.teleport_hover"))));
                        context.getSource().getPlayer().sendMessage(spawnerText, false);
                        sid++;
                    }

                    return Command.SINGLE_SUCCESS;
                })
            )
        );

        // /radar:reset
        dispatcher.register(ClientCommandManager.literal("radar:reset")
            .executes(context ->
            {
                RadarClient.reset(context.getSource().getPlayer());
                RadarClient.LOGGER.info("Radar reset via command.");
                return Command.SINGLE_SUCCESS;
            })
        );

        // /radar:help
        dispatcher.register(ClientCommandManager.literal("radar:help")
            .executes(context ->
            {
                FabricClientCommandSource source = context.getSource();

                // Print each command help line using command.spawn_radar keys
                source.sendFeedback(Text.translatable("command.spawn_radar.help.scan"));
                source.sendFeedback(Text.translatable("command.spawn_radar.help.toggle"));
                source.sendFeedback(Text.translatable("command.spawn_radar.help.info"));
                source.sendFeedback(Text.translatable("command.spawn_radar.help.reset"));
                source.sendFeedback(Text.translatable("command.spawn_radar.help.help"));

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
