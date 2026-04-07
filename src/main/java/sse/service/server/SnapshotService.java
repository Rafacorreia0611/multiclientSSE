package sse.service.server;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sse.domain.IndexAddress;
import sse.snapshot.SsePlainSnapshotData;
import sse.state.SseServerState;
import vss.secretsharing.VerifiableShare;

public final class SnapshotService {

    public SsePlainSnapshotData getPlainSnapshotData(SseServerState state) {
        return new SsePlainSnapshotData(
                new HashMap<>(state.searchCounter()),
                state.encryptedUpdateCounter(),
                state.activeClientId(),
                state.searchCache().snapshotCachedAddresses(),
                state.searchCache().snapshotNextSearchIndex(),
                state.invertedIndexStore().snapshot(),
                state.keyShareStore().snapshotUpdateTupleShareOrder(),
                state.keyShareStore().hasTokenGenKeyShare(),
                state.keyShareStore().hasUpdateCounterKeyShare()
        );
    }

    public VerifiableShare[] getSnapshotShares(SseServerState state, List<IndexAddress> updateTupleShareOrder,
                                               boolean includeTokenGenKeyShare,
                                               boolean includeUpdateCounterKeyShare) {
        return state.keyShareStore().sharesInOrder(
                updateTupleShareOrder,
                includeTokenGenKeyShare,
                includeUpdateCounterKeyShare
        );
    }

    public void installSnapshot(SseServerState state, SsePlainSnapshotData snapshotData, VerifiableShare tokenGenKeyShare,
                                VerifiableShare updateCounterKeyShare,
                                Map<IndexAddress, VerifiableShare> updateTupleShares) {
        if (snapshotData == null) {
            throw new IllegalArgumentException("snapshotData cannot be null");
        }
        state.setSearchCounter(new HashMap<>(snapshotData.searchCounter()));
        state.setEncryptedUpdateCounter(snapshotData.encryptedUpdateCounter());
        state.setActiveClientId(snapshotData.activeClientId());
        state.searchCache().restore(snapshotData.dbCache(), snapshotData.nextSearchIndex());
        state.invertedIndexStore().restore(snapshotData.invertedIndex());
        state.keyShareStore().restoreUpdateTupleShares(updateTupleShares);
        state.keyShareStore().setTokenGenKeyShare(tokenGenKeyShare);
        state.keyShareStore().setUpdateCounterKeyShare(updateCounterKeyShare);
    }
}
