package sse.domain;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class State implements Serializable {
    private final Map<KeywordToken, KeywordState> keywordStates;

    public State(Map<KeywordToken, KeywordState> keywordStates) {
        if (keywordStates == null) {
            throw new IllegalArgumentException("keywordStates cannot be null");
        }
        this.keywordStates = Collections.unmodifiableMap(new LinkedHashMap<>(keywordStates));
    }

    public Map<KeywordToken, KeywordState> keywordStates() {
        return keywordStates;
    }

    public byte[] serialize() {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutput out = new ObjectOutputStream(bos)) {
            out.writeObject(this);
            out.flush();
            bos.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Error serializing state", e);
        }
    }

    public static State deserialize(byte[] serializedState) {
        if (serializedState == null) {
            return null;
        }
        try (ByteArrayInputStream bis = new ByteArrayInputStream(serializedState);
             ObjectInput in = new ObjectInputStream(bis)) {
            return (State) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("Error deserializing state", e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        State state = (State) o;
        return Objects.equals(keywordStates, state.keywordStates);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keywordStates);
    }

    @Override
    public String toString() {
        return "State[" +
                "keywordStates=" + keywordStates +
                ']';
    }
}
