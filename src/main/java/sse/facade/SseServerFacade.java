package sse.facade;

import java.util.Map;
import java.util.List;
import java.security.interfaces.RSAPublicKey;

import sse.crypto.TrapdoorPermutation;
import sse.domain.id.IndexAddress;
import sse.domain.search.SearchResponseData;
import sse.domain.search.SearchToken;
import sse.domain.state.EncryptedKeywordLocationMap;
import sse.domain.state.State;
import sse.domain.update.UpdateToken;
import sse.snapshot.SsePlainSnapshotData;
import sse.service.server.SearchService;
import sse.service.server.SnapshotService;
import sse.service.server.UpdateService;
import sse.state.SseServerState;
import vss.secretsharing.VerifiableShare;

public final class SseServerFacade {

    private final SseServerState state;
    private final SearchService searchService;
    private final UpdateService updateService;
    private final SnapshotService snapshotService;

    public SseServerFacade() {
        this.state = new SseServerState();
        this.searchService = new SearchService();
        this.updateService = new UpdateService();
        this.snapshotService = new SnapshotService();
    }

    public SearchResponseData searchQuery(SearchToken searchToken) {
        return searchService.search(state, searchToken);
    }

    public void updateQuery(UpdateToken updateToken, VerifiableShare[] updateTupleKeyShares) {
        updateService.update(state, updateToken, updateTupleKeyShares);
    }

    public boolean isInitialized() {
        return state.isInitialized();
    }

    public State getState() {
        if (!state.isInitialized()) {
            throw new IllegalStateException("Server state is not initialized");
        }
        return new State(
                state.keywordStates(),
                TrapdoorPermutation.encodePublicKey(state.trapdoorPublicKey()),
                state.encryptedKeywordLocationMap()
        );
    }

    public VerifiableShare getMasterKey() {
        return state.keyShareStore().masterKeyShare();
    }

    public VerifiableShare getTrapdoorPrivateKey() {
        return state.keyShareStore().trapdoorPrivateKeyShare();
    }

    public int getActiveClientId() {
        return state.activeClientId();
    }

    public void activateClient(int clientId, boolean setupInProgress) {
        state.setActiveClientId(clientId);
        state.setBlockedStateRequestsWhileActive(0);
        state.setSetupInProgress(setupInProgress);
    }

    public void clearActiveClientId() {
        state.setActiveClientId(-1);
        state.setBlockedStateRequestsWhileActive(0);
        state.setSetupInProgress(false);
    }

    public int incrementBlockedStateRequestsWhileActive() {
        int nextValue = state.blockedStateRequestsWhileActive() + 1;
        state.setBlockedStateRequestsWhileActive(nextValue);
        return nextValue;
    }

    public void resetBlockedStateRequestsWhileActive() {
        state.setBlockedStateRequestsWhileActive(0);
    }

    public boolean isSetupInProgress() {
        return state.setupInProgress();
    }

    public Boolean initializeState(byte[] encodedTrapdoorPublicKey,
                                   EncryptedKeywordLocationMap encryptedKeywordLocationMap,
                                   VerifiableShare masterKeyShare,
                                   VerifiableShare trapdoorPrivateKeyShare) {
        if (encodedTrapdoorPublicKey == null || encryptedKeywordLocationMap == null
                || masterKeyShare == null || trapdoorPrivateKeyShare == null) {
            return false;
        }
        if (state.isInitialized()
                || state.keyShareStore().hasMasterKeyShare()
                || state.keyShareStore().hasTrapdoorPrivateKeyShare()
                || state.trapdoorPublicKey() != null
                || state.encryptedKeywordLocationMap() != null) {
            return false;
        }

        RSAPublicKey trapdoorPublicKey;
        try {
            trapdoorPublicKey = TrapdoorPermutation.decodePublicKey(encodedTrapdoorPublicKey);
        } catch (IllegalArgumentException e) {
            return false;
        }

        state.setTrapdoorPublicKey(trapdoorPublicKey);
        state.setEncryptedKeywordLocationMap(encryptedKeywordLocationMap);
        state.keyShareStore().setMasterKeyShare(masterKeyShare);
        state.keyShareStore().setTrapdoorPrivateKeyShare(trapdoorPrivateKeyShare);
        return true;
    }

    public SsePlainSnapshotData getPlainSnapshotData() {
        return snapshotService.getPlainSnapshotData(state);
    }

    public VerifiableShare[] getSnapshotShares(List<IndexAddress> updateTupleShareOrder,
                                               boolean includeMasterKeyShare,
                                               boolean includeTrapdoorPrivateKeyShare) {
        return snapshotService.getSnapshotShares(
                state,
                updateTupleShareOrder,
                includeMasterKeyShare,
                includeTrapdoorPrivateKeyShare
        );
    }

    public void installSnapshot(SsePlainSnapshotData snapshotData, VerifiableShare masterKeyShare,
                                VerifiableShare trapdoorPrivateKeyShare,
                                Map<IndexAddress, VerifiableShare> updateTupleShares) {
        snapshotService.installSnapshot(
                state,
                snapshotData,
                masterKeyShare,
                trapdoorPrivateKeyShare,
                updateTupleShares
        );
    }
}
