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
    private final Map<KeywordToken, Integer> searchCounter;
    private final EncryptedUpdateCounter encryptedUpdateCounter;

    public State(Map<KeywordToken, Integer> searchCounter, EncryptedUpdateCounter encryptedUpdateCounter) {
        if (searchCounter == null || encryptedUpdateCounter == null) {
            throw new IllegalArgumentException("searchCounter and encryptedUpdateCounter cannot be null");
        }
        this.searchCounter = Collections.unmodifiableMap(new LinkedHashMap<>(searchCounter));
        this.encryptedUpdateCounter = encryptedUpdateCounter;
    }

    public Map<KeywordToken, Integer> searchCounter() {
        return searchCounter;
    }

    public EncryptedUpdateCounter encryptedUpdateCounter() {
        return encryptedUpdateCounter;
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
                Objects.equals(encryptedUpdateCounter, state.encryptedUpdateCounter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(searchCounter, encryptedUpdateCounter);
    }

    @Override
    public String toString() {
        return "State[" +
                "searchCounter=" + searchCounter +
                ", encryptedUpdateCounter=" + encryptedUpdateCounter +
                ']';
    }
}
