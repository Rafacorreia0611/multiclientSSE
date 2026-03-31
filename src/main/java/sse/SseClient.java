package sse;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
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
import sse.model.EncryptedUpdateTuple;
import sse.model.EpochSearchKey;
import sse.model.IndexAddress;
import sse.model.KeywordToken;
import sse.model.SearchToken;
import sse.model.State;
import sse.model.UpdateToken;
import sse.model.UpdateTuple;

public class SseClient {
    
    private SseClient() {
        // Private constructor to prevent instantiation
    }

    public static SecretKey generateTupleSecretKey(){
        return TupleEncryption.generateRandomKey();
    }

    public static byte[] generateTupleIv(){
        return TupleEncryption.generateIv();
    }

    public static EncryptedUpdateTuple encryptUpdateTuple(SecretKey key, byte[] iv, UpdateTuple tuple) throws NoSuchAlgorithmException,
        NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, IOException, IllegalBlockSizeException {
        return new EncryptedUpdateTuple(TupleEncryption.encryptTuple(key, iv, tuple), iv);
    }

    public static UpdateTuple decryptUpdateTuple(SecretKey key, byte[] iv, EncryptedUpdateTuple encryptedTuple) throws NoSuchPaddingException,
        NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, ClassNotFoundException, 
        IllegalBlockSizeException, IOException, BadPaddingException {
        return TupleEncryption.decryptTuple(key, iv, encryptedTuple.encryptedTuple());
    }

    public static SearchToken generateSearchToken(SecretKey tokenGenKey, State state, String keyword) {

        byte[] keywordTokenBytes = Prf.prf(tokenGenKey, keyword);
        KeywordToken keywordToken = new KeywordToken(keywordTokenBytes);

        int searchCount = state.searchCounter().getOrDefault(keywordToken, 0);

        byte[] epochSearchKeyBytes = Prf.prf(tokenGenKey, keyword + ":" + searchCount);
        EpochSearchKey epochSearchKey = new EpochSearchKey(epochSearchKeyBytes);

        return new SearchToken(epochSearchKey, keywordToken, searchCount);
    }

    public static UpdateToken generateUpdateToken(SecretKey tokenGenKey, State state, String keyword, EncryptedUpdateTuple encryptedTuple) {

        if (keyword == null || keyword.isEmpty() || encryptedTuple == null) {
             throw new IllegalArgumentException("keyword, encryptedTuple cannot be null or empty");
        }

        byte[] keywordTokenBytes = Prf.prf(tokenGenKey, keyword);
        KeywordToken keywordToken = new KeywordToken(keywordTokenBytes);

        int searchCount = state.searchCounter().getOrDefault(keywordToken, 0);
        int updateCount = state.updateCounter().getOrDefault(keywordToken, 0);

        byte[] epochSearchKeyBytes = Prf.prf(tokenGenKey, keyword + ":" + searchCount);

        IndexAddress address = new IndexAddress(Prf.prf(epochSearchKeyBytes, Integer.toString(updateCount + 1)));
        Map<KeywordToken, Integer> updateCounter = new HashMap<>(state.updateCounter());
        updateCounter.put(keywordToken, updateCount + 1);
        //TODO: Cifrar o updateCounter
        
        return new UpdateToken(address, encryptedTuple, updateCounter);
    }

    public static List<String> extractAddedDocIds(Map<EncryptedUpdateTuple, SecretKey> updates) {
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
