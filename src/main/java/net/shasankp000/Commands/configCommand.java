package net.shasankp000.Commands;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.shasankp000.Network.configNetworkManager;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public class configCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("configMan")
                    .requires(source -> configNetworkManager.canManageServerConfig(source.getPlayer()))
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayer();
                        configNetworkManager.sendOpenConfigPacket(player);

                        return 1;
                    })
            );
        });
    }
}
