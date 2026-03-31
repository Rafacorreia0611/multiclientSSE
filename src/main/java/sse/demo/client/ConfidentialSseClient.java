package sse.demo.client;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import confidential.client.ConfidentialServiceProxy;
import confidential.client.Response;
import sse.SseClient;
import sse.demo.common.RequestType;
import sse.demo.common.ResponseStatus;
import sse.model.EncryptedUpdateTuple;
import sse.model.SearchToken;
import sse.model.State;
import sse.model.UpdateOp;
import sse.model.UpdateToken;
import sse.model.UpdateTuple;
import vss.facade.SecretSharingException;

public class ConfidentialSseClient {

    private static final long STATE_RETRY_DELAY_MS = 250L;

    private ConfidentialServiceProxy service;
    private final BufferedReader userIn;
    private final int clientId;

    public ConfidentialSseClient(int clientId) throws SecretSharingException {
        this.clientId = clientId;
        this.service = new ConfidentialServiceProxy(clientId);
        this.userIn = new BufferedReader(new InputStreamReader(System.in));
    }

    public void close() {
        service.close();
        try {
            userIn.close();
        } catch (IOException e) {
            System.err.println("Error closing user input reader: " + e.getMessage());
        }
    }

    public void run() {
        // Init token Generation Key
        // TODO: Gerar as shares da token generation key de maneira distribuida pelas replicas
        initializeTokenGenKey();

        while (true) {
            System.out.println(printMenu());
            String input = readUserLine();

            if (input == null) {
                System.out.println("Input closed. Exiting client.");
                return;
            }

            String normalized = input.trim().toLowerCase();
            if ("1".equals(normalized) || "search".equals(normalized) || "s".equals(normalized)) {
                handleSearch();
            } else if ("2".equals(normalized) || "add".equals(normalized) || "a".equals(normalized)) {
                handleUpdate("Add");
            } else if ("3".equals(normalized) || "delete".equals(normalized) || "d".equals(normalized)) {
                handleUpdate("Delete");
            } else if ("4".equals(normalized) || "exit".equals(normalized) || "e".equals(normalized)
                    || "q".equals(normalized) || "quit".equals(normalized)) {
                System.out.println("Exiting client.");
                return;
            } else {
                System.out.println("Invalid option: " + input);
            }
        }
    }

    private String printMenu() {
        return "Operations:\n"
                + "1) Search\n"
                + "2) Add\n"
                + "3) Delete\n"
                + "4) Exit";
    }

    private void initializeTokenGenKey() {
        KeyGenerator keyGen;
        try {
            keyGen = KeyGenerator.getInstance("HmacSHA256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("HmacSHA256 algorithm not available for token generation key", e);
        }
        keyGen.init(256);
        byte[] tokenGenKey = keyGen.generateKey().getEncoded();

        byte[] requestData = serialize(RequestType.INIT_TOKEN_GEN_KEY, clientId, null);
        try {
            Response response = service.invokeOrdered(requestData, tokenGenKey);
            byte[] plainResponse = response.getPainData();
            if (plainResponse == null || plainResponse.length == 0) {
                throw new RuntimeException("Response status missing from server");
            }
            ResponseStatus responseStatus = ResponseStatus.getResponseStatus(Byte.toUnsignedInt(plainResponse[0]));
            if (responseStatus == ResponseStatus.OK) {
                System.out.println("Token generation key initialized.");
            } else {
                System.out.println("Token generation key was already initialized.");
            }
        } catch (SecretSharingException e) {
            throw new RuntimeException("Error invoking token generation key initialization", e);
        }
    }

    private String readUserLine() {
        try {
            return userIn.readLine();
        } catch (IOException e) {
            throw new RuntimeException("Error reading input from terminal", e);
        }
    }

    private void handleSearch() {
        System.out.println("Search selected.\n");
        System.out.println("Enter the keyword to search for:");
        String keyword = readUserLine();
        if (keyword == null) {
            System.out.println("Input closed. Returning to main menu.");
            return;
        }

        System.out.println("Keyword: " + keyword);
        boolean resetState = true;
        while (resetState) {
            StateRequestResult stateRequest = requestState(RequestType.STATE_SRCH);
            State state = stateRequest.state();
            SecretKey tokenGenKey = stateRequest.tokenGenKey();

            SearchToken searchToken = SseClient.generateSearchToken(tokenGenKey, state, keyword);

            Map<EncryptedUpdateTuple, SecretKey> searchResults = sendSearchRequest(searchToken);
            if (searchResults == null) {
                resetState = true;
            } else {
                resetState = false;
                List<String> docIds = SseClient.extractAddedDocIds(searchResults);
                System.out.println("Search results for keyword '" + keyword + "': " + docIds);
            }
        }
    }

    private Map<EncryptedUpdateTuple, SecretKey> sendSearchRequest(SearchToken searchToken) {
        byte[] requestData = serialize(RequestType.SEARCH, clientId, searchToken.serialize());
        try {
            Response response = service.invokeOrdered(requestData);
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

    private void handleUpdate(String op) {
        boolean isAdd = op.equalsIgnoreCase("Add");

        System.out.println((isAdd ? "Add association" : "Remove association") + " selected.\n");
        System.out.println(isAdd
                ? "Enter the keyword to associate with a document:"
                : "Enter the keyword whose association with a document you want to remove:");
        String keyword = readUserLine();
        if (keyword == null) {
            System.out.println("Input closed. Returning to main menu.");
            return;
        }

        System.out.println("Keyword: " + keyword);
        System.out.println(isAdd
                ? "Enter the document identifier (docId) to associate with that keyword:"
                : "Enter the document identifier (docId) to disassociate from that keyword:");
        String docId = readUserLine();
        if (docId == null) {
            System.out.println("Input closed. Returning to main menu.");
            return;
        }

        System.out.println("DocId: " + docId);

        boolean resetState = true;

        while (resetState) {
            StateRequestResult stateRequest = requestState(RequestType.STATE_UPD);
            State state = stateRequest.state();
            SecretKey tokenGenKey = stateRequest.tokenGenKey();

            SecretKey updateTupleKey = SseClient.generateTupleSecretKey();
            EncryptedUpdateTuple encryptedTuple = generateEncryptedUpdateTuple(keyword, docId, isAdd, updateTupleKey);
            UpdateToken updateToken = SseClient.generateUpdateToken(tokenGenKey, state, keyword, encryptedTuple);

            Response response;
            try {
                response = service.invokeOrdered(
                        serialize(RequestType.UPDATE, clientId, updateToken.serialize()),
                        new byte[][]{updateTupleKey.getEncoded()}
                );
            } catch (SecretSharingException e) {
                throw new RuntimeException("Error invoking update request", e);
            }

            byte[] plainResponse = response.getPainData();
            if (plainResponse == null || plainResponse.length == 0) {
                throw new RuntimeException("Response status missing from server");
            }
            ResponseStatus responseStatus = ResponseStatus.getResponseStatus(Byte.toUnsignedInt(plainResponse[0]));
            if (responseStatus != ResponseStatus.OK) {
                resetState = true;
            } else {
                resetState = false;
            }
        }
    }

    private EncryptedUpdateTuple generateEncryptedUpdateTuple(String keyword, String docId, boolean isAdd,
                                                              SecretKey encryptionKey) {
        byte[] iv = SseClient.generateTupleIv();
        try {
            return SseClient.encryptUpdateTuple(encryptionKey, iv,
                    new UpdateTuple(docId, isAdd ? UpdateOp.ADD : UpdateOp.DEL));
        } catch (Exception e) {
            throw new RuntimeException("Error encrypting update tuple", e);
        }
    }

    private byte[] serialize(RequestType type, int clientId, byte[] payload) {
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

    private StateRequestResult requestState(RequestType type) {
        boolean waitingLogged = false;
        while (true) {
            try {
                Response response = service.invokeOrdered(serialize(type, clientId, null));
                byte[] plainResponse = response.getPainData();
                if (plainResponse == null || plainResponse.length == 0) {
                    throw new RuntimeException("State response missing from server");
                }
                ResponseStatus responseStatus = ResponseStatus.getResponseStatus(Byte.toUnsignedInt(plainResponse[0]));
                if (responseStatus == ResponseStatus.BUSY) {
                    if (!waitingLogged) {
                        System.out.println("Another client is currently active. Waiting a bit and retrying...");
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
                if (response.getConfidentialData() == null || response.getConfidentialData().length == 0) {
                    throw new RuntimeException("Token generation key missing from response");
                }
                SecretKey tokenGenKey = new SecretKeySpec(response.getConfidentialData()[0], "HmacSHA256");
                return new StateRequestResult(state, tokenGenKey);
            } catch (SecretSharingException e) {
                throw new RuntimeException("Error invoking state operation", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting to retry state request", e);
            }
        }
    }

    private static final class StateRequestResult {
        private final State state;
        private final SecretKey tokenGenKey;

        private StateRequestResult(State state, SecretKey tokenGenKey) {
            this.state = state;
            this.tokenGenKey = tokenGenKey;
        }

        private State state() {
            return state;
        }

        private SecretKey tokenGenKey() {
            return tokenGenKey;
        }
    }
}
