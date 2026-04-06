package sse.state;

import java.util.HashMap;
import java.util.Map;

import sse.domain.KeywordToken;

public final class SseServerState {

    private Map<KeywordToken, Integer> searchCounter;
    private Map<KeywordToken, Integer> updateCounter;
    private final SearchCache searchCache;
    private final InvertedIndexStore invertedIndexStore;
    private final KeyShareStore keyShareStore;

    public SseServerState() {
        this.searchCounter = new HashMap<>();
        this.updateCounter = new HashMap<>();
        this.searchCache = new SearchCache();
        this.invertedIndexStore = new InvertedIndexStore();
        this.keyShareStore = new KeyShareStore();
    }

    public Map<KeywordToken, Integer> searchCounter() {
        return searchCounter;
    }

    public void setSearchCounter(Map<KeywordToken, Integer> searchCounter) {
        this.searchCounter = searchCounter;
    }

    public Map<KeywordToken, Integer> updateCounter() {
        return updateCounter;
    }

    public void setUpdateCounter(Map<KeywordToken, Integer> updateCounter) {
        this.updateCounter = updateCounter;
    }

    public SearchCache searchCache() {
        return searchCache;
    }

    public InvertedIndexStore invertedIndexStore() {
        return invertedIndexStore;
    }

    public KeyShareStore keyShareStore() {
        return keyShareStore;
    }
}
