package sse.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PlainSnapshotData implements Serializable {
    private final Map<KeywordToken, Integer> searchCounter;
    private final Map<KeywordToken, Integer> updateCounter;
    private final Map<KeywordToken, List<IndexAddress>> dbCache;
    private final Map<KeywordToken, Integer> nextSearchIndex;
    private final Map<IndexAddress, EncryptedUpdateTuple> invertedIndex;
    private final List<IndexAddress> updateTupleShareOrder;
    private final boolean hasTokenGenKeyShare;

    public PlainSnapshotData(Map<KeywordToken, Integer> searchCounter,
                             Map<KeywordToken, Integer> updateCounter,
                             Map<KeywordToken, List<IndexAddress>> dbCache,
                             Map<KeywordToken, Integer> nextSearchIndex,
                             Map<IndexAddress, EncryptedUpdateTuple> invertedIndex,
                             List<IndexAddress> updateTupleShareOrder,
                             boolean hasTokenGenKeyShare) {
        this.searchCounter = searchCounter;
        this.updateCounter = updateCounter;
        this.dbCache = dbCache;
        this.nextSearchIndex = nextSearchIndex;
        this.invertedIndex = invertedIndex;
        this.updateTupleShareOrder = updateTupleShareOrder;
        this.hasTokenGenKeyShare = hasTokenGenKeyShare;
    }

    public Map<KeywordToken, Integer> searchCounter() {
        return searchCounter;
    }

    public Map<KeywordToken, Integer> updateCounter() {
        return updateCounter;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PlainSnapshotData that = (PlainSnapshotData) o;
        return hasTokenGenKeyShare == that.hasTokenGenKeyShare &&
                Objects.equals(searchCounter, that.searchCounter) &&
                Objects.equals(updateCounter, that.updateCounter) &&
                Objects.equals(dbCache, that.dbCache) &&
                Objects.equals(nextSearchIndex, that.nextSearchIndex) &&
                Objects.equals(invertedIndex, that.invertedIndex) &&
                Objects.equals(updateTupleShareOrder, that.updateTupleShareOrder);
    }

    @Override
    public int hashCode() {
        return Objects.hash(searchCounter, updateCounter, dbCache, nextSearchIndex,
                invertedIndex, updateTupleShareOrder, hasTokenGenKeyShare);
    }

    @Override
    public String toString() {
        return "PlainSnapshotData[" +
                "searchCounter=" + searchCounter +
                ", updateCounter=" + updateCounter +
                ", dbCache=" + dbCache +
                ", nextSearchIndex=" + nextSearchIndex +
                ", invertedIndex=" + invertedIndex +
                ", updateTupleShareOrder=" + updateTupleShareOrder +
                ", hasTokenGenKeyShare=" + hasTokenGenKeyShare +
                ']';
    }
}
