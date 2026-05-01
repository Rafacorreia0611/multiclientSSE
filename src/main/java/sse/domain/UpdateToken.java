package sse.domain;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class UpdateToken implements Serializable {
    private final List<UpdateTokenItem> items;
    private final EncryptedUpdateCounter encryptedUpdateCounter;

    public UpdateToken(List<UpdateTokenItem> items, EncryptedUpdateCounter encryptedUpdateCounter) {
        if (items == null || items.isEmpty() || encryptedUpdateCounter == null) {
            throw new IllegalArgumentException("items cannot be null or empty and encryptedUpdateCounter cannot be null");
        }

        List<UpdateTokenItem> normalizedItems = new ArrayList<UpdateTokenItem>(items.size());
        for (UpdateTokenItem item : items) {
            if (item == null) {
                throw new IllegalArgumentException("items cannot contain null values");
            }
            normalizedItems.add(item);
        }

        this.items = Collections.unmodifiableList(normalizedItems);
        this.encryptedUpdateCounter = encryptedUpdateCounter;
    }

    public List<UpdateTokenItem> items() {
        return items;
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
        return Objects.equals(items, that.items) &&
                Objects.equals(encryptedUpdateCounter, that.encryptedUpdateCounter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(items, encryptedUpdateCounter);
    }

    @Override
    public String toString() {
        return "UpdateToken[" +
                "items=" + items +
                ", encryptedUpdateCounter=" + encryptedUpdateCounter +
                ']';
    }
}
