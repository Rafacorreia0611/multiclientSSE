package sse.service.server;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import sse.domain.IndexAddress;
import sse.snapshot.SsePlainSnapshotData;
import sse.state.SseServerState;
import vss.secretsharing.VerifiableShare;

public final class SnapshotService {

    public SsePlainSnapshotData getPlainSnapshotData(SseServerState state) {
        return new SsePlainSnapshotData(
                new LinkedHashMap<>(state.keywordStates()),
                state.activeClientId(),
                state.blockedStateRequestsWhileActive(),
                state.setupInProgress(),
                state.searchCache().snapshotCachedAddresses(),
                state.searchCache().snapshotNextSearchIndex(),
                state.invertedIndexStore().snapshot(),
                state.keyShareStore().snapshotUpdateTupleShareOrder(),
                state.keyShareStore().hasTokenGenKeyShare()
        );
    }

    public VerifiableShare[] getSnapshotShares(SseServerState state, List<IndexAddress> updateTupleShareOrder,
                                               boolean includeTokenGenKeyShare) {
        return state.keyShareStore().sharesInOrder(
                updateTupleShareOrder,
                includeTokenGenKeyShare
        );
    }

    public void installSnapshot(SseServerState state, SsePlainSnapshotData snapshotData, VerifiableShare tokenGenKeyShare,
                                Map<IndexAddress, VerifiableShare> updateTupleShares) {
        if (snapshotData == null) {
            throw new IllegalArgumentException("snapshotData cannot be null");
        }
        state.setKeywordStates(new LinkedHashMap<>(snapshotData.keywordStates()));
        state.setActiveClientId(snapshotData.activeClientId());
        state.setBlockedStateRequestsWhileActive(snapshotData.blockedStateRequestsWhileActive());
        state.setSetupInProgress(snapshotData.setupInProgress());
        state.searchCache().restore(snapshotData.dbCache(), snapshotData.nextSearchIndex());
        state.invertedIndexStore().restore(snapshotData.invertedIndex());
        state.keyShareStore().restoreUpdateTupleShares(updateTupleShares);
        state.keyShareStore().setTokenGenKeyShare(tokenGenKeyShare);
    }
}
