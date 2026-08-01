package sse.state;

import java.util.LinkedHashMap;
import java.util.Map;

import sse.domain.KeywordState;
import sse.domain.KeywordToken;

public final class SseServerState {

    private Map<KeywordToken, KeywordState> keywordStates;
    private int activeClientId;
    private int blockedStateRequestsWhileActive;
    private boolean setupInProgress;
    private final SearchCache searchCache;
    private final InvertedIndexStore invertedIndexStore;
    private final KeyShareStore keyShareStore;

    public SseServerState() {
        this.keywordStates = new LinkedHashMap<>();
        this.activeClientId = -1;
        this.blockedStateRequestsWhileActive = 0;
        this.setupInProgress = false;
        this.searchCache = new SearchCache();
        this.invertedIndexStore = new InvertedIndexStore();
        this.keyShareStore = new KeyShareStore();
    }

    public Map<KeywordToken, KeywordState> keywordStates() {
        return keywordStates;
    }

    public void setKeywordStates(Map<KeywordToken, KeywordState> keywordStates) {
        this.keywordStates = new LinkedHashMap<>(keywordStates);
    }

    public int activeClientId() {
        return activeClientId;
    }

    public void setActiveClientId(int activeClientId) {
        this.activeClientId = activeClientId;
    }

    public int blockedStateRequestsWhileActive() {
        return blockedStateRequestsWhileActive;
    }

    public void setBlockedStateRequestsWhileActive(int blockedStateRequestsWhileActive) {
        this.blockedStateRequestsWhileActive = blockedStateRequestsWhileActive;
    }

    public boolean setupInProgress() {
        return setupInProgress;
    }

    public void setSetupInProgress(boolean setupInProgress) {
        this.setupInProgress = setupInProgress;
    }

    public boolean isInitialized() {
        return keyShareStore.hasTokenGenKeyShare();
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
