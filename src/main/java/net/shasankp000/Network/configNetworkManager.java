package net.shasankp000.Network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.server.network.ServerPlayerEntity;
import net.shasankp000.AIPlayer;

public class configNetworkManager {
    public static final Identifier OPEN_CONFIG_PACKET = new Identifier("ai-player", "open_config");
    public static final Identifier SAVE_CONFIG_PACKET = new Identifier("ai-player", "save_config");


    // Called on the server side: sends a packet to the specified player.
    public static void sendOpenConfigPacket(ServerPlayerEntity player) {
        PacketByteBuf buf = PacketByteBufs.create();
        ServerPlayNetworking.send(player, OPEN_CONFIG_PACKET, buf);
    }


    // --- Save Config Packet: Server Receives Updated Config Data from Client ---
    // Called on the client side to send updated config data.
    public static void sendSaveConfigPacket(String configData) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(configData);
        ClientPlayNetworking.send(SAVE_CONFIG_PACKET, buf);
    }

    // On the server side: register a receiver for the save config packet.
    public static void registerServerSaveReceiver(MinecraftServer server) {
        ServerPlayNetworking.registerGlobalReceiver(SAVE_CONFIG_PACKET, (server1, player, handler, buf, responseSender) -> {
            // Read the configuration data sent by the client.
            String newModelName = buf.readString(32767); // Adjust max length as needed.
            server1.execute(() -> {
                // Now that we are on the server thread, update your config and save it.

                // Update the config using your static instance
                // Update the config using your static instance
                AIPlayer.CONFIG.selectedLanguageModel(newModelName);
                AIPlayer.CONFIG.save();


                player.sendMessage(net.minecraft.text.Text.literal("Configuration updated on server end!"), false);
            });
        });
    }



}