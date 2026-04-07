package sse.state;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sse.domain.IndexAddress;
import vss.secretsharing.VerifiableShare;

public final class KeyShareStore {

    private Map<IndexAddress, VerifiableShare> updateTupleShares;
    private VerifiableShare tokenGenKeyShare;
    private VerifiableShare updateCounterKeyShare;

    public KeyShareStore() {
        this.updateTupleShares = new HashMap<>();
        this.tokenGenKeyShare = null;
        this.updateCounterKeyShare = null;
    }

    public VerifiableShare getUpdateTupleShare(IndexAddress address) {
        return updateTupleShares.get(address);
    }

    public void putUpdateTupleShare(IndexAddress address, VerifiableShare share) {
        updateTupleShares.put(address, share);
    }

    public boolean initializeTokenGenKeyShare(VerifiableShare share) {
        if (tokenGenKeyShare != null) {
            return false;
        }
        tokenGenKeyShare = share;
        return true;
    }

    public VerifiableShare tokenGenKeyShare() {
        return tokenGenKeyShare;
    }

    public boolean hasTokenGenKeyShare() {
        return tokenGenKeyShare != null;
    }

    public void setTokenGenKeyShare(VerifiableShare share) {
        tokenGenKeyShare = share;
    }

    public VerifiableShare updateCounterKeyShare() {
        return updateCounterKeyShare;
    }

    public boolean hasUpdateCounterKeyShare() {
        return updateCounterKeyShare != null;
    }

    public void setUpdateCounterKeyShare(VerifiableShare share) {
        updateCounterKeyShare = share;
    }

    public List<IndexAddress> snapshotUpdateTupleShareOrder() {
        return new ArrayList<>(updateTupleShares.keySet());
    }

    public VerifiableShare[] sharesInOrder(List<IndexAddress> updateTupleShareOrder, boolean includeTokenGenKeyShare,
                                           boolean includeUpdateCounterKeyShare) {
        int size = updateTupleShareOrder.size()
                + (includeTokenGenKeyShare ? 1 : 0)
                + (includeUpdateCounterKeyShare ? 1 : 0);
        if (size == 0) {
            return new VerifiableShare[0];
        }

        VerifiableShare[] shares = new VerifiableShare[size];
        int index = 0;
        if (includeTokenGenKeyShare) {
            shares[index++] = tokenGenKeyShare;
        }
        if (includeUpdateCounterKeyShare) {
            shares[index++] = updateCounterKeyShare;
        }
        for (IndexAddress address : updateTupleShareOrder) {
            shares[index++] = updateTupleShares.get(address);
        }
        return shares;
    }

    public void restoreUpdateTupleShares(Map<IndexAddress, VerifiableShare> shares) {
        this.updateTupleShares = new HashMap<>(shares);
    }
}
