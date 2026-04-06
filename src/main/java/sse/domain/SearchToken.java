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

public final class SearchToken implements Serializable {
    private final EpochSearchKey epochSearchKey;
    private final KeywordToken keywordToken;
    private final int searchCounter;

    public SearchToken(EpochSearchKey epochSearchKey, KeywordToken keywordToken, int searchCounter) {
        if (epochSearchKey == null || keywordToken == null || searchCounter < 0) {
            throw new IllegalArgumentException(
                    "epochSearchKey and keywordToken cannot be null and searchCounter cannot be negative");
        }
        this.epochSearchKey = epochSearchKey;
        this.keywordToken = keywordToken;
        this.searchCounter = searchCounter;
    }

    public EpochSearchKey epochSearchKey() {
        return epochSearchKey;
    }

    public KeywordToken keywordToken() {
        return keywordToken;
    }

    public int searchCounter() {
        return searchCounter;
    }

    public byte[] serialize() {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutput out = new ObjectOutputStream(bos)) {
            out.writeObject(this);
            out.flush();
            bos.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Error serializing search token", e);
        }
    }

    public static SearchToken deserialize(byte[] serializedToken) {
        if (serializedToken == null) {
            return null;
        }
        try (ByteArrayInputStream bis = new ByteArrayInputStream(serializedToken);
             ObjectInput in = new ObjectInputStream(bis)) {
            return (SearchToken) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("Error deserializing search token", e);
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
        SearchToken that = (SearchToken) o;
        return searchCounter == that.searchCounter &&
                Objects.equals(epochSearchKey, that.epochSearchKey) &&
                Objects.equals(keywordToken, that.keywordToken);
    }

    @Override
    public int hashCode() {
        return Objects.hash(epochSearchKey, keywordToken, searchCounter);
    }

    @Override
    public String toString() {
        return "SearchToken[" +
                "epochSearchKey=" + epochSearchKey +
                ", keywordToken=" + keywordToken +
                ", searchCounter=" + searchCounter +
                ']';
    }
}
