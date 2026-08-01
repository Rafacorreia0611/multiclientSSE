package sse.domain;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import javax.crypto.SecretKey;

public final class InitializationMaterial {

    private final SecretKey tokenGenKey;
    private final RSAPublicKey trapdoorPublicKey;
    private final RSAPrivateKey trapdoorPrivateKey;

    public InitializationMaterial(SecretKey tokenGenKey, RSAPublicKey trapdoorPublicKey,
                                  RSAPrivateKey trapdoorPrivateKey) {
        if (tokenGenKey == null || trapdoorPublicKey == null || trapdoorPrivateKey == null) {
            throw new IllegalArgumentException("tokenGenKey and trapdoor keys cannot be null");
        }
        this.tokenGenKey = tokenGenKey;
        this.trapdoorPublicKey = trapdoorPublicKey;
        this.trapdoorPrivateKey = trapdoorPrivateKey;
    }

    public SecretKey tokenGenKey() {
        return tokenGenKey;
    }

    public RSAPublicKey trapdoorPublicKey() {
        return trapdoorPublicKey;
    }

    public RSAPrivateKey trapdoorPrivateKey() {
        return trapdoorPrivateKey;
    }
}
