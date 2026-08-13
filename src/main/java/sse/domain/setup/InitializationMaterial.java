package sse.domain.setup;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import javax.crypto.SecretKey;

import sse.domain.state.EncryptedKeywordAddressMap;

public final class InitializationMaterial {

    private final SecretKey masterKey;
    private final RSAPublicKey trapdoorPublicKey;
    private final RSAPrivateKey trapdoorPrivateKey;
    private final EncryptedKeywordAddressMap encryptedKeywordAddressMap;

    public InitializationMaterial(SecretKey masterKey, RSAPublicKey trapdoorPublicKey,
                                  RSAPrivateKey trapdoorPrivateKey,
                                  EncryptedKeywordAddressMap encryptedKeywordAddressMap) {
        if (masterKey == null || trapdoorPublicKey == null || trapdoorPrivateKey == null
                || encryptedKeywordAddressMap == null) {
            throw new IllegalArgumentException("masterKey, trapdoor keys, and encrypted keyword map cannot be null");
        }
        this.masterKey = masterKey;
        this.trapdoorPublicKey = trapdoorPublicKey;
        this.trapdoorPrivateKey = trapdoorPrivateKey;
        this.encryptedKeywordAddressMap = encryptedKeywordAddressMap;
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

    public EncryptedKeywordAddressMap encryptedKeywordAddressMap() {
        return encryptedKeywordAddressMap;
    }
}
