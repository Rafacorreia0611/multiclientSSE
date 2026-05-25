package sse.demo.client;

import java.util.List;
import java.util.Map;

import javax.crypto.SecretKey;

import sse.domain.EncryptedUpdateTuple;
import sse.domain.InitializationMaterial;
import sse.domain.KeywordUpdate;
import sse.domain.PreparedUpdateRequest;
import sse.domain.SearchToken;
import sse.domain.State;
import sse.facade.SseClientFacade;

public final class SseClientHandler {

    private final ConfidentialClientAdapter adapter;
    private final SseClientFacade sseClientFacade;

    public SseClientHandler(ConfidentialClientAdapter adapter) {
        this.adapter = adapter;
        this.sseClientFacade = new SseClientFacade();
    }

    public void initializeState() {
        InitializationMaterial initializationMaterial = sseClientFacade.generateInitialStateData();
        if (adapter.sendInitializeStateRequest(initializationMaterial)) {
            System.out.println("State initialized.");
        } else {
            System.out.println("State was already initialized.");
        }
    }

    public List<String> search(String keyword) {
        while (true) {
            ConfidentialClientAdapter.StateRequestResult stateRequest = adapter.requestState();
            State state = stateRequest.state();
            SecretKey tokenGenKey = stateRequest.tokenGenKey();
            SecretKey updateCounterKey = stateRequest.updateCounterKey();

            SearchToken searchToken = sseClientFacade.generateSearchToken(
                    tokenGenKey,
                    updateCounterKey,
                    state,
                    keyword
            );
            Map<EncryptedUpdateTuple, SecretKey> searchResults = adapter.sendSearchRequest(searchToken);
            if (searchResults != null) {
                return sseClientFacade.extractAddedDocIds(searchResults);
            }
        }
    }

    public void update(List<KeywordUpdate> updates) {
        if (updates == null || updates.isEmpty()) {
            throw new IllegalArgumentException("updates cannot be null or empty");
        }

        while (true) {
            ConfidentialClientAdapter.StateRequestResult stateRequest = adapter.requestState();
            State state = stateRequest.state();
            SecretKey tokenGenKey = stateRequest.tokenGenKey();
            SecretKey updateCounterKey = stateRequest.updateCounterKey();

            PreparedUpdateRequest preparedUpdateRequest = sseClientFacade.prepareUpdateRequest(
                    tokenGenKey,
                    updateCounterKey,
                    state,
                    updates
            );

            SecretKey[] tupleKeys = preparedUpdateRequest.tupleKeys()
                    .toArray(new SecretKey[preparedUpdateRequest.tupleKeys().size()]);
            if (adapter.sendUpdateRequest(preparedUpdateRequest.updateToken(), tupleKeys)) {
                return;
            }
        }
    }

    public void close() {
        adapter.close();
    }
}
