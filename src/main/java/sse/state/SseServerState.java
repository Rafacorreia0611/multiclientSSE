package sse.state;

import java.security.interfaces.RSAPublicKey;

import sse.domain.state.EncryptedKeywordLocationMap;

public final class SseServerState {

    private RSAPublicKey trapdoorPublicKey;
    private EncryptedKeywordLocationMap encryptedKeywordLocationMap;
    private int activeClientId;
    private int blockedStateRequestsWhileActive;
    private boolean setupInProgress;
    private final InvertedIndexStore invertedIndexStore;
    private final KeyShareStore keyShareStore;

    public SseServerState() {
        this.trapdoorPublicKey = null;
        this.encryptedKeywordLocationMap = null;
        this.activeClientId = -1;
        this.blockedStateRequestsWhileActive = 0;
        this.setupInProgress = false;
        this.invertedIndexStore = new InvertedIndexStore();
        this.keyShareStore = new KeyShareStore();
    }

    public RSAPublicKey trapdoorPublicKey() {
        return trapdoorPublicKey;
    }

    public void setTrapdoorPublicKey(RSAPublicKey trapdoorPublicKey) {
        this.trapdoorPublicKey = trapdoorPublicKey;
    }

    public EncryptedKeywordLocationMap encryptedKeywordLocationMap() {
        return encryptedKeywordLocationMap;
    }

    public void setEncryptedKeywordLocationMap(EncryptedKeywordLocationMap encryptedKeywordLocationMap) {
        this.encryptedKeywordLocationMap = encryptedKeywordLocationMap;
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
                && encryptedKeywordLocationMap != null
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
