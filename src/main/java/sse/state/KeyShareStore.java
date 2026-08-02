package sse.state;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import sse.domain.IndexAddress;
import vss.secretsharing.VerifiableShare;

public final class KeyShareStore {

    private Map<IndexAddress, VerifiableShare> updateTupleShares;
    private VerifiableShare masterKeyShare;
    private VerifiableShare trapdoorPrivateKeyShare;

    public KeyShareStore() {
        this.updateTupleShares = new LinkedHashMap<>();
        this.masterKeyShare = null;
        this.trapdoorPrivateKeyShare = null;
    }

    public VerifiableShare getUpdateTupleShare(IndexAddress address) {
        return updateTupleShares.get(address);
    }

    public void putUpdateTupleShare(IndexAddress address, VerifiableShare share) {
        updateTupleShares.put(address, share);
    }

    public VerifiableShare masterKeyShare() {
        return masterKeyShare;
    }

    public boolean hasMasterKeyShare() {
        return masterKeyShare != null;
    }

    public void setMasterKeyShare(VerifiableShare share) {
        masterKeyShare = share;
    }

    public VerifiableShare trapdoorPrivateKeyShare() {
        return trapdoorPrivateKeyShare;
    }

    public boolean hasTrapdoorPrivateKeyShare() {
        return trapdoorPrivateKeyShare != null;
    }

    public void setTrapdoorPrivateKeyShare(VerifiableShare share) {
        trapdoorPrivateKeyShare = share;
    }

    public List<IndexAddress> snapshotUpdateTupleShareOrder() {
        return new ArrayList<>(updateTupleShares.keySet());
    }

    public VerifiableShare[] sharesInOrder(List<IndexAddress> updateTupleShareOrder, boolean includeMasterKeyShare,
                                           boolean includeTrapdoorPrivateKeyShare) {
        int size = updateTupleShareOrder.size()
                + (includeMasterKeyShare ? 1 : 0)
                + (includeTrapdoorPrivateKeyShare ? 1 : 0);
        if (size == 0) {
            return new VerifiableShare[0];
        }

        VerifiableShare[] shares = new VerifiableShare[size];
        int index = 0;
        if (includeMasterKeyShare) {
            shares[index++] = masterKeyShare;
        }
        if (includeTrapdoorPrivateKeyShare) {
            shares[index++] = trapdoorPrivateKeyShare;
        }
        for (IndexAddress address : updateTupleShareOrder) {
            shares[index++] = updateTupleShares.get(address);
        }
        return shares;
    }

    public void restoreUpdateTupleShares(Map<IndexAddress, VerifiableShare> shares) {
        this.updateTupleShares = new LinkedHashMap<>(shares);
    }
}
