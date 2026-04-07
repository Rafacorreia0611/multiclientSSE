package sse.facade;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import sse.domain.EncryptedUpdateTuple;
import sse.domain.SearchToken;
import sse.domain.State;
import sse.domain.UpdateToken;
import sse.domain.UpdateTuple;
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

    public EncryptedUpdateTuple generateEncryptedUpdateTuple(String docId, boolean isAdd, SecretKey encryptionKey) {
        return updateTokenService.generateEncryptedUpdateTuple(docId, isAdd, encryptionKey);
    }

    public UpdateToken generateUpdateToken(SecretKey tokenGenKey, SecretKey updateCounterKey,
                                           State state, String keyword, EncryptedUpdateTuple encryptedTuple) {
        return updateTokenService.generateUpdateToken(tokenGenKey, updateCounterKey, state, keyword, encryptedTuple);
    }

    public List<String> extractAddedDocIds(Map<EncryptedUpdateTuple, SecretKey> updates) {
        return searchTokenService.extractAddedDocIds(updates);
    }
}
