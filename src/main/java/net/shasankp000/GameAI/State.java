package net.shasankp000.GameAI;

import net.minecraft.item.ItemStack;
import net.shasankp000.Entity.EntityDetails;
import net.shasankp000.PlayerUtils.SelectedItemDetails;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class State implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L; // Recommended for Serializable classes

    private static final double DISTANCE_TOLERANCE = 8.0; // Maximum allowable difference
    private static final double ENTITY_SIMILARITY_THRESHOLD = 0.5; // 50% overlap
    private static final double BLOCK_SIMILARITY_THRESHOLD = 0.5; // 50% overlap

    private final int botX, botY, botZ;
    private final int frostLevel;
    private final double distanceToHostileEntity;
    private final double distanceToDangerZone;
    private final int botHealth;
    private final List<String> hotBarItems; // Serialized as a list of item names
    private final SelectedItemDetails selectedItem;
    private final String timeOfDay;
    private final String dimensionType;
    private final int botHungerLevel;
    private final int botOxygenLevel;

    private final String offhandItem; // Serialized as the item name
    private final Map<String, String> armorItems; // Serialized as a map of item names

    private final StateActions.Action actionTaken;
    private List<EntityDetails> nearbyEntities = List.of();
    private Map<StateActions.Action, Double> riskMap;
    private final double riskAppetite;
    private final List<String> nearbyBlocks;
    private Map<StateActions.Action, Double> podMap = new HashMap<>();



    // Full constructor for custom action
    public State(int botX, int botY, int botZ, List<EntityDetails> nearbyEntities, List<String> nearbyBlocks, double distanceToHostileEntity, int botHealth, double distanceToDangerZone,
                 List<ItemStack> hotBarItems, SelectedItemDetails selectedItem, String timeOfDay, String dimensionType,
                 int botHungerLevel, int botOxygenLevel, int frostLevel ,ItemStack offhandItem, Map<String, ItemStack> armorItems,
                 StateActions.Action actionTaken, Map<StateActions.Action, Double> riskMap , double riskAppetite, Map<StateActions.Action, Double> podMap) {

        this.botX = botX;
        this.botY = botY;
        this.botZ = botZ;
        this.frostLevel = frostLevel;

        this.distanceToHostileEntity = distanceToHostileEntity;
        this.distanceToDangerZone = distanceToDangerZone;
        this.botHealth = botHealth;

        // Convert ItemStack to Strings for serialization
        this.hotBarItems = serializeItemStackList(hotBarItems);
        this.selectedItem = selectedItem;
        this.timeOfDay = timeOfDay;
        this.dimensionType = dimensionType;
        this.botHungerLevel = botHungerLevel;
        this.botOxygenLevel = botOxygenLevel;

        this.offhandItem = serializeItemStack(offhandItem);
        this.armorItems = serializeArmorItems(armorItems);
        this.actionTaken = actionTaken;

        this.nearbyEntities = nearbyEntities;

        this.riskMap = riskMap;
        this.riskAppetite = riskAppetite;
        this.nearbyBlocks = nearbyBlocks;
        this.podMap = podMap;
    }

    // Getters for state variables
    public int getBotX() { return botX; }
    public int getBotY() { return botY; }
    public int getBotZ() { return botZ; }
    public double getDistanceToHostileEntity() { return distanceToHostileEntity; }
    public int getBotHealth() { return botHealth; }
    public double getDistanceToDangerZone() { return distanceToDangerZone; }
    public List<String> getHotBarItems() { return hotBarItems; }
    public String getSelectedItem() { return selectedItem.getName(); }
    public SelectedItemDetails getSelectedItemStack() { return selectedItem; }
    public String getTimeOfDay() { return timeOfDay; }
    public String getDimensionType() { return dimensionType; }
    public int getBotHungerLevel() { return botHungerLevel; }
    public int getBotOxygenLevel() { return botOxygenLevel; }
    public String getOffhandItem() { return offhandItem; }
    public Map<String, String> getArmorItems() { return armorItems; }
    public StateActions.Action getActionTaken() { return actionTaken; }
    public List<EntityDetails> getNearbyEntities() { return nearbyEntities;}
    public List<String> getNearbyBlocks() { return nearbyBlocks; }
    public int getFrostLevel() { return frostLevel; }
    public Map<StateActions.Action, Double> getRiskMap() { return riskMap;}
    public double getRiskAppetite() {return riskAppetite;}
    public Map<StateActions.Action, Double> getPodMap() {return podMap;}


    public void setPodMap(Map<StateActions.Action, Double> podMap) {
        this.podMap = podMap;
    }

    public void setRiskMap(Map<StateActions.Action, Double> riskMap) {
        this.riskMap = riskMap;
    }


    // Serialization helpers
    public static String serializeItemStack(ItemStack stack) {
        return stack != null ? stack.getItem().toString() : "empty";
    }

    public static List<String> serializeItemStackList(List<ItemStack> stacks) {
        return stacks.stream().map(State::serializeItemStack).collect(Collectors.toList());
    }

    public static Map<String, String> serializeArmorItems(Map<String, ItemStack> armorItems) {
        return armorItems.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> serializeItemStack(e.getValue())));
    }

    public boolean isOptimal() {

        // Set optimal values
        int optimalBotHealth = 20; // Full health
        int optimalBotHunger = 20; // No hunger
        int optimalBotOxygenLevel = 300; // Full oxygen
        int optimalFrostLevel = 0; // Not freezing

        // Get current values from the input state
        int botHealth = getBotHealth();
        int botHunger = getBotHungerLevel();
        int botOxygen = getBotOxygenLevel();
        double distanceToDangerZone = getDistanceToDangerZone();
        int frostLevel = getFrostLevel();

        // Check for minimum requirements for optimality
        if (botHealth >= optimalBotHealth / 2 && botHunger >= optimalBotHunger / 2 && botOxygen >= optimalBotOxygenLevel / 2 && frostLevel <= optimalFrostLevel) {

            List<EntityDetails> hostileEntities = getNearbyEntities().stream()
                    .filter(EntityDetails::isHostile)
                    .toList();

            // If no hostile entities and not in a danger zone, the state is optimal
            return hostileEntities.isEmpty() && distanceToDangerZone == 0;
        }

        return false;
    }



    // Readable toString for debugging purposes
    @Override
    public String toString() {
        return "State{" +
                "bucketX =" + botX +
                ", bucketY =" + botY +
                ", bucketZ =" + botZ +
                ", nearbyEntities = " + nearbyEntities +
                ", nearbyBlocks = " + nearbyBlocks +
                ", distanceToHostileEntity = " + distanceToHostileEntity +
                ", distanceToDangerZone = " + distanceToDangerZone +
                ", botHealth = " + botHealth +
                ", hotBarItems = " + hotBarItems +
                ", selectedItem = '" + selectedItem.getName() + '\'' +
                ", timeOfDay = '" + timeOfDay + '\'' +
                ", dimensionType = '" + dimensionType + '\'' +
                ", botHungerLevel = " + botHungerLevel +
                ", botOxygenLevel = " + botOxygenLevel +
                ", botFrostLevel = " + frostLevel +
                ", offhandItem ='" + offhandItem + '\'' +
                ", armorItems =" + armorItems +
                ", actionTaken =" + actionTaken +
                ", riskMap = " + riskMap +
                ", riskAppetite = " + riskAppetite +
                ", podMap = " + podMap +
                '}';
    }

    public Map<String, Object> toMap() {
        Map<String, Object> stateMap = new HashMap<>();
        stateMap.put("botX", getBotX());
        stateMap.put("botY", getBotY());
        stateMap.put("botZ", getBotZ());
        stateMap.put("nearbyEntities", getNearbyEntities());
        stateMap.put("nearbyBlocks", getNearbyBlocks());
        stateMap.put("distanceToHostileEntity", getDistanceToHostileEntity());
        stateMap.put("botHealth", getBotHealth());
        stateMap.put("distanceToDangerZone", getDistanceToDangerZone());
        stateMap.put("hotBarItems", getHotBarItems()); // Assuming `getHotBarItems()` returns List<String>
        stateMap.put("selectedItem", getSelectedItem());
        stateMap.put("timeOfDay", getTimeOfDay());
        stateMap.put("dimensionType", getDimensionType());
        stateMap.put("botHungerLevel", getBotHungerLevel());
        stateMap.put("botOxygenLevel", getBotOxygenLevel());
        stateMap.put("botFrostLevel", getFrostLevel());
        stateMap.put("offhandItem", getOffhandItem()); // Assuming `getOffhandItem()` returns String
        stateMap.put("armorItems", getArmorItems()); // Assuming `getArmorItems()` returns Map<String, String>
        stateMap.put("actionTaken", getActionTaken().toString()); // Convert enum to string
        stateMap.put("riskMap", getRiskMap());
        stateMap.put("riskAppetite", getRiskAppetite());
        stateMap.put("podMap", getPodMap());
        return stateMap;
    }


    public static boolean isStateConsistent(State lastState, State currentState) {
        if (lastState == null) return false;

        // Numeric comparison with tolerance
        boolean distanceToHostileEntitySimilar = Math.abs(lastState.getDistanceToHostileEntity() - currentState.getDistanceToHostileEntity()) <= DISTANCE_TOLERANCE;
        boolean distanceToDangerZoneSimilar = Math.abs(lastState.getDistanceToDangerZone() - currentState.getDistanceToDangerZone()) <= DISTANCE_TOLERANCE;

        // Exact match for categorical parameters
        boolean timeOfDaySimilar = lastState.getTimeOfDay().equals(currentState.getTimeOfDay());
        boolean dimensionTypeSimilar = lastState.getDimensionType().equals(currentState.getDimensionType());

        // Overlap check for collections
        boolean nearbyEntitiesSimilar = calculateEntityOverlap(lastState.getNearbyEntities(), currentState.getNearbyEntities()) >= ENTITY_SIMILARITY_THRESHOLD;
        boolean nearbyBlocksSimilar = calculateBlockOverlap(lastState.getNearbyBlocks(), currentState.getNearbyBlocks()) >= BLOCK_SIMILARITY_THRESHOLD;


        System.out.println("distanceToHostileEntitySimilar: " + distanceToHostileEntitySimilar);
        System.out.println("distanceToDangerZoneSimilar: " + distanceToHostileEntitySimilar);
        System.out.println("nearByEntitiesSimilar: " + nearbyEntitiesSimilar);
        System.out.println("nearbyBlockSimilar: " + nearbyBlocksSimilar);

        // Combine all checks
        return distanceToHostileEntitySimilar &&
                distanceToDangerZoneSimilar &&
                timeOfDaySimilar &&
                dimensionTypeSimilar &&
                nearbyEntitiesSimilar ||
                nearbyBlocksSimilar;
    }

    private static double calculateBlockOverlap( List<String> lastBlocks, List<String> currentBlocks) {

        if (lastBlocks.isEmpty()) {
            return 0.0; // No overlap if block list is empty
        }

        double blockOverlapRatio = 0.0;

        // Calculate block similarity overlap

        long similarBlocksCount = currentBlocks.stream()
                .filter(block -> lastBlocks.stream().anyMatch(lastBlock -> lastBlock.contains(block)))
                .count();

        blockOverlapRatio = (double) similarBlocksCount / Math.max(lastBlocks.size(), currentBlocks.size());
        System.out.println("Block overlap ratio: " + blockOverlapRatio);

        // Combine both ratios (weighted equally or adjust weights if needed)
        return (blockOverlapRatio) / 2.0; // Average overlap ratio
    }


    private static double calculateEntityOverlap(List<EntityDetails> lastEntities, List<EntityDetails> currentEntities) {
        if (lastEntities.isEmpty()) {
            return 0.0; // No overlap if one or both lists are empty
        }

        // Extract names from both entity lists
        List<String> lastEntityNames = lastEntities.stream()
                .map(EntityDetails::getName)
                .toList();

        List<String> currentEntityNames = currentEntities.stream()
                .map(EntityDetails::getName)
                .toList();

        // Count exact name matches
        long exactNameMatches = currentEntityNames.stream()
                .filter(lastEntityNames::contains)
                .count();

        // Calculate overlap ratio (based on exact matches)
        double overlapRatio = (double) exactNameMatches / Math.max(lastEntityNames.size(), currentEntityNames.size());

        // Optionally log for debugging
        System.out.println("Entity name overlap ratio: " + overlapRatio);

        return overlapRatio;
    }


}
