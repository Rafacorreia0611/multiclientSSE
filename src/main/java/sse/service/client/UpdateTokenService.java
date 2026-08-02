package sse.service.client;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import sse.crypto.Prf;
import sse.crypto.TrapdoorPermutation;
import sse.crypto.TupleEncryption;
import sse.domain.EncryptedUpdateTuple;
import sse.domain.IndexAddress;
import sse.domain.KeywordState;
import sse.domain.KeywordToken;
import sse.domain.KeywordUpdate;
import sse.domain.PreparedUpdateRequest;
import sse.domain.SearchTokenValue;
import sse.domain.State;
import sse.domain.UpdateOp;
import sse.domain.UpdateToken;
import sse.domain.UpdateTokenItem;
import sse.domain.UpdateTuple;

public final class UpdateTokenService {

    private static final String TOKEN_KEY_LABEL = "TokenKey";
    private static final String ADDRESS_KEY_LABEL = "AddressKey";

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

    public PreparedUpdateRequest prepareUpdateRequest(SecretKey masterKey, RSAPrivateKey trapdoorPrivateKey,
                                                      State state, List<KeywordUpdate> updates) {
        if (masterKey == null || trapdoorPrivateKey == null || state == null) {
            throw new IllegalArgumentException("masterKey, trapdoorPrivateKey, and state cannot be null");
        }
        if (updates == null || updates.isEmpty()) {
            throw new IllegalArgumentException("updates cannot be null or empty");
        }

        RSAPublicKey trapdoorPublicKey =
                TrapdoorPermutation.decodePublicKey(state.encodedTrapdoorPublicKey());
        byte[] tokenKey = Prf.prf(masterKey, TOKEN_KEY_LABEL);
        byte[] addressKey = Prf.prf(masterKey, ADDRESS_KEY_LABEL);
        Map<KeywordToken, KeywordState> workingKeywordStates =
                new LinkedHashMap<KeywordToken, KeywordState>(state.keywordStates());
        Map<KeywordToken, KeywordState> updatedKeywordStates =
                new LinkedHashMap<KeywordToken, KeywordState>();
        List<UpdateTokenItem> items = new ArrayList<UpdateTokenItem>();
        List<SecretKey> tupleKeys = new ArrayList<SecretKey>();

        for (KeywordUpdate update : updates) {
            if (update == null) {
                throw new IllegalArgumentException("updates cannot contain null values");
            }

            String keyword = update.keyword();
            KeywordToken keywordToken = new KeywordToken(Prf.prf(tokenKey, keyword));
            byte[] keywordAddressKey = Prf.prf(addressKey, keyword);
            KeywordState keywordState = workingKeywordStates.get(keywordToken);

            for (String docId : update.docIds()) {
                SecretKey tupleKey = generateTupleSecretKey();
                EncryptedUpdateTuple encryptedTuple =
                        generateEncryptedUpdateTuple(docId, update.operation(), tupleKey);

                SearchTokenValue nextToken;
                int nextCounter;
                if (keywordState == null) {
                    nextToken = TrapdoorPermutation.generateRandomToken(trapdoorPublicKey);
                    nextCounter = 1;
                } else {
                    nextToken = TrapdoorPermutation.privateStep(keywordState.currentToken(), trapdoorPrivateKey);
                    nextCounter = keywordState.counter() + 1;
                }

                IndexAddress address = TrapdoorPermutation.deriveAddress(keywordAddressKey, nextToken);
                items.add(new UpdateTokenItem(address, encryptedTuple));
                tupleKeys.add(tupleKey);

                keywordState = new KeywordState(nextToken, nextCounter, false);
                workingKeywordStates.put(keywordToken, keywordState);
            }

            updatedKeywordStates.put(keywordToken, keywordState);
        }

        return new PreparedUpdateRequest(new UpdateToken(items, updatedKeywordStates), tupleKeys);
    }
}
