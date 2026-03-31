package sse.model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class State implements Serializable {
    private final Map<KeywordToken, Integer> searchCounter;
    private final Map<KeywordToken, Integer> updateCounter;

    public State(Map<KeywordToken, Integer> searchCounter, Map<KeywordToken, Integer> updateCounter) {
        if (searchCounter == null || updateCounter == null) {
            throw new IllegalArgumentException("Counters cannot be null");
        }
        this.searchCounter = Collections.unmodifiableMap(new HashMap<>(searchCounter));
        this.updateCounter = Collections.unmodifiableMap(new HashMap<>(updateCounter));
    }

    public State(Map<KeywordToken, Integer> searchCounter) {
        this(searchCounter, Collections.<KeywordToken, Integer>emptyMap());
    }

    public Map<KeywordToken, Integer> searchCounter() {
        return searchCounter;
    }

    public Map<KeywordToken, Integer> updateCounter() {
        return updateCounter;
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
        return Objects.equals(searchCounter, state.searchCounter) &&
                Objects.equals(updateCounter, state.updateCounter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(searchCounter, updateCounter);
    }

    @Override
    public String toString() {
        return "State[" +
                "searchCounter=" + searchCounter +
                ", updateCounter=" + updateCounter +
                ']';
    }
}
