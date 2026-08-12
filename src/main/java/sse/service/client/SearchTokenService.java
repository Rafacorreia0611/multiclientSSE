package sse.service.client;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
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
import sse.domain.update.EncryptedUpdateTuple;
import sse.domain.state.KeywordState;
import sse.domain.id.KeywordToken;
import sse.domain.search.SearchToken;
import sse.domain.state.State;
import sse.domain.update.UpdateTuple;

public final class SearchTokenService {

    private static final String TOKEN_KEY_LABEL = "TokenKey";
    private static final String ADDRESS_KEY_LABEL = "AddressKey";

    public SearchToken generateSearchToken(SecretKey masterKey, State state, String keyword) {
        if (masterKey == null || state == null || keyword == null) {
            throw new IllegalArgumentException("masterKey, state, and keyword cannot be null");
        }

        byte[] tokenKey = Prf.prf(masterKey, TOKEN_KEY_LABEL);
        KeywordToken keywordToken = new KeywordToken(Prf.prf(tokenKey, keyword));
        KeywordState keywordState = state.keywordStates().get(keywordToken);
        if (keywordState == null) {
            return null;
        }

        byte[] addressKey = Prf.prf(masterKey, ADDRESS_KEY_LABEL);
        byte[] keywordAddressKey = Prf.prf(addressKey, keyword);
        return new SearchToken(keywordAddressKey, keywordState.currentToken(), keywordState.counter());
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
