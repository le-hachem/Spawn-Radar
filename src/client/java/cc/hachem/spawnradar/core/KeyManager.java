package cc.hachem.spawnradar.core;

import cc.hachem.spawnradar.RadarClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public class KeyManager
{
    private static final Map<KeyBinding, Runnable> keyCallbacks = new HashMap<>();
    private static final KeyBinding.Category CATEGORY = new KeyBinding.Category(Identifier.of("spawn_radar", "title"));

    private KeyManager() {}

    public static void init()
    {
        registerDefaultBindings();
        ClientTickEvents.END_CLIENT_TICK.register(client -> pollKeybinds());
    }

    private static void registerDefaultBindings()
    {
        register("key.spawn_radar.scan", InputUtil.UNKNOWN_KEY.getCode(), KeyManager::triggerScan);
        register("key.spawn_radar.toggle", InputUtil.UNKNOWN_KEY.getCode(), KeyManager::triggerToggle);
        register("key.spawn_radar.reset", InputUtil.UNKNOWN_KEY.getCode(), KeyManager::triggerReset);
    }

    private static void pollKeybinds()
    {
        keyCallbacks.forEach((key, callback) ->
        {
            while (key.wasPressed())
                callback.run();
        });
    }

    private static void register(String text, int key, Runnable callback)
    {
        KeyBinding keyBinding = KeyBindingHelper.registerKeyBinding(
            new KeyBinding(text, InputUtil.Type.KEYSYM, key, CATEGORY)
        );
        keyCallbacks.put(keyBinding, callback);
    }

    private static void triggerScan()
    {
        ClientPlayerEntity player = RadarClient.getPlayer();
        if (player == null)
            return;
        RadarClient.generateClusters(player, RadarClient.config.defaultSearchRadius, "", false);
    }

    private static void triggerToggle()
    {
        ClientPlayerEntity player = RadarClient.getPlayer();
        if (player == null)
            return;
        RadarClient.toggleCluster(player, "all");
    }

    private static void triggerReset()
    {
        ClientPlayerEntity player = RadarClient.getPlayer();
        if (player == null)
            return;
        RadarClient.reset(player);
    }
}
