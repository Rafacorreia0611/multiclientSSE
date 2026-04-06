package sse.service.server;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import sse.crypto.Prf;
import sse.domain.EncryptedUpdateTuple;
import sse.domain.EpochSearchKey;
import sse.domain.IndexAddress;
import sse.domain.KeywordToken;
import sse.domain.SearchToken;
import sse.state.SseServerState;
import vss.secretsharing.VerifiableShare;

public final class SearchService {

    public Map<EncryptedUpdateTuple, VerifiableShare> search(SseServerState state, SearchToken searchToken) {
        EpochSearchKey epochSearchKey = searchToken.epochSearchKey();
        KeywordToken keywordToken = searchToken.keywordToken();

        if (!state.searchCounter().containsKey(keywordToken)) {
            state.searchCounter().put(keywordToken, 0);
        }
        Map<EncryptedUpdateTuple, VerifiableShare> result = new LinkedHashMap<>();
        List<IndexAddress> cachedAddresses = state.searchCache().cachedAddressesFor(keywordToken);
        if (!cachedAddresses.isEmpty()) {
            for (IndexAddress address : cachedAddresses) {
                EncryptedUpdateTuple update = state.invertedIndexStore().get(address);
                VerifiableShare updateTupleKey = state.keyShareStore().getUpdateTupleShare(address);
                if (update != null) {
                    result.put(update, updateTupleKey);
                }
            }
        }
        if (searchToken.searchCounter() != state.searchCounter().get(keywordToken)) {
            return result;
        }

        byte[] epochSearchKeyBytes = epochSearchKey.value();
        int currentUpdateCounter = state.updateCounter().getOrDefault(keywordToken, 0);
        int nextIndex = state.searchCache().nextSearchIndexFor(keywordToken);
        for (int i = nextIndex; i <= currentUpdateCounter; i++) {
            byte[] address = Prf.prf(epochSearchKeyBytes, i);
            IndexAddress indexAddress = new IndexAddress(address);
            EncryptedUpdateTuple update = state.invertedIndexStore().get(indexAddress);
            if (update != null) {
                VerifiableShare updateTupleKey = state.keyShareStore().getUpdateTupleShare(indexAddress);
                result.put(update, updateTupleKey);
                state.searchCache().cacheAddress(keywordToken, indexAddress);
            }
        }
        state.searchCache().advanceNextSearchIndex(keywordToken, currentUpdateCounter + 1);
        state.searchCounter().put(keywordToken, state.searchCounter().get(keywordToken) + 1);
        return result;
    }
}
