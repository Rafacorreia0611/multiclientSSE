package sse.domain.state;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

public final class State implements Serializable {
    private final byte[] encodedTrapdoorPublicKey;
    private final EncryptedKeywordLocationMap encryptedKeywordLocationMap;

    public State(byte[] encodedTrapdoorPublicKey, EncryptedKeywordLocationMap encryptedKeywordLocationMap) {
        if (encodedTrapdoorPublicKey == null || encryptedKeywordLocationMap == null) {
            throw new IllegalArgumentException(
                    "encodedTrapdoorPublicKey and encryptedKeywordLocationMap cannot be null"
            );
        }
        this.encodedTrapdoorPublicKey = encodedTrapdoorPublicKey.clone();
        this.encryptedKeywordLocationMap = encryptedKeywordLocationMap;
    }

    public byte[] encodedTrapdoorPublicKey() {
        return encodedTrapdoorPublicKey.clone();
    }

    public EncryptedKeywordLocationMap encryptedKeywordLocationMap() {
        return encryptedKeywordLocationMap;
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
        return Arrays.equals(encodedTrapdoorPublicKey, state.encodedTrapdoorPublicKey) &&
                Objects.equals(encryptedKeywordLocationMap, state.encryptedKeywordLocationMap);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(encryptedKeywordLocationMap);
        result = 31 * result + Arrays.hashCode(encodedTrapdoorPublicKey);
        return result;
    }

    @Override
    public String toString() {
        return "State[" +
                "encodedTrapdoorPublicKeyLength=" + encodedTrapdoorPublicKey.length +
                ", encryptedKeywordLocationMap=" + encryptedKeywordLocationMap +
                ']';
    }
}
