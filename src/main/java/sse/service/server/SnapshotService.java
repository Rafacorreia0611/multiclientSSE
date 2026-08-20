package sse.service.server;

import java.util.List;
import java.util.Map;

import sse.crypto.TrapdoorPermutation;
import sse.domain.id.IndexAddress;
import sse.snapshot.SSEPlainSnapshotData;
import sse.state.SSEServerState;
import vss.secretsharing.VerifiableShare;

public final class SnapshotService {

    public SSEPlainSnapshotData getPlainSnapshotData(SSEServerState state) {
        return new SSEPlainSnapshotData(
                state.trapdoorPublicKey() == null
                        ? null
                        : TrapdoorPermutation.encodePublicKey(state.trapdoorPublicKey()),
                state.encryptedKeywordLocationMap(),
                state.activeClientId(),
                state.blockedStateRequestsWhileActive(),
                state.setupInProgress(),
                state.invertedIndexStore().snapshot(),
                state.keyShareStore().snapshotUpdateTupleShareOrder(),
                state.keyShareStore().hasMasterKeyShare(),
                state.keyShareStore().hasTrapdoorPrivateKeyShare()
        );
    }

    public VerifiableShare[] getSnapshotShares(SSEServerState state, List<IndexAddress> updateTupleShareOrder,
                                               boolean includeMasterKeyShare,
                                               boolean includeTrapdoorPrivateKeyShare) {
        return state.keyShareStore().sharesInOrder(
                updateTupleShareOrder,
                includeMasterKeyShare,
                includeTrapdoorPrivateKeyShare
        );
    }

    public void installSnapshot(SSEServerState state, SSEPlainSnapshotData snapshotData, VerifiableShare masterKeyShare,
                                VerifiableShare trapdoorPrivateKeyShare,
                                Map<IndexAddress, VerifiableShare> updateTupleShares) {
        if (snapshotData == null) {
            throw new IllegalArgumentException("snapshotData cannot be null");
        }
        state.setTrapdoorPublicKey(snapshotData.encodedTrapdoorPublicKey() == null
                ? null
                : TrapdoorPermutation.decodePublicKey(snapshotData.encodedTrapdoorPublicKey()));
        state.setEncryptedKeywordLocationMap(snapshotData.encryptedKeywordLocationMap());
        state.setActiveClientId(snapshotData.activeClientId());
        state.setBlockedStateRequestsWhileActive(snapshotData.blockedStateRequestsWhileActive());
        state.setSetupInProgress(snapshotData.setupInProgress());
        state.invertedIndexStore().restore(snapshotData.invertedIndex());
        state.keyShareStore().restoreUpdateTupleShares(updateTupleShares);
        state.keyShareStore().setMasterKeyShare(masterKeyShare);
        state.keyShareStore().setTrapdoorPrivateKeyShare(trapdoorPrivateKeyShare);
    }
}
