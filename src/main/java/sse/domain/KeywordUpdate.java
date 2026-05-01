package sse.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class KeywordUpdate {

    private final String keyword;
    private final List<String> docIds;
    private final UpdateOp operation;

    public KeywordUpdate(String keyword, List<String> docIds, UpdateOp operation) {
        if (keyword == null || keyword.trim().isEmpty() || docIds == null || docIds.isEmpty() || operation == null) {
            throw new IllegalArgumentException("keyword, docIds and operation cannot be null or empty");
        }

        List<String> normalizedDocIds = new ArrayList<String>(docIds.size());
        for (String docId : docIds) {
            if (docId == null || docId.trim().isEmpty()) {
                throw new IllegalArgumentException("docIds cannot contain null or empty values");
            }
            normalizedDocIds.add(docId.trim());
        }

        this.keyword = keyword.trim();
        this.docIds = Collections.unmodifiableList(normalizedDocIds);
        this.operation = operation;
    }

    public String keyword() {
        return keyword;
    }

    public List<String> docIds() {
        return docIds;
    }

    public UpdateOp operation() {
        return operation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        KeywordUpdate that = (KeywordUpdate) o;
        return Objects.equals(keyword, that.keyword)
                && Objects.equals(docIds, that.docIds)
                && operation == that.operation;
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyword, docIds, operation);
    }
}
