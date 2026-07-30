package net.shasankp000.Network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;


public record SaveConfigPayload(String configData) implements CustomPacketPayload {
    public static final Identifier ID_IDENTIFIER = Identifier.fromNamespaceAndPath("ai-player", "save_config");
    public static final CustomPacketPayload.Type<SaveConfigPayload> ID = new CustomPacketPayload.Type<>(ID_IDENTIFIER);


    // Define a string codec with a max length (adjust 32767 as needed)
    public static final StreamCodec<FriendlyByteBuf ,String> STRING_CODEC = new StringCodec(32767);

    // Use the tuple helper to create a codec for this payload
    public static final StreamCodec<FriendlyByteBuf, SaveConfigPayload> CODEC =
            StreamCodec.composite(STRING_CODEC, SaveConfigPayload::configData, SaveConfigPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
