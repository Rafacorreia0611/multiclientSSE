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
import java.util.Objects;

public final class SearchToken implements Serializable {
    private final byte[] keywordAddressKey;
    private final SearchTokenValue currentToken;
    private final int counter;

    public SearchToken(byte[] keywordAddressKey, SearchTokenValue currentToken, int counter) {
        if (keywordAddressKey == null || currentToken == null || counter <= 0) {
            throw new IllegalArgumentException(
                    "keywordAddressKey and currentToken cannot be null and counter must be greater than zero");
        }
        this.keywordAddressKey = keywordAddressKey.clone();
        this.currentToken = currentToken;
        this.counter = counter;
    }

    public byte[] keywordAddressKey() {
        return keywordAddressKey.clone();
    }

    public SearchTokenValue currentToken() {
        return currentToken;
    }

    public int counter() {
        return counter;
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
        return counter == that.counter
                && Arrays.equals(keywordAddressKey, that.keywordAddressKey)
                && Objects.equals(currentToken, that.currentToken);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(currentToken, counter);
        result = 31 * result + Arrays.hashCode(keywordAddressKey);
        return result;
    }

    @Override
    public String toString() {
        return "SearchToken[" +
                "keywordAddressKeyLength=" + keywordAddressKey.length +
                ", currentToken=" + currentToken +
                ", counter=" + counter +
                ']';
    }
}
