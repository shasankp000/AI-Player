package net.shasankp000.Database;

import net.minecraft.client.MinecraftClient;
import net.shasankp000.GameAI.State;
import net.shasankp000.GameAI.StateActions;

import java.io.*;
import java.util.HashMap;

import java.util.Map;

public class QTableStorage {
    private static final String gameDir = MinecraftClient.getInstance().runDirectory.getAbsolutePath();


    public static void setupQTableStorage() {

        File tableDir = new File(gameDir + "/qtable_storage");

        if (!tableDir.exists()) {
            if(tableDir.mkdirs()) {
                System.out.println("QTable Storage directory created.");
            }
        }
        else{
            System.out.println("QTable Storage directory already exists, ignoring...");
        }

    }

    /**
     * Saves a single state-action pair to the serialized Q-table file.
     * Appends if the file already exists.
     */
    public static void saveStateActionPair(State state, Map<StateActions.Action, Double> actions, String filePath) throws IOException {
        File file = new File(filePath);
        boolean append = file.exists();
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream(filePath, append)) {
            @Override
            protected void writeStreamHeader() throws IOException {
                if (!append) super.writeStreamHeader(); // Write header only for new files
            }
        }) {
            oos.writeObject(new StateActionPair(state, actions));
        }
    }

    /**
     * Loads the entire Q-table from the serialized file.
     */
    public static Map<State, Map<StateActions.Action, Double>> loadQTable(String filePath) throws IOException, ClassNotFoundException {
        File file = new File(filePath);
        if (!file.exists()) {
            return new HashMap<>();
        }
        Map<State, Map<StateActions.Action, Double>> qTable = new HashMap<>();
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filePath))) {
            while (true) {
                try {
                    StateActionPair pair = (StateActionPair) ois.readObject();
                    qTable.put(pair.getState(), pair.getActions());
                } catch (EOFException e) {
                    break;
                }
            }
        }
        return qTable;
    }
}

/**
 * Wrapper class to represent a single state-action pair.
 * This makes it easier to append and read entries incrementally.
 */
class StateActionPair implements Serializable {
    private final State state;
    private final Map<StateActions.Action, Double> actions;

    public StateActionPair(State state, Map<StateActions.Action, Double> actions) {
        this.state = state;
        this.actions = actions;
    }

    public State getState() {
        return state;
    }

    public Map<StateActions.Action, Double> getActions() {
        return actions;
    }

}
