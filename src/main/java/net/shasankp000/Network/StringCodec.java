package net.shasankp000.Network;


import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;


public class StringCodec implements StreamCodec<FriendlyByteBuf, String> {
    private final int maxLength;

    public StringCodec(int maxLength) {
        this.maxLength = maxLength;
    }


    @Override
    public String decode(FriendlyByteBuf buf) {
        return buf.readUtf(maxLength);
    }

    @Override
    public void encode(FriendlyByteBuf buf, String value) {
        buf.writeUtf(value, maxLength);
    }
}
