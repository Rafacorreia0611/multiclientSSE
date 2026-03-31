package sse.model;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

import javax.crypto.SealedObject;

public final class EncryptedUpdateTuple implements Serializable {
    private final SealedObject encryptedTuple;
    private final byte[] iv;

    public EncryptedUpdateTuple(SealedObject encryptedTuple, byte[] iv) {
        if (encryptedTuple == null || iv == null) {
            throw new IllegalArgumentException("encryptedTuple and iv cannot be null");
        }
        this.encryptedTuple = encryptedTuple;
        this.iv = Arrays.copyOf(iv, iv.length);
    }

    public SealedObject encryptedTuple() {
        return encryptedTuple;
    }

    public byte[] iv() {
        return Arrays.copyOf(iv, iv.length);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EncryptedUpdateTuple that = (EncryptedUpdateTuple) o;
        return Objects.equals(encryptedTuple, that.encryptedTuple) && Arrays.equals(iv, that.iv);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(encryptedTuple);
        result = 31 * result + Arrays.hashCode(iv);
        return result;
    }

    @Override
    public String toString() {
        return "EncryptedUpdateTuple[" +
                "encryptedTuple=" + encryptedTuple +
                ", iv=" + Arrays.toString(iv) +
                ']';
    }
}
