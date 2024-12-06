package net.shasankp000.GameAI;

import net.minecraft.item.ItemStack;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class State implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L; // Recommended for Serializable classes
    private static final int BUCKET_SIZE = 16; // Render/simulation distance (chunk size or appropriate value)

    private final int bucketX, bucketY, bucketZ; // Bucketed positions
    private final double distanceToHostileEntity;
    private final double distanceToDangerZone;
    private final int botHealth;
    private final List<String> hotBarItems; // Serialized as a list of item names
    private final String selectedItem;
    private final String timeOfDay;
    private final String dimensionType;
    private final int botHungerLevel;
    private final int botOxygenLevel;

    private final String offhandItem; // Serialized as the item name
    private final Map<String, String> armorItems; // Serialized as a map of item names

    private final StateActions.Action actionTaken;

    // Full constructor for custom action
    public State(int botX, int botY, int botZ, double distanceToHostileEntity, int botHealth, double distanceToDangerZone,
                 List<ItemStack> hotBarItems, String selectedItem, String timeOfDay, String dimensionType,
                 int botHungerLevel, int botOxygenLevel, ItemStack offhandItem, Map<String, ItemStack> armorItems,
                 StateActions.Action actionTaken) {

        this.bucketX = botX / BUCKET_SIZE;
        this.bucketY = botY / BUCKET_SIZE;
        this.bucketZ = botZ / BUCKET_SIZE;

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
    }

    // Getters for state variables
    public int getBucketX() { return bucketX; }
    public int getBucketY() { return bucketY; }
    public int getBucketZ() { return bucketZ; }
    public double getDistanceToHostileEntity() { return distanceToHostileEntity; }
    public int getBotHealth() { return botHealth; }
    public double getDistanceToDangerZone() { return distanceToDangerZone; }
    public List<String> getHotBarItems() { return hotBarItems; }
    public String getSelectedItem() { return selectedItem; }
    public String getTimeOfDay() { return timeOfDay; }
    public String getDimensionType() { return dimensionType; }
    public int getBotHungerLevel() { return botHungerLevel; }
    public int getBotOxygenLevel() { return botOxygenLevel; }
    public String getOffhandItem() { return offhandItem; }
    public Map<String, String> getArmorItems() { return armorItems; }
    public StateActions.Action getActionTaken() { return actionTaken; }

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

    // Equality and hashcode
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        State state = (State) o;
        return bucketX == state.bucketX &&
                bucketY == state.bucketY &&
                bucketZ == state.bucketZ &&
                Double.compare(state.distanceToHostileEntity, distanceToHostileEntity) == 0 &&
                Double.compare(state.distanceToDangerZone, distanceToDangerZone) == 0 &&
                botHealth == state.botHealth &&
                botHungerLevel == state.botHungerLevel &&
                botOxygenLevel == state.botOxygenLevel &&
                Objects.equals(hotBarItems, state.hotBarItems) &&
                Objects.equals(selectedItem, state.selectedItem) &&
                Objects.equals(timeOfDay, state.timeOfDay) &&
                Objects.equals(dimensionType, state.dimensionType) &&
                Objects.equals(offhandItem, state.offhandItem) &&
                Objects.equals(armorItems, state.armorItems) &&
                actionTaken == state.actionTaken;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bucketX, bucketY, bucketZ, distanceToHostileEntity, distanceToDangerZone, botHealth,
                hotBarItems, selectedItem, timeOfDay, dimensionType, botHungerLevel, botOxygenLevel,
                offhandItem, armorItems, actionTaken);
    }

    // Readable toString for debugging purposes
    @Override
    public String toString() {
        return "State{" +
                "bucketX=" + bucketX +
                ", bucketY=" + bucketY +
                ", bucketZ=" + bucketZ +
                ", distanceToHostileEntity=" + distanceToHostileEntity +
                ", distanceToDangerZone=" + distanceToDangerZone +
                ", botHealth=" + botHealth +
                ", hotBarItems=" + hotBarItems +
                ", selectedItem='" + selectedItem + '\'' +
                ", timeOfDay='" + timeOfDay + '\'' +
                ", dimensionType='" + dimensionType + '\'' +
                ", botHungerLevel=" + botHungerLevel +
                ", botOxygenLevel=" + botOxygenLevel +
                ", offhandItem='" + offhandItem + '\'' +
                ", armorItems=" + armorItems +
                ", actionTaken=" + actionTaken +
                '}';
    }
}
