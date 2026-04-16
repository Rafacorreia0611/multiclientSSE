package sse.service.server;

import java.util.HashSet;
import java.util.Set;

import sse.domain.EncryptedUpdateCounter;
import sse.domain.EncryptedUpdateTuple;
import sse.domain.IndexAddress;
import sse.domain.UpdateToken;
import sse.domain.populatedb.BulkUpdateItem;
import sse.domain.populatedb.BulkUpdateRequest;
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

    public void bulkUpdate(SseServerState state, BulkUpdateRequest bulkUpdateRequest, VerifiableShare[] updateTupleKeyShares) {
        if (bulkUpdateRequest == null || updateTupleKeyShares == null) {
            throw new IllegalArgumentException("bulkUpdateRequest and updateTupleKeyShares cannot be null");
        }

        if (bulkUpdateRequest.items().size() != updateTupleKeyShares.length) {
            throw new IllegalArgumentException("Mismatch between bulk update items and update tuple key shares");
        }

        Set<IndexAddress> batchAddresses = new HashSet<IndexAddress>();
        for (int i = 0; i < bulkUpdateRequest.items().size(); i++) {
            BulkUpdateItem item = bulkUpdateRequest.items().get(i);
            VerifiableShare updateTupleKeyShare = updateTupleKeyShares[i];

            if (item == null || updateTupleKeyShare == null) {
                throw new IllegalArgumentException("Bulk update items and tuple key shares cannot contain null values");
            }

            IndexAddress address = item.address();
            if (!batchAddresses.add(address)) {
                throw new IllegalArgumentException("Duplicate address inside the same bulk update request");
            }
            if (state.invertedIndexStore().contains(address)) {
                throw new IllegalArgumentException("Address already exists in the inverted index");
            }
        }

        for (int i = 0; i < bulkUpdateRequest.items().size(); i++) {
            BulkUpdateItem item = bulkUpdateRequest.items().get(i);
            IndexAddress address = item.address();

            state.invertedIndexStore().put(address, item.encryptedTuple());
            state.keyShareStore().putUpdateTupleShare(address, updateTupleKeyShares[i]);
        }
        state.setEncryptedUpdateCounter(bulkUpdateRequest.encryptedUpdateCounter());
    }
}
