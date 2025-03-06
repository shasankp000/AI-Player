package net.shasankp000;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.shasankp000.Commands.configCommand;
import net.shasankp000.Commands.modCommandRegistry;
import net.shasankp000.GameAI.BotEventHandler;

import net.shasankp000.Database.QTableStorage;
import net.shasankp000.Database.SQLiteDB;
import net.shasankp000.Entity.AutoFaceEntity;
import net.shasankp000.FilingSystem.AIPlayerConfig;
import net.shasankp000.Network.configNetworkManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class AIPlayer implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("ai-player");
	public static final AIPlayerConfig CONFIG = AIPlayerConfig.createAndLoad(); // initialize the config.
	public static MinecraftServer serverInstance = null; // default for now

	@Override
	public void onInitialize() {

		LOGGER.info("Hello Fabric world!");

		modCommandRegistry.register();
		configCommand.register();
		SQLiteDB.createDB();
		QTableStorage.setupQTableStorage();

		// Inside AIPlayer.onInitialize()
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			configNetworkManager.registerServerSaveReceiver(server);
			serverInstance = server;
			LOGGER.info("Server instance stored!");

			System.out.println("Server instance is " + serverInstance);

		});


		ServerLifecycleEvents.SERVER_STOPPED.register(AutoFaceEntity::onServerStopped);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (entity instanceof ServerPlayerEntity serverPlayer) {

				if (BotEventHandler.bot != null) {

					if(serverPlayer.getName().getString().equals(BotEventHandler.bot.getName().getString())) {

						QTableStorage.saveLastKnownState(BotEventHandler.getCurrentState(), BotEventHandler.qTableDir + "/lastKnownState.bin");

						BotEventHandler.botDied = true; // set flag for bot's death.
//						System.out.println("Set botDied flag to true");
					}
				}

			}

		});

		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			// Check if the respawned player is the bot
			if (oldPlayer instanceof ServerPlayerEntity && newPlayer instanceof ServerPlayerEntity && oldPlayer.getName().getString().equals(newPlayer.getName().getString())) {
				System.out.println("Bot has respawned. Updating state...");
				BotEventHandler.hasRespawned = true;
				BotEventHandler.botSpawnCount++;

			}
		});

	}


}
