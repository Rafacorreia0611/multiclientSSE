package sse.domain;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Objects;

public final class UpdateToken implements Serializable {
    private final IndexAddress address;
    private final EncryptedUpdateTuple encryptedTuple;
    private final EncryptedUpdateCounter encryptedUpdateCounter;

    public UpdateToken(IndexAddress address, EncryptedUpdateTuple encryptedTuple,
                       EncryptedUpdateCounter encryptedUpdateCounter) {
        if (address == null || encryptedTuple == null || encryptedUpdateCounter == null) {
            throw new IllegalArgumentException("address, encryptedTuple or encryptedUpdateCounter cannot be null");
        }
        this.address = address;
        this.encryptedTuple = encryptedTuple;
        this.encryptedUpdateCounter = encryptedUpdateCounter;
    }

    public IndexAddress address() {
        return address;
    }

    public EncryptedUpdateTuple encryptedTuple() {
        return encryptedTuple;
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
                Objects.equals(encryptedUpdateCounter, that.encryptedUpdateCounter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, encryptedTuple, encryptedUpdateCounter);
    }

    @Override
    public String toString() {
        return "UpdateToken[" +
                "address=" + address +
                ", encryptedTuple=" + encryptedTuple +
                ", encryptedUpdateCounter=" + encryptedUpdateCounter +
                ']';
    }
}
