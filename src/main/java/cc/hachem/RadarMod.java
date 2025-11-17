package cc.hachem;

import cc.hachem.network.RadarHandshakePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RadarMod implements ModInitializer
{
    public static final String MOD_ID = "spawn-radar";
    public static final Identifier HANDSHAKE_PACKET_ID = Identifier.of(MOD_ID, "handshake");

    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize()
    {
        registerNetworking();
        LOGGER.info("Spawn Radar common initializer loaded.");
    }

    private void registerNetworking()
    {
        PayloadTypeRegistry.playS2C().register(RadarHandshakePayload.ID, RadarHandshakePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RadarHandshakePayload.ID, RadarHandshakePayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(RadarHandshakePayload.ID, (payload, context) ->
        {
            LOGGER.info("Received spawn radar handshake from {}", context.player().getName().getString());
            ServerPlayNetworking.send(context.player(), RadarHandshakePayload.INSTANCE);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
        {
            LOGGER.info("Sending spawn radar handshake to {}", handler.player.getName().getString());
            ServerPlayNetworking.send(handler.player, RadarHandshakePayload.INSTANCE);
        });
    }
}

