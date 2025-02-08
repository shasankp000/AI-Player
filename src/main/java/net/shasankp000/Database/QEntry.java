package net.shasankp000.Database;

import net.shasankp000.GameAI.State;

import java.io.Serializable;

public class QEntry implements Serializable {
    private final double qValue;
    private final State nextState;

    public QEntry(double qValue, State nextState) {
        this.qValue = qValue;
        this.nextState = nextState;
    }

    public double getQValue() {
        return qValue;
    }

    public State getNextState() {
        return nextState;
    }

    @Override
    public String toString() {
        return String.format("QValue: %.2f, NextState: %s", qValue, nextState);
    }
}

