package sse.state;

import java.security.interfaces.RSAPublicKey;

import sse.domain.state.EncryptedKeywordLocationMap;

public final class SSEServerState {

    private RSAPublicKey trapdoorPublicKey;
    private EncryptedKeywordLocationMap encryptedKeywordLocationMap;
    private final InvertedIndexStore invertedIndexStore;
    private final KeyShareStore keyShareStore;

    public SSEServerState() {
        this.trapdoorPublicKey = null;
        this.encryptedKeywordLocationMap = null;
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
