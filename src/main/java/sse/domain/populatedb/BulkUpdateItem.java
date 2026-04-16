package sse.domain.populatedb;

import java.io.Serializable;
import java.util.Objects;

import sse.domain.EncryptedUpdateTuple;
import sse.domain.IndexAddress;

public final class BulkUpdateItem implements Serializable {

    private final IndexAddress address;
    private final EncryptedUpdateTuple encryptedTuple;

    public BulkUpdateItem(IndexAddress address, EncryptedUpdateTuple encryptedTuple) {
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
        BulkUpdateItem that = (BulkUpdateItem) o;
        return Objects.equals(address, that.address)
                && Objects.equals(encryptedTuple, that.encryptedTuple);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, encryptedTuple);
    }

    @Override
    public String toString() {
        return "BulkUpdateItem[" +
                "address=" + address +
                ", encryptedTuple=" + encryptedTuple +
                ']';
    }
}
