package sse.facade;

import java.util.Map;
import java.util.List;

import sse.domain.EncryptedUpdateTuple;
import sse.domain.IndexAddress;
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

    public Map<EncryptedUpdateTuple, VerifiableShare> searchQuery(SearchToken searchToken) {
        return searchService.search(state, searchToken);
    }

    public void updateQuery(UpdateToken updateToken, VerifiableShare updateTupleKeyShare) {
        updateService.update(state, updateToken, updateTupleKeyShare);
    }

    public State getState(String op) {
        if (op == null || (!op.equalsIgnoreCase("search") && !op.equalsIgnoreCase("update"))) {
            throw new IllegalArgumentException("Operation must be either 'search' or 'update'");
        }
        if (op.equalsIgnoreCase("search")) {
            return new State(state.searchCounter());
        }
        return new State(state.searchCounter(), state.updateCounter());
    }

    public VerifiableShare getTokenGenKey() {
        return state.keyShareStore().tokenGenKeyShare();
    }

    public Boolean initializeTokenGenKey(VerifiableShare vss) {
        return state.keyShareStore().initializeTokenGenKeyShare(vss);
    }

    public SsePlainSnapshotData getPlainSnapshotData() {
        return snapshotService.getPlainSnapshotData(state);
    }

    public VerifiableShare[] getSnapshotShares(List<IndexAddress> updateTupleShareOrder,
                                               boolean includeTokenGenKeyShare) {
        return snapshotService.getSnapshotShares(state, updateTupleShareOrder, includeTokenGenKeyShare);
    }

    public void installSnapshot(SsePlainSnapshotData snapshotData, VerifiableShare tokenGenKeyShare,
                                Map<IndexAddress, VerifiableShare> updateTupleShares) {
        snapshotService.installSnapshot(state, snapshotData, tokenGenKeyShare, updateTupleShares);
    }
}
