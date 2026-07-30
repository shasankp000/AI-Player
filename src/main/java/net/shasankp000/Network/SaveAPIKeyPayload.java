package net.shasankp000.Network;


import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SaveAPIKeyPayload(String provider, String key) implements CustomPayload {
    public static final Identifier ID_IDENTIFIER = Identifier.of("ai-player", "save_api_key");
    public static final CustomPayload.Id<SaveAPIKeyPayload> ID = new CustomPayload.Id<>(ID_IDENTIFIER);


    // Define a string codec with a max length (adjust 32767 as needed)
    public static final PacketCodec<PacketByteBuf ,String> STRING_CODEC = new StringCodec(32767);

    // Use the tuple helper to create a codec for this payload
    public static final PacketCodec<PacketByteBuf, SaveAPIKeyPayload> CODEC =
            PacketCodec.tuple(
                    STRING_CODEC, SaveAPIKeyPayload::provider,
                    STRING_CODEC, SaveAPIKeyPayload::key,
                    SaveAPIKeyPayload::new
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}