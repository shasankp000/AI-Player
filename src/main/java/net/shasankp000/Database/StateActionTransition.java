package net.shasankp000.Database;

import net.shasankp000.GameAI.State;
import net.shasankp000.GameAI.StateActions;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.shasankp000.GameAI.State.isStateConsistent;

/**
 * Wrapper class to represent a complex state-action-nextState-reward structure.
 */
public class StateActionTransition implements Serializable {
    private final Map<State, Map<StateActions.Action, Map<State, Double>>> transitionMap;

    public StateActionTransition() {
        this.transitionMap = new HashMap<>();
    }

    public Map<State, Map<StateActions.Action, Map<State, Double>>> getTransitionMap() {
        return transitionMap;
    }

    /**
     * Add or update a transition in the map.
     *
     * @param initialState the initial state
     * @param action the action taken
     * @param nextState the resulting state
     * @param reward the reward for the transition
     */
    public void addTransition(State initialState, StateActions.Action action, State nextState, double reward) {
        transitionMap
                .computeIfAbsent(initialState, k -> new HashMap<>())
                .computeIfAbsent(action, k -> new HashMap<>())
                .put(nextState, reward);
    }

    /**
     * Get the reward for a specific transition.
     *
     * @param initialState the initial state
     * @param action the action taken
     * @param nextState the resulting state
     * @return the reward for the transition, or null if not found
     */
    public Double getReward(State initialState, StateActions.Action action, State nextState) {
        return transitionMap.getOrDefault(initialState, new HashMap<>())
                .getOrDefault(action, new HashMap<>())
                .get(nextState);
    }

    /**
     * Check if a specific transition exists.
     *
     * @param initialState the initial state
     * @param action the action taken
     * @param nextState the resulting state
     * @return true if the transition exists, false otherwise
     */
    public boolean hasTransition(State initialState, StateActions.Action action, State nextState) {
        return transitionMap.containsKey(initialState) &&
                transitionMap.get(initialState).containsKey(action) &&
                transitionMap.get(initialState).get(action).containsKey(nextState);
    }

    /**
     * Find similar states in the transition map based on consistency.
     *
     * @param currentState the current state to match
     * @return a map of similar states
     */
    public Map<State, Map<StateActions.Action, Map<State, Double>>> getSimilarStates(State currentState) {
        Map<State, Map<StateActions.Action, Map<State, Double>>> similarStates = new HashMap<>();

        for (Map.Entry<State, Map<StateActions.Action, Map<State, Double>>> entry : transitionMap.entrySet()) {
            State knownState = entry.getKey();
            if (isStateConsistent(knownState, currentState)) {
                similarStates.put(knownState, entry.getValue());
            }
        }

        return similarStates;
    }

    /**
     * Append a single transition to the Q-table file.
     *
     * @param filePath the file to save to
     */
    public void appendTransition(String filePath) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filePath, true)) {
            @Override
            protected void writeStreamHeader() throws IOException {
                if (new File(filePath).length() == 0) super.writeStreamHeader();
            }
        }) {
            oos.writeObject(this);
        }
    }

    /**
     * Load the Q-table file in chunks.
     *
     * @param filePath the file to read from
     * @return a list of StateActionTransition objects
     */
    public static List<StateActionTransition> loadQTableInChunks(String filePath) throws IOException, ClassNotFoundException {
        List<StateActionTransition> transitions = new ArrayList<>();

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filePath))) {
            while (true) {
                try {
                    StateActionTransition transition = (StateActionTransition) ois.readObject();
                    transitions.add(transition);
                } catch (EOFException e) {
                    break;
                }
            }
        }

        System.out.println("Loaded Q-table: Total chunks = " + transitions.size());
        return transitions;
    }

    /**
     * Combine multiple StateActionTransition objects into one.
     *
     * @param transitions the list of transitions to combine
     * @return a single StateActionTransition object
     */
    public static StateActionTransition combineTransitions(List<StateActionTransition> transitions) {
        StateActionTransition combined = new StateActionTransition();

        for (StateActionTransition transition : transitions) {
            for (Map.Entry<State, Map<StateActions.Action, Map<State, Double>>> entry : transition.getTransitionMap().entrySet()) {
                State initialState = entry.getKey();
                for (Map.Entry<StateActions.Action, Map<State, Double>> actionEntry : entry.getValue().entrySet()) {
                    StateActions.Action action = actionEntry.getKey();
                    for (Map.Entry<State, Double> nextStateEntry : actionEntry.getValue().entrySet()) {
                        State nextState = nextStateEntry.getKey();
                        Double reward = nextStateEntry.getValue();
                        combined.addTransition(initialState, action, nextState, reward);
                    }
                }
            }
        }

        return combined;
    }

    /**
     * Save the entire Q-table to a file.
     *
     * @param filePath the file to save to
     */
    public void saveTransitionMap(String filePath) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filePath))) {
            oos.writeObject(this);
        }
    }
}
