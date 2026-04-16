package sse.state;

import java.util.LinkedHashMap;
import java.util.Map;

import sse.domain.EncryptedUpdateCounter;
import sse.domain.KeywordToken;

public final class SseServerState {

    private Map<KeywordToken, Integer> searchCounter;
    private EncryptedUpdateCounter encryptedUpdateCounter;
    private int activeClientId;
    private int blockedStateRequestsWhileActive;
    private boolean setupInProgress;
    private final SearchCache searchCache;
    private final InvertedIndexStore invertedIndexStore;
    private final KeyShareStore keyShareStore;

    public SseServerState() {
        this.searchCounter = new LinkedHashMap<>();
        this.encryptedUpdateCounter = null;
        this.activeClientId = -1;
        this.blockedStateRequestsWhileActive = 0;
        this.setupInProgress = false;
        this.searchCache = new SearchCache();
        this.invertedIndexStore = new InvertedIndexStore();
        this.keyShareStore = new KeyShareStore();
    }

    public Map<KeywordToken, Integer> searchCounter() {
        return searchCounter;
    }

    public void setSearchCounter(Map<KeywordToken, Integer> searchCounter) {
        this.searchCounter = new LinkedHashMap<>(searchCounter);
    }

    public EncryptedUpdateCounter encryptedUpdateCounter() {
        return encryptedUpdateCounter;
    }

    public void setEncryptedUpdateCounter(EncryptedUpdateCounter encryptedUpdateCounter) {
        this.encryptedUpdateCounter = encryptedUpdateCounter;
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
        return encryptedUpdateCounter != null
                && keyShareStore.hasTokenGenKeyShare()
                && keyShareStore.hasUpdateCounterKeyShare();
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
