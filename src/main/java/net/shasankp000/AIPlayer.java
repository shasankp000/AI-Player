package net.shasankp000;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.shasankp000.Commands.configCommand;
import net.shasankp000.Commands.spawnFakePlayer;

import net.shasankp000.Database.SQLiteDB;
import net.shasankp000.Entity.AutoFaceEntity;
import net.shasankp000.FilingSystem.AIPlayerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class AIPlayer implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("ai-player");
	public static final AIPlayerConfig CONFIG = AIPlayerConfig.createAndLoad(); // initialize the config.

	@Override
	public void onInitialize() {

		LOGGER.info("Hello Fabric world!");

		spawnFakePlayer.register();
		configCommand.register();
		SQLiteDB.createDB();

		ServerLifecycleEvents.SERVER_STOPPED.register(AutoFaceEntity::onServerStopped);
	}


}
