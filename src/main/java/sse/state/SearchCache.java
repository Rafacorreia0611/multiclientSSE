package sse.state;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import sse.domain.IndexAddress;
import sse.domain.KeywordToken;

public final class SearchCache {

    private Map<KeywordToken, List<IndexAddress>> cachedAddressesByKeyword;
    private Map<KeywordToken, Integer> nextSearchIndexByKeyword;

    public SearchCache() {
        this.cachedAddressesByKeyword = new HashMap<>();
        this.nextSearchIndexByKeyword = new HashMap<>();
    }

    public List<IndexAddress> cachedAddressesFor(KeywordToken keywordToken) {
        cachedAddressesByKeyword.computeIfAbsent(keywordToken, ignored -> new LinkedList<>());
        return cachedAddressesByKeyword.get(keywordToken);
    }

    public int nextSearchIndexFor(KeywordToken keywordToken) {
        nextSearchIndexByKeyword.putIfAbsent(keywordToken, 1);
        return nextSearchIndexByKeyword.get(keywordToken);
    }

    public void cacheAddress(KeywordToken keywordToken, IndexAddress address) {
        cachedAddressesFor(keywordToken).add(address);
    }

    public void advanceNextSearchIndex(KeywordToken keywordToken, int nextIndex) {
        nextSearchIndexByKeyword.put(keywordToken, nextIndex);
    }

    public Map<KeywordToken, List<IndexAddress>> snapshotCachedAddresses() {
        Map<KeywordToken, List<IndexAddress>> copy = new HashMap<>(cachedAddressesByKeyword.size());
        for (Map.Entry<KeywordToken, List<IndexAddress>> entry : cachedAddressesByKeyword.entrySet()) {
            copy.put(entry.getKey(), new LinkedList<>(entry.getValue()));
        }
        return copy;
    }

    public Map<KeywordToken, Integer> snapshotNextSearchIndex() {
        return new HashMap<>(nextSearchIndexByKeyword);
    }

    public void restore(Map<KeywordToken, List<IndexAddress>> cachedAddresses,
                        Map<KeywordToken, Integer> nextSearchIndex) {
        this.cachedAddressesByKeyword = new HashMap<>(cachedAddresses.size());
        for (Map.Entry<KeywordToken, List<IndexAddress>> entry : cachedAddresses.entrySet()) {
            this.cachedAddressesByKeyword.put(entry.getKey(), new LinkedList<>(entry.getValue()));
        }
        this.nextSearchIndexByKeyword = new HashMap<>(nextSearchIndex);
    }
}
