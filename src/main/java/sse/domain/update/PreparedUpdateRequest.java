package sse.domain.update;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.crypto.SecretKey;

public final class PreparedUpdateRequest {

    private final UpdateToken updateToken;
    private final List<SecretKey> tupleKeys;

    public PreparedUpdateRequest(UpdateToken updateToken, List<SecretKey> tupleKeys) {
        if (updateToken == null || tupleKeys == null || tupleKeys.isEmpty()) {
            throw new IllegalArgumentException("updateToken and tupleKeys cannot be null or empty");
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
    }

    public UpdateToken updateToken() {
        return updateToken;
    }

    public List<SecretKey> tupleKeys() {
        return tupleKeys;
    }
}
