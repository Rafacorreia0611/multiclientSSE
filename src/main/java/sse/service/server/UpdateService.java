package sse.service.server;

import sse.domain.EncryptedUpdateCounter;
import sse.domain.EncryptedUpdateTuple;
import sse.domain.IndexAddress;
import sse.domain.UpdateToken;
import sse.state.SseServerState;
import vss.secretsharing.VerifiableShare;

public final class UpdateService {

    public void update(SseServerState state, UpdateToken updateToken, VerifiableShare updateTupleKeyShare) {
        IndexAddress address = updateToken.address();
        EncryptedUpdateTuple encryptedTuple = updateToken.encryptedTuple();
        EncryptedUpdateCounter encryptedUpdateCounter = updateToken.encryptedUpdateCounter();

        if (state.invertedIndexStore().contains(address)) {
            throw new IllegalArgumentException("Address already exists in the inverted index");
        }
        state.invertedIndexStore().put(address, encryptedTuple);
        state.keyShareStore().putUpdateTupleShare(address, updateTupleKeyShare);
        state.setEncryptedUpdateCounter(encryptedUpdateCounter);
    }
}
