package sse.demo.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.security.interfaces.RSAPrivateKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import confidential.client.ConfidentialServiceProxy;
import confidential.client.Response;
import sse.crypto.Prf;
import sse.crypto.TrapdoorPermutation;
import sse.demo.messages.RequestType;
import sse.demo.messages.ResponseStatus;
import sse.domain.EncryptedUpdateTuple;
import sse.domain.InitializationMaterial;
import sse.domain.SearchToken;
import sse.domain.State;
import sse.domain.UpdateToken;
import vss.facade.SecretSharingException;

public final class ConfidentialClientAdapter {

    private static final long STATE_RETRY_DELAY_MS = 250L;

    private final ConfidentialServiceProxy service;
    private final int clientId;

    public ConfidentialClientAdapter(int clientId) throws SecretSharingException {
        this.clientId = clientId;
        this.service = new ConfidentialServiceProxy(clientId);
    }

    public void close() {
        service.close();
    }

    public boolean sendInitializeStateRequest(InitializationMaterial initializationMaterial) {
        if (initializationMaterial == null) {
            throw new IllegalArgumentException("initializationMaterial cannot be null");
        }

        return sendStatusOnlyRequest(
                RequestType.INIT_STATE,
                TrapdoorPermutation.encodePublicKey(initializationMaterial.trapdoorPublicKey()),
                new byte[][] {
                        initializationMaterial.masterKey().getEncoded(),
                        TrapdoorPermutation.encodePrivateKey(initializationMaterial.trapdoorPrivateKey())
                }
        );
    }

    public StateRequestResult requestState() {
        return requestState(RequestType.STATE);
    }

    public StateRequestResult requestSetupState() {
        return requestState(RequestType.SETUP_STATE);
    }

    private StateRequestResult requestState(RequestType requestType) {
        boolean waitingLogged = false;
        while (true) {
            try {
                Response response = service.invokeOrdered(serialize(requestType, null));
                ensureResponsePresent(response, requestType + " operation");
                byte[] plainResponse = response.getPainData();
                if (plainResponse == null || plainResponse.length == 0) {
                    throw new RuntimeException("State response missing from server");
                }
                ResponseStatus responseStatus = ResponseStatus.getResponseStatus(Byte.toUnsignedInt(plainResponse[0]));
                if (responseStatus == ResponseStatus.BUSY) {
                    if (!waitingLogged) {
                        System.out.println("Another client is active. Retrying...");
                        waitingLogged = true;
                    }
                    Thread.sleep(STATE_RETRY_DELAY_MS);
                    continue;
                }
                if (responseStatus != ResponseStatus.OK) {
                    throw new RuntimeException("Unexpected state response status: " + responseStatus);
                }
                State state = State.deserialize(Arrays.copyOfRange(plainResponse, 1, plainResponse.length));
                if (state == null) {
                    throw new RuntimeException("State missing from response");
                }
                if (response.getConfidentialData() == null || response.getConfidentialData().length < 2) {
                    throw new RuntimeException("State keys missing from response");
                }
                SecretKey masterKey = new SecretKeySpec(response.getConfidentialData()[0], Prf.ALGORITHM);
                RSAPrivateKey trapdoorPrivateKey = TrapdoorPermutation.decodePrivateKey(response.getConfidentialData()[1]);
                return new StateRequestResult(
                        state,
                        masterKey,
                        trapdoorPrivateKey
                );
            } catch (SecretSharingException e) {
                throw new RuntimeException("Error invoking " + requestType + " operation", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting to retry state request", e);
            }
        }
    }

    public Map<EncryptedUpdateTuple, SecretKey> sendSearchRequest(SearchToken searchToken) {
        byte[] requestData = serialize(RequestType.SEARCH, searchToken.serialize());
        try {
            Response response = service.invokeOrdered(requestData);
            ensureResponsePresent(response, "SEARCH request");
            byte[] plainResponse = response.getPainData();
            if (plainResponse == null || plainResponse.length == 0) {
                throw new RuntimeException("Response status missing from server");
            }
            ResponseStatus responseStatus = ResponseStatus.getResponseStatus(Byte.toUnsignedInt(plainResponse[0]));
            if (responseStatus != ResponseStatus.OK) {
                return null;
            }

            return deserializeSearchResults(
                    Arrays.copyOfRange(plainResponse, 1, plainResponse.length),
                    response.getConfidentialData()
            );

        } catch (SecretSharingException e) {
            throw new RuntimeException("Error invoking search request", e);
        }
    }

    public boolean sendUpdateRequest(UpdateToken updateToken, SecretKey[] updateTupleKeys) {
        byte[][] confidentialData = null;
        if (updateTupleKeys != null) {
            confidentialData = new byte[updateTupleKeys.length][];
            for (int i = 0; i < updateTupleKeys.length; i++) {
                if (updateTupleKeys[i] == null) {
                    throw new IllegalArgumentException("updateTupleKeys cannot contain null values");
                }
                confidentialData[i] = updateTupleKeys[i].getEncoded();
            }
        }

        return sendStatusOnlyRequest(
                RequestType.UPDATE,
                updateToken == null ? null : updateToken.serialize(),
                confidentialData
        );
    }

    public boolean sendSetupCompleteRequest() {
        return sendStatusOnlyRequest(RequestType.SETUP_COMPLETE, null, null);
    }

    public boolean sendSetupAbortRequest() {
        return sendStatusOnlyRequest(RequestType.SETUP_ABORT, null, null);
    }

    private byte[] serialize(RequestType type, byte[] payload) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutput out = new ObjectOutputStream(bos)) {

            out.write((byte) type.ordinal());
            out.writeInt(clientId);
            if (payload != null) {
                out.writeInt(payload.length);
                out.write(payload);
            } else {
                out.writeInt(-1);
            }
            out.flush();
            bos.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Error serializing request", e);
        }
    }

    private boolean sendStatusOnlyRequest(RequestType requestType, byte[] payload, byte[][] confidentialData) {
        Response response;
        try {
            if (confidentialData == null) {
                response = service.invokeOrdered(serialize(requestType, payload));
            } else {
                response = service.invokeOrdered(serialize(requestType, payload), confidentialData);
            }
        } catch (SecretSharingException e) {
            throw new RuntimeException("Error invoking " + requestType + " request", e);
        }

        ensureResponsePresent(response, requestType + " request");
        byte[] plainResponse = response.getPainData();
        if (plainResponse == null || plainResponse.length == 0) {
            throw new RuntimeException("Response status missing from server");
        }
        ResponseStatus responseStatus = ResponseStatus.getResponseStatus(Byte.toUnsignedInt(plainResponse[0]));
        return responseStatus == ResponseStatus.OK;
    }

    private void ensureResponsePresent(Response response, String operationDescription) {
        if (response == null) {
            throw new RuntimeException("No response received for " + operationDescription
                    + ". This usually means a timeout or lower-layer failure.");
        }
    }

    private Map<EncryptedUpdateTuple, SecretKey> deserializeSearchResults(byte[] serializedTuples, byte[][] tupleKeyBytes) {
        List<EncryptedUpdateTuple> encryptedTuples = deserializeEncryptedTuples(serializedTuples);
        byte[][] confidentialData = tupleKeyBytes == null ? new byte[0][] : tupleKeyBytes;
        if (encryptedTuples.size() != confidentialData.length) {
            throw new RuntimeException("Mismatch between encrypted tuples and tuple keys in search response");
        }

        Map<EncryptedUpdateTuple, SecretKey> result =
                new LinkedHashMap<EncryptedUpdateTuple, SecretKey>(encryptedTuples.size());
        for (int i = 0; i < encryptedTuples.size(); i++) {
            result.put(encryptedTuples.get(i), new SecretKeySpec(confidentialData[i], "AES"));
        }
        return result;
    }

    private List<EncryptedUpdateTuple> deserializeEncryptedTuples(byte[] data) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             ObjectInput in = new ObjectInputStream(bis)) {

            Object obj = in.readObject();
            if (!(obj instanceof List<?>)) {
                throw new RuntimeException("Search results are not a list");
            }

            List<?> rawList = (List<?>) obj;
            List<EncryptedUpdateTuple> result = new ArrayList<EncryptedUpdateTuple>(rawList.size());
            for (Object entry : rawList) {
                if (!(entry instanceof EncryptedUpdateTuple)) {
                    throw new RuntimeException("Invalid encrypted tuple type in search results");
                }
                result.add((EncryptedUpdateTuple) entry);
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Error deserializing search results", e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Error deserializing search results", e);
        }
    }

    public static final class StateRequestResult {
        private final State state;
        private final SecretKey masterKey;
        private final RSAPrivateKey trapdoorPrivateKey;

        private StateRequestResult(State state, SecretKey masterKey, RSAPrivateKey trapdoorPrivateKey) {
            this.state = state;
            this.masterKey = masterKey;
            this.trapdoorPrivateKey = trapdoorPrivateKey;
        }

        public State state() {
            return state;
        }

        public SecretKey masterKey() {
            return masterKey;
        }

        public RSAPrivateKey trapdoorPrivateKey() {
            return trapdoorPrivateKey;
        }
    }


}
