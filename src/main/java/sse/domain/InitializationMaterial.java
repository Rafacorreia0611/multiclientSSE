package sse.domain;

import javax.crypto.SecretKey;

public final class InitializationMaterial {

    private final SecretKey tokenGenKey;
    private final SecretKey updateCounterKey;
    private final EncryptedUpdateCounter encryptedUpdateCounter;

    public InitializationMaterial(SecretKey tokenGenKey, SecretKey updateCounterKey,
                                  EncryptedUpdateCounter encryptedUpdateCounter) {
        if (tokenGenKey == null || updateCounterKey == null || encryptedUpdateCounter == null) {
            throw new IllegalArgumentException(
                    "tokenGenKey, updateCounterKey, and encryptedUpdateCounter cannot be null");
        }
        this.tokenGenKey = tokenGenKey;
        this.updateCounterKey = updateCounterKey;
        this.encryptedUpdateCounter = encryptedUpdateCounter;
    }

    public SecretKey tokenGenKey() {
        return tokenGenKey;
    }

    public SecretKey updateCounterKey() {
        return updateCounterKey;
    }

    public EncryptedUpdateCounter encryptedUpdateCounter() {
        return encryptedUpdateCounter;
    }
}
