package sse.domain.setup;

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

import sse.domain.state.EncryptedKeywordAddressMap;

public final class InitialStatePayload implements Serializable {

    private final byte[] encodedTrapdoorPublicKey;
    private final EncryptedKeywordAddressMap encryptedKeywordAddressMap;

    public InitialStatePayload(byte[] encodedTrapdoorPublicKey,
                               EncryptedKeywordAddressMap encryptedKeywordAddressMap) {
        if (encodedTrapdoorPublicKey == null || encryptedKeywordAddressMap == null) {
            throw new IllegalArgumentException("encodedTrapdoorPublicKey and encryptedKeywordAddressMap cannot be null");
        }
        this.encodedTrapdoorPublicKey = Arrays.copyOf(encodedTrapdoorPublicKey, encodedTrapdoorPublicKey.length);
        this.encryptedKeywordAddressMap = encryptedKeywordAddressMap;
    }

    public byte[] encodedTrapdoorPublicKey() {
        return Arrays.copyOf(encodedTrapdoorPublicKey, encodedTrapdoorPublicKey.length);
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
            throw new RuntimeException("Error serializing initial state payload", e);
        }
    }

    public static InitialStatePayload deserialize(byte[] serializedPayload) {
        if (serializedPayload == null) {
            return null;
        }
        try (ByteArrayInputStream bis = new ByteArrayInputStream(serializedPayload);
             ObjectInput in = new ObjectInputStream(bis)) {
            Object obj = in.readObject();
            if (!(obj instanceof InitialStatePayload)) {
                throw new RuntimeException("Initial state payload has invalid type");
            }
            return (InitialStatePayload) obj;
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("Error deserializing initial state payload", e);
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
        InitialStatePayload that = (InitialStatePayload) o;
        return Arrays.equals(encodedTrapdoorPublicKey, that.encodedTrapdoorPublicKey)
                && Objects.equals(encryptedKeywordAddressMap, that.encryptedKeywordAddressMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(encodedTrapdoorPublicKey), encryptedKeywordAddressMap);
    }

    @Override
    public String toString() {
        return "InitialStatePayload[" +
                "encodedTrapdoorPublicKeyLength=" + encodedTrapdoorPublicKey.length +
                ", encryptedKeywordAddressMap=" + encryptedKeywordAddressMap +
                ']';
    }
}
