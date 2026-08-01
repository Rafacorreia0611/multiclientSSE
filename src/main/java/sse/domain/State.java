package sse.domain;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class State implements Serializable {
    private final Map<KeywordToken, KeywordState> keywordStates;
    private final byte[] encodedTrapdoorPublicKey;

    public State(Map<KeywordToken, KeywordState> keywordStates, byte[] encodedTrapdoorPublicKey) {
        if (keywordStates == null || encodedTrapdoorPublicKey == null) {
            throw new IllegalArgumentException("keywordStates and encodedTrapdoorPublicKey cannot be null");
        }
        this.keywordStates = Collections.unmodifiableMap(new LinkedHashMap<>(keywordStates));
        this.encodedTrapdoorPublicKey = encodedTrapdoorPublicKey.clone();
    }

    public Map<KeywordToken, KeywordState> keywordStates() {
        return keywordStates;
    }

    public byte[] encodedTrapdoorPublicKey() {
        return encodedTrapdoorPublicKey.clone();
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
        return Objects.equals(keywordStates, state.keywordStates) &&
                Arrays.equals(encodedTrapdoorPublicKey, state.encodedTrapdoorPublicKey);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(keywordStates);
        result = 31 * result + Arrays.hashCode(encodedTrapdoorPublicKey);
        return result;
    }

    @Override
    public String toString() {
        return "State[" +
                "keywordStates=" + keywordStates +
                ", encodedTrapdoorPublicKeyLength=" + encodedTrapdoorPublicKey.length +
                ']';
    }
}
