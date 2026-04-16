package sse.dataset.enron;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class KeywordDocIdsEntry {

    private final String keyword;
    private final List<String> docIds;

    public KeywordDocIdsEntry(String keyword, List<String> docIds) {
        if (keyword == null || keyword.trim().isEmpty()) {
            throw new IllegalArgumentException("keyword cannot be null or empty");
        }
        if (docIds == null || docIds.isEmpty()) {
            throw new IllegalArgumentException("docIds cannot be null or empty");
        }

        List<String> normalizedDocIds = new ArrayList<String>(docIds.size());
        for (String docId : docIds) {
            if (docId == null || docId.trim().isEmpty()) {
                throw new IllegalArgumentException("docIds cannot contain null or empty values");
            }
            normalizedDocIds.add(docId);
        }

        this.keyword = keyword;
        this.docIds = Collections.unmodifiableList(normalizedDocIds);
    }

    public String keyword() {
        return keyword;
    }

    public List<String> docIds() {
        return docIds;
    }
}
