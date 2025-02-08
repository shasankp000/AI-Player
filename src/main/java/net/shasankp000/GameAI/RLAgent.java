package net.shasankp000.GameAI;

import java.util.*;
import java.util.stream.Collectors;


import net.minecraft.item.ItemStack;
import net.shasankp000.Database.QEntry;
import net.shasankp000.Database.QTable;
import net.shasankp000.Database.StateActionPair;
import net.shasankp000.Database.StateActionTransition;
import net.shasankp000.Entity.EntityDetails;
import net.shasankp000.GameAI.StateActions.Action;
import net.shasankp000.PlayerUtils.ResourceEvaluator;
import net.shasankp000.PlayerUtils.ThreatDetector;
import net.shasankp000.Commands.modCommandRegistry;
import net.shasankp000.DangerZoneDetector.CliffDetector;

public class RLAgent {
    private static final double ALPHA = 0.1;  // Learning rate
    private static final double GAMMA = 0.9;  // Discount factor
    public double epsilon;
    private static final double MIN_EPSILON = 0.1; // Minimum exploration rate
    private static final double EPSILON_DECAY_RATE = 0.99; // Decay rate for epsilon

    // Q-table: Maps each state to a map of actions and their Q-values
    private final QTable qTable;
    private final Random random;

    /**
     * Default constructor with epsilon initialized to 1.0.
     */

    public RLAgent() {
        this.epsilon = 1.0; // Initial exploration rate
        qTable = new QTable();
        random = new Random();
    }

    /**
     * Overloaded constructor to allow custom epsilon initialization.
     *
     * @param epsilon Initial epsilon value for exploration-exploitation tradeoff.
     * @param customQTable to load in the existing Q-table.
     */
    public RLAgent(double epsilon, QTable customQTable) {
        this.epsilon = epsilon;
        this.qTable = customQTable != null ? customQTable : new QTable();
        random = new Random();
    }

    // Choose action based on epsilon-greedy strategy and risk appetite
    public Map<Action, Double> chooseAction(State state, double riskAppetite, Map<Action, Double> riskMap) {
        double selectedRandomValue = random.nextDouble();
        Action chosenAction;
        double chosenRiskValue = 0.0; // Default risk value


        System.out.println("Generated random value: " + selectedRandomValue);

        // Get the podMap from the state
        Map<Action, Double> podMap = state.getPodMap();

        System.out.println("PodMap from the state: " + podMap);

        double podThreshold = 0.7; // Define PoD threshold

        // Adjust the risk threshold dynamically based on risk appetite
        double riskThreshold = riskAppetite >= 0.7 ? 5.0  // High risk appetite allows riskier actions
                : riskAppetite >= 0.3 ? 2.5  // Moderate risk appetite uses default threshold
                : 1.0;                      // Low risk appetite restricts to very low-risk actions

        System.out.println("Calculated risk threshold: " + riskThreshold);

        // Build the viableActions map by filtering for valid actions
        Map<Action, Double> viableActions = riskMap.entrySet().stream()
                .filter(entry -> podMap.getOrDefault(entry.getKey(), 0.0) < podThreshold) // Exclude actions with high PoD
                .filter(entry -> entry.getValue() < riskThreshold) // Include actions below the adjusted risk threshold
                .filter(entry -> entry.getValue() < 0.0) // including actions with negative risk, widening the exploration space a bit
                .filter(entry -> entry.getValue() != 0.0) // don't include absolutely pointless actions.
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        System.out.println("Viable actions map: " + viableActions);

        // Apply epsilon-greedy strategy to choose an action from viableActions
        if (selectedRandomValue < epsilon) {
            // Exploration: Randomly select an action from viable actions
            System.out.println("Exploring with epsilon: " + epsilon);

            if (!viableActions.isEmpty()) {
                List<Action> actionList = new ArrayList<>(viableActions.keySet());
                chosenAction = actionList.get(random.nextInt(actionList.size()));
                chosenRiskValue = viableActions.get(chosenAction);
            } else {
                // Default to STAY if no viable actions exist
                chosenAction = Action.STAY;
            }

        } else {

            // Exploitation, choose the action with the highest Q-value from the table.

            chosenAction = chooseActionPlayMode(state, qTable, riskMap, "chooseAction");

            if (chosenAction.equals(Action.STAY)) {

                // no similar states could be found and thus no Q-value.

                System.out.println("No suitable action found within the Qtable");

                // Fallback to viable actions if no similar state found
                chosenAction = viableActions.entrySet().stream()
                        .min(Map.Entry.comparingByValue()) // Select action with the lowest risk value
                        .map(Map.Entry::getKey)
                        .orElse(Action.STAY);
                chosenRiskValue = viableActions.getOrDefault(chosenAction, 0.0);


            }

        }

        // Return the chosen action, its associated risk value, and whether a risky action was taken
        Map<Action, Double> result = new HashMap<>();
        result.put(chosenAction, chosenRiskValue);

        return result;
    }


    public Action chooseActionPlayMode(State currentState, QTable qTable, Map<Action, Double> riskMap, String triggeredFrom) {
        Action bestAction = null;
        double bestQValue = Double.NEGATIVE_INFINITY;

        // Iterate over the entire QTable to find matching or similar states
        for (Map.Entry<StateActionPair, QEntry> entry : qTable.getTable().entrySet()) {
            StateActionPair pair = entry.getKey();
            QEntry qEntry = entry.getValue();


            if (State.isStateConsistent(pair.getState(), currentState)) {
                State nextState = entry.getValue().getNextState();

                // Skip if the next state is not optimal
                if (!nextState.isOptimal()) {
                    System.out.println("Skipping action " + pair.getAction() + " as it leads to a non-optimal state.");
                    continue;
                }

                double qValue = qEntry.getQValue();
                double pod = getPodForAction(nextState.getActionTaken(), qTable);

                // Only consider actions with acceptable Q-values and optionally filter by PoD
                if (pod < 0.7 && qValue > bestQValue) {
                    bestQValue = qValue;
                    bestAction = pair.getAction();
                }
            }
        }

        if (bestAction == null) {
            System.out.println("No viable actions found. Defaulting to STAY.");

            if ("detectAndReactPlayMode".equals(triggeredFrom)) {
                return determineViableAction(currentState, riskMap);
            }

            return Action.STAY;
        }

        System.out.println("Chosen action: " + bestAction + " with Q-value: " + bestQValue);
        return bestAction;
    }


    private Action determineViableAction(State currentState, Map<Action, Double> riskMap) {
        // Define a podThreshold for viable actions
        double podThreshold = 0.7;
        Map<Action, Double> podMap = currentState.getPodMap();

        double riskAppetite = currentState.getRiskAppetite();

        // Adjust the risk threshold dynamically based on risk appetite
        double riskThreshold = riskAppetite >= 0.7 ? 5.0  // High risk appetite allows riskier actions
                : riskAppetite >= 0.3 ? 2.5  // Moderate risk appetite uses default threshold
                : 1.0;                      // Low risk appetite restricts to very low-risk actions


        // Build the viableActions map by filtering for valid actions
        Map<Action, Double> viableActions = riskMap.entrySet().stream()
                .filter(entry -> podMap.getOrDefault(entry.getKey(), 0.0) < podThreshold) // Exclude actions with high PoD
                .filter(entry -> entry.getValue() < riskThreshold) // Include actions below the adjusted risk threshold
                .filter(entry -> entry.getValue() < 0.0) // including actions with negative risk, widening the exploration space a bit
                .filter(entry -> entry.getValue() != 0.0) // don't include absolutely pointless actions.
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));


        if (viableActions.isEmpty()) {
            System.out.println("No viable actions available. Defaulting to STAY.");
            return Action.STAY;
        }

        // Choose the action with the lowest risk from the viable actions
        return viableActions.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(Action.STAY);
    }



    private double getQValueForAction(State state, Action action, QTable qTable) {
        StateActionPair pair = new StateActionPair(state, action);
        QEntry entry = qTable.getEntry(pair);

        return (entry != null) ? entry.getQValue() : Double.NEGATIVE_INFINITY;
    }



    private double getPodForAction(Action action, QTable qTable) {
        double totalReward = 0.0;
        double deathCount = 0.0;

        for (Map.Entry<StateActionPair, QEntry> entry : qTable.getTable().entrySet()) {
            StateActionPair pair = entry.getKey();
            QEntry qEntry = entry.getValue();

            if (pair.getAction() == action) {
                double qValue = qEntry.getQValue();
                totalReward += Math.abs(qValue);
                if (qValue < 0) {
                    deathCount++;
                }
            }
        }

        return deathCount / Math.max(1, qTable.getTable().size());
    }





    // Decay epsilon after each episode or iteration
    public void decayEpsilon() {
        epsilon = Math.max(MIN_EPSILON, epsilon * EPSILON_DECAY_RATE);
        System.out.println("Updated epsilon: " + epsilon);
    }

    public Double getEpsilon() {

        return epsilon;

    }


    // Method to calculate the risk of a potential action based on the current state
    public Map<Action, Double> calculateRisk(State currentState, List<Action> possibleActions) {
        Map<Action, Double> riskMap = new HashMap<>();

        List<EntityDetails> nearbyEntities = currentState.getNearbyEntities();

        List<EntityDetails> hostileEntities = nearbyEntities.stream()
                .filter(EntityDetails::isHostile)
                .toList();

        boolean hasWardenNearby = nearbyEntities.stream()
                .anyMatch(entity -> "Warden".equals(entity.getName()));
        boolean hasSculkNearby = currentState.getNearbyBlocks().stream()
                .anyMatch(block -> block.contains("Sculk Sensor") || block.contains("Sculk Shrieker"));

        double nearbyHostileCount = hostileEntities.size();

        for (Action action : possibleActions) {
            double risk = 0.0;

            try {

            // Evaluate risk factors based on the current state and the action
            switch (action) {

                case MOVE_FORWARD:
                    for (EntityDetails entity : hostileEntities) {
                        double distance = Math.hypot(entity.getX() - currentState.getBotX(), entity.getZ() - currentState.getBotZ());
                        double threat = getEntityRisk(currentState, entity);
                        if ("front".equals(entity.getDirectionToBot())) {
                            risk += distance == 0 ? 100.0 : (1.0 / distance) * 6.0; // Higher penalty for advancing closer to an enemy
                            risk +=threat;
                        } else if ("left".equals(entity.getDirectionToBot()) || "right".equals(entity.getDirectionToBot())) {
                            risk += (1.0 / Math.max(distance, 1)) * 2.0; // Moderate penalty for advancing when enemies are flanking
                            risk +=threat;
                        } else if ("behind".equals(entity.getDirectionToBot())) {
                            risk -= (1.0 / Math.max(distance, 1)) * 1.5; // Slight reward for distancing from enemies behind
                            risk +=threat;
                        }
                    }
                    break;

                case MOVE_BACKWARD:
                    for (EntityDetails entity : hostileEntities) {
                        double distance = Math.hypot(entity.getX() - currentState.getBotX(), entity.getZ() - currentState.getBotZ());
                        double threat = getEntityRisk(currentState, entity);
                        if ("behind".equals(entity.getDirectionToBot())) {
                            risk += distance == 0 ? 100.0 : (1.0 / distance) * 6.0; // Higher penalty for retreating into an enemy
                            risk +=threat;
                        } else if ("front".equals(entity.getDirectionToBot())) {
                            risk -= (1.0 / Math.max(distance, 1)) * 3.0; // Slight reward for retreating from enemies in front
                            risk +=threat;
                        }
                    }
                    break;

                case TURN_LEFT:
                    for (EntityDetails entity : hostileEntities) {
                        double distance = Math.hypot(entity.getX() - currentState.getBotX(), entity.getZ() - currentState.getBotZ());
                        double threat = getEntityRisk(currentState, entity);
                        if ("left".equals(entity.getDirectionToBot())) {
                            risk += (1.0 / Math.max(distance, 1)) * 4.0; // Penalty for exposing the left side to nearby enemies
                            risk +=threat;
                        } else if ("front".equals(entity.getDirectionToBot())) {
                            risk -= (1.0 / Math.max(distance, 1)) * 2.0; // Favor turning to face an enemy in front
                            risk +=threat;
                        }
                    }
                    break;

                case TURN_RIGHT:
                    for (EntityDetails entity : hostileEntities) {
                        double distance = Math.hypot(entity.getX() - currentState.getBotX(), entity.getZ() - currentState.getBotZ());
                        double threat = getEntityRisk(currentState, entity);
                        if ("right".equals(entity.getDirectionToBot())) {
                            risk += (1.0 / Math.max(distance, 1)) * 4.0; // Penalty for exposing the right side to nearby enemies
                            risk +=threat;
                        } else if ("front".equals(entity.getDirectionToBot())) {
                            risk -= (1.0 / Math.max(distance, 1)) * 2.0; // Favor turning to face an enemy in front
                            risk +=threat;
                        }
                    }
                    break;


                case JUMP:

                    if (hasWardenNearby) {
                        risk += 20.0; // High penalty for jumping near the Warden
                    } else if (hasSculkNearby) {
                        risk += 10.0; // Moderate penalty for jumping near sculk blocks
                    } else if (currentState.getBotHealth() < 10) {
                        risk += 5.0; // Jumping is risky with low health
                    }

                    break;

                case STAY:
                    if (!hostileEntities.isEmpty()) {
                        risk += 10.0; // Staying still is risky when hostiles are nearby
                    }

                    else {risk += 0.0;}
                    break;

                case SNEAK:

                    if (hasWardenNearby || hasSculkNearby) {
                        risk -= 20.0; // Encourage sneaking near the Warden
                    }
                    else if(currentState.getDistanceToDangerZone() <= 5 && currentState.getDistanceToDangerZone()!=0) {
                        risk -= 30;
                    }
                    else {
                        risk += 0.0; // pointless sneaking otherwise
                    }


                    break;

                case SPRINT:

                    if (currentState.getBotHungerLevel() < 6) {
                        risk += 4.5; // Sprinting can deplete hunger quickly or there's a warden nearby or it's in an ancient city.
                    }
                    else if(!hostileEntities.isEmpty()) {
                        if (hasWardenNearby || hasSculkNearby) {
                            risk += 4.5;
                        }
                        for (EntityDetails entity : hostileEntities) {
                            if (entity.getName().equalsIgnoreCase("creeper") || nearbyHostileCount >= 5) {
                                risk -= 5.0;
                            }
                        }
                    }
                    else {
                        risk += 0.0; // pointless sprinting otherwise.

                    }

                    break;

                case STOP_MOVING:

                    if (!hostileEntities.isEmpty()) {
                        risk += 5.0; // Stopping can leave the bot vulnerable
                    }
                    else if (currentState.getDistanceToDangerZone() <= 5.0 && currentState.getDistanceToDangerZone()!=0.0) {
                        risk -= 10.0; // not risky to stop moving when close to lava pools or cliffs, might fall accidentally. Bot can then re-assess and start sneaking.
                    }
                    else {
                        risk += 0.0; // otherwise pointless calling this action.
                    }
                    break;

                case STOP_SNEAKING:
                    if (currentState.getDistanceToHostileEntity() < 5 && (hasWardenNearby || hasSculkNearby)) {
                        risk += 4.0; // Risky to stop sneaking when close to hostiles or near the warden or in an ancient city.
                    }
                    else if (currentState.getDistanceToDangerZone() <= 5.0 && currentState.getDistanceToDangerZone()!=0.0) {
                        risk += 10.0; // risky to stop sneaking when close to lava pools or cliffs, might fall accidentally.
                    }
                    else {
                        risk += 0.0; // otherwise pointless calling this action
                    }
                    break;

                case STOP_SPRINTING:

                    if (!hostileEntities.isEmpty()) {
                        for (EntityDetails entity: hostileEntities) {
                            if (entity.getName().equalsIgnoreCase("creeper") || nearbyHostileCount >= 5) {
                                risk += 10.0;
                            }

                        }
                    }

                    else {
                        risk += 0.0; // otherwise pointless calling this action
                    }

                    break;

                case USE_ITEM:
                    if (currentState.getSelectedItemStack().isFood() && currentState.getBotHungerLevel() > 13) {
                        risk += 3.0; // Penalize using food unnecessarily
                    }

                    else {
                        risk += 0.0; // pointless calling this action
                    }

                    break;

                case ATTACK:
                    double totalRisk = 0.0;

                    // Single-pass calculation for cumulative risk
                    totalRisk = hostileEntities.stream()
                            .mapToDouble(entity -> {
                                double entityRisk = getEntityRisk(currentState, entity);

                                System.out.println("Entity risk: " + entityRisk + "for " + entity);

                                if (entityRisk == 0.0) { // unknown entity or zombie/husk

                                    System.out.println("Set unknown entity/zombie/husk risk to -10 by default");

                                    entityRisk = -10; // set this purposely so that the action's value doesn't return 0.0;
                                }

                                // Reduce entity risk if the bot has strong offensive capabilities
                                if (currentState.getSelectedItem().contains("Sword") || currentState.getSelectedItem().contains("Axe") ||
                                        currentState.getSelectedItem().contains("Bow") || currentState.getSelectedItem().contains("Trident")) {
                                    entityRisk *= 0.5; // Reduce risk by half when equipped for attack
                                    entityRisk -= 5.0;
                                }

                                // Amplify risk if the bot's health is critically low
                                if (currentState.getBotHealth() < 10) {
                                    entityRisk *= 1.5; // Increase risk for attacking with low health
                                    entityRisk += 10.0;
                                }

                                return entityRisk;
                            })
                            .sum();

                    // Add a penalty for attacking when outnumbered
                    if (nearbyHostileCount > 3) {
                        totalRisk += 10.0; // Add extra risk for multiple enemies
                    }

                    // Subtract risk for strong armor
                    if (currentState.getArmorItems().containsKey("chestplate") &&
                            (currentState.getArmorItems().get("chestplate").contains("diamond") ||
                                    currentState.getArmorItems().get("chestplate").contains("netherite") ||
                                    currentState.getArmorItems().get("chestplate").contains("iron"))) {
                        totalRisk -= 50.0; // Reduce risk for having strong armor
                    }

                    // Add reward for single weak entities
                    if (nearbyHostileCount == 1) {
                        totalRisk -= 25.0; // Favor attacking a single enemy
                    }

                    // Final adjustments
                    risk += totalRisk; // Add total risk for attacking

                    System.out.println("Risk for ATTACK action: " + risk);
                    break;


                case EQUIP_ARMOR:

                    if (currentState.getArmorItems().containsValue("air") && (hasWardenNearby || hasSculkNearby)) {
                        if (!currentState.getHotBarItems().contains("chestplate") && !currentState.getHotBarItems().contains("helmet") && !currentState.getHotBarItems().contains("leggings") && !currentState.getHotBarItems().contains("boots")) {
                            risk += 0.0; // pointless calling this action when the bot doesn't have any armor on it.
                        }
                        else {
                            risk -= 14.0;
                        }
                    }

                    if (!hostileEntities.isEmpty()) {
                        if (!currentState.getArmorItems().containsValue("air")) {
                            risk += 0.0; // pointless calling this action when armor is already equipped
                        }

                        if (!currentState.getHotBarItems().contains("chestplate") && !currentState.getHotBarItems().contains("helmet") && !currentState.getHotBarItems().contains("leggings") && !currentState.getHotBarItems().contains("boots")) {
                            risk += 0.0; // pointless calling this action when the bot doesn't have any armor on it.
                        }
                        else {
                            risk -= 10.0;
                        }

                    }

                    else {
                        risk += 0.0; // pointless calling this action when there's no hostile entities around.
                    }

                    break;

                case HOTBAR_1, HOTBAR_2, HOTBAR_3, HOTBAR_4, HOTBAR_5, HOTBAR_6, HOTBAR_7, HOTBAR_8, HOTBAR_9:
                    int hotbarIndex = action.ordinal() - Action.HOTBAR_1.ordinal();
                    System.out.println("hotbar index: " + hotbarIndex);

                    // Ensure the index is within bounds (0-8)
                    if (hotbarIndex >= currentState.getHotBarItems().size()) {
                        System.err.println("Invalid hotbar index: " + hotbarIndex + ". Skipping action.");
                        break; // Skip if index is invalid
                    }

                    String hotbarItem = currentState.getHotBarItems().get(hotbarIndex);

                    if (hotbarItem == null || hotbarItem.equalsIgnoreCase("air")) {
                        risk += 5.0;
                        System.out.println("Empty or air slot selected. Risk: " + risk);
                    } else {
                        // Favor selecting weapons when hostile entities are nearby
                        if (!hostileEntities.isEmpty() &&
                                (hotbarItem.contains("Sword") || hotbarItem.contains("Bow") ||
                                        hotbarItem.contains("Axe") || hotbarItem.contains("Crossbow") ||
                                        hotbarItem.contains("Trident"))) {
                            risk -= 3.0;
                            System.out.println("Weapon selected. Risk reduced: " + risk);
                        }

                        // Favor selecting food when hungry
                        if (hotbarItem.equals(currentState.getSelectedItemStack().getName()) &&
                                currentState.getSelectedItemStack().isFood() &&
                                currentState.getBotHungerLevel() <= 6) {
                            risk -= 3.0;
                            System.out.println("Food selected when hungry. Risk reduced: " + risk);
                        }

                        // Penalize irrelevant item selection near hostiles
                        if (hotbarItem.contains("Pickaxe") || hotbarItem.contains("Hoe") ||
                                currentState.getSelectedItemStack().isBlock()) {
                            if (!hostileEntities.isEmpty()) {
                                risk += 2.0;
                                System.out.println("Irrelevant item selected near hostiles. Risk increased: " + risk);
                            }
                        }
                    }
                    break;



                default:
                        risk += 0.0; // unknown action, don't penalize without context.
                        break;

                }

            }

            catch (Exception e) {
                System.out.println("Exception in risk calculation: " + e.getMessage());
            }

            // Normalize and cap the risk value
            risk = Math.min(risk, 100.0); // Cap risk to 100.0 for balance
            riskMap.put(action, risk);
        }

        System.out.println("Final risk map: " + riskMap);

        return riskMap; // Return the map of actions and their associated risks

    }

    private double getEntityRisk(State currentState, EntityDetails entity) {
        double baseThreat = 0.0;
        double distance = Math.hypot(entity.getX() - currentState.getBotX(), entity.getZ() - currentState.getBotZ());

        // Adjust threat based on entity type
        switch (entity.getName()) {
            case "Creeper":
                baseThreat += 50.0; // Creepers are highly dangerous
                if (distance <= 3.0) {
                    baseThreat += 30.0; // High penalty if within explosion range
                }
                break;

            case "Drowned":
                baseThreat += 10.0; // Moderate threat
                if (distance <= 1.0) {
                    baseThreat += 1.0; // Threat increases as it closes in
                }
                break;

            case "Skeleton":
                baseThreat += 25.0; // Skeletons are ranged attackers
                if (distance <= 10.0) {
                    baseThreat += 15.0; // Higher penalty if within their effective range
                }
                break;

            case "Spider":
                baseThreat += 5.0; // Spiders are fast but not as lethal
                if (distance <= 2.0) {
                    baseThreat += 10.0; // Higher threat if close
                }
                break;

            case "Enderman":
                baseThreat += 30.0; // High threat if provoked
                if (distance <= 4.0 && currentState.getArmorItems().containsKey("carved pumpkin")) {
                    baseThreat -= 20.0; // Lower threat if wearing a pumpkin
                }
                break;

            case "Witch":
                baseThreat += 35.0; // Witches use potions and are dangerous
                if (distance <= 5.0) {
                    baseThreat += 20.0; // Potions are more effective at close range
                }
                break;

            case "Warden":
                baseThreat += 100.0; // Extremely dangerous mob
                if (distance <= 10.0) {
                    baseThreat += 50.0; // Danger increases if close
                }
                break;

            case "Blaze":
                baseThreat += 30.0; // Blaze is a ranged attacker
                if (distance <= 10.0) {
                    baseThreat += 20.0; // High threat within fireball range
                }
                break;

            case "Ghast":
                baseThreat += 25.0; // Moderate threat from ranged fireballs
                if (distance <= 20.0) {
                    baseThreat += 15.0; // More dangerous if close
                }
                break;

            case "Pillager":
                baseThreat += 30.0; // Pillagers use ranged crossbows
                if (distance <= 10.0) {
                    baseThreat += 15.0; // Higher threat in their attack range
                }
                break;

            case "Vindicator":
                baseThreat += 50.0; // Vindicators are melee attackers with high damage
                if (distance <= 2.0) {
                    baseThreat += 20.0; // More dangerous if close
                }
                break;

            case "Evoker":
                baseThreat += 60.0; // Evokers summon vexes and use fangs
                if (distance <= 6.0) {
                    baseThreat += 30.0; // Fang attacks are more dangerous at close range
                }
                break;

            case "Ravager":
                baseThreat += 80.0; // Ravagers deal heavy damage
                if (distance <= 3.0) {
                    baseThreat += 40.0; // Extremely dangerous if close
                }
                break;

            case "Slime":
            case "Magma Cube":
                baseThreat += 10.0; // Low base threat
                if (distance <= 2.0) {
                    baseThreat += 5.0; // Threat increases if close
                }
                break;

            case "Phantom":
                baseThreat += 20.0; // Moderate threat from aerial attacks
                if (distance <= 5.0) {
                    baseThreat += 10.0; // Closer range increases threat
                }
                break;

            case "Piglin Brute":
                baseThreat += 60.0; // Highly aggressive mob
                if (distance <= 2.0) {
                    baseThreat += 30.0; // Deadly at close range
                }
                break;

            case "Piglin":
                baseThreat += 20.0; // Moderate threat
                if (distance <= 3.0) {
                    baseThreat += 10.0; // More dangerous if close
                }
                break;

            case "Silverfish":
            case "Endermite":
                baseThreat += 10.0; // Low threat but annoying in groups
                break;

            case "Guardian":
                baseThreat += 40.0; // Guardians attack from range
                if (distance <= 8.0) {
                    baseThreat += 20.0; // Higher threat within their beam range
                }
                break;

            case "Elder Guardian":
                baseThreat += 80.0; // Elder Guardians are highly dangerous
                if (distance <= 8.0) {
                    baseThreat += 40.0; // Very high threat within beam range
                }
                break;

            default:
                baseThreat += 0.0; // don't penalize for unknown entities without context.
                break;
        }

        // Amplify threat if the bot's health is critically low
        if (currentState.getBotHealth() < 10) {
            baseThreat *= 1.5;
        }

        // Scale down threat based on distance (further = lower threat)
        baseThreat /= Math.max(distance, 1.0); // Avoid division by zero

        return baseThreat;
    }



    public Map<Action, Double> assessRiskOutcome(State initialState, State postActionState, Action action) {

        Map<Action, Double> actionPodMap = new HashMap<>();

        double pod = 0.0;

        // Calculate PoD based on depletion in critical parameters
        if (postActionState.getBotHealth() < initialState.getBotHealth()) {
            pod += (initialState.getBotHealth() - postActionState.getBotHealth()) * 0.5; // Weight for health depletion
        }
        if (postActionState.getBotHungerLevel() < initialState.getBotHungerLevel()) {
            pod += (initialState.getBotHungerLevel() - postActionState.getBotHungerLevel()) * 0.3; // Weight for hunger depletion
        }
        if (postActionState.getFrostLevel() > initialState.getFrostLevel()) {
            pod += (postActionState.getFrostLevel() - initialState.getFrostLevel()) * 0.2; // Weight for frost increase
        }

        System.out.println("Critical parameters stage passed, pod value: " + pod);

        // Proximity to danger zones
        if (initialState.getDistanceToDangerZone() != 0 && postActionState.getDistanceToDangerZone() != 0) {
            if (postActionState.getDistanceToDangerZone() < initialState.getDistanceToDangerZone()) {
                pod += (initialState.getDistanceToDangerZone() - postActionState.getDistanceToDangerZone()) * 0.4; // Moving closer increases risk
            }
        }

        System.out.println("Danger zone pod value: " + pod);

        // Proximity to hostile entities
        if (initialState.getDistanceToHostileEntity() != 0 && postActionState.getDistanceToHostileEntity() != 0) {
            if (postActionState.getDistanceToHostileEntity() < initialState.getDistanceToHostileEntity()) {
                pod += (initialState.getDistanceToHostileEntity() - postActionState.getDistanceToHostileEntity()) * 0.6; // Moving closer increases risk significantly
            }
        }

        System.out.println("Hostile entity pod value: " + pod);

        // Normalize PoD to a value between 0 and 1
        pod = Math.min(1.0, pod);

        System.out.println("final pod value: " + pod);

        actionPodMap.put(action, pod);

        return actionPodMap;
    }


    public List<Action> suggestPotentialActions(State currentState) {
        // Use a map to store actions and their weights
        Map<Action, Integer> actionWeights = new HashMap<>();

        // Assign weights to actions
        for (Action action : Action.values()) {
            int weight = calculateWeightForAction(currentState, action);
            actionWeights.put(action, weight);
        }

        // Create a list of unique actions
        List<Action> suggestions = new ArrayList<>(actionWeights.keySet());

        // Shuffle the list based on weights for probabilistic prioritization
        suggestions.sort((a, b) -> Integer.compare(actionWeights.get(b), actionWeights.get(a)));
        Collections.shuffle(suggestions);

        return suggestions;
    }


    // Helper method to calculate weights for actions
    private static int calculateWeightForAction(State currentState, Action action) {
        int weight = 1; // Default weight

        switch (action) {
            case ATTACK:
                if (currentState.getDistanceToHostileEntity() < 5) weight += 3; // Prioritize attack if hostile entity is nearby
                break;
            case MOVE_BACKWARD:
                if (currentState.getDistanceToDangerZone() < 5) weight += 2; // Higher weight if near danger zone
                break;
            case USE_ITEM:
                if (currentState.getBotHealth() < 10 || currentState.getBotHungerLevel() < 8) weight += 3; // Prioritize consumables
                break;
            // Other cases can be added based on relevance
        }

        return weight;
    }

    // Method to calculate risk appetite
    public double calculateRiskAppetite(State currentState) {
        // Normalize inputs

        double maxHealth = 20.0;
        double maxResources = 280.0; // calculated from all the max possible values from ResourceEvaluator.

        double health = currentState.getBotHealth();
        List<String> hotbarItems = currentState.getHotBarItems();

        double resources = ResourceEvaluator.evaluateHotbarResourceValue(hotbarItems);
        double threatLevel = ThreatDetector.calculateThreatLevel(currentState);

        double healthFactor = health / maxHealth; // Value between 0 and 1
        double resourceFactor = resources / maxResources; // Value between 0 and 1

        // Incorporate isOptimal check
        if (currentState.isOptimal()) {
            return Math.max(0.7, getBaseRiskAppetite(threatLevel, healthFactor, resourceFactor)); // Favor higher risk appetite if optimal
        }

        return getBaseRiskAppetite(threatLevel, healthFactor, resourceFactor); // Value between 0 and 1
    }


    private double getBaseRiskAppetite(double threatLevel, double healthFactor, double resourceFactor) {
        double threatFactor = Math.max(0, Math.min(1, threatLevel)); // Clamp threat level between 0 and 1

        // Weights for each factor
        double healthWeight = 0.5;
        double resourceWeight = 0.3;
        double threatWeight = 0.2;

        // Calculate base risk appetite
        double baseRiskAppetite = (healthFactor * healthWeight) +
                (resourceFactor * resourceWeight) +
                (1 - threatFactor) * threatWeight;

        // Adjust for exploration phase
        if (modCommandRegistry.isTrainingMode) {
            baseRiskAppetite += 0.2; // Encourage higher risk-taking during exploration
            baseRiskAppetite = Math.min(baseRiskAppetite, 1.0); // Ensure it doesn't exceed 1
        }
        return baseRiskAppetite;
    }


    public int calculateReward(int botX, int botY, int botZ, List<EntityDetails> nearbyEntities, List<String> nearbyBlocks, double distanceToHostileEntity, int botHealth,
                               double distanceToDanger, List<ItemStack> hotBarItems, String selectedItem,
                               String timeOfDay, String dimension, int botHungerLevel, int botOxygenLevel,
                               ItemStack offhandItem, Map<String, ItemStack> armorItems,
                               StateActions.Action actionTaken, double risk, double pod) {

        boolean hasWoolItems = hotBarItems.stream()
                .anyMatch(item -> item.getItem().getName().getString().toLowerCase().contains("wool") || item.getItem().getName().getString().toLowerCase().contains("carpet"));

        boolean hasWardenNearby = nearbyEntities.stream()
                .anyMatch(entity -> "Warden".equals(entity.getName()));
        boolean hasSculkNearby = nearbyBlocks.stream()
                .anyMatch(block -> block.contains("Sculk Sensor") || block.contains("Sculk Shrieker"));

        List<EntityDetails> hostileEntities = nearbyEntities.stream()
                .filter(EntityDetails::isHostile)
                .toList();

        int reward = 0;

        // 1. Distance to hostile entity
        if (distanceToHostileEntity > 10) {
            reward += 10; // Safe distance
        } else if (distanceToHostileEntity <= 5) {
            reward -= 10; // Dangerously close
            if (actionTaken == StateActions.Action.ATTACK) {
                reward += 15; // Higher reward for attacking nearby threats
            } else if (actionTaken == StateActions.Action.STAY) {
                reward -= 5; // Penalize for inaction
            }
        }



        // 2. Health
        if (botHealth > 15) {
            reward += 10; // Healthy
        } else if (botHealth <= 5) {
            reward -= 20; // Critically low health
            if (actionTaken == StateActions.Action.STAY || actionTaken == StateActions.Action.USE_ITEM) {
                reward += 10; // Reward defensive behavior when health is low
            }
        } else {
            reward += 5; // Moderate health
        }


        // 3. Distance to danger
        if (distanceToDanger > 10) {
            reward += 10; // Safe from danger zones
        } else if (distanceToDanger <= 5) {
            reward -= 15; // Too close to danger
            if (actionTaken == StateActions.Action.MOVE_BACKWARD || actionTaken == StateActions.Action.TURN_LEFT || actionTaken == StateActions.Action.TURN_RIGHT) {
                reward += 10; // Reward moving away from danger
            }
        }




        // 4. Selected item and offhand
        if (!hostileEntities.isEmpty() && selectedItem.contains("Sword") || selectedItem.contains("Bow") || selectedItem.contains("Axe") || selectedItem.contains("Crossbow") || selectedItem.contains("Trident") && offhandItem.getItem().getName().getString().equalsIgnoreCase("shield")) {
            reward += 20; // Weapon and shield equipped
        } else if (!hostileEntities.isEmpty() && selectedItem.contains("Pickaxe") || selectedItem.contains("Hoe") && offhandItem.getItem().getName().getString().equalsIgnoreCase("shield")) {
            reward += 15; // lower value weapon and shield equipped
        } else if (!hostileEntities.isEmpty() && selectedItem.contains("Air") && offhandItem.getItem().getName().getString().equalsIgnoreCase("shield")) {
            reward += 10; // only shield equipped
        } else {
            reward -= 5; // Irrelevant item selected
        }




        // 5. Time of day
        if (timeOfDay.equals("day")) {
            reward += 5; // Daytime is safer
        } else if (timeOfDay.equals("night")) {
            reward -= 10; // Nighttime is riskier
        }



        // 6. Dimension-specific rewards
        switch (dimension) {
            case "minecraft:overworld":
                reward += 1; // Normal behavior
                break;
            case "minecraft:nether":
                reward -= 5; // Riskier dimension
                break;
            case "minecraft:end":
                reward -= 10; // Very risky dimension
                break;
            default:
                reward -= 20; // Unknown dimension, penalize
        }



        // 7. Risk adjustment
        double riskWeight = (risk >= 0.5) ? 1.5 : 1.0; // Amplify reward for high-risk actions
        reward = (int) Math.round(reward * riskWeight); // casting to int might lose the value, let's say, 3.8 to 3 so it's better to round off.

        // 8. PoD adjustment
        if (pod >= 0.5) {
            reward -= (int) Math.round(pod * 10); // Penalize based on PoD
        }



        // 9. High-Risk Specific Scenarios
        if (risk > 0.7 && actionTaken == StateActions.Action.ATTACK && distanceToHostileEntity <= 5 && distanceToHostileEntity != 0) {
            reward += 20; // High reward for calculated risk
        } else if (risk > 0.7 && (actionTaken == StateActions.Action.MOVE_BACKWARD || actionTaken == StateActions.Action.TURN_LEFT || actionTaken == StateActions.Action.TURN_RIGHT)) {
            reward += 10; // Reward cautious behavior in high-risk situations
        }



        // 10. Case for being in ancient city biome / deep dark biome

        if (hasWoolItems && (hasWardenNearby || hasSculkNearby)) {
            reward += 10; // Reward for having sound-dampening items in inventory
        }



        // 11. Hunger and Oxygen logic (unchanged for now)
        if (botHungerLevel <= 6) {
            reward -= 10; // Penalize low hunger
        } else if (botHungerLevel > 16) {
            reward += 5; // Reward high hunger levels
        }

        if (botOxygenLevel < 60) {
            reward -= 20; // Penalize low oxygen levels
        } else if (botOxygenLevel >= 150) {
            reward += 10; // Reward full or more than half full oxygen levels
        }


        return reward;
    }



    // Calculate Q-value for the given state-action-nextState transition
    public double calculateQValue(State initialState, Action action, double reward, State nextState, QTable qTable) {
        // Create the StateActionPair for the initial state and action
        StateActionPair pair = new StateActionPair(initialState, action);

        // Retrieve the existing QEntry or use a default value
        QEntry existingEntry = qTable.getEntry(pair);
        double oldQValue = (existingEntry != null) ? existingEntry.getQValue() : 0.0;

        // Get the maximum Q-value for the next state's possible actions
        double maxNextQValue = qTable.getTable().entrySet().stream()
                .filter(e -> State.isStateConsistent(e.getKey().getState(), nextState))
                .mapToDouble(e -> e.getValue().getQValue())
                .max()
                .orElse(0.0);

        // Q-learning formula
        double newQValue = oldQValue + ALPHA * (reward + GAMMA * maxNextQValue - oldQValue);

        System.out.println("Calculated Q-value for state-action pair: " + pair +
                " with reward: " + reward +
                ", new Q-value: " + newQValue);

        return newQValue; // Return the computed Q-value
    }



}
