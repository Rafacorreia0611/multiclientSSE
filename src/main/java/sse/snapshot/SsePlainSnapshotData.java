package sse.snapshot;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import sse.domain.EncryptedUpdateCounter;
import sse.domain.EncryptedUpdateTuple;
import sse.domain.IndexAddress;
import sse.domain.KeywordToken;

public final class SsePlainSnapshotData implements Serializable {
    private final Map<KeywordToken, Integer> searchCounter;
    private final EncryptedUpdateCounter encryptedUpdateCounter;
    private final Map<KeywordToken, List<IndexAddress>> dbCache;
    private final Map<KeywordToken, Integer> nextSearchIndex;
    private final Map<IndexAddress, EncryptedUpdateTuple> invertedIndex;
    private final List<IndexAddress> updateTupleShareOrder;
    private final boolean hasTokenGenKeyShare;
    private final boolean hasUpdateCounterKeyShare;

    public SsePlainSnapshotData(Map<KeywordToken, Integer> searchCounter,
                                EncryptedUpdateCounter encryptedUpdateCounter,
                                Map<KeywordToken, List<IndexAddress>> dbCache,
                                Map<KeywordToken, Integer> nextSearchIndex,
                                Map<IndexAddress, EncryptedUpdateTuple> invertedIndex,
                                List<IndexAddress> updateTupleShareOrder,
                                boolean hasTokenGenKeyShare,
                                boolean hasUpdateCounterKeyShare) {
        this.searchCounter = searchCounter;
        this.encryptedUpdateCounter = encryptedUpdateCounter;
        this.dbCache = dbCache;
        this.nextSearchIndex = nextSearchIndex;
        this.invertedIndex = invertedIndex;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SsePlainSnapshotData that = (SsePlainSnapshotData) o;
        return hasTokenGenKeyShare == that.hasTokenGenKeyShare &&
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
        return Objects.hash(searchCounter, encryptedUpdateCounter, dbCache, nextSearchIndex,
                invertedIndex, updateTupleShareOrder, hasTokenGenKeyShare, hasUpdateCounterKeyShare);
    }

    @Override
    public String toString() {
        return "SsePlainSnapshotData[" +
                "searchCounter=" + searchCounter +
                ", encryptedUpdateCounter=" + encryptedUpdateCounter +
                ", dbCache=" + dbCache +
                ", nextSearchIndex=" + nextSearchIndex +
                ", invertedIndex=" + invertedIndex +
                ", updateTupleShareOrder=" + updateTupleShareOrder +
                ", hasTokenGenKeyShare=" + hasTokenGenKeyShare +
                ", hasUpdateCounterKeyShare=" + hasUpdateCounterKeyShare +
                ']';
    }
}
