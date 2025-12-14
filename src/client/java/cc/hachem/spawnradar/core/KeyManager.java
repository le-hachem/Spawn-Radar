package cc.hachem.spawnradar.core;

import cc.hachem.spawnradar.RadarClient;import com.mojang.blaze3d.platform.InputConstants;import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;import net.minecraft.client.KeyMapping;import net.minecraft.client.player.LocalPlayer;import net.minecraft.resources.ResourceLocation;import java.util.HashMap;
import java.util.Map;

public class KeyManager
{
    private static final Map<KeyMapping, Runnable> keyCallbacks = new HashMap<>();
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(ResourceLocation.fromNamespaceAndPath("spawn_radar", "title"));

    private KeyManager() {}

    public static void init()
    {
        registerDefaultBindings();
        ClientTickEvents.END_CLIENT_TICK.register(client -> pollKeybinds());
    }

    private static void registerDefaultBindings()
    {
        register("key.spawn_radar.scan", InputConstants.UNKNOWN.getValue(), KeyManager::triggerScan);
        register("key.spawn_radar.toggle", InputConstants.UNKNOWN.getValue(), KeyManager::triggerToggle);
        register("key.spawn_radar.reset", InputConstants.UNKNOWN.getValue(), KeyManager::triggerReset);
    }

    private static void pollKeybinds()
    {
        keyCallbacks.forEach((key, callback) ->
        {
            while (key.consumeClick())
                callback.run();
        });
    }

    private static void register(String text, int key, Runnable callback)
    {
        KeyMapping keyBinding = KeyBindingHelper.registerKeyBinding(
            new KeyMapping(text, InputConstants.Type.KEYSYM, key, CATEGORY)
        );
        keyCallbacks.put(keyBinding, callback);
    }

    private static void triggerScan()
    {
        LocalPlayer player = RadarClient.getPlayer();
        if (player == null)
            return;
        RadarClient.generateClusters(player, RadarClient.config.defaultSearchRadius, "", false);
    }

    private static void triggerToggle()
    {
        LocalPlayer player = RadarClient.getPlayer();
        if (player == null)
            return;
        RadarClient.toggleCluster(player, "all");
    }

    private static void triggerReset()
    {
        LocalPlayer player = RadarClient.getPlayer();
        if (player == null)
            return;
        RadarClient.reset(player);
    }
}
