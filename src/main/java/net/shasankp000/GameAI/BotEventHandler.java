package net.shasankp000.GameAI;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.world.World;
import net.shasankp000.ChatUtils.ChatUtils;
import net.shasankp000.DangerZoneDetector.DangerZoneDetector;
import net.shasankp000.Database.QTable;
import net.shasankp000.Database.QTableStorage;
import net.shasankp000.Database.StateActionPair;
import net.shasankp000.Database.StateActionTransition;
import net.shasankp000.Entity.AutoFaceEntity;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.shasankp000.Entity.FaceClosestEntity;
import net.shasankp000.PlayerUtils.*;
import net.shasankp000.WorldUitls.GetTime;
import net.shasankp000.Entity.EntityDetails;
import net.shasankp000.WorldUitls.isBlockItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

import static net.shasankp000.GameAI.State.isStateConsistent;


public class BotEventHandler {
    public static final Logger LOGGER = LoggerFactory.getLogger("ai-player");
    private static MinecraftServer server = null;
    public static ServerPlayerEntity bot = null;
    private static final String gameDir = MinecraftClient.getInstance().runDirectory.getAbsolutePath();
    public static final String qTableDir = gameDir + "/qtable_storage/";
    private static final Object monitorLock = new Object();
    private static boolean isExecuting = false;
    private static final double DEFAULT_RISK_APPETITE = 0.5; // Default value upon respawn
    public static boolean botDied = false; // Flag to track if the bot died
    public static boolean hasRespawned = false; // flag to track if the bot has respawned before or not
    public static int botSpawnCount = 0;
    private static State currentState = null;

    public BotEventHandler(MinecraftServer server, ServerPlayerEntity bot) {
        BotEventHandler.server = server;
        BotEventHandler.bot = bot;

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
        synchronized (monitorLock) {
            if (isExecuting) {
                System.out.println("Executing detection code");
                return; // Skip if already executing
            } else {
                System.out.println("No immediate threats detected");
                // Reset state when no threats are detected
                BotEventHandler.currentState = createInitialState(bot);
            }
            isExecuting = true;
        }

        try {
            ServerCommandSource botSource = bot.getCommandSource().withSilent().withMaxLevel(4);

            System.out.println("Distance from danger zone: " + DangerZoneDetector.detectDangerZone(bot, 10, 10 , 10) + " blocks");

            List<Entity> nearbyEntities = AutoFaceEntity.detectNearbyEntities(bot, 10); // Example bounding box size
            List<Entity> hostileEntities = nearbyEntities.stream()
                    .filter(entity -> entity instanceof HostileEntity)
                    .toList();


            BlockDistanceLimitedSearch blockDistanceLimitedSearch = new BlockDistanceLimitedSearch(bot, 3, 5);

            List<String> nearbyBlocks = blockDistanceLimitedSearch.detectNearbyBlocks();

            boolean hasSculkNearby = nearbyBlocks.stream()
                    .anyMatch(block -> block.contains("Sculk Sensor") || block.contains("Sculk Shrieker"));
            System.out.println("Nearby blocks: " + nearbyBlocks);

            int timeofDay = GetTime.getTimeOfWorld(bot);
            String time = (timeofDay >= 12000 && timeofDay < 24000) ? "night" : "day";

            World world = bot.getCommandSource().getWorld();
            RegistryKey<World> dimType = world.getRegistryKey();
            String dimension = dimType.getValue().toString();

            if (!hostileEntities.isEmpty()) {
                List<EntityDetails> nearbyEntitiesList = new ArrayList<>();
                for (Entity entity : nearbyEntities) {
                    String directionToBot = AutoFaceEntity.determineDirectionToBot(bot, entity);
                    nearbyEntitiesList.add(new EntityDetails(
                            entity.getName().getString(),
                            entity.getX(),
                            entity.getY(),
                            entity.getZ(),
                            entity instanceof HostileEntity,
                            directionToBot
                    ));
                }

                State currentState;

                if (hasRespawned && botDied) {
                    State lastKnownState = QTableStorage.loadLastKnownState(qTableDir + "/lastKnownState.bin");
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
                Map<StateActions.Action, Double> riskMap = rlAgentHook.calculateRisk(currentState, potentialActionList);

                Map<StateActions.Action, Double> chosenActionMap = rlAgentHook.chooseAction(currentState, riskAppetite, riskMap);
                Map.Entry<StateActions.Action, Double> entry = chosenActionMap.entrySet().iterator().next();

                StateActions.Action chosenAction = entry.getKey();
                double risk = entry.getValue();

                System.out.println("Chosen action: " + chosenAction);

                executeAction(chosenAction, botSource);


                List<ItemStack> hotBarItems = hotBarUtils.getHotbarItems(bot);
                SelectedItemDetails selectedItem = new SelectedItemDetails(
                        hotBarUtils.getSelectedHotbarItemStack(bot).getItem().getName().getString(),
                        hotBarUtils.getSelectedHotbarItemStack(bot).isFood(),
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

                double qValue = rlAgentHook.calculateQValue(currentState, chosenAction, reward, nextState, qTable);
                qTable.addEntry(currentState, chosenAction, qValue, nextState);


                QTableStorage.saveQTable(qTable, qTableDir + "/qtable.bin");
                QTableStorage.saveEpsilon(rlAgentHook.getEpsilon(), qTableDir + "/epsilon.bin");

                BotEventHandler.currentState = nextState;

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
                            entity instanceof HostileEntity,
                            directionToBot
                    ));
                }

                State currentState;

                if (hasRespawned && botDied) {
                    State lastKnownState = QTableStorage.loadLastKnownState(qTableDir + "/lastKnownState.bin");
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
                Map<StateActions.Action, Double> riskMap = rlAgentHook.calculateRisk(currentState, potentialActionList);

                Map<StateActions.Action, Double> chosenActionMap = rlAgentHook.chooseAction(currentState, riskAppetite, riskMap);
                Map.Entry<StateActions.Action, Double> entry = chosenActionMap.entrySet().iterator().next();

                StateActions.Action chosenAction = entry.getKey();
                double risk = entry.getValue();

                System.out.println("Chosen action: " + chosenAction);

                executeAction(chosenAction, botSource);

                nearbyBlocks = blockDistanceLimitedSearch.detectNearbyBlocks();

                List<ItemStack> hotBarItems = hotBarUtils.getHotbarItems(bot);
                SelectedItemDetails selectedItem = new SelectedItemDetails(
                        hotBarUtils.getSelectedHotbarItemStack(bot).getItem().getName().getString(),
                        hotBarUtils.getSelectedHotbarItemStack(bot).isFood(),
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

                double qValue = rlAgentHook.calculateQValue(currentState, chosenAction, reward, nextState, qTable);
                qTable.addEntry(currentState, chosenAction, qValue, nextState);


                QTableStorage.saveQTable(qTable, qTableDir + "/qtable.bin");
                QTableStorage.saveEpsilon(rlAgentHook.getEpsilon(), qTableDir + "/epsilon.bin");

                BotEventHandler.currentState = nextState;
            }


        } finally {
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
        synchronized (monitorLock) {
            if (isExecuting) {
                System.out.println("Already executing detection code, skipping...");
                return; // Skip if already executing
            }
            isExecuting = true;
        }

        try {
            ServerCommandSource botSource = bot.getCommandSource().withSilent().withMaxLevel(4);


            if (qTable == null) {
                ChatUtils.sendChatMessages(botSource, "I have no training data to work with! Please spawn me in training mode so that I can learn first!");
            }

            else {
                // Detect nearby hostile entities
                List<Entity> nearbyEntities = AutoFaceEntity.detectNearbyEntities(bot, 10); // Example bounding box size
                List<Entity> hostileEntities = nearbyEntities.stream()
                        .filter(entity -> entity instanceof HostileEntity)
                        .toList();

                if (!hostileEntities.isEmpty()) {
                    // Gather state information
                    State currentState = createInitialState(bot);

//                double riskAppetite = currentState.getRiskAppetite();
//
                    Map<StateActions.Action, Double> riskMap = currentState.getRiskMap();



                    // Choose action
                    StateActions.Action chosenAction = rlAgentHook.chooseActionPlayMode(currentState, qTable, riskMap, "detectAndReactPlayMode");


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
                    StateActions.Action chosenAction = rlAgentHook.chooseActionPlayMode(currentState, qTable, riskMap, "detectAndReactPlayMode");


                    // Log chosen action for debugging
                    System.out.println("Play Mode - Chosen action: " + chosenAction);

                    // Execute action
                    executeAction(chosenAction, botSource);
                }


            }
        } finally {
            synchronized (monitorLock) {
                System.out.println("Resetting handler trigger flag.");
                isExecuting = false;
                AutoFaceEntity.isHandlerTriggered = false; // Reset the trigger flag
            }
        }
    }

    private static void executeAction(StateActions.Action chosenAction, ServerCommandSource botSource) {
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


    private static State createInitialState(ServerPlayerEntity bot) {
        List<ItemStack> hotBarItems = hotBarUtils.getHotbarItems(bot);
        ItemStack selectedItemStack = hotBarUtils.getSelectedHotbarItemStack(bot);

        BlockDistanceLimitedSearch blockDistanceLimitedSearch = new BlockDistanceLimitedSearch(bot, 3, 5);

        List<String> nearbyBlocks = blockDistanceLimitedSearch.detectNearbyBlocks();

        SelectedItemDetails selectedItem = new SelectedItemDetails(
                selectedItemStack.getItem().getName().getString(),
                selectedItemStack.isFood(),
                isBlockItem.checkBlockItem(selectedItemStack)
        );

        List<Entity> nearbyEntities = AutoFaceEntity.detectNearbyEntities(bot, 10);

        List<EntityDetails> nearbyEntitiesList = new ArrayList<>();

        String directionToBot = "";

        for(Entity entity: nearbyEntities) {

            directionToBot = AutoFaceEntity.determineDirectionToBot(bot, entity);

            nearbyEntitiesList.add(new EntityDetails(
                    entity.getName().getString(),
                    entity.getX(),
                    entity.getY(),
                    entity.getZ(),
                    entity instanceof HostileEntity,
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
        String dimension = bot.getCommandSource().getWorld().getRegistryKey().getValue().toString();
        Map<StateActions.Action, Double> riskMap = new HashMap<>();

        Map<StateActions.Action, Double> podMap = new HashMap<>(); // blank pod map for now.

        return new State(
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
    }


    private static void performAction(String action, ServerCommandSource botSource) {

        String botName = botSource.getName();


        switch (action) {
            case "moveForward":
                System.out.println("Performing action: move forward");
                server.getCommandManager().executeWithPrefix(botSource, "/player " + botName + " move forward");
                AutoFaceEntity.isBotMoving = true;
                break;
            case "moveBackward":
                System.out.println("Performing action: move backward");
                server.getCommandManager().executeWithPrefix(botSource, "/player " + botName + " move backward");
                AutoFaceEntity.isBotMoving = true;
                break;
            case "turnLeft":
                System.out.println("Performing action: turn left");
                server.getCommandManager().executeWithPrefix(botSource, "/player " + botName + " turn left");
                break;
            case "turnRight":
                System.out.println("Performing action: turn right");
                server.getCommandManager().executeWithPrefix(botSource, "/player " + botName + " turn right");
                break;
            case "jump":
                System.out.println("Performing action: jump");
                bot.jump();
                break;
            case "sneak":
                System.out.println("Performing action: sneak");
                server.getCommandManager().executeWithPrefix(botSource, "/player " + botName + " sneak");
                break;
            case "sprint":
                System.out.println("Performing action: sprint");
                server.getCommandManager().executeWithPrefix(botSource, "/player " + botName + " sprint");
                break;
            case "unsneak":
                System.out.println("Performing action: unsneak");
                server.getCommandManager().executeWithPrefix(botSource, "/player " + botName + " unsneak");
                break;
            case "unsprint":
                System.out.println("Performing action: unsprint");
                server.getCommandManager().executeWithPrefix(botSource, "/player " + botName + " unsprint");
                break;
            case "stopMoving":
                System.out.println("Performing action: stop moving");
                server.getCommandManager().executeWithPrefix(botSource, "/player " + botName + " stop");
                AutoFaceEntity.isBotMoving = false;
                break;
            case "useItem":
                System.out.println("Performing action: use currently selected item");
                server.getCommandManager().executeWithPrefix(botSource, "/player " + botName + " use");
                break;
            case "attack":
                System.out.println("Performing action: attack");
                FaceClosestEntity.faceClosestEntity(bot, AutoFaceEntity.hostileEntities);
                server.getCommandManager().executeWithPrefix(botSource, "/player " + botName + " attack");
                break;
            case "hotbar1":
                System.out.println("Performing action: Select hotbar slot 1");
                server.getCommandManager().executeWithPrefix(botSource, "/player " + botName + " hotbar 1");
                break;
            case "hotbar2":
                System.out.println("Performing action: Select hotbar slot 2");
                server.getCommandManager().executeWithPrefix(botSource, "/player " + botName + " hotbar 2");
                break;
            case "hotbar3":
                System.out.println("Performing action: Select hotbar slot 3");
                server.getCommandManager().executeWithPrefix(botSource, "/player " + botName + " hotbar 3");
                break;
            case "hotbar4":
                System.out.println("Performing action: Select hotbar slot 4");
                server.getCommandManager().executeWithPrefix(botSource, "/player " + botName + " hotbar 4");
                break;
            case "hotbar5":
                System.out.println("Performing action: Select hotbar slot 5");
                server.getCommandManager().executeWithPrefix(botSource, "/player " + botName + " hotbar 5");
                break;
            case "hotbar6":
                System.out.println("Performing action: Select hotbar slot 6");
                server.getCommandManager().executeWithPrefix(botSource, "/player " + botName + " hotbar 6");
                break;
            case "hotbar7":
                System.out.println("Performing action: Select hotbar slot 7");
                server.getCommandManager().executeWithPrefix(botSource, "/player " + botName + " hotbar 7");
                break;
            case "hotbar8":
                System.out.println("Performing action: Select hotbar slot 8");
                server.getCommandManager().executeWithPrefix(botSource, "/player " + botName + " hotbar 8");
                break;
            case "hotbar9":
                System.out.println("Performing action: Select hotbar slot 9");
                server.getCommandManager().executeWithPrefix(botSource, "/player " + botName + " hotbar 9");
                break;

            default:
                System.out.println("Invalid action");
                break;
        }
    }
}

