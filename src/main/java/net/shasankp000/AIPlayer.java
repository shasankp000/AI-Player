package net.shasankp000;

import net.fabricmc.api.ModInitializer;
import net.shasankp000.Commands.spawnFakePlayer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AIPlayer implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("ai-player");

	@Override
	public void onInitialize() {

		LOGGER.info("Hello Fabric world!");

		spawnFakePlayer.register();




	}
}