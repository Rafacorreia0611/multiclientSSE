package sse.state;

import java.security.interfaces.RSAPublicKey;
import java.util.LinkedHashMap;
import java.util.Map;

import sse.domain.state.KeywordState;
import sse.domain.id.KeywordToken;

public final class SseServerState {

    private Map<KeywordToken, KeywordState> keywordStates;
    private RSAPublicKey trapdoorPublicKey;
    private int activeClientId;
    private int blockedStateRequestsWhileActive;
    private boolean setupInProgress;
    private final InvertedIndexStore invertedIndexStore;
    private final KeyShareStore keyShareStore;

    public SseServerState() {
        this.keywordStates = new LinkedHashMap<>();
        this.trapdoorPublicKey = null;
        this.activeClientId = -1;
        this.blockedStateRequestsWhileActive = 0;
        this.setupInProgress = false;
        this.invertedIndexStore = new InvertedIndexStore();
        this.keyShareStore = new KeyShareStore();
    }

    public Map<KeywordToken, KeywordState> keywordStates() {
        return keywordStates;
    }

    public void setKeywordStates(Map<KeywordToken, KeywordState> keywordStates) {
        this.keywordStates = new LinkedHashMap<>(keywordStates);
    }

    public RSAPublicKey trapdoorPublicKey() {
        return trapdoorPublicKey;
    }

    public void setTrapdoorPublicKey(RSAPublicKey trapdoorPublicKey) {
        this.trapdoorPublicKey = trapdoorPublicKey;
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
        return trapdoorPublicKey != null
                && keyShareStore.hasMasterKeyShare()
                && keyShareStore.hasTrapdoorPrivateKeyShare();
    }

    public InvertedIndexStore invertedIndexStore() {
        return invertedIndexStore;
    }

    public KeyShareStore keyShareStore() {
        return keyShareStore;
    }
}
