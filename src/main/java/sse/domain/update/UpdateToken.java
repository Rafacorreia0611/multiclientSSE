package sse.domain.update;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import sse.domain.id.KeywordToken;
import sse.domain.state.KeywordState;

public final class UpdateToken implements Serializable {
    private final List<UpdateTokenItem> items;
    private final Map<KeywordToken, KeywordState> updatedKeywordStates;

    public UpdateToken(List<UpdateTokenItem> items, Map<KeywordToken, KeywordState> updatedKeywordStates) {
        if (items == null || items.isEmpty() || updatedKeywordStates == null || updatedKeywordStates.isEmpty()) {
            throw new IllegalArgumentException("items and updatedKeywordStates cannot be null or empty");
        }

        List<UpdateTokenItem> normalizedItems = new ArrayList<UpdateTokenItem>(items.size());
        for (UpdateTokenItem item : items) {
            if (item == null) {
                throw new IllegalArgumentException("items cannot contain null values");
            }
            normalizedItems.add(item);
        }

        this.items = Collections.unmodifiableList(normalizedItems);
        this.updatedKeywordStates = Collections.unmodifiableMap(new LinkedHashMap<>(updatedKeywordStates));
    }

    public List<UpdateTokenItem> items() {
        return items;
    }

    public Map<KeywordToken, KeywordState> updatedKeywordStates() {
        return updatedKeywordStates;
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
                Objects.equals(updatedKeywordStates, that.updatedKeywordStates);
    }

    @Override
    public int hashCode() {
        return Objects.hash(items, updatedKeywordStates);
    }

    @Override
    public String toString() {
        return "UpdateToken[" +
                "items=" + items +
                ", updatedKeywordStates=" + updatedKeywordStates +
                ']';
    }
}
