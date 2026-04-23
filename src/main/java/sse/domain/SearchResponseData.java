package sse.domain;

import java.util.ArrayList;
import java.util.List;

import vss.secretsharing.VerifiableShare;

public final class SearchResponseData {
    private final List<EncryptedUpdateTuple> encryptedTuples;
    private final List<VerifiableShare> updateTupleShares;

    public SearchResponseData() {
        this.encryptedTuples = new ArrayList<>();
        this.updateTupleShares = new ArrayList<>();
    }

    public void add(EncryptedUpdateTuple encryptedTuple, VerifiableShare updateTupleShare) {
        if (encryptedTuple == null || updateTupleShare == null) {
            throw new IllegalArgumentException("encryptedTuple and updateTupleShare cannot be null");
        }
        encryptedTuples.add(encryptedTuple);
        updateTupleShares.add(updateTupleShare);
    }

    public List<EncryptedUpdateTuple> encryptedTuples() {
        return encryptedTuples;
    }

    public VerifiableShare[] updateTupleSharesArray() {
        return updateTupleShares.toArray(new VerifiableShare[updateTupleShares.size()]);
    }
}
