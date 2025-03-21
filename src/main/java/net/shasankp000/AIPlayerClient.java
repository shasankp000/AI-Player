package net.shasankp000;

import net.fabricmc.api.ClientModInitializer;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.shasankp000.Network.OpenConfigPayload;



public class AIPlayerClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Register the custom packet receiver so that when the server sends the packet,
        // the client opens the config screen.


        // Setup the handler

        ClientPlayNetworking.registerGlobalReceiver(OpenConfigPayload.ID, (client, handler) -> {

            // Open the config screen on the client.
            // Adjust ConfigManager and the Text as needed.
            net.minecraft.client.gui.screen.Screen currentScreen = net.minecraft.client.MinecraftClient.getInstance().currentScreen;
            net.minecraft.client.MinecraftClient.getInstance().setScreen(
                    new net.shasankp000.GraphicalUserInterface.ConfigManager(net.minecraft.text.Text.empty(), currentScreen)
            );
        });

    }
}

