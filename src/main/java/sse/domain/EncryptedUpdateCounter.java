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

public final class EncryptedUpdateCounter implements Serializable {
    private final byte[] encryptedData;
    private final byte[] iv;

    public EncryptedUpdateCounter(byte[] encryptedData, byte[] iv) {
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
            throw new RuntimeException("Error serializing encrypted update counter", e);
        }
    }

    public static EncryptedUpdateCounter deserialize(byte[] serializedCounter) {
        if (serializedCounter == null) {
            return null;
        }
        try (ByteArrayInputStream bis = new ByteArrayInputStream(serializedCounter);
             ObjectInput in = new ObjectInputStream(bis)) {
            return (EncryptedUpdateCounter) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("Error deserializing encrypted update counter", e);
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
        EncryptedUpdateCounter that = (EncryptedUpdateCounter) o;
        return Arrays.equals(encryptedData, that.encryptedData) && Arrays.equals(iv, that.iv);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(encryptedData), Arrays.hashCode(iv));
    }

    @Override
    public String toString() {
        return "EncryptedUpdateCounter[" +
                "encryptedData=" + Arrays.toString(encryptedData) +
                ", iv=" + Arrays.toString(iv) +
                ']';
    }
}
