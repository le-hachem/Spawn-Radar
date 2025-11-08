package cc.hachem;

import net.fabricmc.api.ClientModInitializer;

public class RadarClient implements ClientModInitializer
{
	@Override
	public void onInitializeClient()
	{
		CommandManager.init();
	}
}