package cc.hachem.spawnradar.network;

import cc.hachem.spawnradar.RadarMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RadarHandshakePayload() implements CustomPacketPayload
{
    public static final CustomPacketPayload.Type<RadarHandshakePayload> ID = new CustomPacketPayload.Type<>(RadarMod.HANDSHAKE_PACKET_ID);
    public static final RadarHandshakePayload INSTANCE = new RadarHandshakePayload();

    public static final StreamCodec<RegistryFriendlyByteBuf, RadarHandshakePayload> CODEC = new StreamCodec<>()
    {
        @Override
        public RadarHandshakePayload decode(RegistryFriendlyByteBuf buf)
        {
            return INSTANCE;
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, RadarHandshakePayload value) { }
    };

    @Override
    public CustomPacketPayload.Type<RadarHandshakePayload> type()
    {
        return ID;
    }
}
