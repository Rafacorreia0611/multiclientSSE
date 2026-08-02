package sse.domain;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import javax.crypto.SecretKey;

public final class InitializationMaterial {

    private final SecretKey masterKey;
    private final RSAPublicKey trapdoorPublicKey;
    private final RSAPrivateKey trapdoorPrivateKey;

    public InitializationMaterial(SecretKey masterKey, RSAPublicKey trapdoorPublicKey,
                                  RSAPrivateKey trapdoorPrivateKey) {
        if (masterKey == null || trapdoorPublicKey == null || trapdoorPrivateKey == null) {
            throw new IllegalArgumentException("masterKey and trapdoor keys cannot be null");
        }
        this.masterKey = masterKey;
        this.trapdoorPublicKey = trapdoorPublicKey;
        this.trapdoorPrivateKey = trapdoorPrivateKey;
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
}
