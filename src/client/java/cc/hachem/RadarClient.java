package cc.hachem;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RadarClient implements ClientModInitializer
{
	private static final Logger LOGGER = LoggerFactory.getLogger("radar");

	@Override
	public void onInitializeClient()
	{
		LOGGER.info("Client Mod Loaded Successfully");
	}
}