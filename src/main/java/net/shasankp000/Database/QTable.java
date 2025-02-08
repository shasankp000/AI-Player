package net.shasankp000.Database;

import net.shasankp000.GameAI.State;
import net.shasankp000.GameAI.StateActions;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class QTable implements Serializable {
    private final Map<StateActionPair, QEntry> qTable;

    public QTable() {
        this.qTable = new HashMap<>();
    }

    public void addEntry(State state, StateActions.Action action, double qValue, State nextState) {
        StateActionPair pair = new StateActionPair(state, action);
        qTable.put(pair, new QEntry(qValue, nextState));
    }

    public QEntry getEntry(StateActionPair pair) {
        return qTable.get(pair);
    }

    public Map<StateActionPair, QEntry> getTable() {
        return qTable;
    }
}

