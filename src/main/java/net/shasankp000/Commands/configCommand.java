package net.shasankp000.Commands;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.shasankp000.Network.configNetworkManager;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.api.EnvType;

public class configCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("configMan")
                    .executes(context -> {
                        // Get the player who executed the command.
                        ServerPlayerEntity player = context.getSource().getPlayer();

                        // Check if we're on a dedicated server.
                        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
                            // On a dedicated server, send a packet to the client to open the config GUI.
                            configNetworkManager.sendOpenConfigPacket(player);
                        }
                        return 1;
                    })
            );
        });
    }
}
