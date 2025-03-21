package net.shasankp000.Network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;

import net.minecraft.server.network.ServerPlayerEntity;
import net.shasankp000.AIPlayer;
import net.shasankp000.ChatUtils.ChatUtils;


public class configNetworkManager {

    // Called on the server side: sends a packet to the specified player.
    public static void sendOpenConfigPacket(ServerPlayerEntity player) {
        String configData = ConfigJsonUtil.configToJson();
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(configData);
        OpenConfigPayload payload = new OpenConfigPayload(configData); // initialize the config data onto the payload
        ServerPlayNetworking.send(player, payload);
    }


    // --- Save Config Packet: Server Receives Updated Config Data from Client ---
    // Called on the client side to send updated config data.
    public static void sendSaveConfigPacket(String configData) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(configData);
        SaveConfigPayload payload = new SaveConfigPayload(configData);
        ClientPlayNetworking.send(payload);
    }

    // On the server side: register a receiver for the save config packet.
    @SuppressWarnings("resource")
    public static void registerServerSaveReceiver(MinecraftServer server) {
        ServerPlayNetworking.registerGlobalReceiver(SaveConfigPayload.ID, (payload, context) -> {
            // Retrieve the configuration data from the payload
            String newConfigData = payload.configData();
            System.out.println("Config data to save: ");
            System.out.println(newConfigData);

            // Run the config update on the server thread
            context.server().execute(() -> {
                AIPlayer.CONFIG.selectedLanguageModel(newConfigData);
                AIPlayer.CONFIG.save();
                ServerCommandSource serverCommandSource = server.getCommandSource().withSilent().withMaxLevel(4);
                ChatUtils.sendChatMessages(serverCommandSource, "Config saved to server successfully!");
            });
        });
    }





}