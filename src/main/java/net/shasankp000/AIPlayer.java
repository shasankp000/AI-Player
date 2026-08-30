package net.shasankp000;

import ai.djl.ModelException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.shasankp000.ChatUtils.BERTModel.BertModelManager;
import net.shasankp000.ChatUtils.NLPProcessor;
import net.shasankp000.Commands.configCommand;
import net.shasankp000.Commands.modCommandRegistry;
import net.shasankp000.Database.QTable;
import net.shasankp000.Database.SQLiteDB;
import net.shasankp000.FilingSystem.ManualConfig;
import net.shasankp000.GameAI.BotEventHandler;
import net.shasankp000.GameAI.autonomous.AutonomousManager;
import net.shasankp000.GameAI.autonomous.ServerChatEventBridge;
import net.shasankp000.GameAI.handoff.ItemHandoffListener;
import net.shasankp000.GameAI.handoff.TradeListener;

import net.shasankp000.Database.QTableStorage;
import net.shasankp000.Entity.AutoFaceEntity;
import net.shasankp000.GameAI.RLAgent;
import net.shasankp000.Network.OpenConfigPayload;
import net.shasankp000.Network.SaveAPIKeyPayload;
import net.shasankp000.Network.SaveConfigPayload;
import net.shasankp000.Network.SaveCustomProviderPayload;
import net.shasankp000.Network.configNetworkManager;
import net.shasankp000.PlayerUtils.FoodConsumptionTool;
import net.shasankp000.PlayerUtils.MiningTool;
import net.shasankp000.PathFinding.NavigationService;
import net.shasankp000.WebSearch.AISearchConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;


public class AIPlayer implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("ai-player");
	public static final ManualConfig CONFIG = ManualConfig.load();
	public static MinecraftServer serverInstance = null; // default for now
	public static BertModelManager modelManager;
	public static boolean loadedBERTModelIntoMemory = false;


	@Override
	public void onInitialize() {

		LOGGER.info("Hello Fabric world!");

		LOGGER.debug("Running on environment type: {}", FabricLoader.getInstance().getEnvironmentType());

		// Fix DJL cache directory path on Windows (Issue #33)
		// DJL constructs paths incorrectly on Windows, missing backslash after username
		// Explicitly set the cache directory to avoid path construction bugs
		String userHome = System.getProperty("user.home");
		if (userHome != null && !userHome.isEmpty()) {
			String djlCacheDir = userHome + "/.djl.ai";
			System.setProperty("DJL_CACHE_DIR", djlCacheDir);
			LOGGER.info("Set DJL cache directory to: {}", djlCacheDir);
		}

		String llmProvider = System.getProperty("aiplayer.llmMode", "custom");

		System.out.println("Using provider: " + llmProvider);

		// Debug: Print ALL system properties to see what's available
		System.out.println("=== ALL SYSTEM PROPERTIES ===");
		System.getProperties().forEach((key, value) -> {
			if (key.toString().contains("aiplayer") || key.toString().contains("llm")) {
				System.out.println(key + " = " + value);
			}
		});
		System.out.println("=== END DEBUG ===");


        // registering the packets on the global entrypoint to recognise them

		PayloadTypeRegistry.serverboundPlay().register(SaveConfigPayload.ID, SaveConfigPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenConfigPayload.ID, OpenConfigPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SaveAPIKeyPayload.ID, SaveAPIKeyPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SaveCustomProviderPayload.ID, SaveCustomProviderPayload.CODEC);


		modCommandRegistry.register();
		configCommand.register();
		SQLiteDB.createDB();
		QTableStorage.setupQTableStorage();

		// Register the server-side chat bridge so WorldEventListener receives
		// join/leave/death/advancement messages for all bots.
		ServerChatEventBridge.register();

		// Feature 4.2 — Smart Item Handoff: react to players throwing items at the bot.
		ItemHandoffListener.register();

		// Feature 7 — Trade Request System: wire up sneak-throw item detection.
		// TradeListener hooks PlayerPickupItemCallback to detect when a player throws
		// an item while sneaking near the bot, driving the two-phase chat-based trade flow.
		TradeListener.register();
		NavigationService.register();
		MiningTool.register();

		CompletableFuture.runAsync(() -> {

			AISearchConfig.setupIfMissing();
			NLPProcessor.ensureLocalNLPModel();
			try {
				Thread.sleep(2000);
				System.out.println("NLP model deployment task complete");
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}

		});


		modelManager = BertModelManager.getInstance();

		// Inside AIPlayer.onInitialize()
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			configNetworkManager.registerServerModelNameSaveReceiver(server);
			configNetworkManager.registerServerAPIKeySaveReceiver(server);
			configNetworkManager.registerServerCustomProviderSaveReceiver(server);
			serverInstance = server;
			LOGGER.info("Server instance stored!");

			System.out.println("Server instance is " + serverInstance);

			LOGGER.info("Proceeding to load BERT model into memory");

			try {
				modelManager.loadModel();
				loadedBERTModelIntoMemory = true;
				LOGGER.info("BERT model loaded into memory. It will stay in memory as long as any bot stays active in game.");
			} catch (IOException | ModelException e) {
				LOGGER.error("BERT Model loading failed! {}", e.getMessage());
			}


		});

		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			MiningTool.cancelAll(server, "Server stopped");
			NavigationService.cancelAll(server, "Server stopped");
			FoodConsumptionTool.reset();

			AutoFaceEntity.onServerStopped(server);

			// Gracefully shut down all autonomous goal engines
			AutonomousManager.getInstance().stopAll();

			try {
				if (modelManager.isModelLoaded() || loadedBERTModelIntoMemory) {
					modelManager.unloadModel();
					System.out.println("Unloaded BERT Model from memory");
				}
				else {
					System.out.println("BERT Model was not loaded, skipping unloading...");
				}

			} catch (IOException e) {
				LOGGER.error("BERT Model unloading failed!", e);
			}

		});

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayer serverPlayer) {
                if (BotEventHandler.bot != null && serverPlayer.getUUID().equals(BotEventHandler.bot.getUUID())) {
                    // Save state first
                    QTableStorage.saveLastKnownState(BotEventHandler.getCurrentState(), BotEventHandler.qTableDir + "/lastKnownState.bin");

                    try {
                        // Load needed data for learning
                        QTable qTable = QTableStorage.loadQTable();
                        if (qTable == null) qTable = new QTable();

                        // Create a temporary agent wrapper for the learning process
                        RLAgent tempAgent = new RLAgent(0.1, qTable);

                        // Trigger death learning
                        BotEventHandler.handleBotDeath(qTable, tempAgent);

                    } catch (Exception e) {
                        LOGGER.error("Error during death learning trigger: ", e);
                    }
                }
            }
        });

		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			// Check if the respawned player is the bot
			if (oldPlayer instanceof ServerPlayer && newPlayer instanceof ServerPlayer && oldPlayer.getName().getString().equals(newPlayer.getName().getString())) {
				System.out.println("Bot has respawned. Updating state...");
				BotEventHandler.hasRespawned = true;
				BotEventHandler.botSpawnCount++;

			}
		});

		// Player retaliation tracking - track hits on bot players
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			// Check if the damaged entity is a bot player
			if (entity instanceof ServerPlayer bot) {
				// Check if damage source is another player
				if (source.getEntity() instanceof net.minecraft.world.entity.player.Player attacker) {
					// Record the hit for retaliation tracking
					net.shasankp000.PlayerUtils.PlayerRetaliationTracker.recordPlayerHit(bot, attacker);
				}
			}
			return true; // Allow damage to proceed
		});

	}


}
