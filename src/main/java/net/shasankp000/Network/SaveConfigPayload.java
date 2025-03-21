package net.shasankp000.Network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;


public record SaveConfigPayload(String configData) implements CustomPayload {
    public static final Identifier ID_IDENTIFIER = new Identifier("ai-player", "save_config");
    public static final CustomPayload.Id<SaveConfigPayload> ID = new CustomPayload.Id<>(ID_IDENTIFIER);


    // Define a string codec with a max length (adjust 32767 as needed)
    public static final PacketCodec<PacketByteBuf ,String> STRING_CODEC = new StringCodec(32767);

    // Use the tuple helper to create a codec for this payload
    public static final PacketCodec<PacketByteBuf, SaveConfigPayload> CODEC =
            PacketCodec.tuple(STRING_CODEC, SaveConfigPayload::configData, SaveConfigPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
