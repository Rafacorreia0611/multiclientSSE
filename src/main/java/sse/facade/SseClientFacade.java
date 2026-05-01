package sse.facade;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import sse.crypto.UpdateCounterEncryption;
import sse.domain.EncryptedUpdateCounter;
import sse.domain.EncryptedUpdateTuple;
import sse.domain.InitializationMaterial;
import sse.domain.PendingKeywordUpdates;
import sse.domain.PreparedKeywordUpdates;
import sse.domain.SearchToken;
import sse.domain.State;
import sse.domain.UpdateToken;
import sse.domain.UpdateTuple;
import sse.domain.KeywordToken;
import sse.domain.UpdateOp;
import sse.service.client.SearchTokenService;
import sse.service.client.UpdateTokenService;

public final class SseClientFacade {

    private final SearchTokenService searchTokenService;
    private final UpdateTokenService updateTokenService;

    public SseClientFacade() {
        this.searchTokenService = new SearchTokenService();
        this.updateTokenService = new UpdateTokenService();
    }

    public SecretKey generateTupleSecretKey() {
        return updateTokenService.generateTupleSecretKey();
    }

    public byte[] generateTupleIv() {
        return updateTokenService.generateTupleIv();
    }

    public InitializationMaterial generateInitialStateData() {
        SecretKey tokenGenKey = generateTokenGenKey();
        SecretKey updateCounterKey = UpdateCounterEncryption.generateRandomKey();
        EncryptedUpdateCounter encryptedUpdateCounter;
        try {
            encryptedUpdateCounter = UpdateCounterEncryption.encryptUpdateCounter(
                    updateCounterKey,
                    UpdateCounterEncryption.generateIv(),
                    new HashMap<KeywordToken, Integer>()
            );
        } catch (Exception e) {
            throw new RuntimeException("Error encrypting initial update counter", e);
        }
        return new InitializationMaterial(tokenGenKey, updateCounterKey, encryptedUpdateCounter);
    }

    public UpdateTuple decryptUpdateTuple(SecretKey key, byte[] iv, EncryptedUpdateTuple encryptedTuple)
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException,
            InvalidKeyException, ClassNotFoundException, IllegalBlockSizeException, IOException,
            BadPaddingException {
        return searchTokenService.decryptUpdateTuple(key, iv, encryptedTuple);
    }

    public SearchToken generateSearchToken(SecretKey tokenGenKey, SecretKey updateCounterKey,
                                           State state, String keyword) {
        return searchTokenService.generateSearchToken(tokenGenKey, updateCounterKey, state, keyword);
    }

    public PreparedKeywordUpdates prepareKeywordUpdates(String keyword, List<String> docIds, UpdateOp operation) {
        return updateTokenService.prepareKeywordUpdates(keyword, docIds, operation);
    }

    public UpdateToken generateUpdateToken(SecretKey tokenGenKey, SecretKey updateCounterKey,
                                           State state,
                                           List<PendingKeywordUpdates> pendingUpdates) {
        return updateTokenService.generateUpdateToken(
                tokenGenKey,
                updateCounterKey,
                state,
                pendingUpdates
        );
    }

    public List<String> extractAddedDocIds(Map<EncryptedUpdateTuple, SecretKey> updates) {
        return searchTokenService.extractAddedDocIds(updates);
    }

    private SecretKey generateTokenGenKey() {
        KeyGenerator keyGen;
        try {
            keyGen = KeyGenerator.getInstance("HmacSHA256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("HmacSHA256 algorithm not available for token generation key", e);
        }
        keyGen.init(256);
        return keyGen.generateKey();
    }
}
