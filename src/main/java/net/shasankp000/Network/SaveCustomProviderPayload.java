package net.shasankp000.Network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SaveCustomProviderPayload(String apiKey, String apiUrl) implements CustomPayload {
    public static final Identifier ID_IDENTIFIER = Identifier.of("ai-player", "save_custom_provider");
    public static final CustomPayload.Id<SaveCustomProviderPayload> ID = new CustomPayload.Id<>(ID_IDENTIFIER);

    // Define a string codec with a max length (adjust 32767 as needed)
    public static final PacketCodec<PacketByteBuf, String> STRING_CODEC = new StringCodec(32767);

    // Use the tuple helper to create a codec for this payload
    public static final PacketCodec<PacketByteBuf, SaveCustomProviderPayload> CODEC =
            PacketCodec.tuple(
                    STRING_CODEC, SaveCustomProviderPayload::apiKey,
                    STRING_CODEC, SaveCustomProviderPayload::apiUrl,
                    SaveCustomProviderPayload::new
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}