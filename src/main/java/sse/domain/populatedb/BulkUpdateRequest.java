package sse.domain.populatedb;

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

import sse.domain.EncryptedUpdateCounter;

public final class BulkUpdateRequest implements Serializable {

    private final List<BulkUpdateItem> items;
    private final EncryptedUpdateCounter encryptedUpdateCounter;

    public BulkUpdateRequest(List<BulkUpdateItem> items, EncryptedUpdateCounter encryptedUpdateCounter) {
        if (items == null || items.isEmpty() || encryptedUpdateCounter == null) {
            throw new IllegalArgumentException("items cannot be null or empty and encryptedUpdateCounter cannot be null");
        }

        List<BulkUpdateItem> normalizedItems = new ArrayList<BulkUpdateItem>(items.size());
        for (BulkUpdateItem item : items) {
            if (item == null) {
                throw new IllegalArgumentException("items cannot contain null values");
            }
            normalizedItems.add(item);
        }

        this.items = Collections.unmodifiableList(normalizedItems);
        this.encryptedUpdateCounter = encryptedUpdateCounter;
    }

    public List<BulkUpdateItem> items() {
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
            throw new RuntimeException("Error serializing bulk update request", e);
        }
    }

    public static BulkUpdateRequest deserialize(byte[] serializedRequest) {
        if (serializedRequest == null) {
            return null;
        }
        try (ByteArrayInputStream bis = new ByteArrayInputStream(serializedRequest);
             ObjectInput in = new ObjectInputStream(bis)) {
            return (BulkUpdateRequest) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("Error deserializing bulk update request", e);
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
        BulkUpdateRequest that = (BulkUpdateRequest) o;
        return Objects.equals(items, that.items)
                && Objects.equals(encryptedUpdateCounter, that.encryptedUpdateCounter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(items, encryptedUpdateCounter);
    }

    @Override
    public String toString() {
        return "BulkUpdateRequest[" +
                "items=" + items +
                ", encryptedUpdateCounter=" + encryptedUpdateCounter +
                ']';
    }
}
