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
import sse.domain.KeywordToken;
import sse.domain.PendingKeywordUpdates;
import sse.domain.PreparedKeywordUpdates;
import sse.domain.State;
import sse.domain.UpdateOp;
import sse.domain.UpdateToken;
import sse.domain.UpdateTokenItem;
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

    public EncryptedUpdateTuple generateEncryptedUpdateTuple(String docId, UpdateOp operation, SecretKey encryptionKey) {
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

    public PreparedKeywordUpdates prepareKeywordUpdates(String keyword, List<String> docIds, UpdateOp operation) {
        if (docIds == null || docIds.isEmpty()) {
            throw new IllegalArgumentException("docIds cannot be null or empty");
        }
        List<EncryptedUpdateTuple> encryptedTuples = new ArrayList<EncryptedUpdateTuple>(docIds.size());
        List<SecretKey> tupleKeys = new ArrayList<SecretKey>(docIds.size());

        for (String docId : docIds) {
            SecretKey tupleKey = generateTupleSecretKey();
            tupleKeys.add(tupleKey);
            encryptedTuples.add(generateEncryptedUpdateTuple(docId, operation, tupleKey));
        }

        return new PreparedKeywordUpdates(new PendingKeywordUpdates(keyword, encryptedTuples), tupleKeys);
    }

    public UpdateToken generateUpdateToken(SecretKey tokenGenKey, SecretKey updateCounterKey,
                                           State state,
                                           List<PendingKeywordUpdates> pendingUpdates) {
        if (pendingUpdates == null || pendingUpdates.isEmpty()) {
            throw new IllegalArgumentException("pendingUpdates cannot be null or empty");
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
        for (PendingKeywordUpdates pendingUpdate : pendingUpdates) {
            if (pendingUpdate == null) {
                throw new IllegalArgumentException("pendingUpdates cannot contain null values");
            }

            String keyword = pendingUpdate.keyword();
            byte[] keywordTokenBytes = Prf.prf(tokenGenKey, keyword);
            KeywordToken keywordToken = new KeywordToken(keywordTokenBytes);

            int searchCount = state.searchCounter().getOrDefault(keywordToken, 0);
            int updateCount = updateCounter.getOrDefault(keywordToken, 0);
            byte[] epochSearchKeyBytes = Prf.prf(tokenGenKey, keyword + ":" + searchCount);

            for (EncryptedUpdateTuple encryptedTuple : pendingUpdate.encryptedTuples()) {
                updateCount++;
                IndexAddress address = new IndexAddress(Prf.prf(epochSearchKeyBytes, updateCount));
                items.add(new UpdateTokenItem(address, encryptedTuple));
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
        return new UpdateToken(items, updatedEncryptedUpdateCounter);
    }
}
