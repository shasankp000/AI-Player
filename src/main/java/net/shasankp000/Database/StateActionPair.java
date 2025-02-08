package net.shasankp000.Database;

import net.shasankp000.GameAI.State;
import net.shasankp000.GameAI.StateActions;

import java.io.Serializable;
import java.util.Objects; /**
 * Wrapper class to represent a single state-action pair.
 */
public class StateActionPair implements Serializable {
    private final State state;
    private final StateActions.Action action;

    public StateActionPair(State state, StateActions.Action action) {
        this.state = state;
        this.action = action;
    }

    public State getState() {
        return state;
    }

    public StateActions.Action getAction() {
        return action;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StateActionPair that = (StateActionPair) o;
        return Objects.equals(state, that.state) && action == that.action;
    }

    @Override
    public int hashCode() {
        return Objects.hash(state, action);
    }

    @Override
    public String toString() {
        return String.format("State: %s, Action: %s", state, action);
    }
}
