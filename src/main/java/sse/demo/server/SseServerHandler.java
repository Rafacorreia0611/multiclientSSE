package sse.demo.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import confidential.ConfidentialMessage;
import confidential.statemanagement.ConfidentialSnapshot;
import sse.demo.messages.ResponseStatus;
import sse.domain.EncryptedUpdateCounter;
import sse.domain.EncryptedUpdateTuple;
import sse.domain.SearchToken;
import sse.domain.State;
import sse.domain.UpdateToken;
import sse.facade.SseServerFacade;
import sse.snapshot.SsePlainSnapshotData;
import vss.secretsharing.VerifiableShare;

public final class SseServerHandler {

    private static final int MAX_BLOCKED_STATE_REQUESTS = 3;

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
            Map<EncryptedUpdateTuple, VerifiableShare> searchResults = sseServerFacade.searchQuery(searchToken);
            List<EncryptedUpdateTuple> encryptedTuples = new ArrayList<EncryptedUpdateTuple>(searchResults.size());
            List<VerifiableShare> updateTupleShares = new ArrayList<VerifiableShare>(searchResults.size());
            for (Map.Entry<EncryptedUpdateTuple, VerifiableShare> entry : searchResults.entrySet()) {
                encryptedTuples.add(entry.getKey());
                updateTupleShares.add(entry.getValue());
            }

            byte[] plainResponse = withStatus(ResponseStatus.OK, serializeSearchResults(encryptedTuples));
            return new ConfidentialMessage(plainResponse,
                    updateTupleShares.toArray(new VerifiableShare[updateTupleShares.size()]));
        } finally {
            sseServerFacade.clearActiveClientId();
        }
    }

    public ConfidentialMessage handleState(int clientId) {
        int activeClientId = sseServerFacade.getActiveClientId();
        if (activeClientId != -1 && activeClientId != clientId) {
            int blockedStateRequests = sseServerFacade.incrementBlockedStateRequestsWhileActive();
            if (blockedStateRequests >= MAX_BLOCKED_STATE_REQUESTS) {
                System.out.println("Expiring active client " + activeClientId
                        + " after " + blockedStateRequests + " blocked STATE requests.");
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

        sseServerFacade.activateClient(clientId);
        byte[] plainResponse = withStatus(ResponseStatus.OK, state.serialize());
        if (tokenGenKeyShare == null || updateCounterKeyShare == null) {
            return new ConfidentialMessage(plainResponse);
        }

        return new ConfidentialMessage(plainResponse, tokenGenKeyShare, updateCounterKeyShare);
    }

    public ConfidentialMessage handleUpdate(int clientId, UpdateToken updateToken, VerifiableShare updateTupleKeyShare) {
        int activeClientId = sseServerFacade.getActiveClientId();
        if (activeClientId == -1) {
            return statusMessage(ResponseStatus.RETRY);
        } else if (activeClientId != clientId) {
            return statusMessage(ResponseStatus.BUSY);
        }
        try {
            if (updateToken == null || updateTupleKeyShare == null) {
                return statusMessage(ResponseStatus.FAILED);
            }
            sseServerFacade.updateQuery(updateToken, updateTupleKeyShare);
            return statusMessage(ResponseStatus.OK);
        } finally {
            sseServerFacade.clearActiveClientId();
        }
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

    private byte[] withStatus(ResponseStatus status, byte[] payload) {
        byte[] result;
        if (payload == null) {
            result = new byte[1];
            result[0] = (byte) status.ordinal();
            return result;
        }
        result = new byte[1 + payload.length];
        result[0] = (byte) status.ordinal();
        System.arraycopy(payload, 0, result, 1, payload.length);
        return result;
    }

    private byte[] serializeSearchResults(List<EncryptedUpdateTuple> searchResults) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutput out = new ObjectOutputStream(bos)) {
            out.writeObject(searchResults);
            out.flush();
            bos.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Error serializing search results", e);
        }
    }
}
