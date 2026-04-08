package sse.snapshot;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import sse.domain.EncryptedUpdateCounter;
import sse.domain.EncryptedUpdateTuple;
import sse.domain.IndexAddress;
import sse.domain.KeywordToken;
import vss.secretsharing.VerifiableShare;

public final class SsePlainSnapshotData implements Serializable {
    private final Map<KeywordToken, Integer> searchCounter;
    private final EncryptedUpdateCounter encryptedUpdateCounter;
    private final int activeClientId;
    private final int blockedStateRequestsWhileActive;
    private final Map<KeywordToken, List<IndexAddress>> dbCache;
    private final Map<KeywordToken, Integer> nextSearchIndex;
    private final Map<IndexAddress, EncryptedUpdateTuple> invertedIndex;
    private final List<IndexAddress> updateTupleShareOrder;
    private final boolean hasTokenGenKeyShare;
    private final boolean hasUpdateCounterKeyShare;

    public SsePlainSnapshotData(Map<KeywordToken, Integer> searchCounter,
                                EncryptedUpdateCounter encryptedUpdateCounter,
                                int activeClientId,
                                int blockedStateRequestsWhileActive,
                                Map<KeywordToken, List<IndexAddress>> dbCache,
                                Map<KeywordToken, Integer> nextSearchIndex,
                                Map<IndexAddress, EncryptedUpdateTuple> invertedIndex,
                                List<IndexAddress> updateTupleShareOrder,
                                boolean hasTokenGenKeyShare,
                                boolean hasUpdateCounterKeyShare) {
        this.searchCounter = searchCounter;
        this.encryptedUpdateCounter = encryptedUpdateCounter;
        this.activeClientId = activeClientId;
        this.blockedStateRequestsWhileActive = blockedStateRequestsWhileActive;
        this.dbCache = new LinkedHashMap<>(dbCache);
        this.nextSearchIndex = new LinkedHashMap<>(nextSearchIndex);
        this.invertedIndex = new LinkedHashMap<>(invertedIndex);
        this.updateTupleShareOrder = updateTupleShareOrder;
        this.hasTokenGenKeyShare = hasTokenGenKeyShare;
        this.hasUpdateCounterKeyShare = hasUpdateCounterKeyShare;
    }

    public Map<KeywordToken, Integer> searchCounter() {
        return searchCounter;
    }

    public EncryptedUpdateCounter encryptedUpdateCounter() {
        return encryptedUpdateCounter;
    }

    public int activeClientId() {
        return activeClientId;
    }

    public int blockedStateRequestsWhileActive() {
        return blockedStateRequestsWhileActive;
    }

    public Map<KeywordToken, List<IndexAddress>> dbCache() {
        return dbCache;
    }

    public Map<KeywordToken, Integer> nextSearchIndex() {
        return nextSearchIndex;
    }

    public Map<IndexAddress, EncryptedUpdateTuple> invertedIndex() {
        return invertedIndex;
    }

    public List<IndexAddress> updateTupleShareOrder() {
        return updateTupleShareOrder;
    }

    public boolean hasTokenGenKeyShare() {
        return hasTokenGenKeyShare;
    }

    public boolean hasUpdateCounterKeyShare() {
        return hasUpdateCounterKeyShare;
    }

    public byte[] serialize() {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutput out = new ObjectOutputStream(bos)) {
            out.writeObject(this);
            out.flush();
            bos.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Error serializing snapshot", e);
        }
    }

    public static SsePlainSnapshotData deserialize(byte[] plainData) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(plainData);
             ObjectInput in = new ObjectInputStream(bis)) {
            return (SsePlainSnapshotData) in.readObject();
        } catch (IOException e) {
            throw new RuntimeException("Error deserializing snapshot", e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Error deserializing snapshot", e);
        }
    }

    public VerifiableShare tokenGenKeyShare(VerifiableShare[] shares) {
        if (!hasTokenGenKeyShare) {
            return null;
        }
        if (shares == null || shares.length == 0) {
            throw new IllegalStateException("Snapshot is missing token generation key share");
        }
        return shares[0];
    }

    public VerifiableShare updateCounterKeyShare(VerifiableShare[] shares) {
        if (!hasUpdateCounterKeyShare) {
            return null;
        }
        int index = hasTokenGenKeyShare ? 1 : 0;
        if (shares == null || index >= shares.length) {
            throw new IllegalStateException("Snapshot is missing update counter key share");
        }
        return shares[index];
    }

    public Map<IndexAddress, VerifiableShare> updateTupleShares(VerifiableShare[] shares) {
        int index = 0;
        if (hasTokenGenKeyShare) {
            index++;
        }
        if (hasUpdateCounterKeyShare) {
            index++;
        }
        Map<IndexAddress, VerifiableShare> result = new LinkedHashMap<IndexAddress, VerifiableShare>();
        for (IndexAddress address : updateTupleShareOrder) {
            if (shares == null || index >= shares.length) {
                throw new IllegalStateException("Snapshot is missing update tuple shares");
            }
            result.put(address, shares[index++]);
        }
        if (shares != null && index != shares.length) {
            throw new IllegalStateException("Snapshot contains unexpected extra shares");
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SsePlainSnapshotData that = (SsePlainSnapshotData) o;
        return activeClientId == that.activeClientId &&
                blockedStateRequestsWhileActive == that.blockedStateRequestsWhileActive &&
                hasTokenGenKeyShare == that.hasTokenGenKeyShare &&
                hasUpdateCounterKeyShare == that.hasUpdateCounterKeyShare &&
                Objects.equals(searchCounter, that.searchCounter) &&
                Objects.equals(encryptedUpdateCounter, that.encryptedUpdateCounter) &&
                Objects.equals(dbCache, that.dbCache) &&
                Objects.equals(nextSearchIndex, that.nextSearchIndex) &&
                Objects.equals(invertedIndex, that.invertedIndex) &&
                Objects.equals(updateTupleShareOrder, that.updateTupleShareOrder);
    }

    @Override
    public int hashCode() {
        return Objects.hash(searchCounter, encryptedUpdateCounter, activeClientId, blockedStateRequestsWhileActive,
                dbCache, nextSearchIndex,
                invertedIndex, updateTupleShareOrder, hasTokenGenKeyShare, hasUpdateCounterKeyShare);
    }

    @Override
    public String toString() {
        return "SsePlainSnapshotData[" +
                "searchCounter=" + searchCounter +
                ", encryptedUpdateCounter=" + encryptedUpdateCounter +
                ", activeClientId=" + activeClientId +
                ", blockedStateRequestsWhileActive=" + blockedStateRequestsWhileActive +
                ", dbCache=" + dbCache +
                ", nextSearchIndex=" + nextSearchIndex +
                ", invertedIndex=" + invertedIndex +
                ", updateTupleShareOrder=" + updateTupleShareOrder +
                ", hasTokenGenKeyShare=" + hasTokenGenKeyShare +
                ", hasUpdateCounterKeyShare=" + hasUpdateCounterKeyShare +
                ']';
    }
}
