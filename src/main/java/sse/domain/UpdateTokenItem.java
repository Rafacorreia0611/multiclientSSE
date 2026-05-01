package sse.domain;

import java.io.Serializable;
import java.util.Objects;

public final class UpdateTokenItem implements Serializable {

    private final IndexAddress address;
    private final EncryptedUpdateTuple encryptedTuple;

    public UpdateTokenItem(IndexAddress address, EncryptedUpdateTuple encryptedTuple) {
        if (address == null || encryptedTuple == null) {
            throw new IllegalArgumentException("address and encryptedTuple cannot be null");
        }
        this.address = address;
        this.encryptedTuple = encryptedTuple;
    }

    public IndexAddress address() {
        return address;
    }

    public EncryptedUpdateTuple encryptedTuple() {
        return encryptedTuple;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        UpdateTokenItem that = (UpdateTokenItem) o;
        return Objects.equals(address, that.address)
                && Objects.equals(encryptedTuple, that.encryptedTuple);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, encryptedTuple);
    }

    @Override
    public String toString() {
        return "UpdateTokenItem[" +
                "address=" + address +
                ", encryptedTuple=" + encryptedTuple +
                ']';
    }
}
