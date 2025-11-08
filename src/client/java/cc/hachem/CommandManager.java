package cc.hachem;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

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
		dispatcher.register(ClientCommandManager.literal("select_block")
			.then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
				.suggests((context, builder) ->
				{
					BlockHitResult hit = getTargetedBlock();
					if (hit == null)
						return builder.buildFuture();
					BlockPos pos = hit.getBlockPos();
					builder.suggest(pos.getX());
					return builder.buildFuture();
				})
				.then(ClientCommandManager.argument("y", IntegerArgumentType.integer())
					.suggests((context, builder) ->
					{
						BlockHitResult hit = getTargetedBlock();
						if (hit == null)
							return builder.buildFuture();
						BlockPos pos = hit.getBlockPos();
						builder.suggest(pos.getY());
						return builder.buildFuture();
					})
					.then(ClientCommandManager.argument("z", IntegerArgumentType.integer())
						.suggests((context, builder) ->
						{
							BlockHitResult hit = getTargetedBlock();
							if (hit == null)
								return builder.buildFuture();
							BlockPos pos = hit.getBlockPos();
							builder.suggest(pos.getZ());
							return builder.buildFuture();
						})
						.executes(context ->
						{
							int x = IntegerArgumentType.getInteger(context, "x");
							int y = IntegerArgumentType.getInteger(context, "y");
							int z = IntegerArgumentType.getInteger(context, "z");

							String message = String.format("Added block @ [%d, %d, %d] to block bank.", x, y, z);
							context.getSource().sendFeedback(Text.literal(message));
							BlockBank.add(new BlockPos(x, y, z));
							return Command.SINGLE_SUCCESS;
						})
					)
				)
			)
		);

		dispatcher.register(ClientCommandManager.literal("clear_selection")
			.executes(context ->
			{
				BlockBank.clear();
				context.getSource().sendFeedback(Text.literal("Cleared block bank."));
				return Command.SINGLE_SUCCESS;
			})
		);

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
					RadarClient.scanForSpawners(context.getSource(), RadarClient.DEFAULT_SCAN_RADIUS);
					return Command.SINGLE_SUCCESS;
				})
			)
		);
	}

    public static void init()
    {
        ClientCommandRegistrationCallback.EVENT.register(CommandManager::registerCommands);
    }
}
