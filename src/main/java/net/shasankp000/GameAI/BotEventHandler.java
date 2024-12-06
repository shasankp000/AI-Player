package net.shasankp000.GameAI;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.world.World;
import net.shasankp000.DangerZoneDetector.DangerZoneDetector;
import net.shasankp000.Database.QTableStorage;
import net.shasankp000.Entity.AutoFaceEntity;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.shasankp000.PlayerUtils.hotBarUtils;
import net.shasankp000.PlayerUtils.getPlayerHunger;
import net.shasankp000.PlayerUtils.getPlayerOxygen;
import net.shasankp000.WorldUitls.GetTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import net.shasankp000.PlayerUtils.getArmorStack;
import net.shasankp000.PlayerUtils.getOffHandStack;


public class BotEventHandler {
    public static final Logger LOGGER = LoggerFactory.getLogger("ai-player");
    private static MinecraftServer server = null;
    private static ServerPlayerEntity bot = null;
    private static final String gameDir = MinecraftClient.getInstance().runDirectory.getAbsolutePath();
    private static final String qTableDir = gameDir + "/qtable_storage/";
    private static final Object monitorLock = new Object();
    private static boolean isExecuting = false;
    private static Map<State, Map<StateActions.Action, Double>> qTable;


    public BotEventHandler(MinecraftServer server, ServerPlayerEntity bot) {
        BotEventHandler.server = server;
        BotEventHandler.bot = bot;

        // Load Q-table from storage
        try {
            qTable = QTableStorage.loadQTable(qTableDir + "/qtable.bin");
            System.out.println("Loaded Q-table from storage.");
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.err.println("No existing Q-table found. Starting fresh.");
            qTable = new HashMap<>(); // Initialize an empty Q-table
        }

    }

    private static State initializeBotState(Map<State, Map<StateActions.Action, Double>> qTable) {

        State initialState = null;

        if (qTable == null || qTable.isEmpty()) {
            System.out.println("No initial state available. Q-table is empty.");
        }

        else {

            // Get the most recent state-action pair (guaranteed to exist for non-empty qTable)
            initialState = qTable.keySet().iterator().next();

            System.out.println("Setting initial state to: " + initialState);
            // Perform any initialization logic using this state

        }

        return initialState;
    }




    public void detectAndReact(RLAgent rlAgentHook, double distanceToHostileEntity) {
        // one single function for all detection events (various States).

        synchronized (monitorLock) {
            if(isExecuting) {
                System.out.println("Executing detection code");

                return; // Skip if already executing
            }

            isExecuting = true;

        }

        try {
            ServerCommandSource botSource = bot.getCommandSource().withSilent().withMaxLevel(4);


            List<Entity> nearbyEntities = AutoFaceEntity.detectNearbyEntities(bot, 10); // Example bounding box size

            List<Entity> hostileEntities = nearbyEntities.stream()
                    .filter(entity -> entity instanceof HostileEntity)
                    .toList();


            int timeofDay = GetTime.getTimeOfWorld(bot);

            String time;

            if (timeofDay >= 12000 && timeofDay < 24000) {
                time = "night";
            }
            else {
                time = "day";
            }

            World world = bot.getCommandSource().getWorld();

            RegistryKey<World> dimType = world.getRegistryKey();

            String dimension = dimType.getValue().toString(); // minecraft:overworld or any other dimension, including custom dimensions from other mods.


            if (!hostileEntities.isEmpty()) {

                List<ItemStack> hotBarItems;

                String selectedItem;

                double dangerDistance;

                int botHungerLevel;

                int botOxygenLevel;

                Map<String, ItemStack> armorItems;

                ItemStack offhandItem;



                State currentState = initializeBotState(qTable);

                if (currentState == null) {

                    // Gather state information
                    currentState = createInitialState(bot);

                }

                // Choose action
                StateActions.Action chosenAction = rlAgentHook.chooseAction(currentState);

                // Log chosen action for debugging
                System.out.println("Chosen action: " + chosenAction);

                // Execute action
                switch (chosenAction) {
                    case MOVE_FORWARD:
                        performAction("moveForward", botSource);
                        break;
                    case MOVE_BACKWARD:
                        performAction("moveBackward", botSource);
                        break;
                    case TURN_LEFT:
                        performAction("turnLeft", botSource);
                        break;
                    case TURN_RIGHT:
                        performAction("turnRight", botSource);
                        break;
                    case JUMP:
                        performAction("jump", botSource);
                        break;
                    case SNEAK:
                        performAction("sneak", botSource);
                        break;
                    case SPRINT:
                        performAction("sprint", botSource);
                        break;
                    case STOP_SNEAKING:
                        performAction("unsneak", botSource);
                        break;
                    case STOP_SPRINTING:
                        performAction("unsprint", botSource);
                        break;
                    case STOP_MOVING:
                        performAction("stopMoving", botSource);
                        break;
                    case USE_ITEM:
                        performAction("useItem", botSource);
                        break;
                    case ATTACK:
                        performAction("attack", botSource);
                        break;
                    case HOTBAR_1:
                        performAction("hotbar1", botSource);
                        break;
                    case HOTBAR_2:
                        performAction("hotbar2", botSource);
                        break;
                    case HOTBAR_3:
                        performAction("hotbar3", botSource);
                        break;
                    case HOTBAR_4:
                        performAction("hotbar4", botSource);
                        break;
                    case HOTBAR_5:
                        performAction("hotbar5", botSource);
                        break;
                    case HOTBAR_6:
                        performAction("hotbar6", botSource);
                        break;
                    case HOTBAR_7:
                        performAction("hotbar7", botSource);
                        break;
                    case HOTBAR_8:
                        performAction("hotbar8", botSource);
                        break;
                    case HOTBAR_9:
                        performAction("hotbar9", botSource);
                        break;
                    case HOTBAR_10:
                        performAction("hotbar10", botSource);
                        break;
                    case STAY:
                        System.out.println("Performing action: Stay and do nothing");
                        break;
                }

                // State after whatever action was taken

                hotBarItems = hotBarUtils.getHotbarItems(bot);

                selectedItem = hotBarUtils.getSelectedHotbarItemName(bot);

                dangerDistance = DangerZoneDetector.detectDangerZone(bot, 10, 5, 5);

                botHungerLevel = getPlayerHunger.getBotHungerLevel(bot);

                botOxygenLevel = getPlayerOxygen.getBotOxygenLevel(bot);

                armorItems = getArmorStack.getArmorItems(bot); // Get armor items from the helper method
                offhandItem = getOffHandStack.getOffhandItem(bot); // Get offhand item

                State nextState = new State(
                        (int) bot.getX(),
                        (int) bot.getY(),
                        (int) bot.getZ(),
                        distanceToHostileEntity, // Distance to nearest hostile
                        (int) bot.getHealth(),
                        dangerDistance,
                        hotBarItems,
                        selectedItem,
                        time,
                        dimension,
                        botHungerLevel,
                        botOxygenLevel,
                        offhandItem,
                        armorItems,
                        chosenAction
                );


                // Calculate reward
                double reward = RLAgent.calculateReward(
                        (int) bot.getX(),
                        (int) bot.getY(),
                        (int) bot.getZ(),
                        distanceToHostileEntity, // Distance to nearest hostile
                        (int) bot.getHealth(),
                        dangerDistance,
                        hotBarItems,
                        selectedItem,
                        time,
                        dimension,
                        botHungerLevel,
                        botOxygenLevel,
                        offhandItem,
                        armorItems,
                        chosenAction

                ); //


                System.out.println("Reward: " + reward);

                // Update Q-table
                rlAgentHook.updateQValue(currentState, chosenAction, reward, nextState);

                Map<StateActions.Action, Double> actionMap = new HashMap<>();

                rlAgentHook.decayEpsilon();

                actionMap.put(chosenAction, reward);


                try {
                    QTableStorage.saveStateActionPair(nextState, actionMap,qTableDir + "/qtable.bin");
                    System.out.println("Q-table successfully saved to " + qTableDir + "/qtable.bin");
                } catch (Exception e) {
                    System.err.println("Failed to save Q-table: " + e.getMessage());
                    LOGGER.error(e.getMessage());
                }

            }

        }
        finally {
            synchronized (monitorLock) {
                System.out.println("Resetting handler trigger flag.");
                isExecuting = false;
                AutoFaceEntity.isHandlerTriggered = false; // reset the trigger flag.
            }
        }

    }

    private static State createInitialState(ServerPlayerEntity bot) {
        List<ItemStack> hotBarItems = hotBarUtils.getHotbarItems(bot);
        String selectedItem = hotBarUtils.getSelectedHotbarItemName(bot);
        double dangerDistance = DangerZoneDetector.detectDangerZone(bot, 10, 5, 5);
        int botHungerLevel = getPlayerHunger.getBotHungerLevel(bot);
        int botOxygenLevel = getPlayerOxygen.getBotOxygenLevel(bot);
        Map<String, ItemStack> armorItems = getArmorStack.getArmorItems(bot);
        ItemStack offhandItem = getOffHandStack.getOffhandItem(bot);
        String time = GetTime.getTimeOfWorld(bot) >= 12000 ? "night" : "day";
        String dimension = bot.getCommandSource().getWorld().getRegistryKey().getValue().toString();


        return new State(
                (int) bot.getX(),
                (int) bot.getY(),
                (int) bot.getZ(),
                0.0, // Distance to hostile can be updated dynamically elsewhere
                (int) bot.getHealth(),
                dangerDistance,
                hotBarItems,
                selectedItem,
                time,
                dimension,
                botHungerLevel,
                botOxygenLevel,
                offhandItem,
                armorItems,
                StateActions.Action.NONE
        );
    }


    private static void performAction(String action, ServerCommandSource botSource) {

        String botName = botSource.getName();


        switch (action) {
            case "moveForward":
                System.out.println("Performing action: move forward");
                server.getCommandManager().executeWithPrefix(botSource, "/player " + botName + " move forward");
                break;
            case "moveBackward":
                System.out.println("Performing action: move backward");
                server.getCommandManager().executeWithPrefix(botSource, "/player " + botName + " move backward");
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
                break;
            case "useItem":
                System.out.println("Performing action: use currently selected item");
                server.getCommandManager().executeWithPrefix(botSource, "/player " + botName + " use");
                break;
            case "attack":
                System.out.println("Performing action: attack");
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
            case "hotbar10":
                System.out.println("Performing action: Select hotbar slot 10");
                server.getCommandManager().executeWithPrefix(botSource, "/player " + botName + " hotbar 0");
                break;

            default:
                System.out.println("Invalid action");
                break;
        }
    }
}

