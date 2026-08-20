package sse.domain.update;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.crypto.SecretKey;

import sse.domain.state.KeywordState;

public final class PreparedUpdateRequest {

    private final UpdateToken updateToken;
    private final List<SecretKey> tupleKeys;
    private final KeywordState keywordState;

    public PreparedUpdateRequest(UpdateToken updateToken, List<SecretKey> tupleKeys, KeywordState keywordState) {
        if (updateToken == null || tupleKeys == null || tupleKeys.isEmpty() || keywordState == null) {
            throw new IllegalArgumentException("updateToken, tupleKeys and keywordState cannot be null or empty");
        }
        if (updateToken.items().size() != tupleKeys.size()) {
            throw new IllegalArgumentException("update token item count must match tuple key count");
        }

        List<SecretKey> normalizedTupleKeys = new ArrayList<SecretKey>(tupleKeys.size());
        for (SecretKey tupleKey : tupleKeys) {
            if (tupleKey == null) {
                throw new IllegalArgumentException("tupleKeys cannot contain null values");
            }
            normalizedTupleKeys.add(tupleKey);
        }

        this.updateToken = updateToken;
        this.tupleKeys = Collections.unmodifiableList(normalizedTupleKeys);
        this.keywordState = keywordState;
    }

    public UpdateToken updateToken() {
        return updateToken;
    }

    public List<SecretKey> tupleKeys() {
        return tupleKeys;
    }

    public KeywordState keywordState() {
        return keywordState;
    }
}
