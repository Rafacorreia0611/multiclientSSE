package sse.service.client;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
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
import sse.domain.KeywordUpdate;
import sse.domain.KeywordToken;
import sse.domain.PreparedUpdateRequest;
import sse.domain.State;
import sse.domain.UpdateOp;
import sse.domain.UpdateToken;
import sse.domain.UpdateTokenItem;
import sse.domain.UpdateTuple;

public final class UpdateTokenService {

    private SecretKey generateTupleSecretKey() {
        return TupleEncryption.generateRandomKey();
    }

    private byte[] generateTupleIv() {
        return TupleEncryption.generateIv();
    }

    private EncryptedUpdateTuple encryptUpdateTuple(SecretKey key, byte[] iv, UpdateTuple tuple)
            throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException,
            InvalidAlgorithmParameterException, IOException, IllegalBlockSizeException {
        return new EncryptedUpdateTuple(TupleEncryption.encryptTuple(key, iv, tuple), iv);
    }

    private EncryptedUpdateTuple generateEncryptedUpdateTuple(String docId, UpdateOp operation, SecretKey encryptionKey) {
        if (operation == null) {
            throw new IllegalArgumentException("operation cannot be null");
        }
        byte[] iv = generateTupleIv();
        try {
            return encryptUpdateTuple(encryptionKey, iv, new UpdateTuple(docId, operation));
        } catch (Exception e) {
            throw new RuntimeException("Error encrypting update tuple", e);
        }
    }

    public PreparedUpdateRequest prepareUpdateRequest(SecretKey tokenGenKey, SecretKey updateCounterKey,
                                                      State state, List<KeywordUpdate> updates) {
        if (updates == null || updates.isEmpty()) {
            throw new IllegalArgumentException("updates cannot be null or empty");
        }

        Map<KeywordToken, Integer> updateCounter;
        try {
            updateCounter = UpdateCounterEncryption.decryptUpdateCounter(
                    updateCounterKey,
                    state.encryptedUpdateCounter()
            );
        } catch (Exception e) {
            throw new RuntimeException("Error decrypting update counter", e);
        }

        List<UpdateTokenItem> items = new ArrayList<UpdateTokenItem>();
        List<SecretKey> tupleKeys = new ArrayList<SecretKey>();
        for (KeywordUpdate update : updates) {
            if (update == null) {
                throw new IllegalArgumentException("updates cannot contain null values");
            }

            String keyword = update.keyword();
            byte[] keywordTokenBytes = Prf.prf(tokenGenKey, keyword);
            KeywordToken keywordToken = new KeywordToken(keywordTokenBytes);

            int searchCount = state.searchCounter().getOrDefault(keywordToken, 0);
            int updateCount = updateCounter.getOrDefault(keywordToken, 0);
            byte[] epochSearchKeyBytes = Prf.prf(tokenGenKey, keyword + ":" + searchCount);

            for (String docId : update.docIds()) {
                SecretKey tupleKey = generateTupleSecretKey();
                EncryptedUpdateTuple encryptedTuple =
                        generateEncryptedUpdateTuple(docId, update.operation(), tupleKey);
                updateCount++;
                IndexAddress address = new IndexAddress(Prf.prf(epochSearchKeyBytes, updateCount));
                items.add(new UpdateTokenItem(address, encryptedTuple));
                tupleKeys.add(tupleKey);
            }

            updateCounter.put(keywordToken, updateCount);
        }

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
        return new PreparedUpdateRequest(new UpdateToken(items, updatedEncryptedUpdateCounter), tupleKeys);
    }
}
