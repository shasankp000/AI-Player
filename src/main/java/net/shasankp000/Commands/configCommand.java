package net.shasankp000.Commands;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.shasankp000.Network.configNetworkManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.api.EnvType;

public class configCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("configMan")
                    .executes(context -> {
                        // Get the player who executed the command.
                        ServerPlayer player = context.getSource().getPlayer();

                        // Check if we're on a dedicated server.
                        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
                            // On a dedicated server, send a packet to the client to open the config GUI.
                            configNetworkManager.sendOpenConfigPacket(player);
                        }
                        else {
                            // we are on client, send packet to the client to open the config GUI

                            configNetworkManager.sendOpenConfigPacket(player);

                        }

                        return 1;
                    })
            );
        });
    }
}
