package cc.hachem;

import cc.hachem.core.BlockBank;
import cc.hachem.core.ChunkSnapshot;
import cc.hachem.core.ClusterManager;
import cc.hachem.core.CommandManager;
import cc.hachem.renderer.BlockHighlightRenderer;
import cc.hachem.renderer.TextRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class RadarClient implements ClientModInitializer
{
	public static final String MOD_ID = "radar";
	public static final int DEFAULT_SCAN_RADIUS = 64;
	public static Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static void reset()
	{
		ClusterManager.clearHighlights();
		ClusterManager.getClusters().clear();
		BlockBank.clear();
	}

	private void onRender(WorldRenderContext context)
	{
		for (BlockPos pos : ClusterManager.getHighlights())
		{
			BlockHighlightRenderer.draw(context, pos, 0, 1, 0, 0.5f);

			List<Integer> ids = ClusterManager.getClusterIDAt(pos);
			if (!ids.isEmpty())
			{
				StringBuilder label = new StringBuilder();

				for (int id : ids)
				{
					label.append("Cluster #").append(id+1);
					if (id != ids.getLast())
						label.append("\n");
				}
				TextRenderer.renderBlockNametag(context, pos, label.toString());
			}
		}

		List<List<BlockPos>> intersections = ClusterManager.getHighlightedIntersectionRegions();
		for (List<BlockPos> region : intersections)
		{
			BlockHighlightRenderer.fillRegionMesh(context, region, 1f, 0f, 0f, 0.3f);
			BlockHighlightRenderer.submit(MinecraftClient.getInstance());
		}

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