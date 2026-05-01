package sse.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class PendingKeywordUpdates {

    private final String keyword;
    private final List<EncryptedUpdateTuple> encryptedTuples;

    public PendingKeywordUpdates(String keyword, List<EncryptedUpdateTuple> encryptedTuples) {
        if (keyword == null || keyword.trim().isEmpty() || encryptedTuples == null || encryptedTuples.isEmpty()) {
            throw new IllegalArgumentException("keyword and encryptedTuples cannot be null or empty");
        }

        List<EncryptedUpdateTuple> normalizedTuples = new ArrayList<EncryptedUpdateTuple>(encryptedTuples.size());
        for (EncryptedUpdateTuple encryptedTuple : encryptedTuples) {
            if (encryptedTuple == null) {
                throw new IllegalArgumentException("encryptedTuples cannot contain null values");
            }
            normalizedTuples.add(encryptedTuple);
        }

        this.keyword = keyword;
        this.encryptedTuples = Collections.unmodifiableList(normalizedTuples);
    }

    public String keyword() {
        return keyword;
    }

    public List<EncryptedUpdateTuple> encryptedTuples() {
        return encryptedTuples;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PendingKeywordUpdates that = (PendingKeywordUpdates) o;
        return Objects.equals(keyword, that.keyword)
                && Objects.equals(encryptedTuples, that.encryptedTuples);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyword, encryptedTuples);
    }

    @Override
    public String toString() {
        return "PendingKeywordUpdates[" +
                "keyword='" + keyword + '\'' +
                ", encryptedTuples=" + encryptedTuples +
                ']';
    }
}
