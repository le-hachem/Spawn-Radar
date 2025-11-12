package cc.hachem.core;

import cc.hachem.RadarClient;
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
    private static final KeyBinding.Category category = new KeyBinding.Category(Identifier.of("spawn_radar", "title"));


    private static void load()
    {
        register("key.spawn_radar.scan", InputUtil.UNKNOWN_KEY.getCode(), () ->
        {
            ClientPlayerEntity player = RadarClient.getPlayer();
            if (player == null)
                return;
            RadarClient.generateClusters(player, RadarClient.config.defaultSearchRadius, "");
        });

        register("key.spawn_radar.toggle", InputUtil.UNKNOWN_KEY.getCode(), () ->
        {
            ClientPlayerEntity player = RadarClient.getPlayer();
            if (player == null)
                return;
            RadarClient.toggleCluster(player, "all");
        });

        register("key.spawn_radar.reset", InputUtil.UNKNOWN_KEY.getCode(), () ->
        {
            ClientPlayerEntity player = RadarClient.getPlayer();
            if (player == null)
                return;
            RadarClient.reset(player);
        });
    }

    private static void register(String text, int key, Runnable callback)
    {
        KeyBinding keyBinding = KeyBindingHelper.registerKeyBinding(
            new KeyBinding(text, InputUtil.Type.KEYSYM, key, category)
        );
        keyCallbacks.put(keyBinding, callback);
    }

    public static void init()
    {
        load();

        ClientTickEvents.END_CLIENT_TICK.register(client ->
            keyCallbacks.forEach((key, callback) ->
            {
                while (key.wasPressed())
                    callback.run();
            }));
    }
}
