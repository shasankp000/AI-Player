package net.shasankp000.GameAI;

import net.shasankp000.ChatUtils.ChatUtils;
import net.shasankp000.DangerZoneDetector.DangerZoneDetector;
import net.shasankp000.Database.QTable;
import net.shasankp000.Database.QTableStorage;
import net.shasankp000.Database.StateActionPair;
import net.shasankp000.Entity.AutoFaceEntity;
import net.shasankp000.GameAI.StateTransition; // Ensure this import exists
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.shasankp000.Entity.FaceClosestEntity;
import net.shasankp000.LauncherDetection.LauncherEnvironment;
import net.shasankp000.PlayerUtils.*;
import net.shasankp000.WorldUitls.GetTime;
import net.shasankp000.Entity.EntityDetails;
import net.shasankp000.WorldUitls.isBlockItem;
import net.shasankp000.GameAI.mood.MoodEngine;
import net.shasankp000.GameAI.persona.PersonaRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static net.shasankp000.GameAI.State.isStateConsistent;


public class BotEventHandler {
    public static final Logger LOGGER = LoggerFactory.getLogger("ai-player");
    private static MinecraftServer server = null;
    public static ServerPlayer bot = null;
    public static final String qTableDir = LauncherEnvironment.getStorageDirectory("qtable_storage");
    private static final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    private static final Object monitorLock = new Object();
    private static boolean isExecuting = false;
    private static final double DEFAULT_RISK_APPETITE = 0.5; // Default value upon respawn
    public static boolean botDied = false; // Flag to track if the bot died
    public static boolean hasRespawned = false; // flag to track if the bot has respawned before or not

    // ForkJoinPool for parallel synchronous computation (uses all CPU cores efficiently)
    private static final java.util.concurrent.ForkJoinPool parallelComputePool =
        new java.util.concurrent.ForkJoinPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() / 2), // Use half of CPU cores
            java.util.concurrent.ForkJoinPool.defaultForkJoinWorkerThreadFactory,
            null,
            false // FIFO mode for predictable behavior
        );
    public static int botSpawnCount = 0;
    private static State currentState = null;

    // Action execution tracking - prevents action spam and ensures completion
    private static final Map<String, Boolean> actionInProgress = new HashMap<>();
    private static final Map<String, String> currentAction = new HashMap<>();
    private static final Map<String, Long> actionStartTime = new HashMap<>();
    private static final long ACTION_TIMEOUT_MS = 5000; // 5 second timeout per action

    // State transition tracking for lookahead learning
    private static final StateTransition.TransitionHistory transitionHistory =
        new StateTransition.TransitionHistory(50); // Keep last 50 transitions
    private static State previousState = null;
    private static StateActions.Action previousAction = null;
    private static double previousReward = 0.0;

    // Singleton RLAgent – lazily created and cached for external callers
    private static RLAgent cachedRLAgent = null;

    // Periodic reflection scheduler
    private static long lastReflectionTime = System.currentTimeMillis();
    private static final long REFLECTION_INTERVAL_MS = TimeUnit.MINUTES.toMillis(5); // Reflect every 5 minutes



    public BotEventHandler(MinecraftServer server, ServerPlayer bot) {
        BotEventHandler.server = server;
        BotEventHandler.bot = bot;

    }

    // ── Mood / Persona lifecycle ──────────────────────────────────────────────

    /**
     * Called when a bot despawns (death, /bot despawn, server stop).
     * Cleans up per-bot MoodEngine and PersonaRegistry entries so stale
     * state does not bleed into the next spawn.
     */
    public static void onBotDespawn(String botName) {
        MoodEngine.evict(botName);
        PersonaRegistry.evict(botName);
        LOGGER.info("[mood/persona] Evicted state for bot '{}'", botName);
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the shared TransitionHistory used by this handler's RL loop.
     * Callers such as {@code modCommandRegistry} can use this to surface
     * recent state-action transitions for diagnostics or the trade evaluator.
     */
    public static StateTransition.TransitionHistory getTransitionHistory() {
        return transitionHistory;
    }

    /**
     * Returns (or lazily creates) a shared {@link RLAgent} instance.
     * When an active agent is passed into {@link #detectAndReact} its epsilon
     * is kept in sync with this cached copy so external callers always see
     * an up-to-date agent.
     *
     * @return the singleton RLAgent for this handler
     */
    public static RLAgent getRLAgent() {
        if (cachedRLAgent == null) {
            // Try to restore epsilon from disk so training progress is preserved
            double savedEpsilon = 1.0;
            try {
                Double loaded = QTableStorage.loadEpsilon(qTableDir + File.separator + "epsilon.bin");
                if (loaded != null) savedEpsilon = loaded;
            } catch (Exception ignored) { /* first run – start fresh */ }
            cachedRLAgent = new RLAgent(savedEpsilon, null);
        }
        return cachedRLAgent;
    }

    /**
     * Handle bot death - learn from the sequence of actions that led to death
     */
    public static void handleBotDeath(QTable qTable, RLAgent rlAgent) {
        LOGGER.info("💀 Bot died - analyzing death sequence for learning...");

        // Keep cached agent in sync
        if (rlAgent != null) cachedRLAgent = rlAgent;

        // Evict mood/persona state on death so the next spawn starts fresh
        if (bot != null) {
            onBotDespawn(bot.getName().getString());
        }

        executor.submit(() -> {
            try {
                LookaheadLearning.learnFromDeath(transitionHistory, qTable, rlAgent);
                QTableStorage.saveQTable(qTable, "qtable.bin");
                LOGGER.info("✓ Death learning complete, Q-table updated");
                LookaheadLearning.cleanupOldTransitions(transitionHistory);
            } catch (Exception e) {
                LOGGER.error("Error during death learning", e);
            }
        });
    }

    /**
     * Perform periodic reflection on past experiences
     */
    private static void performPeriodicReflection(QTable qTable, RLAgent rlAgent) {
        long now = System.currentTimeMillis();
        if (now - lastReflectionTime >= REFLECTION_INTERVAL_MS) {
            LOGGER.info("⏰ Time for periodic reflection...");

            executor.submit(() -> {
                try {
                    LookaheadLearning.periodicReflection(transitionHistory, qTable, rlAgent);
                    QTableStorage.saveQTable(qTable, "qtable.bin");
                    lastReflectionTime = System.currentTimeMillis();
                } catch (Exception e) {
                    LOGGER.error("Error during periodic reflection", e);
                }
            });
        }
    }

    private static State initializeBotState(QTable qTable) {
        State initialState = null;

        if (qTable == null || qTable.getTable().isEmpty()) {
            System.out.println("No initial state available. Q-table is empty.");
        } else {
            System.out.println("Loaded Q-table: Total state-action pairs = " + qTable.getTable().size());

            // Get the most recent state from the Q-table
            StateActionPair recentPair = qTable.getTable().keySet().iterator().next();
            initialState = recentPair.getState();

            System.out.println("Setting initial state to: " + initialState);
        }

        return initialState;
    }

    public void detectAndReact(RLAgent rlAgentHook, double distanceToHostileEntity, QTable qTable) throws IOException {
        // Keep cached agent in sync with whatever the caller passes in
        if (rlAgentHook != null) cachedRLAgent = rlAgentHook;

        synchronized (monitorLock) {
            if (isExecuting) {
                System.out.println("Executing detection code - already processing threat");
                return; // Skip if already executing
            }
            isExecuting = true;
        }

        try {
            CommandSourceStack botSource = bot.createCommandSourceStack().withSuppressedOutput().withMaximumPermission(net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS);

            System.out.println("Distance from danger zone: " + DangerZoneDetector.detectDangerZone(bot, 10, 10 , 10) + " blocks");

            List<Entity> nearbyEntities = AutoFaceEntity.detectNearbyEntities(bot, 32); // Increased detection range for better awareness
            List<Entity> hostileEntities = nearbyEntities.stream()
                    .filter(entity -> {
                        // Include HostileEntity mobs
                        if (entity instanceof Monster) {
                            return true;
                        }
                        // Include hostile players tracked by retaliation system
                        if (entity instanceof net.minecraft.world.entity.player.Player player &&
                            !player.getUUID().equals(bot.getUUID())) {
                            return net.shasankp000.PlayerUtils.PlayerRetaliationTracker.isPlayerHostile(bot, player);
                        }
                        return false;
                    })
                    .toList();


            BlockDistanceLimitedSearch blockDistanceLimitedSearch = new BlockDistanceLimitedSearch(bot, 3, 5);

            List<String> nearbyBlocks = blockDistanceLimitedSearch.detectNearbyBlocks();

            boolean hasSculkNearby = nearbyBlocks.stream()
                    .anyMatch(block -> block.contains("Sculk Sensor") || block.contains("Sculk Shrieker"));
            System.out.println("Nearby blocks: " + nearbyBlocks);

            int timeofDay = GetTime.getTimeOfWorld(bot);
            String time = (timeofDay >= 12000 && timeofDay < 24000) ? "night" : "day";

            Level world = bot.createCommandSourceStack().withSuppressedOutput().withMaximumPermission(net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS).getLevel();
            ResourceKey<Level> dimType = world.dimension();
            String dimension = dimType.identifier().toString();

            if (!hostileEntities.isEmpty()) {
                List<EntityDetails> nearbyEntitiesList = new ArrayList<>();
                for (Entity entity : nearbyEntities) {
                    String directionToBot = AutoFaceEntity.determineDirectionToBot(bot, entity);
                    nearbyEntitiesList.add(new EntityDetails(
                            entity.getName().getString(),
                            entity.getX(),
                            entity.getY(),
                            entity.getZ(),
                            entity instanceof Monster,
                            directionToBot
                    ));
                }

                State currentState;

                if (hasRespawned && botDied) {
                    State lastKnownState = QTableStorage.loadLastKnownState(qTableDir + File.separator + "lastKnownState.bin");
                    currentState = createInitialState(bot);
                    BotEventHandler.botDied = false;

                    if (isStateConsistent(lastKnownState, currentState)) {
                        System.out.println("Merged values from last known state.");
                        currentState.setRiskMap(lastKnownState.getRiskMap());
                        currentState.setPodMap(lastKnownState.getPodMap());
                    }
                } else {
                    currentState = initializeBotState(qTable);

                    System.out.println("Created initial state");
                }

                if (botSpawnCount == 0) {
                    currentState = createInitialState(bot);
                }

                double riskAppetite = rlAgentHook.calculateRiskAppetite(currentState);
                List<StateActions.Action> potentialActionList = rlAgentHook.suggestPotentialActions(currentState);
                Map<StateActions.Action, Double> riskMap = rlAgentHook.calculateRisk(currentState, potentialActionList, bot);

                Map<StateActions.Action, Double> chosenActionMap =
                        rlAgentHook.chooseAction(currentState, riskAppetite, riskMap, transitionHistory);
                Map.Entry<StateActions.Action, Double> entry = chosenActionMap.entrySet().iterator().next();

                StateActions.Action chosenAction = entry.getKey();
                double risk = entry.getValue();

                System.out.println("Chosen action: " + chosenAction);

                executeAction(chosenAction, botSource);


                List<ItemStack> hotBarItems = hotBarUtils.getHotbarItems(bot);
                SelectedItemDetails selectedItem = new SelectedItemDetails(
                        hotBarUtils.getSelectedHotbarItemStack(bot).getHoverName().getString(),
                        hotBarUtils.getSelectedHotbarItemStack(bot).getComponents().has(DataComponents.FOOD), // as per 1.20.6 changes.
                        isBlockItem.checkBlockItem(hotBarUtils.getSelectedHotbarItemStack(bot))
                );

                double dangerDistance = DangerZoneDetector.detectDangerZone(bot, 10, 5, 5);
                int botHungerLevel = getPlayerHunger.getBotHungerLevel(bot);
                int botOxygenLevel = getPlayerOxygen.getBotOxygenLevel(bot);
                int botFrostLevel = getFrostLevel.calculateFrostLevel(bot);
                Map<String, ItemStack> armorItems = getArmorStack.getArmorItems(bot);
                ItemStack offhandItem = getOffHandStack.getOffhandItem(bot);

                State nextState = new State(
                        (int) bot.getX(),
                        (int) bot.getY(),
                        (int) bot.getZ(),
                        nearbyEntitiesList,
                        nearbyBlocks,
                        distanceToHostileEntity,
                        (int) bot.getHealth(),
                        dangerDistance,
                        hotBarItems,
                        selectedItem,
                        time,
                        dimension,
                        botHungerLevel,
                        botOxygenLevel,
                        botFrostLevel,
                        offhandItem,
                        armorItems,
                        chosenAction,
                        riskMap,
                        riskAppetite,
                        currentState.getPodMap()
                );

                // Log if bot is in a dangerous structure
                if (nextState.isInDangerousStructure()) {
                    System.out.println("WARNING: Bot is in a dangerous structure (Trial Chamber/Dungeon/Nether Fortress/Bastion)!");
                    System.out.println("Structure risk modifier applied: +20.0 to all action risks");
                }

                rlAgentHook.decayEpsilon();
                Map<StateActions.Action, Double> actionPodMap = rlAgentHook.assessRiskOutcome(currentState, nextState, chosenAction);
                nextState.setPodMap(actionPodMap);

                double reward = rlAgentHook.calculateReward(
                        (int) bot.getX(),
                        (int) bot.getY(),
                        (int) bot.getZ(),
                        nearbyEntitiesList,
                        nearbyBlocks,
                        distanceToHostileEntity,
                        (int) bot.getHealth(),
                        dangerDistance,
                        hotBarItems,
                        selectedItem.getName(),
                        time,
                        dimension,
                        botHungerLevel,
                        botOxygenLevel,
                        offhandItem,
                        armorItems,
                        chosenAction,
                        risk,
                        actionPodMap.getOrDefault(chosenAction, 0.0)
                );

                System.out.println("Reward: " + reward);

                // Check for death risk patterns before updating Q-value
                double deathRiskPenalty = LookaheadLearning.analyzeDeathRisk(currentState, chosenAction, transitionHistory);
                double adjustedReward = reward - deathRiskPenalty;

                double qValue = rlAgentHook.calculateQValue(currentState, chosenAction, adjustedReward, nextState, qTable);
                qTable.addEntry(currentState, chosenAction, qValue, nextState);

                // Record the transition for future learning
                double podValue = actionPodMap.getOrDefault(chosenAction, 0.0);
                StateTransition transition = new StateTransition(
                    currentState,
                    nextState,
                    chosenAction,
                    adjustedReward,
                    podValue,
                    false, // Will be marked true if death occurs
                    -1
                );
                transitionHistory.addTransition(transition);

                // Periodic reflection check
                performPeriodicReflection(qTable, rlAgentHook);

                QTableStorage.saveQTable(qTable, "qtable.bin");
                QTableStorage.saveEpsilon(rlAgentHook.getEpsilon(), qTableDir + File.separator + "epsilon.bin");

                BotEventHandler.currentState = nextState;
                previousState = currentState;
                previousAction = chosenAction;
                previousReward = adjustedReward;

            } else if ((DangerZoneDetector.detectDangerZone(bot, 10, 10, 10) <= 5.0 && DangerZoneDetector.detectDangerZone(bot, 10, 10, 10) > 0.0) || hasSculkNearby) {
                System.out.println("Danger zone detected within 5 blocks");

                System.out.println("Triggered handler for danger zone case.");

                List<EntityDetails> nearbyEntitiesList = new ArrayList<>();
                for (Entity entity : nearbyEntities) {
                    String directionToBot = AutoFaceEntity.determineDirectionToBot(bot, entity);
                    nearbyEntitiesList.add(new EntityDetails(
                            entity.getName().getString(),
                            entity.getX(),
                            entity.getY(),
                            entity.getZ(),
                            entity instanceof Monster,
                            directionToBot
                    ));
                }

                State currentState;

                if (hasRespawned && botDied) {
                    State lastKnownState = QTableStorage.loadLastKnownState(qTableDir + File.separator + "lastKnownState.bin");
                    currentState = createInitialState(bot);
                    BotEventHandler.botDied = false;

                    if (isStateConsistent(lastKnownState, currentState)) {
                        System.out.println("Merged values from last known state.");
                        currentState.setRiskMap(lastKnownState.getRiskMap());
                        currentState.setPodMap(lastKnownState.getPodMap());
                    }
                } else {
                    currentState = initializeBotState(qTable);
                }

                if (botSpawnCount == 0) {
                    currentState = createInitialState(bot);
                }

                double riskAppetite = rlAgentHook.calculateRiskAppetite(currentState);
                List<StateActions.Action> potentialActionList = rlAgentHook.suggestPotentialActions(currentState);
                Map<StateActions.Action, Double> riskMap = rlAgentHook.calculateRisk(currentState, potentialActionList, bot);

                Map<StateActions.Action, Double> chosenActionMap = rlAgentHook.chooseAction(currentState, riskAppetite, riskMap, transitionHistory);
                Map.Entry<StateActions.Action, Double> entry = chosenActionMap.entrySet().iterator().next();

                StateActions.Action chosenAction = entry.getKey();
                double risk = entry.getValue();

                System.out.println("Chosen action: " + chosenAction);

                executeAction(chosenAction, botSource);

                nearbyBlocks = blockDistanceLimitedSearch.detectNearbyBlocks();

                List<ItemStack> hotBarItems = hotBarUtils.getHotbarItems(bot);
                SelectedItemDetails selectedItem = new SelectedItemDetails(
                        hotBarUtils.getSelectedHotbarItemStack(bot).getHoverName().getString(),
                        hotBarUtils.getSelectedHotbarItemStack(bot).getComponents().has(DataComponents.FOOD), // as per 1.20.6 changes.,
                        isBlockItem.checkBlockItem(hotBarUtils.getSelectedHotbarItemStack(bot))
                );

                double dangerDistance = DangerZoneDetector.detectDangerZone(bot, 10, 5, 5);
                int botHungerLevel = getPlayerHunger.getBotHungerLevel(bot);
                int botOxygenLevel = getPlayerOxygen.getBotOxygenLevel(bot);
                int botFrostLevel = getFrostLevel.calculateFrostLevel(bot);
                Map<String, ItemStack> armorItems = getArmorStack.getArmorItems(bot);
                ItemStack offhandItem = getOffHandStack.getOffhandItem(bot);

                State nextState = new State(
                        (int) bot.getX(),
                        (int) bot.getY(),
                        (int) bot.getZ(),
                        nearbyEntitiesList,
                        nearbyBlocks,
                        distanceToHostileEntity,
                        (int) bot.getHealth(),
                        dangerDistance,
                        hotBarItems,
                        selectedItem,
                        time,
                        dimension,
                        botHungerLevel,
                        botOxygenLevel,
                        botFrostLevel,
                        offhandItem,
                        armorItems,
                        chosenAction,
                        riskMap,
                        riskAppetite,
                        currentState.getPodMap()
                );

                // Log if bot is in a dangerous structure
                if (nextState.isInDangerousStructure()) {
                    System.out.println("WARNING: Bot is in a dangerous structure (Trial Chamber/Dungeon/Nether Fortress/Bastion)!");
                    System.out.println("Structure risk modifier applied: +20.0 to all action risks");
                }

                rlAgentHook.decayEpsilon();
                Map<StateActions.Action, Double> actionPodMap = rlAgentHook.assessRiskOutcome(currentState, nextState, chosenAction);
                nextState.setPodMap(actionPodMap);

                double reward = rlAgentHook.calculateReward(
                        (int) bot.getX(),
                        (int) bot.getY(),
                        (int) bot.getZ(),
                        nearbyEntitiesList,
                        nearbyBlocks,
                        distanceToHostileEntity,
                        (int) bot.getHealth(),
                        dangerDistance,
                        hotBarItems,
                        selectedItem.getName(),
                        time,
                        dimension,
                        botHungerLevel,
                        botOxygenLevel,
                        offhandItem,
                        armorItems,
                        chosenAction,
                        risk,
                        actionPodMap.getOrDefault(chosenAction, 0.0)
                );

                System.out.println("Reward: " + reward);

                // Check for death risk patterns before updating Q-value (danger zone case)
                double deathRiskPenalty = LookaheadLearning.analyzeDeathRisk(currentState, chosenAction, transitionHistory);
                if (deathRiskPenalty > 0) {
                    reward -= deathRiskPenalty; // Apply risk penalty from past death patterns
                    System.out.println("Applied death risk penalty: -" + String.format("%.1f", deathRiskPenalty));
                }

                double qValue = rlAgentHook.calculateQValue(currentState, chosenAction, reward, nextState, qTable);
                qTable.addEntry(currentState, chosenAction, qValue, nextState);

                // Record state transition for lookahead learning (danger zone case)
                StateTransition transition = new StateTransition(
                    currentState,
                    nextState,
                    chosenAction,
                    reward,
                    actionPodMap.getOrDefault(chosenAction, 0.0),
                    false, // Not known to lead to death yet
                    -1
                );
                transitionHistory.addTransition(transition);

                // Store for next iteration
                previousState = currentState;
                previousAction = chosenAction;
                previousReward = reward;

                QTableStorage.saveQTable(qTable, "qtable.bin");
                QTableStorage.saveEpsilon(rlAgentHook.getEpsilon(), qTableDir + File.separator + "epsilon.bin");

                // Perform periodic reflection if needed
                performPeriodicReflection(qTable, rlAgentHook);

                BotEventHandler.currentState = nextState;
            }


        } finally {
            // ── Mood decay: one tick per RL loop iteration ────────────────────────
            if (bot != null) {
                MoodEngine.decayTick(bot.getName().getString());
            }
            // ─────────────────────────────────────────────────────────────────────

            // ⏸ Wait for any ongoing action to complete before next RL loop iteration
            String botName = bot.getName().getString();
            if (isActionInProgress(botName)) {
                LOGGER.info("[RL-LOOP] Waiting for action '{}' to complete...", currentAction.get(botName));
                waitForActionCompletion(botName, 3000); // Wait up to 3 seconds
            }

            // Small cooldown between RL decisions to prevent action spam
            try {
                Thread.sleep(200); // 200ms cooldown
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            synchronized (monitorLock) {
                isExecuting = false;
                AutoFaceEntity.isHandlerTriggered = false;
                System.out.println("Resetting handler trigger flag to: " + false);
            }
        }
    }


    public static State getCurrentState() {

        return BotEventHandler.currentState;

    }

    public void detectAndReactPlayMode(RLAgent rlAgentHook, QTable qTable) {
        // Keep cached agent in sync
        if (rlAgentHook != null) cachedRLAgent = rlAgentHook;

        synchronized (monitorLock) {
            if (isExecuting) {
                System.out.println("Already executing detection code, skipping...");
                return; // Skip if already executing
            }
            isExecuting = true;
        }

        try {
            CommandSourceStack botSource = bot.createCommandSourceStack().withSuppressedOutput().withMaximumPermission(net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS);


            if (qTable == null) {
                ChatUtils.sendChatMessages(botSource, "I have no training data to work with! Please spawn me in training mode so that I can learn first!");
            }

            else {
                // Detect nearby hostile entities (including hostile players)
                List<Entity> nearbyEntities = AutoFaceEntity.detectNearbyEntities(bot, 32); // Increased detection range for better awareness
                List<Entity> hostileEntities = nearbyEntities.stream()
                        .filter(entity -> {
                            // Include HostileEntity mobs
                            if (entity instanceof Monster) {
                                return true;
                            }
                            // Include hostile players tracked by retaliation system
                            if (entity instanceof net.minecraft.world.entity.player.Player player &&
                                !player.getUUID().equals(bot.getUUID())) {
                                return net.shasankp000.PlayerUtils.PlayerRetaliationTracker.isPlayerHostile(bot, player);
                            }
                            return false;
                        })
                        .toList();

                if (!hostileEntities.isEmpty()) {
                    // Gather state information
                    State currentState = createInitialState(bot);

//                double riskAppetite = currentState.getRiskAppetite();
//
                    Map<StateActions.Action, Double> riskMap = currentState.getRiskMap();



                    // Choose action
                    StateActions.Action chosenAction = rlAgentHook.chooseActionPlayMode(currentState, qTable, riskMap, "detectAndReactPlayMode", transitionHistory);


                    // Log chosen action for debugging
                    System.out.println("Play Mode - Chosen action: " + chosenAction);

                    // Execute action
                    executeAction(chosenAction, botSource);
                }
                else if (DangerZoneDetector.detectDangerZone(bot, 10, 10, 10) <= 5.0 && DangerZoneDetector.detectDangerZone(bot, 10, 10, 10) > 0.0) {

                    // Gather state information
                    State currentState = createInitialState(bot);

                    Map<StateActions.Action, Double> riskMap = currentState.getRiskMap();


                    // Choose action
                    StateActions.Action chosenAction = rlAgentHook.chooseActionPlayMode(currentState, qTable, riskMap, "detectAndReactPlayMode", transitionHistory);


                    // Log chosen action for debugging
                    System.out.println("Play Mode - Chosen action: " + chosenAction);

                    // Execute action
                    executeAction(chosenAction, botSource);
                }


            }
        } finally {
            // ── Mood decay: one tick per RL loop iteration ────────────────────────
            if (bot != null) {
                MoodEngine.decayTick(bot.getName().getString());
            }
            // ─────────────────────────────────────────────────────────────────────

            synchronized (monitorLock) {
                System.out.println("Resetting handler trigger flag.");
                isExecuting = false;
                AutoFaceEntity.isHandlerTriggered = false; // Reset the trigger flag
            }
        }
    }

    private static void executeAction(StateActions.Action chosenAction, CommandSourceStack botSource) {
        switch (chosenAction) {
            case MOVE_FORWARD -> performAction("moveForward", botSource);
            case MOVE_BACKWARD -> performAction("moveBackward", botSource);
            case TURN_LEFT -> performAction("turnLeft", botSource);
            case TURN_RIGHT -> performAction("turnRight", botSource);
            case JUMP -> performAction("jump", botSource);
            case SNEAK -> performAction("sneak", botSource);
            case SPRINT -> performAction("sprint", botSource);
            case STOP_SNEAKING -> performAction("unsneak", botSource);
            case STOP_SPRINTING -> performAction("unsprint", botSource);
            case STOP_MOVING -> performAction("stopMoving", botSource);
            case USE_ITEM -> performAction("useItem", botSource);
            case EQUIP_ARMOR -> armorUtils.autoEquipArmor(bot);
            case ATTACK -> performAction("attack", botSource);
            case SHOOT_ARROW -> performAction("shootArrow", botSource);
            case EVADE -> performAction("evade", botSource);
            case HOTBAR_1 -> performAction("hotbar1", botSource);
            case HOTBAR_2 -> performAction("hotbar2", botSource);
            case HOTBAR_3 -> performAction("hotbar3", botSource);
            case HOTBAR_4 -> performAction("hotbar4", botSource);
            case HOTBAR_5 -> performAction("hotbar5", botSource);
            case HOTBAR_6 -> performAction("hotbar6", botSource);
            case HOTBAR_7 -> performAction("hotbar7", botSource);
            case HOTBAR_8 -> performAction("hotbar8", botSource);
            case HOTBAR_9 -> performAction("hotbar9", botSource);
            case STAY -> System.out.println("Performing action: Stay and do nothing");
        }
    }


    public static State createInitialState(ServerPlayer bot) {
        List<ItemStack> hotBarItems = hotBarUtils.getHotbarItems(bot);
        ItemStack selectedItemStack = hotBarUtils.getSelectedHotbarItemStack(bot);

        BlockDistanceLimitedSearch blockDistanceLimitedSearch = new BlockDistanceLimitedSearch(bot, 3, 5);

        List<String> nearbyBlocks = blockDistanceLimitedSearch.detectNearbyBlocks();

        SelectedItemDetails selectedItem = new SelectedItemDetails(
                selectedItemStack.getHoverName().getString(),
                selectedItemStack.getComponents().has(DataComponents.FOOD),
                isBlockItem.checkBlockItem(selectedItemStack)
        );

        List<Entity> nearbyEntities = AutoFaceEntity.detectNearbyEntities(bot, 32);

        List<EntityDetails> nearbyEntitiesList = new ArrayList<>();

        String directionToBot;

        for(Entity entity: nearbyEntities) {

            directionToBot = AutoFaceEntity.determineDirectionToBot(bot, entity);

            // Determine if entity is hostile (either HostileEntity mob or hostile player)
            boolean isHostile = entity instanceof Monster;
            if (entity instanceof net.minecraft.world.entity.player.Player player &&
                !player.getUUID().equals(bot.getUUID())) {
                isHostile = net.shasankp000.PlayerUtils.PlayerRetaliationTracker.isPlayerHostile(bot, player);
            }

            nearbyEntitiesList.add(new EntityDetails(
                    entity.getName().getString(),
                    entity.getX(),
                    entity.getY(),
                    entity.getZ(),
                    isHostile,
                    directionToBot
            ));

        }

        double dangerDistance = DangerZoneDetector.detectDangerZone(bot, 10, 5, 5);
        int botHungerLevel = getPlayerHunger.getBotHungerLevel(bot);
        int botOxygenLevel = getPlayerOxygen.getBotOxygenLevel(bot);
        int botFrostLevel = getFrostLevel.calculateFrostLevel(bot);
        Map<String, ItemStack> armorItems = getArmorStack.getArmorItems(bot);
        ItemStack offhandItem = getOffHandStack.getOffhandItem(bot);
        String time = GetTime.getTimeOfWorld(bot) >= 12000 ? "night" : "day";
        String dimension = bot.createCommandSourceStack().getLevel().dimension().identifier().toString();
        Map<StateActions.Action, Double> riskMap = new HashMap<>();

        Map<StateActions.Action, Double> podMap = new HashMap<>(); // blank pod map for now.

        State initialState = new State(
                (int) bot.getX(),
                (int) bot.getY(),
                (int) bot.getZ(),
                nearbyEntitiesList,
                nearbyBlocks,
                0.0, // Distance to hostile can be updated dynamically elsewhere
                (int) bot.getHealth(),
                dangerDistance,
                hotBarItems,
                selectedItem,
                time,
                dimension,
                botHungerLevel,
                botOxygenLevel,
                botFrostLevel,
                offhandItem,
                armorItems,
                StateActions.Action.STAY,
                riskMap,
                DEFAULT_RISK_APPETITE,
                podMap
        );

        // Log if bot is in a dangerous structure during initial state creation
        if (initialState.isInDangerousStructure()) {
            LOGGER.info("Bot spawned/initialized in a dangerous structure! Extra caution advised.");
        }

        return initialState;
    }


    private static void performAction(String action, CommandSourceStack botSource) {

        String botName = botSource.getTextName();


        switch (action) {
            case "moveForward":
                System.out.println("Performing action: move forward");
                server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " move forward");
                AutoFaceEntity.isBotMoving = true;
                break;
            case "moveBackward":
                System.out.println("Performing action: move backward");
                server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " move backward");
                AutoFaceEntity.isBotMoving = true;
                break;
            case "turnLeft":
                System.out.println("Performing action: turn left");
                server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " turn left");
                break;
            case "turnRight":
                System.out.println("Performing action: turn right");
                server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " turn right");
                break;
            case "jump":
                System.out.println("Performing action: jump");
                bot.jumpFromGround();
                break;
            case "sneak":
                System.out.println("Performing action: sneak");
                server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " sneak");
                break;
            case "sprint":
                System.out.println("Performing action: sprint");
                server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " sprint");
                break;
            case "unsneak":
                System.out.println("Performing action: unsneak");
                server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " unsneak");
                break;
            case "unsprint":
                System.out.println("Performing action: unsprint");
                server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " unsprint");
                break;
            case "stopMoving":
                System.out.println("Performing action: stop moving");
                server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " stop");
                AutoFaceEntity.isBotMoving = false;
                break;
            case "useItem":
                System.out.println("Performing action: use currently selected item");
                server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " use");
                break;
            case "attack":
                System.out.println("Performing action: ATTACK (intelligent combat)");

                // ⏸ BLOCK if action in progress
                if (isActionInProgress(botName)) {
                    System.out.println("❌ ATTACK blocked - another action in progress: " + currentAction.get(botName));
                    break;
                }

                startAction(botName, "ATTACK");

                // Find highest threat hostile entity (not just closest!)
                if (AutoFaceEntity.hostileEntities == null || AutoFaceEntity.hostileEntities.isEmpty()) {
                    System.out.println("No hostile entities to attack");
                    completeAction(botName);
                    break;
                }

                // ✨ INTELLIGENT TARGETING: Prioritize high-threat entities (e.g., Creeper > Zombie)
                Entity attackTarget = selectHighestThreatTarget(bot, AutoFaceEntity.hostileEntities);

                if (attackTarget == null) {
                    System.out.println("Could not find attack target");
                    completeAction(botName);
                    break;
                }

                double distanceToTarget = Math.sqrt(attackTarget.distanceToSqr(bot));
                boolean hasRangedWeapon = RangedWeaponUtils.hasBowOrCrossbow(bot);
                boolean hasAmmo = RangedWeaponUtils.hasArrows(bot);

                System.out.println("Target: " + attackTarget.getName().getString() +
                                 " at " + String.format("%.1f", distanceToTarget) + "m");
                System.out.println("Ranged weapon: " + hasRangedWeapon + ", Ammo: " + hasAmmo);

                // Decision logic: Use ranged if available and target is far, otherwise melee
                if (hasRangedWeapon && hasAmmo && distanceToTarget > 4.0) {
                    // RANGED ATTACK STRATEGY
                    System.out.println("Using RANGED attack (distance > 4m)");

                    // Execute shooting command synchronously
                    server.getCommands().performPrefixedCommand(botSource, "/bot shoot_arrow " + botName + " false");

                    // Wait for shoot to complete (with timeout)
                    waitForActionCompletion(botName, 3000); // 3 second max wait
                } else {
                    // MELEE ATTACK STRATEGY
                    System.out.println("Using MELEE attack (close range or no ranged weapon)");

                    // ⚔ AUTO-EQUIP BEST MELEE WEAPON (if not already holding one)
                    boolean weaponEquipped = net.shasankp000.PlayerUtils.WeaponUtils.equipBestMeleeWeapon(bot);
                    if (weaponEquipped) {
                        System.out.println("✓ Best melee weapon equipped for combat");
                    } else {
                        System.out.println("⚠ No melee weapon found, attacking with current item");
                    }

                    FaceClosestEntity.faceClosestEntity(bot, AutoFaceEntity.hostileEntities);
                    server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " attack");

                    // Melee completes instantly
                    completeAction(botName);
                }
                break;
            case "shootArrow":
                System.out.println("Performing action: SHOOT_ARROW");

                // ⏸ BLOCK if action in progress
                if (isActionInProgress(botName)) {
                    System.out.println("❌ SHOOT_ARROW blocked - another action in progress: " + currentAction.get(botName));
                    break;
                }

                startAction(botName, "SHOOT_ARROW");
                server.getCommands().performPrefixedCommand(botSource, "/bot shoot_arrow " + botName + " false");

                // Wait for action completion
                waitForActionCompletion(botName, 3000);
                break;

            case "hotbar1":
                System.out.println("Performing action: Select hotbar slot 1");
                server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " hotbar 1");
                break;
            case "hotbar2":
                System.out.println("Performing action: Select hotbar slot 2");
                server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " hotbar 2");
                break;
            case "hotbar3":
                System.out.println("Performing action: Select hotbar slot 3");
                server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " hotbar 3");
                break;
            case "hotbar4":
                System.out.println("Performing action: Select hotbar slot 4");
                server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " hotbar 4");
                break;
            case "hotbar5":
                System.out.println("Performing action: Select hotbar slot 5");
                server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " hotbar 5");
                break;
            case "hotbar6":
                System.out.println("Performing action: Select hotbar slot 6");
                server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " hotbar 6");
                break;
            case "hotbar7":
                System.out.println("Performing action: Select hotbar slot 7");
                server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " hotbar 7");
                break;
            case "hotbar8":
                System.out.println("Performing action: Select hotbar slot 8");
                server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " hotbar 8");
                break;
            case "hotbar9":
                System.out.println("Performing action: Select hotbar slot 9");
                server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " hotbar 9");
                break;

            case "evade":
                System.out.println("Performing action: EVADE");

                // ⏸ BLOCK if action in progress
                if (isActionInProgress(botName)) {
                    System.out.println("❌ EVADE blocked - another action in progress: " + currentAction.get(botName));
                    break;
                }

                startAction(botName, "EVADE");

                // Find nearest hostile entity to evade from
                List<Entity> nearbyHostiles = AutoFaceEntity.detectNearbyEntities(bot, 20.0).stream()
                    .filter(e -> e instanceof Monster || e instanceof Slime)
                    .toList();

                if (!nearbyHostiles.isEmpty()) {
                    // PRIORITY 1: Check for dangerous creepers first (critical/ignited phase)
                    net.minecraft.world.entity.monster.Creeper dangerousCreeper =
                        net.shasankp000.PlayerUtils.MobThreatEvaluator.getMostDangerousCreeper(nearbyHostiles, bot);

                    Entity closestThreat;
                    if (dangerousCreeper != null) {
                        // Prioritize creeper threat (ignited or critical phase)
                        closestThreat = dangerousCreeper;
                        double distance = Math.sqrt(dangerousCreeper.distanceToSqr(bot));
                        LOGGER.warn("🧨 Prioritizing dangerous CREEPER for evasion at {}m",
                            String.format("%.1f", distance));
                    } else {
                        // No critical creeper - find closest threat normally
                        closestThreat = nearbyHostiles.stream()
                            .min(Comparator.comparingDouble(e -> e.distanceToSqr(bot)))
                            .orElse(null);
                    }

                    if (closestThreat != null) {
                        double distance = Math.sqrt(closestThreat.distanceToSqr(bot));
                        LOGGER.info("⚠ Evading from {} at {}m",
                            closestThreat.getName().getString(),
                            String.format("%.1f", distance));

                        // Check if threat is using ranged weapon and bot has shield
                        boolean isRangedThreat = false;

                        // Check for ranged mobs
                        if (closestThreat instanceof net.minecraft.world.entity.monster.skeleton.Skeleton ||
                            closestThreat instanceof net.minecraft.world.entity.monster.skeleton.WitherSkeleton ||
                            closestThreat instanceof net.minecraft.world.entity.monster.skeleton.Stray ||
                            closestThreat instanceof net.minecraft.world.entity.monster.illager.Pillager) {
                            isRangedThreat = true;
                        }

                        // Check for hostile players with ranged weapons
                        if (closestThreat instanceof net.minecraft.world.entity.player.Player player) {
                            net.minecraft.world.item.ItemStack mainHand = player.getMainHandItem();
                            net.minecraft.world.item.ItemStack activeItem = player.getUseItem();
                            String mainHandId = mainHand.isEmpty() ? "" :
                                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(mainHand.getItem()).toString();
                            String activeItemId = activeItem.isEmpty() ? "" :
                                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(activeItem.getItem()).toString();

                            if (mainHandId.contains("bow") || mainHandId.contains("crossbow") ||
                                activeItemId.contains("bow") || activeItemId.contains("crossbow")) {
                                isRangedThreat = true;
                                LOGGER.info("🎯 Hostile player {} has ranged weapon - shield defense available",
                                    player.getName().getString());
                            }
                        }

                        boolean hasShield = ProjectileDefenseUtils.hasShield(bot);
                        boolean isCloseRange = distance <= 8.0;

                        if (isRangedThreat && hasShield && isCloseRange) {
                          // Shield blocking strategy for ranged threats
                          LOGGER.info("🛡 Ranged threat detected - attempting shield block");

                          // Equip shield if not already equipped
                          if (!ProjectileDefenseUtils.hasShieldEquipped(bot)) {
                            LOGGER.info("Equipping shield from inventory...");
                            boolean equipped = ProjectileDefenseUtils.equipShieldToOffhand(bot);
                            if (!equipped) {
                              LOGGER.warn("Failed to equip shield - falling back to dodge");
                            } else {
                              LOGGER.info("✓ Shield equipped successfully");
                              // Start persistent blocking - will continue until threat changes weapon/dies/goes far
                              if (closestThreat instanceof net.minecraft.world.entity.LivingEntity) {
                                AutoFaceEntity.startPersistentBlocking(bot, (net.minecraft.world.entity.LivingEntity) closestThreat, server);
                                break; // Exit case - persistent blocking handles everything
                              }
                            }
                          } else {
                            // Shield already equipped - start persistent blocking
                            if (closestThreat instanceof net.minecraft.world.entity.LivingEntity) {
                              LOGGER.info("✓ Shield already equipped - starting persistent block");
                              AutoFaceEntity.startPersistentBlocking(bot, (net.minecraft.world.entity.LivingEntity) closestThreat, server);
                              break; // Exit case - persistent blocking handles everything
                            }
                          }
                        }

                        // Dodge/evasion strategy
                        // Calculate escape direction away from threat
                        Vec3 botPos = bot.position();
                        Vec3 threatPos = closestThreat.position();
                        Vec3 awayFromThreat = botPos.subtract(threatPos).normalize();

                        // Add randomness for unpredictability
                        double randomAngle = (Math.random() - 0.5) * Math.PI / 2.0; // ±90°
                        double cos = Math.cos(randomAngle);
                        double sin = Math.sin(randomAngle);
                        Vec3 scrambledDir = new Vec3(
                            awayFromThreat.x * cos - awayFromThreat.z * sin,
                            0,
                            awayFromThreat.x * sin + awayFromThreat.z * cos
                        ).normalize();

                        // Check obstacle clearance (20 blocks ahead)
                        double clearance = ProjectileDefenseUtils.checkObstacleClearance(bot, scrambledDir, 20.0);

                        if (clearance > 15.0) {
                          // Path is clear - use direct adaptive evasion (fast!)
                          LOGGER.info("✓ Path clear ({}m) - Direct sprint evasion", String.format("%.1f", clearance));

                          // Create threat object for evasion
                          if (closestThreat instanceof net.minecraft.world.entity.LivingEntity) {
                            PredictiveThreatDetector.DrawingBowThreat threat =
                              new PredictiveThreatDetector.DrawingBowThreat(
                                (net.minecraft.world.entity.LivingEntity) closestThreat, bot);
                            AutoFaceEntity.executeAdaptivePanicEvasion(bot, threat, server);
                          }
                        } else {
                          // Obstacles detected - use PathFinder for smart routing
                          LOGGER.warn("⚠ Obstacles at {}m - Using PathFinder navigation", String.format("%.1f", clearance));

                          // Calculate target position (10 blocks in escape direction)
                          Vec3 targetVec = botPos.add(scrambledDir.scale(10.0));
                          net.minecraft.core.BlockPos targetPos = new net.minecraft.core.BlockPos(
                            (int) Math.floor(targetVec.x),
                            (int) Math.floor(targetVec.y),
                            (int) Math.floor(targetVec.z)
                          );

                          // Find path around obstacles
                          net.minecraft.server.level.ServerLevel world = (net.minecraft.server.level.ServerLevel) bot.level();
                          List<net.shasankp000.PathFinding.PathFinder.PathNode> path =
                            net.shasankp000.PathFinding.PathFinder.calculatePath(bot.blockPosition(), targetPos, world);

                          if (!path.isEmpty()) {
                            LOGGER.info("✓ PathFinder found route with {} nodes - executing", path.size());

                            // Simplify and convert to segments
                            List<net.shasankp000.PathFinding.PathFinder.PathNode> simplified =
                              net.shasankp000.PathFinding.PathFinder.simplifyPath(path, world);
                            java.util.Queue<net.shasankp000.PathFinding.Segment> segments =
                              net.shasankp000.PathFinding.PathFinder.convertPathToSegments(simplified, true); // Sprint!

                            // Execute path with PathTracer
                            net.shasankp000.PathFinding.PathTracer.BotSegmentManager manager =
                              new net.shasankp000.PathFinding.PathTracer.BotSegmentManager(server, botSource, botName);
                            segments.forEach(manager::addSegmentJob);
                            manager.startProcessing();

                            LOGGER.info("✓ PathFinder evasion started - navigating around obstacles");
                          } else {
                            // No path found - use direct evasion as fallback
                            LOGGER.warn("⚠ PathFinder failed - using direct evasion fallback");
                            if (closestThreat instanceof net.minecraft.world.entity.LivingEntity) {
                              PredictiveThreatDetector.DrawingBowThreat threat =
                                new PredictiveThreatDetector.DrawingBowThreat(
                                  (net.minecraft.world.entity.LivingEntity) closestThreat, bot);
                              AutoFaceEntity.executeAdaptivePanicEvasion(bot, threat, server);
                            }
                          }
                        }
                      }
                    } else {
                      // No hostile entities nearby - evasion pointless
                      LOGGER.info("No threats detected - evasion unnecessary");
                      System.out.println("No threats to evade from");
                      completeAction(botName); // Complete immediately if no threat
                    }

                    // Note: EVADE completion is also handled in AutoFaceEntity.executeAdaptivePanicEvasion
                    // when evasion finishes or times out
                    break;

                default:
                    System.out.println("Invalid action");
                    break;
        }
    }

    // ==================== COMBAT STRATEGY EXECUTION METHODS ====================

    /**
     * Execute ranged combat strategy - shoot arrows/crossbow at enemies
     */
    private static void executeRangedCombat(ServerPlayer bot, List<Entity> hostiles,
                                           CombatStrategyUtils.CombatStrategy strategy,
                                           MinecraftServer server, CommandSourceStack botSource, String botName) {
        LOGGER.info("⚔ Executing RANGED_COMBAT strategy");

        // Find closest hostile
        Entity target = findClosestEntity(bot, hostiles);
        if (target == null) return;

        double distance = Math.sqrt(target.distanceToSqr(bot));
        LOGGER.info("🎯 Targeting {} at {}m", target.getName().getString(), String.format("%.1f", distance));

        // Equip ranged weapon
        String weaponType = strategy.primaryWeapon;
        boolean weaponReady = equipWeapon(bot, weaponType, server, botSource, botName);

        if (!weaponReady) {
            LOGGER.warn("⚠ Could not equip {} - falling back to melee", weaponType);
            server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " attack");
            return;
        }

        // Face the target and use item (shoot)
        FaceClosestEntity.faceClosestEntity(bot, List.of(target));
        server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " use continuous");

        LOGGER.info("✓ Ranged attack executed - shooting at target");

        // Release after 1 second
        executor.schedule(() -> {
            server.execute(() -> {
                server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " use");
            });
        }, 1000, TimeUnit.MILLISECONDS);
    }

    /**
     * Execute shield advance strategy - block while moving forward, then melee attack
     */
    private static void executeShieldAdvance(ServerPlayer bot, List<Entity> hostiles,
                                            CombatStrategyUtils.CombatStrategy strategy,
                                            MinecraftServer server, CommandSourceStack botSource, String botName) {
        LOGGER.info("⚔ Executing SHIELD_ADVANCE strategy");

        Entity target = findClosestEntity(bot, hostiles);
        if (target == null) return;

        double distance = Math.sqrt(target.distanceToSqr(bot));
        LOGGER.info("🛡 Advancing on {} at {}m with shield", target.getName().getString(), String.format("%.1f", distance));

        // Equip shield to offhand if not equipped
        if (!ProjectileDefenseUtils.hasShieldEquipped(bot)) {
            boolean shieldEquipped = ProjectileDefenseUtils.equipShieldToOffhand(bot);
            if (!shieldEquipped) {
                LOGGER.warn("⚠ Could not equip shield - using standard melee");
                executeMeleeRush(bot, hostiles, strategy, server, botSource, botName);
                return;
            }
        }

        // Equip melee weapon
        equipWeapon(bot, strategy.primaryWeapon, server, botSource, botName);

        // Start persistent blocking against the target
        if (target instanceof net.minecraft.world.entity.LivingEntity) {
            AutoFaceEntity.startPersistentBlocking(bot, (net.minecraft.world.entity.LivingEntity) target, server);
        }

        // Move forward while blocking (for 2-3 seconds or until in melee range)
        int advanceDuration = (int) Math.min(3000, distance * 200); // ~200ms per block
        LOGGER.info("🛡 Advancing for {}ms", advanceDuration);

        server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " sprint");
        server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " move forward");

        // Schedule attack when close
        executor.schedule(() -> {
            server.execute(() -> {
                // Stop blocking and attack
                AutoFaceEntity.stopPersistentBlocking(bot, server);
                server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " stopMoving");
                server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " attack continuous");

                LOGGER.info("⚔ Reached melee range - attacking!");

                // Continue attacking for a few seconds
                executor.schedule(() -> {
                    server.execute(() -> {
                        server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " attack");
                        LOGGER.info("✓ Shield advance complete");
                    });
                }, 2000, TimeUnit.MILLISECONDS);
            });
        }, advanceDuration, TimeUnit.MILLISECONDS);
    }

    /**
     * Execute melee rush strategy - sprint at enemy and attack
     */
    private static void executeMeleeRush(ServerPlayer bot, List<Entity> hostiles,
                                        CombatStrategyUtils.CombatStrategy strategy,
                                        MinecraftServer server, CommandSourceStack botSource, String botName) {
        LOGGER.info("⚔ Executing MELEE_RUSH strategy");

        Entity target = findClosestEntity(bot, hostiles);
        if (target == null) return;

        double distance = Math.sqrt(target.distanceToSqr(bot));
        LOGGER.info("⚔ Rushing {} at {}m", target.getName().getString(), String.format("%.1f", distance));

        // Equip melee weapon
        equipWeapon(bot, strategy.primaryWeapon, server, botSource, botName);

        // Sprint and attack
        server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " sprint");
        server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " attack continuous");

        LOGGER.info("✓ Melee rush initiated");
    }

    /**
     * Execute evasive melee strategy - zigzag approach to avoid ranged attacks
     */
    private static void executeEvasiveMelee(ServerPlayer bot, List<Entity> hostiles,
                                           CombatStrategyUtils.CombatStrategy strategy,
                                           MinecraftServer server, CommandSourceStack botSource, String botName) {
        LOGGER.info("⚔ Executing EVASIVE_MELEE strategy");

        Entity target = findClosestEntity(bot, hostiles);
        if (target == null) return;

        double distance = Math.sqrt(target.distanceToSqr(bot));
        LOGGER.info("🏃 Evasive approach to {} at {}m", target.getName().getString(), String.format("%.1f", distance));

        // Equip melee weapon
        equipWeapon(bot, strategy.primaryWeapon, server, botSource, botName);

        // Use adaptive panic evasion but in reverse - move TOWARD enemy with zigzag
        if (target instanceof net.minecraft.world.entity.LivingEntity) {
            // Calculate zigzag approach direction
            Vec3 botPos = bot.position();
            Vec3 targetPos = target.position();
            Vec3 toTarget = targetPos.subtract(botPos).normalize();

            // Add random lateral movement
            double randomAngle = (Math.random() - 0.5) * Math.PI / 3.0; // ±60°
            double cos = Math.cos(randomAngle);
            double sin = Math.sin(randomAngle);
            Vec3 zigzagDir = new Vec3(
                toTarget.x * cos - toTarget.z * sin,
                0,
                toTarget.x * sin + toTarget.z * cos
            ).normalize();

            // Set direction and sprint
            double yaw = Math.toDegrees(Math.atan2(zigzagDir.z, zigzagDir.x)) - 90;
            bot.setYRot((float) yaw);

            server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " sprint");
            server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " move forward");
            server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " jump continuous");

            LOGGER.info("🏃 Zigzag approach started");

            // Schedule attack when close (estimated time based on distance)
            int approachTime = (int) Math.min(3000, distance * 150);
            executor.schedule(() -> {
                server.execute(() -> {
                    server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " stopMoving");
                    server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " jump");
                    server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " attack continuous");
                    LOGGER.info("⚔ Evasive melee attack commenced");
                });
            }, approachTime, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Execute tactical retreat - run away from enemies
     */
    private static void executeTacticalRetreat(ServerPlayer bot, List<Entity> hostiles,
                                              MinecraftServer server, CommandSourceStack botSource, String botName) {
        LOGGER.info("🏃 Executing TACTICAL_RETREAT");

        Entity closestThreat = findClosestEntity(bot, hostiles);
        if (closestThreat == null) return;

        double distance = Math.sqrt(closestThreat.distanceToSqr(bot));
        LOGGER.info("🏃 Retreating from {} at {}m", closestThreat.getName().getString(), String.format("%.1f", distance));

        // Calculate retreat direction (away from threat)
        Vec3 botPos = bot.position();
        Vec3 threatPos = closestThreat.position();
        Vec3 awayFromThreat = botPos.subtract(threatPos).normalize();

        // Set direction
        double yaw = Math.toDegrees(Math.atan2(awayFromThreat.z, awayFromThreat.x)) - 90;
        bot.setYRot((float) yaw);

        // Sprint away
        server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " sprint");
        server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " move forward");

        LOGGER.info("✓ Tactical retreat initiated");

        // Continue retreating for 3 seconds
        executor.schedule(() -> {
            server.execute(() -> {
                server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " stopMoving");
                server.getCommands().performPrefixedCommand(botSource, "/player " + botName + " unsprint");
                LOGGER.info("✓ Tactical retreat complete");
            });
        }, 3000, TimeUnit.MILLISECONDS);
    }

    /**
     * Find the closest entity to the bot
     */
    private static Entity findClosestEntity(ServerPlayer bot, List<Entity> entities) {
        Entity closest = null;
        double minDistance = Double.MAX_VALUE;

        for (Entity entity : entities) {
            double dist = entity.distanceToSqr(bot);
            if (dist < minDistance) {
                minDistance = dist;
                closest = entity;
            }
        }

        return closest;
    }

    /**
     * Equip a specific weapon type by searching inventory and switching hotbar
     */
    private static boolean equipWeapon(ServerPlayer bot, String weaponType,
                                      MinecraftServer server, CommandSourceStack botSource, String botName) {
        if (weaponType.equals("none")) {
            return false;
        }

        LOGGER.info("🗡 Attempting to equip {}", weaponType);

        // Check if already holding the correct weapon type
        ItemStack currentItem = bot.getMainHandItem();
        String currentItemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(currentItem.getItem()).toString();

        if (currentItemId.contains(weaponType.replace("_", "")) ||
            currentItemId.contains(weaponType)) {
            LOGGER.info("✓ Already holding {}", weaponType);
            return true;
        }

        // Search hotbar for weapon
        for (int i = 0; i < 9; i++) {
            ItemStack stack = bot.getInventory().getItem(i);
            if (stack.isEmpty()) continue;

            String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (itemId.contains(weaponType.replace("_", "")) || itemId.contains(weaponType)) {
                // Found weapon in hotbar - switch to it
                server.getCommands().performPrefixedCommand(botSource,
                    "/player " + botName + " hotbar " + (i + 1));
                LOGGER.info("✓ Equipped {} from hotbar slot {}", weaponType, i + 1);
                return true;
            }
        }

        // Search main inventory
        for (int i = 9; i < bot.getInventory().getContainerSize(); i++) {
            ItemStack stack = bot.getInventory().getItem(i);
            if (stack.isEmpty()) continue;

            String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (itemId.contains(weaponType.replace("_", "")) || itemId.contains(weaponType)) {
                // Found weapon in inventory - need to move to hotbar first
                LOGGER.info("Found {} in inventory slot {} - moving to hotbar", weaponType, i);

                // Find empty hotbar slot or replace slot 1
                int targetHotbarSlot = 0; // Default to first slot
                for (int j = 0; j < 9; j++) {
                    if (bot.getInventory().getItem(j).isEmpty()) {
                        targetHotbarSlot = j;
                        break;
                    }
                }

                // Swap items (simplified - in real impl would need inventory manipulation)
                bot.getInventory().setItem(targetHotbarSlot, stack.copy());
                bot.getInventory().setItem(i, ItemStack.EMPTY);

                // Switch to that slot
                server.getCommands().performPrefixedCommand(botSource,
                    "/player " + botName + " hotbar " + (targetHotbarSlot + 1));

                LOGGER.info("✓ Equipped {} from inventory", weaponType);
                return true;
            }
        }

        LOGGER.warn("⚠ {} not found in inventory", weaponType);
        return false;
    }

    /**
     * Shutdown all executors when server stops to prevent resource leaks
     */
    public static void shutdown() {
        LOGGER.info("Shutting down BotEventHandler executors...");

        // Shutdown parallel compute pool
        parallelComputePool.shutdown();
        try {
            if (!parallelComputePool.awaitTermination(2, TimeUnit.SECONDS)) {
                parallelComputePool.shutdownNow();
            }
        } catch (InterruptedException e) {
            parallelComputePool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Shutdown scheduled executor
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        LOGGER.info("BotEventHandler executors shut down successfully");
    }

    /**
     * Intelligently selects the highest threat target from a list of hostile entities.
     * Uses the entity threat scoring system to prioritize targets:
     * - Creepers get highest priority (explosive threat)
     * - Ranged attackers (skeletons, witches) get high priority
     * - Closer enemies get higher threat scores
     * - Stronger mobs (wardens, ravagers) get maximum priority
     *
     * @param bot The bot selecting a target
     * @param hostileEntities List of nearby hostile entities
     * @return The highest threat entity, or null if none suitable
     */
    private static Entity selectHighestThreatTarget(ServerPlayer bot, List<Entity> hostileEntities) {
        if (hostileEntities.isEmpty()) {
            return null;
        }

        Entity highestThreatEntity = null;
        double highestThreat = -1.0;

        for (Entity entity : hostileEntities) {
            double distance = Math.sqrt(entity.distanceToSqr(bot));

            // Calculate base threat based on entity type
            double baseThreat = calculateBaseThreatForEntity(entity, distance);

            // Apply distance modifier (closer = more dangerous)
            double distanceModifier = 0.0;
            if (distance < 3.0) {
                distanceModifier = 10.0; // Critical threat - very close
            } else if (distance < 6.0) {
                distanceModifier = 5.0; // High threat - close range
            } else if (distance < 10.0) {
                distanceModifier = 2.0; // Medium threat
            }

            double totalThreat = baseThreat + distanceModifier;

            System.out.println("Target analysis: " + entity.getName().getString() +
                " at " + String.format("%.1f", distance) + "m" +
                " - Base: " + String.format("%.1f", baseThreat) +
                ", Distance bonus: " + String.format("%.1f", distanceModifier) +
                ", Total: " + String.format("%.1f", totalThreat));

            // Select highest threat
            if (totalThreat > highestThreat) {
                highestThreat = totalThreat;
                highestThreatEntity = entity;
            }
        }

        if (highestThreatEntity != null) {
            String targetName = highestThreatEntity.getName().getString();
            double distance = Math.sqrt(highestThreatEntity.distanceToSqr(bot));

            System.out.println("⚔ Selected Priority Target: " + targetName +
                " (Threat: " + String.format("%.1f", highestThreat) +
                ", Distance: " + String.format("%.1f", distance) + "m)");

            // Log reason if multiple enemies
            if (hostileEntities.size() > 1) {
                String reason = getTargetSelectionReason(highestThreatEntity, distance);
                System.out.println("Reason: " + reason);
            }
        }

        return highestThreatEntity;
    }

    /**
     * Calculates base threat value for an entity based on type and distance.
     */
    private static double calculateBaseThreatForEntity(Entity entity, double distance) {
        // HOSTILE PLAYERS - HIGH PRIORITY THREATS
        if (entity instanceof net.minecraft.world.entity.player.Player player) {
            double baseThreat = 30.0; // Base threat for hostile player

            // Analyze player equipment to assess threat level
            net.minecraft.world.item.ItemStack mainHand = player.getMainHandItem();
            net.minecraft.world.item.ItemStack offHand = player.getOffhandItem();

            // Check for weapons
            if (mainHand.getItem() instanceof net.minecraft.world.item.Item) {
                baseThreat += 15.0; // Sword wielding player
            } else if (mainHand.getItem() instanceof net.minecraft.world.item.AxeItem) {
                baseThreat += 12.0; // Axe wielding player
            } else if (mainHand.getItem() instanceof net.minecraft.world.item.BowItem ||
                      mainHand.getItem() instanceof net.minecraft.world.item.CrossbowItem) {
                baseThreat += 20.0; // Ranged weapon - very dangerous
            } else if (mainHand.getItem() instanceof net.minecraft.world.item.TridentItem) {
                baseThreat += 18.0; // Trident
            }

            // Check for shield (defensive capability)
            if (offHand.getItem() instanceof net.minecraft.world.item.ShieldItem) {
                baseThreat += 8.0; // Player with shield is more dangerous
            }

            // Check armor (increases survivability = higher threat)
            int armorPieces = 0;
            for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) {
                net.minecraft.world.item.ItemStack armorSlot = player.getItemBySlot(slot);
                if (!armorSlot.isEmpty()) {
                    armorPieces++;
                    // Diamond/Netherite armor is particularly dangerous
                    String armorName = armorSlot.getItem().toString().toLowerCase();
                    if (armorName.contains("diamond") || armorName.contains("netherite")) {
                        baseThreat += 5.0;
                    } else {
                        baseThreat += 2.0;
                    }
                }
            }

            // Close range player = critical threat
            if (distance < 4.0) {
                baseThreat += 15.0;
            }

            LOGGER.info("⚔ Hostile player threat analysis: {} - Base: {}, Equipment bonus included",
                player.getName().getString(), String.format("%.1f", baseThreat));

            return baseThreat;
        }

        // MOB THREATS
        String entityType = entity.getName().getString().toLowerCase();
        double baseThreat = 5.0;

        // EXPLOSIVE THREATS - HIGHEST PRIORITY
        if (entityType.contains("creeper")) {
            baseThreat = 50.0;
            if (distance <= 3.0) baseThreat += 30.0;
        }
        // MAXIMUM DANGER MOBS
        else if (entityType.contains("warden")) baseThreat = 100.0;
        else if (entityType.contains("ravager")) baseThreat = 40.0;
        // RANGED ATTACKERS
        else if (entityType.contains("skeleton") || entityType.contains("stray")) baseThreat = 20.0;
        else if (entityType.contains("witch")) baseThreat = 25.0;
        else if (entityType.contains("blaze")) baseThreat = 30.0;
        else if (entityType.contains("ghast")) baseThreat = 35.0;
        else if (entityType.contains("drowned") && distance > 5.0) baseThreat = 15.0;
        else if (entityType.contains("pillager")) baseThreat = 18.0;
        // FLYING THREATS
        else if (entityType.contains("phantom")) baseThreat = 22.0;
        // MELEE THREATS
        else if (entityType.contains("zombie") || entityType.contains("husk")) baseThreat = 8.0;
        else if (entityType.contains("spider") || entityType.contains("cave_spider")) baseThreat = 12.0;
        else if (entityType.contains("enderman")) baseThreat = 15.0;
        else if (entityType.contains("vindicator")) baseThreat = 25.0;
        else if (entityType.contains("piglin")) baseThreat = 10.0;
        else if (entityType.contains("slime") || entityType.contains("magma_cube")) baseThreat = 6.0;
        else if (entityType.contains("silverfish")) baseThreat = 4.0;

        return baseThreat;
    }

    /**
     * Returns a human-readable reason for why a target was selected.
     */
    private static String getTargetSelectionReason(Entity entity, double distance) {
        String name = entity.getName().getString().toLowerCase();
        if (name.contains("creeper")) return "Explosive threat - highest priority";
        if (name.contains("warden")) return "Maximum danger mob";
        if (name.contains("skeleton") || name.contains("stray")) return "Ranged attacker";
        if (name.contains("witch")) return "Potion thrower - high threat";
        if (entity instanceof net.minecraft.world.entity.player.Player) return "Hostile player detected";
        if (distance < 3.0) return "Critical range - immediate threat";
        return "Closest/highest threat in range";
    }

    // ==================== ACTION TRACKING HELPERS ====================

    public static boolean isActionInProgress(String botName) {
        Boolean inProgress = actionInProgress.get(botName);
        if (inProgress == null || !inProgress) return false;

        // Check for timeout
        Long startTime = actionStartTime.get(botName);
        if (startTime != null && System.currentTimeMillis() - startTime > ACTION_TIMEOUT_MS) {
            LOGGER.warn("[ACTION] Action '{}' timed out after {}ms - forcing completion",
                currentAction.get(botName), ACTION_TIMEOUT_MS);
            completeAction(botName);
            return false;
        }
        return true;
    }

    public static void startAction(String botName, String actionName) {
        actionInProgress.put(botName, true);
        currentAction.put(botName, actionName);
        actionStartTime.put(botName, System.currentTimeMillis());
        LOGGER.debug("[ACTION] Started: {}", actionName);
    }

    public static void completeAction(String botName) {
        actionInProgress.put(botName, false);
        String completedAction = currentAction.get(botName);
        currentAction.remove(botName);
        actionStartTime.remove(botName);
        if (completedAction != null) {
            LOGGER.debug("[ACTION] Completed: {}", completedAction);
        }
    }

    public static void waitForActionCompletion(String botName, long timeoutMs) {
        long start = System.currentTimeMillis();
        while (isActionInProgress(botName)) {
            if (System.currentTimeMillis() - start > timeoutMs) {
                LOGGER.warn("[ACTION] waitForActionCompletion timed out after {}ms", timeoutMs);
                completeAction(botName);
                break;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
