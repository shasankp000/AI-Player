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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class configNetworkManager {

    public static final Logger LOGGER = LoggerFactory.getLogger("ConfigNetworkMan");

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

    // Called on the client side to send updated config data.
    public static void sendSaveAPIPacket(String provider, String key) {
        PacketByteBuf serviceProvider = PacketByteBufs.create();
        serviceProvider.writeString(provider);

        PacketByteBuf apiKey = PacketByteBufs.create();
        apiKey.writeString(key);
        SaveAPIKeyPayload payload = new SaveAPIKeyPayload(provider, key);
        ClientPlayNetworking.send(payload);
    }

    // Called on the client side to send custom provider settings (both API key and URL).
    public static void sendSaveCustomProviderPacket(String apiKey, String apiUrl) {
        SaveCustomProviderPayload payload = new SaveCustomProviderPayload(apiKey, apiUrl);
        ClientPlayNetworking.send(payload);
    }


    // On the server side: register a receiver for the model name save config packet.
    @SuppressWarnings("resource")
    public static void registerServerModelNameSaveReceiver(MinecraftServer server) {
        ServerPlayNetworking.registerGlobalReceiver(SaveConfigPayload.ID, (payload, context) -> {
            // Retrieve the configuration data from the payload
            String newConfigData = payload.configData();
            System.out.println("Config data to save: ");
            System.out.println(newConfigData);

            // Run the config update on the server thread
            context.server().execute(() -> {
                AIPlayer.CONFIG.setSelectedLanguageModel(newConfigData);
                AIPlayer.CONFIG.save();
                ServerCommandSource serverCommandSource = server.getCommandSource().withSilent().withMaxLevel(4);
                ChatUtils.sendSystemMessage(serverCommandSource, "Config saved to server successfully!");
            });
        });
    }

    // On the server side: register a single receiver for all API key save packets.
    public static void registerServerAPIKeySaveReceiver(MinecraftServer server) {
        ServerPlayNetworking.registerGlobalReceiver(SaveAPIKeyPayload.ID, (payload, context) -> {
            String provider = payload.provider();
            String newKey = payload.key();

            // Run the config update on the server thread
            context.server().execute(() -> {
                switch (provider) {
                    case "openai":
                        AIPlayer.CONFIG.setOpenAIKey(newKey);
                        break;
                    case "gemini":
                        AIPlayer.CONFIG.setGeminiKey(newKey);
                        break;
                    case "claude":
                        AIPlayer.CONFIG.setClaudeKey(newKey);
                        break;
                    case "grok":
                        AIPlayer.CONFIG.setGrokKey(newKey);
                        break;
                    case "custom":
                        AIPlayer.CONFIG.setCustomApiKey(newKey);
                        break;
                    case "ollama":
                        LOGGER.error("Error! Ollama is not supported in this mode!");
                        return;
                    default:
                        LOGGER.error("Error! Unsupported provider!");
                        return;
                }
                AIPlayer.CONFIG.save();
                ServerCommandSource serverCommandSource = server.getCommandSource().withSilent().withMaxLevel(4);
                ChatUtils.sendSystemMessage(serverCommandSource, "API Key for " + provider + " saved successfully!");
            });
        });
    }

    // On the server side: register a receiver for custom provider settings.
    public static void registerServerCustomProviderSaveReceiver(MinecraftServer server) {
        ServerPlayNetworking.registerGlobalReceiver(SaveCustomProviderPayload.ID, (payload, context) -> {
            String newApiKey = payload.apiKey();
            String newApiUrl = payload.apiUrl();

            // Run the config update on the server thread
            context.server().execute(() -> {
                AIPlayer.CONFIG.setCustomApiKey(newApiKey);
                AIPlayer.CONFIG.setCustomApiUrl(newApiUrl);
                AIPlayer.CONFIG.save();
                ServerCommandSource serverCommandSource = server.getCommandSource().withSilent().withMaxLevel(4);
                ChatUtils.sendSystemMessage(serverCommandSource, "Custom provider settings saved successfully!");
            });
        });
    }


}