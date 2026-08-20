package sse.demo.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.Map;

import confidential.ConfidentialMessage;
import confidential.statemanagement.ConfidentialSnapshot;
import sse.demo.messages.ResponseStatus;
import sse.domain.setup.InitialStatePayload;
import sse.domain.search.SearchResponseData;
import sse.domain.search.SearchToken;
import sse.domain.state.State;
import sse.domain.update.UpdateToken;
import sse.facade.SSEServerFacade;
import sse.snapshot.SSEPlainSnapshotData;
import vss.secretsharing.VerifiableShare;

public final class SSEServerHandler {

    private static final int MAX_BLOCKED_STATE_REQUESTS = 3;
    private static final int MAX_BLOCKED_STATE_REQUESTS_DURING_SETUP = 20;

    private final SSEServerFacade sseServerFacade;

    public SSEServerHandler() {
        this.sseServerFacade = new SSEServerFacade();
    }

    public boolean initializeState(InitialStatePayload initialStatePayload,
                                   VerifiableShare masterKeyShare,
                                   VerifiableShare trapdoorPrivateKeyShare) {
        if (initialStatePayload == null) {
            return false;
        }
        return sseServerFacade.initializeState(
                initialStatePayload.encodedTrapdoorPublicKey(),
                initialStatePayload.encryptedKeywordLocationMap(),
                masterKeyShare,
                trapdoorPrivateKeyShare
        );
    }

    public ConfidentialMessage handleIsInitialized() {
        return new ConfidentialMessage(new byte[] {
                (byte) ResponseStatus.OK.ordinal(),
                (byte) (sseServerFacade.isInitialized() ? 1 : 0)
        });
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
        VerifiableShare masterKeyShare = sseServerFacade.getMasterKey();
        VerifiableShare trapdoorPrivateKeyShare = sseServerFacade.getTrapdoorPrivateKey();

        sseServerFacade.activateClient(clientId, setupRequested);
        byte[] plainResponse = serializeResponse(ResponseStatus.OK, state);
        if (masterKeyShare == null || trapdoorPrivateKeyShare == null) {
            return new ConfidentialMessage(plainResponse);
        }

        return new ConfidentialMessage(plainResponse, masterKeyShare, trapdoorPrivateKeyShare);
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
        SSEPlainSnapshotData sseSnapshotData = sseServerFacade.getPlainSnapshotData();
        byte[] plainData = sseSnapshotData.serialize();
        VerifiableShare[] shares = sseServerFacade.getSnapshotShares(
                sseSnapshotData.updateTupleShareOrder(),
                sseSnapshotData.hasMasterKeyShare(),
                sseSnapshotData.hasTrapdoorPrivateKeyShare()
        );
        return new ConfidentialSnapshot(plainData, shares);
    }

    public void installConfidentialSnapshot(ConfidentialSnapshot cs) {
        SSEPlainSnapshotData snapshot = SSEPlainSnapshotData.deserialize(cs.getPlainData());
        sseServerFacade.installSnapshot(
                snapshot,
                snapshot.masterKeyShare(cs.getShares()),
                snapshot.trapdoorPrivateKeyShare(cs.getShares()),
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
