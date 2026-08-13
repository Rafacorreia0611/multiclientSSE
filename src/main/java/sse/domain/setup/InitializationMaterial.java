package sse.domain.setup;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import javax.crypto.SecretKey;

import sse.domain.state.EncryptedKeywordLocationMap;

public final class InitializationMaterial {

    private final SecretKey masterKey;
    private final RSAPublicKey trapdoorPublicKey;
    private final RSAPrivateKey trapdoorPrivateKey;
    private final EncryptedKeywordLocationMap encryptedKeywordLocationMap;

    public InitializationMaterial(SecretKey masterKey, RSAPublicKey trapdoorPublicKey,
                                  RSAPrivateKey trapdoorPrivateKey,
                                  EncryptedKeywordLocationMap encryptedKeywordLocationMap) {
        if (masterKey == null || trapdoorPublicKey == null || trapdoorPrivateKey == null
                || encryptedKeywordLocationMap == null) {
            throw new IllegalArgumentException("masterKey, trapdoor keys, and encrypted keyword map cannot be null");
        }
        this.masterKey = masterKey;
        this.trapdoorPublicKey = trapdoorPublicKey;
        this.trapdoorPrivateKey = trapdoorPrivateKey;
        this.encryptedKeywordLocationMap = encryptedKeywordLocationMap;
    }

    public SecretKey masterKey() {
        return masterKey;
    }

    public RSAPublicKey trapdoorPublicKey() {
        return trapdoorPublicKey;
    }

    public RSAPrivateKey trapdoorPrivateKey() {
        return trapdoorPrivateKey;
    }

    public EncryptedKeywordLocationMap encryptedKeywordLocationMap() {
        return encryptedKeywordLocationMap;
    }
}
