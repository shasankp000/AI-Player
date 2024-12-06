package net.shasankp000.GameAI;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.item.ItemStack;
import net.shasankp000.GameAI.StateActions.Action;

public class RLAgent {
    private static final double ALPHA = 0.1;  // Learning rate
    private static final double GAMMA = 0.9;  // Discount factor
    private double epsilon;
    private static final double MIN_EPSILON = 0.1; // Minimum exploration rate
    private static final double EPSILON_DECAY_RATE = 0.95; // Decay rate for epsilon

    // Q-table: Maps each state to a map of actions and their Q-values
    private Map<State, Map<Action, Double>> qTable;
    private Random random;

    public RLAgent() {
        this.epsilon = 1.0; // Initial exploration rate
        qTable = new HashMap<>();
        random = new Random();
    }

    // Choose action based on epsilon-greedy strategy
    public Action chooseAction(State state) {
        double selectedRandomValue = random.nextDouble();

        System.out.println("Generated random value: " + selectedRandomValue);

        if ( selectedRandomValue < epsilon) {
            // Exploration: Choose a random action
            System.out.println("Exploring with epsilon: " + epsilon);
            return Action.values()[random.nextInt(Action.values().length)];
        } else {
            // Exploitation: Choose the action with the highest Q-value for the given state
            Map<Action, Double> actions = qTable.getOrDefault(state, new HashMap<>());
            return actions.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(Action.STAY);  // Default action if no actions in Q-table
        }
    }

    // Decay epsilon after each episode or iteration
    public void decayEpsilon() {
        epsilon = Math.max(MIN_EPSILON, epsilon * EPSILON_DECAY_RATE);
        System.out.println("Updated epsilon: " + epsilon);
    }

    // Expose the Q-table for external operations like saving
    public Map<State, Map<StateActions.Action, Double>> getQTable() {
        return qTable;
    }

    public static int calculateReward(int botX, int botY, int botZ, double distanceToHostileEntity, int botHealth,
                                      double distanceToDanger, List<ItemStack> hotBarItems, String selectedItem,
                                      String timeOfDay, String dimension, int botHungerLevel, int botOxygenLevel,
                                      ItemStack offhandItem, Map<String, ItemStack> armorItems, StateActions.Action actionTaken) {

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
        if (selectedItem.equalsIgnoreCase("sword") || selectedItem.equalsIgnoreCase("bow")) {
            reward += 10; // Weapon equipped
        } else if (selectedItem.equalsIgnoreCase("shield")) {
            reward += 5; // Defensive item equipped
        } else {
            reward -= 5; // Irrelevant item selected
        }

        if (offhandItem.getItem().toString().equalsIgnoreCase("minecraft:shield")) {
            reward += 10; // Extra reward for using shield
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
                reward += 0; // Normal behavior
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

        // 7. Hotbar item checks
        boolean hasTotem = false, hasGoldenApple = false, hasWaterBucket = false, hasFood = false;

        for (ItemStack item : hotBarItems) {
            if (item.getItem().toString().equalsIgnoreCase("minecraft:totem_of_undying")) {
                hasTotem = true;
            } else if (item.getItem().toString().equalsIgnoreCase("minecraft:golden_apple")) {
                hasGoldenApple = true;
            } else if (item.getItem().toString().equalsIgnoreCase("minecraft:water_bucket")) {
                hasWaterBucket = true;
            } else if (item.getItem().toString().equalsIgnoreCase("minecraft:bread") ||
                    item.getItem().toString().equalsIgnoreCase("minecraft:cooked_beef")) {
                hasFood = true;
            }
        }

        if (hasTotem) reward += 15; // Totem is very valuable
        if (hasGoldenApple) reward += 10; // Golden apple is highly valuable
        if (hasWaterBucket) reward += 5; // Optional but situationally useful
        if (hasFood) reward += 5; // Food ensures sustainability
        else reward -= 10; // Penalize for lack of food

        // 8. Hunger-based logic
        if (botHungerLevel <= 6) {
            reward -= 10; // Penalize low hunger
        } else if (botHungerLevel > 16) {
            reward += 5; // Reward high hunger levels
        }

        // 9. Oxygen-based logic
        if (botOxygenLevel < 60) {
            reward -= 20; // Penalize low oxygen levels
        } else if (botOxygenLevel >= 150) {
            reward += 10; // Reward full or more than half full oxygen levels
        }

        // 10. Armor-based logic
        for (Map.Entry<String, ItemStack> entry : armorItems.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                reward += 5; // Reward for wearing armor
            }
        }

        // 11. Risk-reward factor
        if (botHealth <= 10 && !hasTotem && actionTaken == StateActions.Action.ATTACK && distanceToHostileEntity <= 5) {
            reward += 20; // High reward for taking a calculated risk
        }

        return reward;
    }

    // Update Q-value for the given state-action pair
    public void updateQValue(State state, Action action, double reward, State nextState) {
        Map<Action, Double> actions = qTable.computeIfAbsent(state, k -> new HashMap<>());
        double oldQValue = actions.getOrDefault(action, 0.0);

        // Get the maximum Q-value for the next state
        double maxNextQValue = qTable.getOrDefault(nextState, new HashMap<>())
                .values().stream()
                .max(Double::compare)
                .orElse(0.0);

        // Q-learning formula
        double newQValue = oldQValue + ALPHA * (reward + GAMMA * maxNextQValue - oldQValue);
        actions.put(action, newQValue);
    }

    // Decay epsilon after each episode
    public void endEpisode() {
        decayEpsilon();
    }
}
