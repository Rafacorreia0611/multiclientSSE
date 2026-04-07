package sse.service.client;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import sse.crypto.Prf;
import sse.crypto.TupleEncryption;
import sse.crypto.UpdateCounterEncryption;
import sse.domain.EncryptedUpdateCounter;
import sse.domain.EncryptedUpdateTuple;
import sse.domain.IndexAddress;
import sse.domain.KeywordToken;
import sse.domain.State;
import sse.domain.UpdateOp;
import sse.domain.UpdateToken;
import sse.domain.UpdateTuple;

public final class UpdateTokenService {

    public SecretKey generateTupleSecretKey() {
        return TupleEncryption.generateRandomKey();
    }

    public byte[] generateTupleIv() {
        return TupleEncryption.generateIv();
    }

    public EncryptedUpdateTuple encryptUpdateTuple(SecretKey key, byte[] iv, UpdateTuple tuple)
            throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException,
            InvalidAlgorithmParameterException, IOException, IllegalBlockSizeException {
        return new EncryptedUpdateTuple(TupleEncryption.encryptTuple(key, iv, tuple), iv);
    }

    public EncryptedUpdateTuple generateEncryptedUpdateTuple(String docId, boolean isAdd, SecretKey encryptionKey) {
        byte[] iv = generateTupleIv();
        try {
            return encryptUpdateTuple(encryptionKey, iv, new UpdateTuple(docId, isAdd ? UpdateOp.ADD : UpdateOp.DEL));
        } catch (Exception e) {
            throw new RuntimeException("Error encrypting update tuple", e);
        }
    }

    public UpdateToken generateUpdateToken(SecretKey tokenGenKey, SecretKey updateCounterKey,
                                           State state, String keyword, EncryptedUpdateTuple encryptedTuple) {
        if (keyword == null || keyword.isEmpty() || encryptedTuple == null) {
            throw new IllegalArgumentException("keyword, encryptedTuple cannot be null or empty");
        }

        byte[] keywordTokenBytes = Prf.prf(tokenGenKey, keyword);
        KeywordToken keywordToken = new KeywordToken(keywordTokenBytes);

        int searchCount = state.searchCounter().getOrDefault(keywordToken, 0);
        EncryptedUpdateCounter encryptedUpdateCounter = state.encryptedUpdateCounter();
        Map<KeywordToken, Integer> updateCounter;
        try {
            updateCounter = UpdateCounterEncryption.decryptUpdateCounter(updateCounterKey, encryptedUpdateCounter);
        } catch (Exception e) {
            throw new RuntimeException("Error decrypting update counter", e);
        }
        int updateCount = updateCounter.getOrDefault(keywordToken, 0);

        byte[] epochSearchKeyBytes = Prf.prf(tokenGenKey, keyword + ":" + searchCount);

        IndexAddress address = new IndexAddress(Prf.prf(epochSearchKeyBytes, updateCount + 1));
        updateCounter.put(keywordToken, updateCount + 1);
        EncryptedUpdateCounter updatedEncryptedUpdateCounter;
        try {
            updatedEncryptedUpdateCounter = UpdateCounterEncryption.encryptUpdateCounter(
                    updateCounterKey,
                    UpdateCounterEncryption.generateIv(),
                    updateCounter
            );
        } catch (Exception e) {
            throw new RuntimeException("Error encrypting update counter", e);
        }

        return new UpdateToken(address, encryptedTuple, updatedEncryptedUpdateCounter);
    }
}
