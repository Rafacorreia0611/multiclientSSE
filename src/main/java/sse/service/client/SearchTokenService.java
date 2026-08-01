package sse.service.client;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import sse.crypto.Prf;
import sse.crypto.TupleEncryption;
import sse.crypto.UpdateCounterEncryption;
import sse.domain.EncryptedUpdateTuple;
import sse.domain.EpochSearchKey;
import sse.domain.KeywordToken;
import sse.domain.SearchToken;
import sse.domain.State;
import sse.domain.UpdateTuple;

public final class SearchTokenService {

    public SearchToken generateSearchToken(SecretKey tokenGenKey, RSAPrivateKey trapdoorPrivateKey,
                                           State state, String keyword) {
        byte[] keywordTokenBytes = Prf.prf(tokenGenKey, keyword);
        KeywordToken keywordToken = new KeywordToken(keywordTokenBytes);

        int searchCount = state.searchCounter().getOrDefault(keywordToken, 0);
        Map<KeywordToken, Integer> updateCounter;
        try {
            updateCounter = UpdateCounterEncryption.decryptUpdateCounter(
                    updateCounterKey,
                    state.encryptedUpdateCounter()
            );
        } catch (Exception e) {
            throw new RuntimeException("Error decrypting update counter", e);
        }
        int currentUpdateCounter = updateCounter.getOrDefault(keywordToken, 0);

        byte[] epochSearchKeyBytes = Prf.prf(tokenGenKey, keyword + ":" + searchCount);
        EpochSearchKey epochSearchKey = new EpochSearchKey(epochSearchKeyBytes);

        return new SearchToken(epochSearchKey, keywordToken, searchCount, currentUpdateCounter);
    }

    public UpdateTuple decryptUpdateTuple(SecretKey key, byte[] iv, EncryptedUpdateTuple encryptedTuple)
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException,
            InvalidKeyException, ClassNotFoundException, IllegalBlockSizeException, java.io.IOException,
            BadPaddingException {
        return TupleEncryption.decryptTuple(key, iv, encryptedTuple.encryptedTuple());
    }

    public List<String> extractAddedDocIds(Map<EncryptedUpdateTuple, SecretKey> updates) {
        if (updates == null) {
            throw new IllegalArgumentException("updates cannot be null");
        }

        Set<String> activeDocIds = new LinkedHashSet<>();
        for (Map.Entry<EncryptedUpdateTuple, SecretKey> entry : updates.entrySet()) {
            if (entry == null || entry.getKey() == null || entry.getValue() == null) {
                continue;
            }

            UpdateTuple update;
            try {
                update = decryptUpdateTuple(entry.getValue(), entry.getKey().iv(), entry.getKey());
            } catch (Exception e) {
                throw new IllegalStateException("Failed to decrypt update tuple", e);
            }

            switch (update.op()) {
                case ADD:
                    activeDocIds.add(update.docId());
                    break;
                case DEL:
                    activeDocIds.remove(update.docId());
                    break;
                default:
                    throw new IllegalStateException("Unknown update operation: " + update.op());
            }
        }
        return new ArrayList<>(activeDocIds);
    }
}
