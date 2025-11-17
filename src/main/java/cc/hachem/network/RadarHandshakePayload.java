package cc.hachem.network;

import cc.hachem.RadarMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record RadarHandshakePayload() implements CustomPayload
{
    public static final CustomPayload.Id<RadarHandshakePayload> ID = new CustomPayload.Id<>(RadarMod.HANDSHAKE_PACKET_ID);
    public static final RadarHandshakePayload INSTANCE = new RadarHandshakePayload();

    public static final PacketCodec<RegistryByteBuf, RadarHandshakePayload> CODEC = new PacketCodec<>()
    {
        @Override
        public RadarHandshakePayload decode(RegistryByteBuf buf)
        {
            return INSTANCE;
        }

        @Override
        public void encode(RegistryByteBuf buf, RadarHandshakePayload value) { }
    };

    @Override
    public CustomPayload.Id<RadarHandshakePayload> getId()
    {
        return ID;
    }
}

