package ru.mail.polis.psaer.domain;

import java.io.Serializable;

public class DaoValue implements Serializable {

    private final State state;
    private final long timestamp;
    private final byte[] data;

    public DaoValue(State state, long timestamp, byte[] data) {
        this.state = state;
        this.timestamp = timestamp;
        this.data = data;
    }

    public State getState() {
        return state;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public byte[] getData() {
        return data;
    }

    public enum State {
        PRESENT,
        REMOVED
    }
}
