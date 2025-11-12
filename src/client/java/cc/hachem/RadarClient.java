package cc.hachem;

import cc.hachem.config.ConfigManager;
import cc.hachem.config.ConfigSerializer;
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
	public static Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static ConfigManager config;

	public static void reset()
	{
		LOGGER.info("Resetting RadarClient data...");
		int clustersBefore = ClusterManager.getClusters().size();
		int highlightsBefore = ClusterManager.getHighlights().size();

		ClusterManager.unhighlightAllClusters();
		ClusterManager.getClusters().clear();
		BlockBank.clear();

		LOGGER.debug("Cleared {} clusters and {} highlights.", clustersBefore, highlightsBefore);
	}

	private void onRender(WorldRenderContext context)
	{
		try
		{
			for (BlockPos pos : ClusterManager.getHighlights())
			{
				BlockHighlightRenderer.draw(context, pos, config.spawnerHighlightColor, config.spawnerHighlightOpacity/100f);

				List<Integer> ids = ClusterManager.getClusterIDAt(pos);
				if (!ids.isEmpty())
				{
					StringBuilder label = new StringBuilder();

					for (int id : ids)
					{
						label.append("Cluster #").append(id + 1);
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

				if (spawnerCount >= config.minimumSpawnersForRegion)
				{
					List<BlockPos> region = hc.cluster().intersectionRegion();
					BlockHighlightRenderer.fillRegionMesh(context, region, clusterColor, config.regionHighlightOpacity/100f);
				}
			}

			BlockHighlightRenderer.submit(MinecraftClient.getInstance());
		}
		catch (Exception e)
		{
			LOGGER.error("Error during rendering: ", e);
		}
	}

	@Override
	public void onInitializeClient()
	{
		LOGGER.info("Initializing RadarClient...");
		ConfigSerializer.load();
		LOGGER.info("Config file loaded.");
		CommandManager.init();
		LOGGER.info("CommandManager initialized.");

		WorldRenderEvents.BEFORE_TRANSLUCENT.register(this::onRender);
		ClientPlayConnectionEvents.JOIN.register(((handler, sender, client) ->
		{
			LOGGER.info("Client joined world, resetting state.");
			reset();
		}));

		LOGGER.info("Initialized successfully.");
	}
}
