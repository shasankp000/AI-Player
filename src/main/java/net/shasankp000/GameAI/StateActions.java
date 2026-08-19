package net.shasankp000.GameAI;

public class StateActions {

    public enum Action {
        MOVE_FORWARD,
        MOVE_BACKWARD,
        TURN_LEFT,
        TURN_RIGHT,
        JUMP,
        STAY,
        SNEAK,
        SPRINT,
        STOP_MOVING,
        STOP_SNEAKING,
        STOP_SPRINTING,
        USE_ITEM,
        ATTACK,
        SHOOT_ARROW,
        EQUIP_ARMOR,
        HOTBAR_1,
        HOTBAR_2,
        HOTBAR_3,
        HOTBAR_4,
        HOTBAR_5,
        HOTBAR_6,
        HOTBAR_7,
        HOTBAR_8,
        HOTBAR_9,
        EVADE, // Adaptive evasion from threats - uses smart movement and pathfinding
        /** Learned survival action: find or place a bed and sleep when safe at night. */
        SLEEP,
    }

}
