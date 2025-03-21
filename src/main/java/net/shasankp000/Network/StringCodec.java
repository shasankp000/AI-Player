package net.shasankp000.Network;


import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;


public class StringCodec implements PacketCodec<PacketByteBuf, String> {
    private final int maxLength;

    public StringCodec(int maxLength) {
        this.maxLength = maxLength;
    }


    @Override
    public String decode(PacketByteBuf buf) {
        return buf.readString(maxLength);
    }

    @Override
    public void encode(PacketByteBuf buf, String value) {
        buf.writeString(value, maxLength);
    }
}
