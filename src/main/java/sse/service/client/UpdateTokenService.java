package sse.service.client;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import sse.crypto.Prf;
import sse.crypto.TrapdoorPermutation;
import sse.crypto.TupleEncryption;
import sse.domain.update.EncryptedUpdateTuple;
import sse.domain.id.IndexAddress;
import sse.domain.state.KeywordState;
import sse.domain.update.KeywordUpdate;
import sse.domain.update.PreparedUpdateRequest;
import sse.domain.id.SearchTokenValue;
import sse.domain.state.State;
import sse.domain.update.UpdateOp;
import sse.domain.update.UpdateToken;
import sse.domain.update.UpdateTokenItem;
import sse.domain.update.UpdateTuple;

public final class UpdateTokenService {

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
                                                      State state, KeywordState keywordState, KeywordUpdate update) {
        if (masterKey == null || trapdoorPrivateKey == null || state == null) {
            throw new IllegalArgumentException("masterKey, trapdoorPrivateKey, and state cannot be null");
        }
        if (update == null) {
            throw new IllegalArgumentException("update cannot be null");
        }

        RSAPublicKey trapdoorPublicKey =
                TrapdoorPermutation.decodePublicKey(state.encodedTrapdoorPublicKey());
        byte[] addressKey = Prf.prf(masterKey, ADDRESS_KEY_LABEL);
        List<UpdateTokenItem> items = new ArrayList<UpdateTokenItem>();
        List<SecretKey> tupleKeys = new ArrayList<SecretKey>();

        String keyword = update.keyword();
        byte[] keywordAddressKey = Prf.prf(addressKey, keyword);

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

            keywordState = new KeywordState(nextToken, nextCounter);
        }

        return new PreparedUpdateRequest(new UpdateToken(items), tupleKeys, keywordState);
    }
}
