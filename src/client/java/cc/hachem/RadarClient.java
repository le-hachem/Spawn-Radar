package cc.hachem;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class RadarClient implements ClientModInitializer
{
	public static final String MOD_ID = "radar";
	public static final int DEFAULT_SCAN_RADIUS = 64;
	public static Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static void scanForSpawners(FabricClientCommandSource source, int chunkRadius)
	{
		ClusterManager.getClusters().clear();
		BlockBank.clear();

		source.sendFeedback(Text.of("Searching for spawners..."));

		new Thread(() ->
		{
			BlockPos playerPos = source.getPlayer().getBlockPos();
			int playerChunkX = playerPos.getX() >> 4;
			int playerChunkZ = playerPos.getZ() >> 4;

			List<BlockPos> foundSpawners = new ArrayList<>();
			for (int dx = -chunkRadius; dx <= chunkRadius; dx++)
				for (int dz = -chunkRadius; dz <= chunkRadius; dz++)
				{
					int chunkX = playerChunkX + dx;
					int chunkZ = playerChunkZ + dz;

					if (!source.getWorld().isChunkLoaded(chunkX, chunkZ)) continue;

					int baseX = chunkX << 4;
					int baseZ = chunkZ << 4;

					int minY = source.getWorld().getBottomY();
					int maxY = source.getWorld().getHeight();

					for (int y = minY; y < maxY; y++)
						for (int x = 0; x < 16; x++)
							for (int z = 0; z < 16; z++)
							{
								BlockPos pos = new BlockPos(baseX + x, y, baseZ + z);
								if (source.getWorld().getBlockState(pos).isOf(Blocks.SPAWNER))
								{
									foundSpawners.add(pos);
									BlockBank.add(pos);
								}
							}
				}

			int spawnersFound = foundSpawners.size();
			source.getClient().execute(() ->
			{
				if (spawnersFound == 0)
					source.sendFeedback(Text.of("No spawners found."));
				else
					source.sendFeedback(Text.of("Found " + spawnersFound + " spawners:"));
			});
		}).start();
	}

	public static void reset()
	{
		ClusterManager.clearHighlights();
		ClusterManager.getClusters().clear();
		BlockBank.clear();
	}

	private void onRender(WorldRenderContext context)
	{
		for (BlockPos pos : ClusterManager.getHighlights())
			BlockHighlightRenderer.draw(context, pos, 0, 1, 0, 0.5f);
		List<BlockPos> intersection = ClusterManager.getHighlightedIntersectionRegion();
		BlockHighlightRenderer.fillRegionMesh(context, intersection, 0f, 0f, 1f, 0.3f);
		BlockHighlightRenderer.submit(MinecraftClient.getInstance());
	}

	@Override
	public void onInitializeClient()
	{
		CommandManager.init();

		WorldRenderEvents.BEFORE_TRANSLUCENT.register(this::onRender);
		ClientPlayConnectionEvents.JOIN.register(((handler, sender, client) -> reset()));
	}
}