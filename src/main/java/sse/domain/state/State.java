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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import sse.domain.id.KeywordToken;

public final class State implements Serializable {
    private final Map<KeywordToken, KeywordState> keywordStates;
    private final byte[] encodedTrapdoorPublicKey;
    private final EncryptedKeywordAddressMap encryptedKeywordAddressMap;

    public State(Map<KeywordToken, KeywordState> keywordStates, byte[] encodedTrapdoorPublicKey,
                 EncryptedKeywordAddressMap encryptedKeywordAddressMap) {
        if (keywordStates == null || encodedTrapdoorPublicKey == null || encryptedKeywordAddressMap == null) {
            throw new IllegalArgumentException(
                    "keywordStates, encodedTrapdoorPublicKey, and encryptedKeywordAddressMap cannot be null"
            );
        }
        this.keywordStates = Collections.unmodifiableMap(new LinkedHashMap<>(keywordStates));
        this.encodedTrapdoorPublicKey = encodedTrapdoorPublicKey.clone();
        this.encryptedKeywordAddressMap = encryptedKeywordAddressMap;
    }

    public Map<KeywordToken, KeywordState> keywordStates() {
        return keywordStates;
    }

    public byte[] encodedTrapdoorPublicKey() {
        return encodedTrapdoorPublicKey.clone();
    }

    public EncryptedKeywordAddressMap encryptedKeywordAddressMap() {
        return encryptedKeywordAddressMap;
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
                Arrays.equals(encodedTrapdoorPublicKey, state.encodedTrapdoorPublicKey) &&
                Objects.equals(encryptedKeywordAddressMap, state.encryptedKeywordAddressMap);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(keywordStates, encryptedKeywordAddressMap);
        result = 31 * result + Arrays.hashCode(encodedTrapdoorPublicKey);
        return result;
    }

    @Override
    public String toString() {
        return "State[" +
                "keywordStates=" + keywordStates +
                ", encodedTrapdoorPublicKeyLength=" + encodedTrapdoorPublicKey.length +
                ", encryptedKeywordAddressMap=" + encryptedKeywordAddressMap +
                ']';
    }
}
