package sse.service.server;

import java.util.ArrayDeque;
import java.util.Deque;

import sse.crypto.TrapdoorPermutation;
import sse.domain.update.EncryptedUpdateTuple;
import sse.domain.id.IndexAddress;
import sse.domain.search.SearchResponseData;
import sse.domain.search.SearchToken;
import sse.domain.id.SearchTokenValue;
import sse.state.SseServerState;
import vss.secretsharing.VerifiableShare;

public final class SearchService {

    public SearchResponseData search(SseServerState state, SearchToken searchToken) {
        if (state == null || searchToken == null) {
            throw new IllegalArgumentException("state and searchToken cannot be null");
        }

        SearchResponseData result = new SearchResponseData();
        Deque<EncryptedUpdateTuple> encryptedTuples = new ArrayDeque<EncryptedUpdateTuple>();
        Deque<VerifiableShare> updateTupleKeys = new ArrayDeque<VerifiableShare>();
        SearchTokenValue currentToken = searchToken.currentToken();
        byte[] keywordAddressKey = searchToken.keywordAddressKey();
        for (int i = 0; i < searchToken.counter(); i++) {
            IndexAddress address = TrapdoorPermutation.deriveAddress(keywordAddressKey, currentToken);
            EncryptedUpdateTuple update = state.invertedIndexStore().get(address);
            if (update != null) {
                VerifiableShare updateTupleKey = state.keyShareStore().getUpdateTupleShare(address);
                if (updateTupleKey != null) {
                    encryptedTuples.addFirst(update);
                    updateTupleKeys.addFirst(updateTupleKey);
                }
            }
            currentToken = TrapdoorPermutation.publicStep(currentToken, state.trapdoorPublicKey());
        }
        while (!encryptedTuples.isEmpty()) {
            result.add(encryptedTuples.removeFirst(), updateTupleKeys.removeFirst());
        }
        return result;
    }
}
