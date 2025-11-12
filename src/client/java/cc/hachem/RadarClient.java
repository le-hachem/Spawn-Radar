package cc.hachem;

import cc.hachem.config.ConfigManager;
import cc.hachem.core.BlockBank;
import cc.hachem.core.ClusterManager;
import cc.hachem.core.CommandManager;
import cc.hachem.renderer.BlockHighlightRenderer;
import cc.hachem.renderer.TextRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

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
			BlockHighlightRenderer.draw(context, pos, ConfigManager.spawnerHighlightColor, 0.5f);

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

		for (ClusterManager.HighlightedCluster hc : ClusterManager.getHighlightedClusters())
		{
			int spawnerCount = hc.cluster().spawners().size();
			int clusterColor = ConfigManager.getClusterColor(spawnerCount);
//			for (BlockPos pos : hc.cluster().spawners())
//				BlockHighlightRenderer.draw(context, pos, clusterColor, 0.5f);
			if (spawnerCount >= ConfigManager.minimumSpawnersForRegion)
			{
				List<BlockPos> region = hc.cluster().intersectionRegion();
				BlockHighlightRenderer.fillRegionMesh(context, region, clusterColor, 0.3f);
			}
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