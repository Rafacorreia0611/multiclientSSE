package sse.domain;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

public final class UpdateToken implements Serializable {
    private final IndexAddress address;
    private final EncryptedUpdateTuple encryptedTuple;
    private final Map<KeywordToken, Integer> updateCounter;

    public UpdateToken(IndexAddress address, EncryptedUpdateTuple encryptedTuple,
                       Map<KeywordToken, Integer> updateCounter) {
        if (address == null || encryptedTuple == null || updateCounter == null) {
            throw new IllegalArgumentException("address, encryptedTuple or updateCounter cannot be null");
        }
        this.address = address;
        this.encryptedTuple = encryptedTuple;
        this.updateCounter = updateCounter;
    }

    public IndexAddress address() {
        return address;
    }

    public EncryptedUpdateTuple encryptedTuple() {
        return encryptedTuple;
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
            throw new RuntimeException("Error serializing update token", e);
        }
    }

    public static UpdateToken deserialize(byte[] serializedToken) {
        if (serializedToken == null) {
            return null;
        }
        try (ByteArrayInputStream bis = new ByteArrayInputStream(serializedToken);
             ObjectInput in = new ObjectInputStream(bis)) {
            return (UpdateToken) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("Error deserializing update token", e);
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
        UpdateToken that = (UpdateToken) o;
        return Objects.equals(address, that.address) &&
                Objects.equals(encryptedTuple, that.encryptedTuple) &&
                Objects.equals(updateCounter, that.updateCounter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, encryptedTuple, updateCounter);
    }

    @Override
    public String toString() {
        return "UpdateToken[" +
                "address=" + address +
                ", encryptedTuple=" + encryptedTuple +
                ", updateCounter=" + updateCounter +
                ']';
    }
}
