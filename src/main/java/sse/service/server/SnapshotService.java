package sse.service.server;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import sse.crypto.TrapdoorPermutation;
import sse.domain.IndexAddress;
import sse.snapshot.SsePlainSnapshotData;
import sse.state.SseServerState;
import vss.secretsharing.VerifiableShare;

public final class SnapshotService {

    public SsePlainSnapshotData getPlainSnapshotData(SseServerState state) {
        return new SsePlainSnapshotData(
                new LinkedHashMap<>(state.keywordStates()),
                state.trapdoorPublicKey() == null
                        ? null
                        : TrapdoorPermutation.encodePublicKey(state.trapdoorPublicKey()),
                state.activeClientId(),
                state.blockedStateRequestsWhileActive(),
                state.setupInProgress(),
                state.invertedIndexStore().snapshot(),
                state.keyShareStore().snapshotUpdateTupleShareOrder(),
                state.keyShareStore().hasMasterKeyShare(),
                state.keyShareStore().hasTrapdoorPrivateKeyShare()
        );
    }

    public VerifiableShare[] getSnapshotShares(SseServerState state, List<IndexAddress> updateTupleShareOrder,
                                               boolean includeMasterKeyShare,
                                               boolean includeTrapdoorPrivateKeyShare) {
        return state.keyShareStore().sharesInOrder(
                updateTupleShareOrder,
                includeMasterKeyShare,
                includeTrapdoorPrivateKeyShare
        );
    }

    public void installSnapshot(SseServerState state, SsePlainSnapshotData snapshotData, VerifiableShare masterKeyShare,
                                VerifiableShare trapdoorPrivateKeyShare,
                                Map<IndexAddress, VerifiableShare> updateTupleShares) {
        if (snapshotData == null) {
            throw new IllegalArgumentException("snapshotData cannot be null");
        }
        state.setKeywordStates(new LinkedHashMap<>(snapshotData.keywordStates()));
        state.setTrapdoorPublicKey(snapshotData.encodedTrapdoorPublicKey() == null
                ? null
                : TrapdoorPermutation.decodePublicKey(snapshotData.encodedTrapdoorPublicKey()));
        state.setActiveClientId(snapshotData.activeClientId());
        state.setBlockedStateRequestsWhileActive(snapshotData.blockedStateRequestsWhileActive());
        state.setSetupInProgress(snapshotData.setupInProgress());
        state.invertedIndexStore().restore(snapshotData.invertedIndex());
        state.keyShareStore().restoreUpdateTupleShares(updateTupleShares);
        state.keyShareStore().setMasterKeyShare(masterKeyShare);
        state.keyShareStore().setTrapdoorPrivateKeyShare(trapdoorPrivateKeyShare);
    }
}
