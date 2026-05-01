package sse.facade;

import java.util.Map;
import java.util.List;

import sse.domain.EncryptedUpdateCounter;
import sse.domain.IndexAddress;
import sse.domain.SearchResponseData;
import sse.domain.SearchToken;
import sse.domain.State;
import sse.domain.UpdateToken;
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
        return new State(state.searchCounter(), state.encryptedUpdateCounter());
    }

    public VerifiableShare getTokenGenKey() {
        return state.keyShareStore().tokenGenKeyShare();
    }

    public VerifiableShare getUpdateCounterKey() {
        return state.keyShareStore().updateCounterKeyShare();
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

    public Boolean initializeState(EncryptedUpdateCounter encryptedUpdateCounter,
                                   VerifiableShare tokenGenKeyShare,
                                   VerifiableShare updateCounterKeyShare) {
        if (encryptedUpdateCounter == null || tokenGenKeyShare == null || updateCounterKeyShare == null) {
            return false;
        }
        if (state.encryptedUpdateCounter() != null
                || state.keyShareStore().hasTokenGenKeyShare()
                || state.keyShareStore().hasUpdateCounterKeyShare()) {
            return false;
        }

        state.setEncryptedUpdateCounter(encryptedUpdateCounter);
        state.keyShareStore().setTokenGenKeyShare(tokenGenKeyShare);
        state.keyShareStore().setUpdateCounterKeyShare(updateCounterKeyShare);
        return true;
    }

    public SsePlainSnapshotData getPlainSnapshotData() {
        return snapshotService.getPlainSnapshotData(state);
    }

    public VerifiableShare[] getSnapshotShares(List<IndexAddress> updateTupleShareOrder,
                                               boolean includeTokenGenKeyShare,
                                               boolean includeUpdateCounterKeyShare) {
        return snapshotService.getSnapshotShares(
                state,
                updateTupleShareOrder,
                includeTokenGenKeyShare,
                includeUpdateCounterKeyShare
        );
    }

    public void installSnapshot(SsePlainSnapshotData snapshotData, VerifiableShare tokenGenKeyShare,
                                VerifiableShare updateCounterKeyShare,
                                Map<IndexAddress, VerifiableShare> updateTupleShares) {
        snapshotService.installSnapshot(
                state,
                snapshotData,
                tokenGenKeyShare,
                updateCounterKeyShare,
                updateTupleShares
        );
    }
}
