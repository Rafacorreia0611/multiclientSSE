package sse.demo.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bftsmart.tom.MessageContext;
import confidential.ConfidentialMessage;
import confidential.facade.server.ConfidentialServerFacade;
import confidential.facade.server.ConfidentialSingleExecutable;
import confidential.statemanagement.ConfidentialSnapshot;
import sse.SseServer;
import sse.demo.common.RequestType;
import sse.demo.common.ResponseStatus;
import sse.model.EncryptedUpdateTuple;
import sse.model.IndexAddress;
import sse.model.PlainSnapshotData;
import sse.model.SearchToken;
import sse.model.State;
import sse.model.UpdateToken;
import vss.secretsharing.VerifiableShare;

public class ConfidentialSseServer implements ConfidentialSingleExecutable {

    private SseServer sseServer;
    private int activeClientId = -1;

    ConfidentialSseServer(int processId) {
        sseServer = new SseServer();
        new ConfidentialServerFacade(processId, this);
    }

    @Override
    public ConfidentialMessage appExecuteOrdered(byte[] plainData, VerifiableShare[] vss, MessageContext mc) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(plainData);
             ObjectInput in = new ObjectInputStream(bis)) {
            RequestType type = RequestType.getRequestType(in.read());
            int clientId = in.readInt();
            switch (type) {
                case INIT_TOKEN_GEN_KEY:
                    return statusMessage(
                            sseServer.initializeTokenGenKey(vss[0]) ? ResponseStatus.OK : ResponseStatus.FAILED
                    );
                case SEARCH:
                    SearchToken searchToken = SearchToken.deserialize(readPayload(in));
                    return handleSearch(clientId, searchToken);
                case UPDATE:
                    UpdateToken updateToken = UpdateToken.deserialize(readPayload(in));
                    return handleUpdate(clientId, updateToken, vss[0]);
                case STATE_SRCH:
                case STATE_UPD:
                    return handleStateOp(type, clientId);
                default:
                    throw new IllegalArgumentException("Unknown request type: " + type);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error processing request", e);
        }
    }

    @Override
    public ConfidentialMessage appExecuteUnordered(byte[] bytes, VerifiableShare[] vss, MessageContext mc) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public ConfidentialSnapshot getConfidentialSnapshot() {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutput out = new ObjectOutputStream(bos)) {
            PlainSnapshotData sseSnapshotData = sseServer.getPlainSnapshotData();
            out.writeObject(new ServerSnapshotData(sseSnapshotData, activeClientId));
            out.flush();
            bos.flush();
            VerifiableShare[] shares = sseServer.getSnapshotShares(
                    sseSnapshotData.updateTupleShareOrder(),
                    sseSnapshotData.hasTokenGenKeyShare()
            );
            return new ConfidentialSnapshot(bos.toByteArray(), shares);
        } catch (IOException e) {
            throw new RuntimeException("Error creating confidential snapshot", e);
        }
    }

    @Override
    public void installConfidentialSnapshot(ConfidentialSnapshot cs) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(cs.getPlainData());
             ObjectInput in = new ObjectInputStream(bis)) {
            ServerSnapshotData snapshotData = (ServerSnapshotData) in.readObject();
            PlainSnapshotData sseSnapshotData = snapshotData.sseSnapshotData();
            VerifiableShare[] shares = cs.getShares();
            int index = 0;

            VerifiableShare tokenGenKeyShare = null;
            if (sseSnapshotData.hasTokenGenKeyShare()) {
                if (shares == null || shares.length == 0) {
                    throw new IllegalStateException("Snapshot is missing token generation key share");
                }
                tokenGenKeyShare = shares[index++];
            }

            Map<IndexAddress, VerifiableShare> updateTupleShares = new HashMap<IndexAddress, VerifiableShare>();
            for (IndexAddress address : sseSnapshotData.updateTupleShareOrder()) {
                if (shares == null || index >= shares.length) {
                    throw new IllegalStateException("Snapshot is missing update tuple shares");
                }
                updateTupleShares.put(address, shares[index++]);
            }

            if (shares != null && index != shares.length) {
                throw new IllegalStateException("Snapshot contains unexpected extra shares");
            }

            sseServer.installSnapshot(sseSnapshotData, tokenGenKeyShare, updateTupleShares);
            activeClientId = snapshotData.activeClientId();
        } catch (IOException e) {
            throw new RuntimeException("Error installing confidential snapshot", e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Error installing confidential snapshot", e);
        }
    }

    private ConfidentialMessage handleSearch(int clientId, SearchToken searchToken) {
        if (activeClientId == -1) {
            return statusMessage(ResponseStatus.RETRY);
        } else if (activeClientId != clientId) {
            return statusMessage(ResponseStatus.BUSY);
        }
        try {
            if (searchToken == null) {
                return statusMessage(ResponseStatus.FAILED);
            }
            Map<EncryptedUpdateTuple, VerifiableShare> searchResults = sseServer.searchQuery(searchToken);
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
            activeClientId = -1;
        }
    }

    private ConfidentialMessage handleStateOp(RequestType type, int clientId) {
        if (activeClientId != -1 && activeClientId != clientId) {
            return statusMessage(ResponseStatus.BUSY);
        }

        State state = type == RequestType.STATE_SRCH ? sseServer.getState("search") : sseServer.getState("update");
        VerifiableShare tokenGenKeyShare = sseServer.getTokenGenKey();

        activeClientId = clientId;
        byte[] plainResponse = withStatus(ResponseStatus.OK, state.serialize());
        if (tokenGenKeyShare == null) {
            return new ConfidentialMessage(plainResponse);
        }

        return new ConfidentialMessage(plainResponse, tokenGenKeyShare);
    }

    private ConfidentialMessage handleUpdate(int clientId, UpdateToken updateToken, VerifiableShare updateTupleKeyShare) {
        if (activeClientId == -1) {
            return statusMessage(ResponseStatus.RETRY);
        } else if (activeClientId != clientId) {
            return statusMessage(ResponseStatus.BUSY);
        }
        try {
            if (updateToken == null || updateTupleKeyShare == null) {
                return statusMessage(ResponseStatus.FAILED);
            }
            sseServer.updateQuery(updateToken, updateTupleKeyShare);
            return statusMessage(ResponseStatus.OK);
        } finally {
            activeClientId = -1;
        }
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

    private byte[] readPayload(ObjectInput in) {
        try {
            int length = in.readInt();
            if (length < 0) {
                return null;
            }
            byte[] payload = new byte[length];
            in.readFully(payload);
            return payload;
        } catch (IOException e) {
            throw new RuntimeException("Error reading request payload", e);
        }
    }

    private static final class ServerSnapshotData implements Serializable {
        private final PlainSnapshotData sseSnapshotData;
        private final int activeClientId;

        private ServerSnapshotData(PlainSnapshotData sseSnapshotData, int activeClientId) {
            this.sseSnapshotData = sseSnapshotData;
            this.activeClientId = activeClientId;
        }

        private PlainSnapshotData sseSnapshotData() {
            return sseSnapshotData;
        }

        private int activeClientId() {
            return activeClientId;
        }
    }
}
