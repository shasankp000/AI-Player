package net.shasankp000.GameAI;

import java.util.*;
import java.util.stream.Collectors;


import net.minecraft.item.ItemStack;
import net.shasankp000.Database.QEntry;
import net.shasankp000.Database.QTable;
import net.shasankp000.Database.StateActionPair;
import net.shasankp000.Entity.EntityDetails;
import net.shasankp000.GameAI.StateActions.Action;
import net.shasankp000.PlayerUtils.ResourceEvaluator;
import net.shasankp000.PlayerUtils.ThreatDetector;
import net.shasankp000.PlayerUtils.ProjectileDefenseUtils;
import net.shasankp000.PlayerUtils.MobThreatEvaluator;
import net.shasankp000.Commands.modCommandRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RLAgent {
    public static final Logger LOGGER = LoggerFactory.getLogger("rl-agent");
    private static final double ALPHA = 0.1;  // Learning rate
    private static final double GAMMA = 0.9;  // Discount factor
    public double epsilon;
    private static final double MIN_EPSILON = 0.1; // Minimum exploration rate
    private static final double EPSILON_DECAY_RATE = 0.99; // Decay rate for epsilon

    // Q-table: Maps each state to a map of actions and their Q-values
    private final QTable qTable;
    private final Random random;

    // State transition tracking for learning from past mistakes
    private final StateTransition.TransitionHistory transitionHistory;
    private State lastState = null;
    private Action lastAction = null;


    /**
     * Default constructor with epsilon initialized to 1.0.
     */

    public RLAgent() {
        this.epsilon = 1.0; // Initial exploration rate
        qTable = new QTable();
        random = new Random();
        transitionHistory = new StateTransition.TransitionHistory(100); // Track last 100 transitions
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
        transitionHistory = new StateTransition.TransitionHistory(100); // Track last 100 transitions
    }

    // Choose action based on epsilon-greedy strategy and risk appetite
    public Map<Action, Double> chooseAction(State state, double riskAppetite, Map<Action, Double> riskMap, StateTransition.TransitionHistory history) {
        double selectedRandomValue = random.nextDouble();
        Action chosenAction;
        double chosenRiskValue = 0.0;

        Map<Action, Double> podMap = state.getPodMap();
        double podThreshold = 0.7;

        double riskThreshold = riskAppetite >= 0.7 ? 5.0
                : riskAppetite >= 0.3 ? 2.5
                : 1.0;

        Map<Action, Double> viableActions = riskMap.entrySet().stream()
                .filter(entry -> podMap.getOrDefault(entry.getKey(), 0.0) < podThreshold)
                .filter(entry -> entry.getValue() < riskThreshold)
                .filter(entry -> entry.getValue() < 0.0)
                .filter(entry -> entry.getValue() != 0.0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // ENHANCED: Filter out actions that match known death patterns
        Map<Action, Double> safeViableActions = new HashMap<>();
        if (history != null) {
            for (Map.Entry<Action, Double> entry : viableActions.entrySet()) {
                if (!history.matchesDeathPattern(state, entry.getKey())) {
                    safeViableActions.put(entry.getKey(), entry.getValue());
                } else {
                    System.out.println("❌ Filtered out " + entry.getKey() + " - matches death pattern!");
                }
            }
        } else {
            safeViableActions = viableActions;
        }

        Map<Action, Double> actionsToConsider = safeViableActions.isEmpty() ? viableActions : safeViableActions;

        if (selectedRandomValue < epsilon) {
            System.out.println("Exploring with epsilon: " + epsilon);
            if (!actionsToConsider.isEmpty()) {
                List<Action> actionList = new ArrayList<>(actionsToConsider.keySet());
                chosenAction = actionList.get(random.nextInt(actionList.size()));
                chosenRiskValue = actionsToConsider.get(chosenAction);
            } else {
                chosenAction = Action.STAY;
            }
        } else {
            // Pass history to play mode choice as well
            chosenAction = chooseActionPlayMode(state, qTable, riskMap, "chooseAction", history);
            if (chosenAction.equals(Action.STAY)) {
                System.out.println("No suitable action found within the Qtable");
                chosenAction = viableActions.entrySet().stream()
                        .min(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse(Action.STAY);
                chosenRiskValue = viableActions.getOrDefault(chosenAction, 0.0);
            }
        }

        Map<Action, Double> result = new HashMap<>();
        result.put(chosenAction, chosenRiskValue);
        return result;
    }


    // REPLACE YOUR EXISTING chooseActionPlayMode METHOD WITH THIS:
    public Action chooseActionPlayMode(State currentState, QTable qTable, Map<Action, Double> riskMap, String triggeredFrom, StateTransition.TransitionHistory history) {
        Action bestAction = null;
        double bestQValue = Double.NEGATIVE_INFINITY;

        for (Map.Entry<StateActionPair, QEntry> entry : qTable.getTable().entrySet()) {
            StateActionPair pair = entry.getKey();
            QEntry qEntry = entry.getValue();

            if (State.isStateConsistent(pair.getState(), currentState)) {
                State nextState = entry.getValue().getNextState();
                if (!nextState.isOptimal()) {
                    continue;
                }

                double qValue = qEntry.getQValue();
                double pod = getPodForAction(nextState.getActionTaken(), qTable);

                // Check for death patterns
                boolean isDeathRisk = (history != null) && history.matchesDeathPattern(currentState, pair.getAction());

                if (pod < 0.7 && qValue > bestQValue && !isDeathRisk) {
                    bestQValue = qValue;
                    bestAction = pair.getAction();
                }
            }
        }

        if (bestAction == null) {
            if ("detectAndReactPlayMode".equals(triggeredFrom)) {
                return determineViableAction(currentState, riskMap);
            }
            return Action.STAY;
        }

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
    public Map<Action, Double> calculateRisk(State currentState, List<Action> possibleActions, net.minecraft.server.network.ServerPlayerEntity bot) {
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

        // Increase risk for all actions if in a dangerous structure
        double structureRiskModifier = currentState.isInDangerousStructure() ? 20.0 : 0.0;
        if (structureRiskModifier > 0.0) {
            System.out.println("Bot is in a dangerous structure! Applying +20.0 risk modifier to all actions.");
        }

        // Calculate projectile threat risk (applies to all actions)
        // Detect incoming projectiles and calculate threat level
        double projectileThreatRisk = 0.0;
        List<ProjectileDefenseUtils.IncomingProjectile> incomingProjectiles =
            ProjectileDefenseUtils.detectIncomingProjectilesSafe(bot);

        if (!incomingProjectiles.isEmpty()) {
            projectileThreatRisk = ProjectileDefenseUtils.calculateProjectileThreatRisk(incomingProjectiles);
            System.out.println("⚠ Projectile threat detected! Total risk: " + projectileThreatRisk);
        }

        // Evaluate special mob threats (creepers, etc.) with phase-aware risk analysis
        Map<String, MobThreatEvaluator.MobThreatInfo> mobThreatMap = new HashMap<>();
        double creeperThreatBonus = 0.0;

        // Collect all nearby entities for evaluation
        List<net.minecraft.entity.Entity> nearbyActualEntities = new ArrayList<>();
        for (EntityDetails entityDetails : nearbyEntities) {
            if (entityDetails.isHostile()) {
                // Get actual entity reference - use proper entity lookup
                net.minecraft.entity.Entity actualEntity = bot.getWorld().getEntityById(entityDetails.hashCode());
                if (actualEntity != null) {
                    nearbyActualEntities.add(actualEntity);

                    MobThreatEvaluator.MobThreatInfo mobThreat = MobThreatEvaluator.evaluateMobThreat(actualEntity, bot);
                    if (mobThreat != null) {
                        mobThreatMap.put(actualEntity.getUuidAsString(), mobThreat);

                        // Track creeper-specific phase threats
                        if (mobThreat.creeperPhase != null) {
                            creeperThreatBonus += mobThreat.phaseThreat;
                        }
                    }
                }
            }
        }

        // Use utility method to check for critical creeper threats
        boolean hasCriticalCreeperThreat = MobThreatEvaluator.hasCriticalCreeperThreat(nearbyActualEntities, bot);

        if (creeperThreatBonus > 0) {
            System.out.println("🧨 Creeper threat bonus applied: +" + String.format("%.1f", creeperThreatBonus) + " risk");
            if (hasCriticalCreeperThreat) {
                System.out.println("🔥 CRITICAL CREEPER EXPLOSION IMMINENT!");
            }
        }

        for (Action action : possibleActions) {
            double risk = 0.0;

            try {

            // Evaluate risk factors based on the current state and the action
            switch (action) {

                case MOVE_FORWARD:
                    for (EntityDetails entity : hostileEntities) {
                        double distance = Math.hypot(entity.getX() - currentState.getBotX(), entity.getZ() - currentState.getBotZ());
                        double threat = getEntityRisk(entity, currentState.getBotX(), currentState.getBotY(), currentState.getBotZ());
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
                        double threat = getEntityRisk(entity, currentState.getBotX(), currentState.getBotY(), currentState.getBotZ());
                        if ("behind".equals(entity.getDirectionToBot())) {
                            risk += distance == 0 ? 100.0 : (1.0 / distance) * 6.0; // Higher penalty for retreating into an enemy
                            risk +=threat;
                        } else if ("front".equals(entity.getDirectionToBot())) {
                            risk -= (1.0 / Math.max(distance, 1)) * 3.0; // Slight reward for retreating from enemies in front
                            risk +=threat;
                        }
                    }

                    // Bonus for moving when projectiles incoming (helps dodge)
                    if (projectileThreatRisk > 0) {
                        risk -= projectileThreatRisk * 0.3; // Reduce risk by 30% of threat
                        System.out.println("MOVE_BACKWARD: Projectile bonus = " + (-projectileThreatRisk * 0.3));
                    }
                    break;

                case TURN_LEFT:
                    for (EntityDetails entity : hostileEntities) {
                        double distance = Math.hypot(entity.getX() - currentState.getBotX(), entity.getZ() - currentState.getBotZ());
                        double threat = getEntityRisk(entity, currentState.getBotX(), currentState.getBotY(), currentState.getBotZ());
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
                        double threat = getEntityRisk(entity, currentState.getBotX(), currentState.getBotY(), currentState.getBotZ());
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

                    // CRITICAL: Staying still with incoming projectiles is extremely dangerous
                    if (projectileThreatRisk > 0) {
                        risk += projectileThreatRisk * 2.0; // Double the projectile risk for staying still
                        System.out.println("STAY action: Projectile risk penalty = " + (projectileThreatRisk * 2.0));
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

                    // Strong bonus for sprinting when projectiles incoming
                    if (projectileThreatRisk > 0) {
                        risk -= projectileThreatRisk * 0.4; // Sprinting is very effective for dodging
                        System.out.println("SPRINT: Projectile bonus = " + (-projectileThreatRisk * 0.4));
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

                    // Stopping with projectiles incoming is dangerous
                    if (projectileThreatRisk > 0) {
                        risk += projectileThreatRisk * 1.5; // Significant penalty
                        System.out.println("STOP_MOVING action: Projectile risk penalty = " + (projectileThreatRisk * 1.5));
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
                                double entityRisk = getEntityRisk(entity, currentState.getBotX(), currentState.getBotY(), currentState.getBotZ());

                                System.out.println("Entity risk: " + entityRisk + "for " + entity);

                                if (entityRisk == 0.0) { // unknown entity or zombie/husk

                                    System.out.println("Set unknown entity/zombie/husk risk to -10 by default");

                                    entityRisk = -10; // set this purposely so that the action's value doesn't return 0.0;
                                }

                                // AGGRESSIVE: Strongly reduce risk if bot has weapons
                                if (currentState.getSelectedItem().contains("Sword") || currentState.getSelectedItem().contains("Axe") ||
                                        currentState.getSelectedItem().contains("Bow") || currentState.getSelectedItem().contains("Trident")) {
                                    entityRisk *= 0.3; // Reduce risk by 70% when equipped for attack
                                    entityRisk -= 15.0; // Additional flat reduction
                                }

                                // Amplify risk if the bot's health is critically low
                                // At low HP, survival must override equipment bonuses
                                if (currentState.getBotHealth() <= 5) {
                                    entityRisk *= 3.0; // TRIPLE risk for attacking with critical health
                                    entityRisk += 50.0; // Massive flat penalty - attacking is suicidal
                                    System.out.println("ATTACK: CRITICAL HP - attacking is extremely dangerous!");
                                } else if (currentState.getBotHealth() <= 8) {
                                    entityRisk *= 2.0; // Double risk for very low health
                                    entityRisk += 30.0;
                                    System.out.println("ATTACK: Very low HP - attacking is very risky!");
                                } else if (currentState.getBotHealth() < 12) {
                                    entityRisk *= 1.5; // Increased penalty
                                    entityRisk += 15.0;
                                    System.out.println("ATTACK: Low HP - attacking is risky!");
                                }

                                return entityRisk;
                            })
                            .sum();

                    // Add a penalty for attacking when outnumbered
                    if (nearbyHostileCount > 3) {
                        totalRisk += 10.0; // Add extra risk for multiple enemies
                    }

                    // ARMOR BONUS: Check for armor pieces and reduce risk accordingly
                    Map<String, String> armorItems = currentState.getArmorItems();
                    int armorPieceCount = 0;
                    int strongArmorCount = 0; // Count of diamond/netherite/iron pieces

                    for (Map.Entry<String, String> armorEntry : armorItems.entrySet()) {
                        String armorPiece = armorEntry.getValue();
                        if (armorPiece != null && !armorPiece.contains("air")) {
                            armorPieceCount++;
                            // Check if it's strong armor
                            if (armorPiece.contains("diamond") || armorPiece.contains("netherite") || armorPiece.contains("iron")) {
                                strongArmorCount++;
                            }
                        }
                    }

                    // Calculate HP-based scaling factor for attack bonuses
                    double hpScaling = 1.0;
                    if (currentState.getBotHealth() <= 5) {
                        hpScaling = 0.1; // Drastically reduce attack bonuses at critical HP
                        System.out.println("ATTACK: Critical HP (≤5) - combat bonuses heavily suppressed (10%)");
                    } else if (currentState.getBotHealth() <= 8) {
                        hpScaling = 0.3; // Significantly reduce at very low HP
                        System.out.println("ATTACK: Very low HP (≤8) - combat bonuses suppressed (30%)");
                    } else if (currentState.getBotHealth() <= 12) {
                        hpScaling = 0.6; // Moderately reduce at low HP
                        System.out.println("ATTACK: Low HP (≤12) - combat bonuses reduced (60%)");
                    }

                    // Armor bonuses (scaled by HP)
                    if (strongArmorCount > 0) {
                        // Strong armor reduces risk significantly
                        double armorBonus = (strongArmorCount * 15.0) * hpScaling;
                        totalRisk -= armorBonus;
                        System.out.println("Strong armor bonus: " + strongArmorCount + " pieces, -" +
                            String.format("%.1f", armorBonus) + " risk (scaled by HP: " +
                            String.format("%.0f%%)", hpScaling * 100));
                    } else if (armorPieceCount > 0) {
                        // Any armor is better than none
                        double armorBonus = (armorPieceCount * 5.0) * hpScaling;
                        totalRisk -= armorBonus;
                        System.out.println("Basic armor bonus: " + armorPieceCount + " pieces, -" +
                            String.format("%.1f", armorBonus) + " risk (scaled by HP: " +
                            String.format("%.0f%%)", hpScaling * 100));
                    }

                    // Full armor set bonus (4 pieces) - scaled
                    if (armorPieceCount >= 4) {
                        double setBonus = 10.0 * hpScaling;
                        totalRisk -= setBonus;
                        System.out.println("Full armor set bonus: -" + String.format("%.1f", setBonus) +
                            " risk (scaled by HP: " + String.format("%.0f%%)", hpScaling * 100));
                    }

                    // Add reward for single weak entities (scaled by HP)
                    if (nearbyHostileCount == 1) {
                        double singleEnemyBonus = 25.0 * hpScaling;
                        totalRisk -= singleEnemyBonus;
                        System.out.println("Single enemy bonus: -" + String.format("%.1f", singleEnemyBonus) +
                            " risk (scaled by HP: " + String.format("%.0f%%)", hpScaling * 100));
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

                case SHOOT_ARROW:
                    // Check if bot has ranged weapon and arrows using RangedWeaponUtils (reliable!)
                    boolean hasBowOrCrossbow = net.shasankp000.PlayerUtils.RangedWeaponUtils.hasBowOrCrossbow(bot);
                    boolean hasArrows = net.shasankp000.PlayerUtils.RangedWeaponUtils.hasArrows(bot);

                    if (!hasBowOrCrossbow || !hasArrows) {
                        risk += 10.0; // High penalty if can't shoot
                        System.out.println("Cannot shoot - missing bow/crossbow or arrows. Risk increased.");
                        break;
                    }

                    LOGGER.debug("✓ Bot CAN shoot! Bow/Crossbow={}, Arrows={}", hasBowOrCrossbow, hasArrows);

                    // Calculate ranged combat viability
                    double shootingRisk = -10.0; // Base bonus for having ranged capability

                    // Process ALL hostile entities and their cumulative risk
                    for (EntityDetails entity : hostileEntities) {
                        double distance = Math.hypot(entity.getX() - currentState.getBotX(),
                                                     entity.getZ() - currentState.getBotZ());
                        String entityType = entity.getName();

                        // Get base entity risk using the unified risk system
                        double baseEntityRisk = getEntityRisk(entity, currentState.getBotX(), currentState.getBotY(), currentState.getBotZ());

                        // Apply distance-based modifiers for ranged combat effectiveness
                        double distanceModifier = 0.0;

                        // Optimal shooting range: 5-20 blocks for most mobs
                        if (distance >= 5.0 && distance <= 20.0) {
                            // Good shooting range - reduce risk proportional to threat level
                            distanceModifier = -(baseEntityRisk * 0.5); // 50% risk reduction in optimal range
                        } else if (distance < 5.0) {
                            // Too close - harder to shoot, melee might be better
                            distanceModifier = (baseEntityRisk * 0.3); // 30% risk increase when too close
                        } else if (distance > 20.0) {
                            // Too far - difficult shot, low priority
                            distanceModifier = (baseEntityRisk * 0.2); // 20% risk increase when far
                        }

                        // ENHANCED: Check for special mob threat phases (e.g., creeper explosion state)
                        String entityId = String.valueOf(entity.hashCode()); // Simplified ID lookup
                        MobThreatEvaluator.MobThreatInfo mobThreat = null;
                        for (MobThreatEvaluator.MobThreatInfo threat : mobThreatMap.values()) {
                            if (threat.mobType.equals(entityType)) {
                                mobThreat = threat;
                                break;
                            }
                        }

                        // Special case modifiers for specific mob types (on top of base risk)
                        if ("Creeper".equals(entityType)) {
                            // Phase-aware creeper threat handling
                            if (mobThreat != null && mobThreat.creeperPhase != null) {
                                switch (mobThreat.creeperPhase) {
                                    case IDLE:
                                        // Normal creeper - standard ranged priority
                                        if (distance >= 5.0 && distance <= 20.0) {
                                            distanceModifier -= 30.0; // Good distance to shoot
                                        }
                                        break;
                                    case IGNITED:
                                        // FUSE STARTED - HIGH PRIORITY TO KILL OR CREATE DISTANCE
                                        if (distance >= 5.0 && distance <= 20.0) {
                                            distanceModifier -= 60.0; // VERY high incentive to shoot ignited creeper
                                            System.out.println("🔥 IGNITED CREEPER - shooting highly encouraged!");
                                        } else if (distance < 5.0) {
                                            distanceModifier += 70.0; // Too close - EVADE instead!
                                            System.out.println("⚠ IGNITED CREEPER TOO CLOSE - evading better than shooting!");
                                        }
                                        break;
                                    case CRITICAL:
                                        // ABOUT TO EXPLODE - Too late to shoot effectively
                                        if (distance < 5.0) {
                                            distanceModifier += 100.0; // MASSIVE penalty - BLOCK/EVADE NOW!
                                            System.out.println("🚨 CRITICAL CREEPER EXPLOSION - SHOOTING TOO RISKY!");
                                        } else {
                                            distanceModifier -= 50.0; // If far enough, still worth shooting
                                        }
                                        break;
                                }
                            } else {
                                // Fallback if no mob threat data
                                if (distance >= 5.0 && distance <= 20.0) {
                                    distanceModifier -= 30.0;
                                } else if (distance < 5.0) {
                                    distanceModifier += 40.0;
                                }
                            }
                        } else if ("Skeleton".equals(entityType) || "Stray".equals(entityType) ||
                                   "Bogged".equals(entityType) || "Pillager".equals(entityType)) {
                            if (distance >= 8.0 && distance <= 20.0) {
                                distanceModifier -= 20.0; // Extra incentive to counter-snipe ranged enemies
                            }
                        } else if ("Witch".equals(entityType) || "Blaze".equals(entityType)) {
                            if (distance >= 10.0 && distance <= 20.0) {
                                distanceModifier -= 25.0; // High priority to keep dangerous mobs at range
                            }
                        }

                        // Apply both base risk and distance modifier
                        double entityShootingRisk = baseEntityRisk + distanceModifier;

                        // Add creeper phase threat if applicable
                        if (mobThreat != null && mobThreat.creeperPhase != null) {
                            entityShootingRisk += mobThreat.phaseThreat * 0.5; // 50% of phase threat affects shooting
                        }

                        shootingRisk += entityShootingRisk;

                        System.out.println("SHOOT_ARROW: " + entityType + " at " + String.format("%.1f", distance) +
                                         "m - Base risk: " + String.format("%.1f", baseEntityRisk) +
                                         ", Distance modifier: " + String.format("%.1f", distanceModifier) +
                                         ", Total entity contribution: " + String.format("%.1f", entityShootingRisk));
                    }

                    // Penalize if health is low (shooting takes time, less safe)
                    // SCALED PENALTIES: More severe at lower HP
                    if (currentState.getBotHealth() <= 5) {
                        shootingRisk += 60.0; // Critical HP - shooting is VERY dangerous (increased from 40)
                        System.out.println("SHOOT_ARROW: CRITICAL health (≤5) penalty +60 risk - EVADE INSTEAD!");
                    } else if (currentState.getBotHealth() <= 8) {
                        shootingRisk += 35.0; // Very low HP (increased from 20)
                        System.out.println("SHOOT_ARROW: Very low health (≤8) penalty +35 risk");
                    } else if (currentState.getBotHealth() <= 12) {
                        shootingRisk += 15.0; // Low HP
                        System.out.println("SHOOT_ARROW: Low health (≤12) penalty +15 risk");
                    }

                    // Penalize if outnumbered and close (melee might be needed)
                    if (nearbyHostileCount > 3 && hostileEntities.stream()
                            .anyMatch(e -> Math.hypot(e.getX() - currentState.getBotX(),
                                                      e.getZ() - currentState.getBotZ()) < 5.0)) {
                        shootingRisk += 20.0;
                        System.out.println("SHOOT_ARROW: Outnumbered + close enemies penalty +20 risk");
                    }

                    // Bonus if using crossbow (instant shot)
                    if (currentState.getSelectedItem().contains("Crossbow")) {
                        shootingRisk -= 5.0;
                        System.out.println("SHOOT_ARROW: Crossbow bonus -5 risk");
                    }

                    // ARMOR BONUS: Armor makes ranged combat safer (can tank hits while aiming)
                    // BUT: Scale by HP - at low HP, armor doesn't help as much
                    int shootArmorCount = 0;
                    int shootStrongArmorCount = 0;

                    for (Map.Entry<String, String> armorEntry : currentState.getArmorItems().entrySet()) {
                        String armorPiece = armorEntry.getValue();
                        if (armorPiece != null && !armorPiece.contains("air")) {
                            shootArmorCount++;
                            if (armorPiece.contains("diamond") || armorPiece.contains("netherite") || armorPiece.contains("iron")) {
                                shootStrongArmorCount++;
                            }
                        }
                    }

                    // Calculate HP scaling for shooting armor bonuses
                    double shootHpScaling = 1.0;
                    if (currentState.getBotHealth() <= 5) {
                        shootHpScaling = 0.1; // Drastically reduce bonuses at critical HP
                    } else if (currentState.getBotHealth() <= 8) {
                        shootHpScaling = 0.3; // Significantly reduce at very low HP
                    } else if (currentState.getBotHealth() <= 12) {
                        shootHpScaling = 0.6; // Moderately reduce at low HP
                    }

                    if (shootStrongArmorCount > 0) {
                        // Strong armor = can safely take time to aim
                        double armorBonus = (shootStrongArmorCount * 8.0) * shootHpScaling;
                        shootingRisk -= armorBonus;
                        System.out.println("SHOOT_ARROW: Strong armor bonus (" + shootStrongArmorCount +
                            " pieces) = -" + String.format("%.1f", armorBonus) + " risk (HP scaled: " +
                            String.format("%.0f%%)", shootHpScaling * 100));
                    } else if (shootArmorCount > 0) {
                        // Any armor helps
                        double armorBonus = (shootArmorCount * 3.0) * shootHpScaling;
                        shootingRisk -= armorBonus;
                        System.out.println("SHOOT_ARROW: Basic armor bonus (" + shootArmorCount +
                            " pieces) = -" + String.format("%.1f", armorBonus) + " risk (HP scaled: " +
                            String.format("%.0f%%)", shootHpScaling * 100));
                    }

                    // Full armor set bonus for ranged combat (scaled)
                    if (shootArmorCount >= 4) {
                        double setBonus = 5.0 * shootHpScaling;
                        shootingRisk -= setBonus;
                        System.out.println("SHOOT_ARROW: Full armor set bonus = -" +
                            String.format("%.1f", setBonus) + " risk (HP scaled: " +
                            String.format("%.0f%%)", shootHpScaling * 100));
                    }

                    // WEAPON COMBO BONUS: Having both ranged and melee weapon = versatile fighter
                    boolean hasMeleeWeapon = currentState.getSelectedItem().contains("Sword") ||
                                            currentState.getSelectedItem().contains("Axe") ||
                                            currentState.getHotBarItems().stream().anyMatch(item ->
                                                item != null && (item.contains("Sword") || item.contains("Axe")));

                    if (hasMeleeWeapon) {
                        // Can switch to melee if enemies get close = safer ranged combat
                        shootingRisk -= 10.0;
                        System.out.println("SHOOT_ARROW: Melee backup weapon bonus = -10 risk");
                    }

                    risk += shootingRisk;
                    System.out.println("SHOOT_ARROW risk: " + shootingRisk);
                    break;

                case EVADE:
                    // Evasion risk calculation - considers all threats (projectiles, melee, environment)
                    double evasionRisk = 0.0;

                    // Calculate entity threat risk from nearby hostile entities
                    double entityThreatRisk = 0.0;
                    for (EntityDetails entityDetails : currentState.getNearbyEntities()) {
                        if (entityDetails.isHostile()) {
                            double entityRisk = getEntityRisk(entityDetails, currentState.getBotX(), currentState.getBotY(), currentState.getBotZ());
                            if (entityRisk > 0) {
                                entityThreatRisk += entityRisk;
                            }
                        }
                    }

                    // CRITICAL: Add creeper phase threat bonus to evasion calculations
                    // Ignited/Critical creepers STRONGLY encourage evasion
                    double creeperEvasionBonus = 0.0;
                    if (creeperThreatBonus > 0) {
                        creeperEvasionBonus = creeperThreatBonus * 0.8; // 80% of creeper threat encourages evasion
                        entityThreatRisk += creeperEvasionBonus;
                        if (hasCriticalCreeperThreat) {
                            System.out.println("EVADE: CRITICAL CREEPER - evasion STRONGLY favored! (+" +
                                String.format("%.1f", creeperEvasionBonus) + " threat)");
                        }
                    }

                    // Calculate total threat level
                    double totalThreat = projectileThreatRisk + entityThreatRisk;
                    double botHealth = currentState.getBotHealth();
                    boolean hasWeapons = (currentState.getSelectedItem().contains("Bow") ||
                                         currentState.getSelectedItem().contains("Crossbow") ||
                                         currentState.getSelectedItem().contains("Sword")) &&
                                         (currentState.getHotBarItems().stream()
                                             .anyMatch(item -> item.toLowerCase().contains("arrow")) ||
                                          currentState.getSelectedItem().contains("Sword"));

                    if (totalThreat > 0 || botHealth < 10.0) {
                        // Base evasion calculation
                        evasionRisk = -(totalThreat * 0.3); // Only 30% reduction (was 70%)

                        // Health-based modifier - only encourage evasion at critical health
                        if (botHealth < 5.0) {
                            evasionRisk -= 40.0; // Critical HP - strongly encourage evasion
                            System.out.println("EVADE: Critical HP! Strong evasion incentive.");
                        } else if (botHealth < 10.0) {
                            evasionRisk -= 10.0; // Low HP - slight evasion bonus
                            System.out.println("EVADE: Low HP, slight evasion encouraged.");
                        } else {
                            // GOOD HEALTH: Discourage evasion, prefer combat
                            evasionRisk += 10.0; // Add risk penalty for healthy bot evading
                            System.out.println("EVADE: Healthy bot - evasion less favorable.");
                        }

                        // Distance to threat modifier - scale by HP
                        if (currentState.getDistanceToHostileEntity() < 5.0) {
                            double proximityBonus = -15.0;
                            // At low HP, being close = MUCH more reason to evade
                            if (botHealth <= 8.0) {
                                proximityBonus -= 15.0; // Extra -15 at critical HP
                                System.out.println("EVADE: CRITICAL HP + close enemy - evasion STRONGLY encouraged!");
                            } else if (botHealth <= 12.0) {
                                proximityBonus -= 10.0; // Extra -10 at low HP
                                System.out.println("EVADE: Low HP + close enemy - evasion highly encouraged!");
                            }
                            evasionRisk += proximityBonus;
                        }

                        // ARMOR CONSIDERATION: Check armor for evasion decision
                        // Recount armor (scope issue - variables from ATTACK case not accessible here)
                        int evadeArmorCount = 0;
                        int evadeStrongArmorCount = 0;

                        for (Map.Entry<String, String> armorEntry : currentState.getArmorItems().entrySet()) {
                            String armorPiece = armorEntry.getValue();
                            if (armorPiece != null && !armorPiece.contains("air")) {
                                evadeArmorCount++;
                                if (armorPiece.contains("diamond") || armorPiece.contains("netherite") || armorPiece.contains("iron")) {
                                    evadeStrongArmorCount++;
                                }
                            }
                        }

                        // CRITICAL HP THRESHOLD: At very low HP, FORCE evasion regardless of equipment
                        if (botHealth <= 5.0) {
                            // Immediate death risk - evasion is MANDATORY
                            evasionRisk -= 80.0; // Massive bonus to evade
                            System.out.println("EVADE: ☠ CRITICAL HP (≤5) - SURVIVAL MODE! Equipment irrelevant!");
                        } else if (botHealth <= 8.0) {
                            // Very low HP - evasion heavily favored
                            evasionRisk -= 50.0; // Strong bonus to evade
                            System.out.println("EVADE: ⚠ VERY LOW HP (≤8) - evasion heavily prioritized!");

                            // At critical HP, equipment bonuses are drastically reduced
                            if (hasWeapons) {
                                evasionRisk += 5.0; // Tiny penalty for being armed
                                System.out.println("EVADE: Armed but critical HP - slight combat consideration.");
                            }
                        } else if (botHealth <= 12.0) {
                            // Low HP - favor evasion but consider equipment
                            evasionRisk -= 25.0; // Moderate bonus to evade
                            System.out.println("EVADE: Low HP (≤12) - evasion encouraged.");

                            // Apply reduced equipment bonuses
                            if (hasWeapons && evadeStrongArmorCount > 2) {
                                evasionRisk += 15.0; // Reduced penalty for being well-equipped
                                System.out.println("EVADE: Well-equipped but low HP - minor combat preference.");
                            } else if (hasWeapons) {
                                evasionRisk += 8.0; // Small penalty for being armed
                                System.out.println("EVADE: Armed but low HP - slight combat consideration.");
                            }
                        } else if (hasWeapons && botHealth > 12.0) {
                            // Moderate to good HP with weapons - apply normal equipment logic
                            if (botHealth > 15.0) {
                                // Healthy + armed = STRONGLY prefer combat
                                evasionRisk += 40.0; // Heavy penalty for evading when strong
                                System.out.println("EVADE: Well-armed & healthy - combat strongly preferred!");
                            } else {
                                // Armed but moderate health = still prefer combat
                                evasionRisk += 25.0; // Moderate penalty
                                System.out.println("EVADE: Armed with moderate health - combat preferred.");
                            }

                            // ARMOR + WEAPON COMBO: Extra penalty for being well-equipped
                            double armorBonus = 0.0;
                            if (evadeStrongArmorCount > 2) {
                                // 3+ strong armor pieces + weapons = tank mode
                                armorBonus = 30.0;
                                System.out.println("EVADE: Well-armored & armed - TANK MODE, combat heavily preferred!");
                            } else if (evadeStrongArmorCount > 0) {
                                // Some armor + weapons = favor combat
                                armorBonus = evadeStrongArmorCount * 10.0;
                                System.out.println("EVADE: Armored & armed - combat preferred (" + evadeStrongArmorCount + " armor pieces)");
                            }
                            evasionRisk += armorBonus;
                        } else {
                            // No weapons = evasion is good choice
                            evasionRisk -= 10.0;
                            System.out.println("EVADE: Unarmed - evasion is smart choice.");

                            // Even with armor, no weapons = still evade but less urgently
                            if (evadeArmorCount > 0) {
                                evasionRisk += (evadeArmorCount * 5.0); // Slight penalty for having armor
                                System.out.println("EVADE: Unarmed but armored - evasion less urgent.");
                            }
                        }

                        // Multiple enemies = evasion more acceptable
                        int hostileCount = (int) currentState.getNearbyEntities().stream()
                            .filter(e -> e.isHostile()).count();
                        if (hostileCount > 2) {
                            evasionRisk -= 10.0; // Reduce penalty when outnumbered
                            System.out.println("EVADE: Outnumbered (" + hostileCount + " enemies) - evasion more acceptable.");
                        }

                        System.out.println("EVADE: Threat detected (risk: " + String.format("%.1f", totalThreat) + "), evasion encouraged.");
                    } else {
                        // No threat = high evasion risk (pointless action)
                        evasionRisk = 15.0;
                        System.out.println("EVADE: No threats detected, action is pointless.");
                    }

                    risk += evasionRisk;
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

                // === PROJECTILE DEFENSE MODIFIERS ===
                // If there are incoming projectiles detected, adjust risk for certain actions
                if (projectileThreatRisk > 30.0) {
                    switch (action) {
                        case STAY:
                            // Standing still is very dangerous with incoming projectiles
                            risk += projectileThreatRisk * 1.5;
                            System.out.println("Projectile threat! STAY action risk increased by: " + (projectileThreatRisk * 1.5));
                            break;

                        case MOVE_FORWARD:
                        case MOVE_BACKWARD:
                            // Moving might help dodge, but not guaranteed
                            risk += projectileThreatRisk * 0.3;
                            System.out.println("Projectile threat! Movement action risk increased by: " + (projectileThreatRisk * 0.3));
                            break;

                        case TURN_LEFT:
                        case TURN_RIGHT:
                            // Turning could help dodge
                            risk -= 5.0;
                            System.out.println("Projectile threat! Turning favored, risk reduced by 5.0");
                            break;

                        case JUMP:
                            // Jumping is unpredictable for dodging
                            risk += projectileThreatRisk * 0.5;
                            break;

                        // Note: BLOCK and DODGE actions would be added here when implemented in StateActions
                        default:
                            break;
                    }
                }
                // === END PROJECTILE DEFENSE MODIFIERS ===

            }

            catch (Exception e) {
                System.out.println("Exception in risk calculation: " + e.getMessage());
            }

            // Normalize and cap the risk value
            risk += structureRiskModifier; // Add structure risk if present
            risk = Math.min(risk, 100.0); // Cap risk to 100.0 for balance
            riskMap.put(action, risk);
        }

        System.out.println("Final risk map: " + riskMap);

        return riskMap; // Return the map of actions and their associated risks

    }

    /**
     * Calculate entity threat/risk score based on entity type and distance
     * Public method for use by combat strategy systems
     */
    public static double getEntityRisk(EntityDetails entity, int botX, int botY, int botZ) {
        double baseThreat = 0.0;
        double distance = Math.hypot(entity.getX() - botX, entity.getZ() - botZ);

        // Report to debug manager if enabled
        if (net.shasankp000.Overlay.ThreatDebugManager.isDebugEnabled()) {
            // We'll update this after calculating the threat
        }

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
                if (distance <= 4.0) {
                    baseThreat += 10.0; // More dangerous when close
                    // Note: Pumpkin helmet check removed - static method has no state access
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
                baseThreat += 15.0; // Moderate threat, especially in larger sizes
                if (distance <= 2.0) {
                    baseThreat += 10.0; // Threat increases if close
                }
                break;

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

            case "Breeze":
                baseThreat += 35.0; // Breeze is a ranged attacker with wind charges
                if (distance <= 15.0) {
                    baseThreat += 20.0; // High threat within wind charge range (knockback is dangerous)
                }
                break;

            case "Bogged":
                baseThreat += 30.0; // Bogged shoots poison arrows, more dangerous than regular skeleton
                if (distance <= 10.0) {
                    baseThreat += 18.0; // Higher threat within arrow range due to poison effect
                }
                break;

            case "Piglin":
                baseThreat += 20.0; // Moderate threat
                if (distance <= 3.0) {
                    baseThreat += 10.0; // More dangerous if close
                }
                break;

            case "Silverfish":
                baseThreat += 15.0; // Low threat but can be dangerous in groups
                if (distance <= 1.0) {
                    baseThreat += 5.0; // Slightly higher threat if very close
                }
                break;

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

            // Add more mob types
            case "Zombie":
            case "Husk":
                baseThreat += 15.0; // Basic undead threat
                if (distance <= 2.0) {
                    baseThreat += 10.0; // Dangerous when in melee range
                }
                break;

            case "Zombified Piglin":
                baseThreat += 20.0; // Can be dangerous if aggro'd
                if (distance <= 2.0) {
                    baseThreat += 15.0;
                }
                break;

            case "Cave Spider":
                baseThreat += 25.0; // Poison effect makes it more dangerous than regular spider
                if (distance <= 2.0) {
                    baseThreat += 15.0;
                }
                break;

            case "Vex":
                baseThreat += 30.0; // Small, fast, can phase through walls
                if (distance <= 3.0) {
                    baseThreat += 20.0;
                }
                break;

            case "Stray":
                baseThreat += 28.0; // Similar to skeleton but with slowness arrows
                if (distance <= 10.0) {
                    baseThreat += 17.0;
                }
                break;

            case "Wither Skeleton":
                baseThreat += 45.0; // Wither effect is very dangerous
                if (distance <= 3.0) {
                    baseThreat += 25.0;
                }
                break;

            case "Hoglin":
                baseThreat += 35.0; // Aggressive and deals high damage
                if (distance <= 3.0) {
                    baseThreat += 20.0;
                }
                break;

            case "Zoglin":
                baseThreat += 40.0; // More dangerous than hoglin
                if (distance <= 3.0) {
                    baseThreat += 25.0;
                }
                break;

            case "Shulker":
                baseThreat += 35.0; // Levitation effect is dangerous
                if (distance <= 16.0) {
                    baseThreat += 20.0; // Can hit from far away
                }
                break;

            case "Illusioner":
                baseThreat += 50.0; // Rare mob with illusion abilities
                if (distance <= 10.0) {
                    baseThreat += 25.0;
                }
                break;

            default:
                baseThreat += 12.0; // Default for unknown hostile entities
                break;
        }


        // Scale down threat based on distance (further = lower threat)
        baseThreat /= Math.max(distance, 1.0); // Avoid division by zero

        // Report to debug manager if enabled (use a synthetic UUID based on entity details)
        if (net.shasankp000.Overlay.ThreatDebugManager.isDebugEnabled()) {
            // Create a pseudo-UUID from entity position and name for tracking
            java.util.UUID entityUUID = java.util.UUID.nameUUIDFromBytes(
                (entity.getName() + entity.getX() + entity.getY() + entity.getZ()).getBytes()
            );
            net.shasankp000.Overlay.ThreatDebugManager.updateThreat(
                entityUUID,
                entity.getName(),
                baseThreat,
                distance,
                "Evaluating"
            );
        }

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
            } else if (actionTaken == StateActions.Action.SHOOT_ARROW) {
                // Shooting at very close range is risky but acceptable
                boolean hasRangedTarget = hostileEntities.stream()
                        .anyMatch(e -> e.getName().equals("Creeper") ||
                                       e.getName().equals("Skeleton") ||
                                       e.getName().equals("Witch"));
                if (hasRangedTarget) {
                    reward += 10; // Good choice for dangerous close enemies
                } else {
                    reward += 5; // Acceptable but melee might be better
                }
            } else if (actionTaken == StateActions.Action.STAY) {
                reward -= 5; // Penalize for inaction
            }
        } else if (distanceToHostileEntity > 5 && distanceToHostileEntity <= 20) {
            // Optimal ranged combat distance
            if (actionTaken == StateActions.Action.SHOOT_ARROW) {
                boolean hasPriorityTarget = hostileEntities.stream()
                        .anyMatch(e -> e.getName().equals("Creeper") ||
                                       e.getName().equals("Skeleton") ||
                                       e.getName().equals("Pillager") ||
                                       e.getName().equals("Witch") ||
                                       e.getName().equals("Blaze"));
                if (hasPriorityTarget) {
                    reward += 25; // High reward for shooting priority targets at good range
                } else {
                    reward += 15; // Good ranged combat choice
                }
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



    // Cache for maxNextQValue to avoid repeated expensive state comparisons
    private static final java.util.Map<String, Double> maxQValueCache = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long CACHE_EXPIRY_MS = 500; // Cache expires after 500ms
    private static long lastCacheClear = System.currentTimeMillis();

    // Calculate Q-value for the given state-action-nextState transition
    public double calculateQValue(State initialState, Action action, double reward, State nextState, QTable qTable) {
        // Create the StateActionPair for the initial state and action
        StateActionPair pair = new StateActionPair(initialState, action);

        // Retrieve the existing QEntry or use a default value
        QEntry existingEntry = qTable.getEntry(pair);
        double oldQValue = (existingEntry != null) ? existingEntry.getQValue() : 0.0;

        // Clear cache if expired (prevents memory buildup and stale data)
        long now = System.currentTimeMillis();
        if (now - lastCacheClear > CACHE_EXPIRY_MS) {
            maxQValueCache.clear();
            lastCacheClear = now;
        }

        // Create a simple cache key based on next state's key features
        // Use only critical state features to avoid cache misses
        String cacheKey = String.format("%d_%d_%d_%s_%d",
                nextState.getBotX(),
                nextState.getBotY(),
                nextState.getBotZ(),
                nextState.getDimensionType(),
                nextState.getNearbyEntities().size()
        );

        // Check cache first to avoid expensive state comparisons
        Double cachedMax = maxQValueCache.get(cacheKey);
        double maxNextQValue;

        if (cachedMax != null) {
            maxNextQValue = cachedMax;
            // System.out.println("✓ Used cached maxQValue: " + maxNextQValue);
        } else {
            // Cache miss - perform expensive calculation
            // OPTIMIZATION: Instead of checking all 109 entries, limit to nearby states
            // by using spatial bucketing (only check states within ±2 buckets)
            int bucketRange = 2;
            maxNextQValue = qTable.getTable().entrySet().stream()
                    .filter(e -> {
                        State s = e.getKey().getState();
                        // Quick spatial filter BEFORE expensive isStateConsistent check
                        return Math.abs(s.getBotX() - nextState.getBotX()) <= bucketRange &&
                               Math.abs(s.getBotY() - nextState.getBotY()) <= bucketRange &&
                               Math.abs(s.getBotZ() - nextState.getBotZ()) <= bucketRange &&
                               s.getDimensionType().equals(nextState.getDimensionType());
                    })
                    .filter(e -> State.isStateConsistent(e.getKey().getState(), nextState))
                    .mapToDouble(e -> e.getValue().getQValue())
                    .max()
                    .orElse(0.0);

            // Cache the result
            maxQValueCache.put(cacheKey, maxNextQValue);
        }

        // Q-learning formula
        double newQValue = oldQValue + ALPHA * (reward + GAMMA * maxNextQValue - oldQValue);

        System.out.println("Calculated Q-value for state-action pair: " + pair +
                " with reward: " + reward +
                ", new Q-value: " + newQValue);

        // ENHANCED: Record this transition for learning from experience
        recordTransition(initialState, nextState, action, reward, nextState.getPodMap().getOrDefault(action, 0.0));

        return newQValue; // Return the computed Q-value
    }

    /**
     * Record a state transition for learning from experience
     */
    public void recordTransition(State fromState, State toState, Action action, double reward, double podValue) {
        StateTransition transition = new StateTransition(
            fromState, toState, action, reward, podValue, false, -1
        );
        transitionHistory.addTransition(transition);

        // Remember this state-action for the next transition
        lastState = fromState;
        lastAction = action;
    }

    /**
     * Mark recent transitions as leading to death and trigger learning
     */
    public void handleDeath(State finalState, int stepsBeforeDeath) {
        LOGGER.info("💀 Bot died! Analyzing last {} transitions to learn from mistakes...", stepsBeforeDeath);

        // Mark death sequence in history
        transitionHistory.markDeathSequence(stepsBeforeDeath);

        // Get death transitions for analysis
        List<StateTransition> deathSequence = transitionHistory.getRecentTransitions(stepsBeforeDeath);

        // Parallel processing: Learn from death pattern asynchronously
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            learnFromDeathSequence(deathSequence, finalState);
        }).exceptionally(e -> {
            LOGGER.error("Error learning from death sequence", e);
            return null;
        });
    }

    /**
     * Learn from a death sequence by adjusting Q-values based on what went wrong
     */
    private void learnFromDeathSequence(List<StateTransition> deathSequence, State finalState) {
        if (deathSequence.isEmpty()) return;

        LOGGER.info("🧠 Analyzing death sequence with {} transitions", deathSequence.size());

        // Analyze critical mistakes
        for (int sequenceIndex = 0; sequenceIndex < deathSequence.size(); sequenceIndex++) {
            StateTransition t = deathSequence.get(sequenceIndex);
            State state = t.getFromState();
            Action action = t.getAction();

            // Calculate how close to death this action was
            double deathProximity = (double) (deathSequence.size() - sequenceIndex) / deathSequence.size();

            // Critical mistake patterns to penalize heavily
            double additionalPenalty = 0.0;

            // Pattern 1: Low HP but chose aggressive action
            if (state.getBotHealth() <= 10) {
                if (action == Action.ATTACK || action == Action.SHOOT_ARROW) {
                    additionalPenalty -= 30.0 * deathProximity;
                    LOGGER.info("❌ Critical mistake: Attacked at {}HP (penalty: {})",
                        state.getBotHealth(), additionalPenalty);
                }
                // Should have evaded or used item
                if (action == Action.EVADE || action == Action.SPRINT) {
                    double bonus = 15.0 * deathProximity;
                    additionalPenalty += bonus; // Actually a reward
                    LOGGER.info("✓ Good decision: Evaded at {}HP (bonus: {})",
                        state.getBotHealth(), bonus);
                }
            }

            // Pattern 2: Multiple hostile entities but stayed or attacked
            long hostileCount = state.getNearbyEntities().stream()
                .filter(EntityDetails::isHostile).count();
            if (hostileCount >= 2 && state.getBotHealth() <= 15) {
                if (action == Action.STAY || action == Action.ATTACK) {
                    additionalPenalty -= 25.0 * deathProximity;
                    LOGGER.info("❌ Critical mistake: Stayed/Attacked with {} hostiles at {}HP",
                        hostileCount, state.getBotHealth());
                }
            }

            // Pattern 3: High PoD action when low HP
            if (t.getPodValue() > 0.5 && state.getBotHealth() <= 12) {
                additionalPenalty -= 20.0 * deathProximity;
                LOGGER.info("❌ Critical mistake: High PoD action ({}) at {}HP",
                    t.getPodValue(), state.getBotHealth());
            }

            // Apply adjusted reward to Q-table
            if (additionalPenalty != 0.0) {
                StateActionPair pair = new StateActionPair(state, action);
                QEntry existingEntry = qTable.getEntry(pair);
                double currentQ = (existingEntry != null) ? existingEntry.getQValue() : 0.0;
                double adjustedQ = currentQ + (ALPHA * additionalPenalty);

                // Get the next state from existing entry or use current state as fallback
                State nextStateForEntry = (existingEntry != null) ? existingEntry.getNextState() : state;

                // Update using the correct QTable.addEntry signature: (State, Action, qValue, nextState)
                qTable.addEntry(state, action, adjustedQ, nextStateForEntry);


                LOGGER.info("📊 Updated Q-value for {} at {}HP: {} -> {}",
                    action, state.getBotHealth(), currentQ, adjustedQ);
            }
        }

        // Log summary
        LOGGER.info("🎓 Learning complete. Death sequence analysis finished.");
    }

    /**
     * Get transition history for external access
     */
    public StateTransition.TransitionHistory getTransitionHistory() {
        return transitionHistory;
    }

    /**
     * Clear old transitions to prevent memory buildup
     */
    public void clearOldTransitions() {
        transitionHistory.clearOldTransitions(3600000); // Keep last hour
    }

    /**
     * Risk estimation for planner system.
     * Returns detailed risk breakdown for an action.
     */
    public RiskEstimate estimateRisk(State state, String actionName) {
        // Convert action name to Action enum
        Action action;
        try {
            action = Action.valueOf(actionName.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            // Unknown action, return default risk
            return new RiskEstimate(0.1, 0.0, 5.0);
        }

        // Get base risk from calculateRisk
        List<Action> singleAction = Collections.singletonList(action);
        Map<Action, Double> riskMap = calculateRisk(state, singleAction, null);
        double totalRisk = riskMap.getOrDefault(action, 10.0);

        // Estimate death probability (sigmoid of risk)
        double deathProb = 1.0 / (1.0 + Math.exp(-totalRisk / 20.0 + 2.5));

        // Estimate expected damage
        double expectedDamage = 0.0;
        if (state.getDistanceToHostileEntity() < 5.0 && !state.getNearbyEntities().isEmpty()) {
            expectedDamage = state.getNearbyEntities().stream()
                .filter(EntityDetails::isHostile)
                .mapToDouble(e -> getEntityRisk(e, 0, 0, 0) * 0.5)
                .sum();
        }

        return new RiskEstimate(deathProb, expectedDamage, totalRisk);
    }

    /**
     * Get Q-value for a state-action pair.
     * Returns 0.0 if not found.
     */
    public double getQValue(State state, String actionName) {
        // Convert action name to Action enum
        Action action;
        try {
            action = Action.valueOf(actionName.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            return 0.0;
        }

        StateActionPair pair = new StateActionPair(state, action);
        QEntry entry = qTable.getEntry(pair);

        return entry != null ? entry.getQValue() : 0.0;
    }

    /**
     * Get the current state for a bot.
     * Creates a new State from the bot's current status.
     */
    public State getCurrentState(net.minecraft.server.network.ServerPlayerEntity bot) {
        return net.shasankp000.GameAI.BotEventHandler.createInitialState(bot);
    }


    /**
     * Risk estimate data structure for planner.
     */
    public static class RiskEstimate {
        public final double deathProbability; // 0.0 to 1.0
        public final double expectedDamage;   // HP damage
        public final double totalRisk;        // Overall risk score

        public RiskEstimate(double deathProbability, double expectedDamage, double totalRisk) {
            this.deathProbability = deathProbability;
            this.expectedDamage = expectedDamage;
            this.totalRisk = totalRisk;
        }
    }

}
