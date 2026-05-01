package sse.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.crypto.SecretKey;

public final class PreparedKeywordUpdates {

    private final PendingKeywordUpdates pendingUpdates;
    private final List<SecretKey> tupleKeys;

    public PreparedKeywordUpdates(PendingKeywordUpdates pendingUpdates, List<SecretKey> tupleKeys) {
        if (pendingUpdates == null || tupleKeys == null || tupleKeys.isEmpty()) {
            throw new IllegalArgumentException("pendingUpdates and tupleKeys cannot be null or empty");
        }
        if (pendingUpdates.encryptedTuples().size() != tupleKeys.size()) {
            throw new IllegalArgumentException("pending update tuple count must match tuple key count");
        }

        List<SecretKey> normalizedTupleKeys = new ArrayList<SecretKey>(tupleKeys.size());
        for (SecretKey tupleKey : tupleKeys) {
            if (tupleKey == null) {
                throw new IllegalArgumentException("tupleKeys cannot contain null values");
            }
            normalizedTupleKeys.add(tupleKey);
        }

        this.pendingUpdates = pendingUpdates;
        this.tupleKeys = Collections.unmodifiableList(normalizedTupleKeys);
    }

    public PendingKeywordUpdates pendingUpdates() {
        return pendingUpdates;
    }

    public List<SecretKey> tupleKeys() {
        return tupleKeys;
    }
}
