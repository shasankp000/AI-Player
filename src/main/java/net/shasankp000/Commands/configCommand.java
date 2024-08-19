package net.shasankp000.Commands;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import net.shasankp000.GraphicalUserInterface.ConfigManager;


public class configCommand {

    public static void register() {

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(CommandManager.literal("configMan")
                         // gets all strings including whitespaces instead of a single string.
                                .executes( context -> {

                                    MinecraftClient.getInstance().execute(() -> {
                                        Screen currentScreen = MinecraftClient.getInstance().currentScreen;
                                        MinecraftClient.getInstance().setScreen(new ConfigManager(Text.empty(), currentScreen));
                                    });

                                    return 1;
                                        }
                                )));

    }

}
