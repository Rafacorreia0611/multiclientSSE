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

public final class EncryptedKeywordLocationMap implements Serializable {

    private final byte[] encryptedData;
    private final byte[] iv;

    public EncryptedKeywordLocationMap(byte[] encryptedData, byte[] iv) {
        if (encryptedData == null || iv == null) {
            throw new IllegalArgumentException("encryptedData and iv cannot be null");
        }
        this.encryptedData = Arrays.copyOf(encryptedData, encryptedData.length);
        this.iv = Arrays.copyOf(iv, iv.length);
    }

    public byte[] encryptedData() {
        return Arrays.copyOf(encryptedData, encryptedData.length);
    }

    public byte[] iv() {
        return Arrays.copyOf(iv, iv.length);
    }

    public byte[] serialize() {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutput out = new ObjectOutputStream(bos)) {
            out.writeObject(this);
            out.flush();
            bos.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Error serializing encrypted keyword location map", e);
        }
    }

    public static EncryptedKeywordLocationMap deserialize(byte[] serializedMap) {
        if (serializedMap == null) {
            return null;
        }
        try (ByteArrayInputStream bis = new ByteArrayInputStream(serializedMap);
             ObjectInput in = new ObjectInputStream(bis)) {
            return (EncryptedKeywordLocationMap) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("Error deserializing encrypted keyword location map", e);
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
        EncryptedKeywordLocationMap that = (EncryptedKeywordLocationMap) o;
        return Arrays.equals(encryptedData, that.encryptedData)
                && Arrays.equals(iv, that.iv);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(encryptedData), Arrays.hashCode(iv));
    }

    @Override
    public String toString() {
        return "EncryptedKeywordLocationMap[" +
                "encryptedDataLength=" + encryptedData.length +
                ", ivLength=" + iv.length +
                ']';
    }
}
