package sse.demo.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.Map;

import confidential.ConfidentialMessage;
import confidential.statemanagement.ConfidentialSnapshot;
import sse.demo.messages.ResponseStatus;
import sse.domain.EncryptedUpdateCounter;
import sse.domain.SearchResponseData;
import sse.domain.SearchToken;
import sse.domain.State;
import sse.domain.UpdateToken;
import sse.facade.SseServerFacade;
import sse.snapshot.SsePlainSnapshotData;
import vss.secretsharing.VerifiableShare;

public final class SseServerHandler {

    private static final int MAX_BLOCKED_STATE_REQUESTS = 3;
    private static final int MAX_BLOCKED_STATE_REQUESTS_DURING_SETUP = 20;

    private final SseServerFacade sseServerFacade;

    public SseServerHandler() {
        this.sseServerFacade = new SseServerFacade();
    }

    public boolean initializeState(EncryptedUpdateCounter encryptedUpdateCounter,
                                   VerifiableShare tokenGenKeyShare,
                                   VerifiableShare updateCounterKeyShare) {
        return sseServerFacade.initializeState(encryptedUpdateCounter, tokenGenKeyShare, updateCounterKeyShare);
    }

    public ConfidentialMessage handleSearch(int clientId, SearchToken searchToken) {
        int activeClientId = sseServerFacade.getActiveClientId();
        if (activeClientId == -1) {
            return statusMessage(ResponseStatus.RETRY);
        } else if (activeClientId != clientId) {
            return statusMessage(ResponseStatus.BUSY);
        }
        try {
            if (searchToken == null) {
                return statusMessage(ResponseStatus.FAILED);
            }
            SearchResponseData searchResults = sseServerFacade.searchQuery(searchToken);
            byte[] plainResponse = serializeResponse(ResponseStatus.OK, searchResults.encryptedTuples());
            return new ConfidentialMessage(plainResponse, searchResults.updateTupleSharesArray());
        } finally {
            sseServerFacade.clearActiveClientId();
        }
    }

    public ConfidentialMessage handleState(int clientId, boolean setupRequested) {
        int activeClientId = sseServerFacade.getActiveClientId();
        if (activeClientId != -1 && activeClientId != clientId) {
            int blockedStateRequests = sseServerFacade.incrementBlockedStateRequestsWhileActive();
            int maxBlockedStateRequests = sseServerFacade.isSetupInProgress()
                    ? MAX_BLOCKED_STATE_REQUESTS_DURING_SETUP
                    : MAX_BLOCKED_STATE_REQUESTS;
            if (blockedStateRequests >= maxBlockedStateRequests) {
                if (sseServerFacade.isSetupInProgress()) {
                    System.out.println("Expiring PopulateDB client " + activeClientId
                            + " after " + blockedStateRequests + " blocked STATE requests during setup.");
                } else {
                    System.out.println("Expiring active client " + activeClientId
                            + " after " + blockedStateRequests + " blocked STATE requests.");
                }
                sseServerFacade.clearActiveClientId();
            } else {
                return statusMessage(ResponseStatus.BUSY);
            }
        }
        if (!sseServerFacade.isInitialized()) {
            return statusMessage(ResponseStatus.FAILED);
        }

        State state = sseServerFacade.getState();
        VerifiableShare tokenGenKeyShare = sseServerFacade.getTokenGenKey();
        VerifiableShare updateCounterKeyShare = sseServerFacade.getUpdateCounterKey();

        sseServerFacade.activateClient(clientId, setupRequested);
        byte[] plainResponse = serializeResponse(ResponseStatus.OK, state);
        if (tokenGenKeyShare == null || updateCounterKeyShare == null) {
            return new ConfidentialMessage(plainResponse);
        }

        return new ConfidentialMessage(plainResponse, tokenGenKeyShare, updateCounterKeyShare);
    }

    public ConfidentialMessage handleUpdate(int clientId, UpdateToken updateToken, VerifiableShare[] updateTupleKeyShares) {
        int activeClientId = sseServerFacade.getActiveClientId();
        if (activeClientId == -1) {
            return statusMessage(ResponseStatus.RETRY);
        } else if (activeClientId != clientId) {
            return statusMessage(ResponseStatus.BUSY);
        }

        boolean setupMode = sseServerFacade.isSetupInProgress();

        try {
            if (updateToken == null || updateTupleKeyShares == null) {
                return statusMessage(ResponseStatus.FAILED);
            }
            sseServerFacade.updateQuery(updateToken, updateTupleKeyShares);
            if (setupMode) {
                sseServerFacade.resetBlockedStateRequestsWhileActive();
            }
            return statusMessage(ResponseStatus.OK);
        } catch (IllegalArgumentException e) {
            System.err.println("Rejected update from client " + clientId + ": " + e.getMessage());
            return statusMessage(ResponseStatus.FAILED);
        } finally {
            if (!setupMode) {
                sseServerFacade.clearActiveClientId();
            }
        }
    }

    public ConfidentialMessage handleSetupComplete(int clientId) {
        int activeClientId = sseServerFacade.getActiveClientId();
        if (activeClientId == -1) {
            return statusMessage(ResponseStatus.RETRY);
        } else if (activeClientId != clientId) {
            return statusMessage(ResponseStatus.BUSY);
        }

        sseServerFacade.clearActiveClientId();
        System.out.println("PopulateDB completed by client " + clientId + ". SSE database population is ready.");
        return statusMessage(ResponseStatus.OK);
    }

    public ConfidentialMessage handleSetupAbort(int clientId) {
        int activeClientId = sseServerFacade.getActiveClientId();
        if (activeClientId == -1) {
            return statusMessage(ResponseStatus.RETRY);
        } else if (activeClientId != clientId) {
            return statusMessage(ResponseStatus.BUSY);
        }

        sseServerFacade.clearActiveClientId();
        System.out.println("PopulateDB aborted by client " + clientId + ". Active setup session was cleared.");
        return statusMessage(ResponseStatus.OK);
    }

    public ConfidentialSnapshot getConfidentialSnapshot() {
        SsePlainSnapshotData sseSnapshotData = sseServerFacade.getPlainSnapshotData();
        byte[] plainData = sseSnapshotData.serialize();
        VerifiableShare[] shares = sseServerFacade.getSnapshotShares(
                sseSnapshotData.updateTupleShareOrder(),
                sseSnapshotData.hasTokenGenKeyShare(),
                sseSnapshotData.hasUpdateCounterKeyShare()
        );
        return new ConfidentialSnapshot(plainData, shares);
    }

    public void installConfidentialSnapshot(ConfidentialSnapshot cs) {
        SsePlainSnapshotData snapshot = SsePlainSnapshotData.deserialize(cs.getPlainData());
        sseServerFacade.installSnapshot(
                snapshot,
                snapshot.tokenGenKeyShare(cs.getShares()),
                snapshot.updateCounterKeyShare(cs.getShares()),
                snapshot.updateTupleShares(cs.getShares())
        );
    }

    private ConfidentialMessage statusMessage(ResponseStatus status) {
        return new ConfidentialMessage(new byte[]{(byte) status.ordinal()});
    }

    private byte[] serializeResponse(ResponseStatus status, Object payload) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            bos.write((byte) status.ordinal());
            try (ObjectOutput out = new ObjectOutputStream(bos)) {
                out.writeObject(payload);
                out.flush();
            }
            bos.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Error serializing response", e);
        }
    }
}
