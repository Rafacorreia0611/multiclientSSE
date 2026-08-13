package sse.demo.client;

import java.nio.file.Path;
import java.security.interfaces.RSAPrivateKey;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.crypto.SecretKey;

import sse.domain.update.EncryptedUpdateTuple;
import sse.domain.setup.InitializationMaterial;
import sse.domain.update.KeywordUpdate;
import sse.domain.update.PreparedUpdateRequest;
import sse.domain.search.SearchToken;
import sse.domain.state.State;
import sse.facade.SseClientFacade;
import sse.vocabulary.VocabularyLoader;

public final class SseClientHandler {

    private final ConfidentialClientAdapter adapter;
    private final SseClientFacade sseClientFacade;
    private final Path vocabularyPath;

    public SseClientHandler(ConfidentialClientAdapter adapter) {
        this(adapter, VocabularyLoader.DEFAULT_VOCABULARY_PATH);
    }

    public SseClientHandler(ConfidentialClientAdapter adapter, Path vocabularyPath) {
        if (vocabularyPath == null) {
            throw new IllegalArgumentException("vocabularyPath cannot be null");
        }
        this.adapter = adapter;
        this.sseClientFacade = new SseClientFacade();
        this.vocabularyPath = vocabularyPath;
    }

    public void initializeState() {
        if (adapter.isInitialized()) {
            System.out.println("State already initialized.");
            return;
        }

        InitializationMaterial initializationMaterial = sseClientFacade.generateInitialStateData(vocabularyPath);
        if (adapter.sendInitializeStateRequest(initializationMaterial)) {
            System.out.println("State initialized.");
        } else {
            System.out.println("State was initialized by another client.");
        }
    }

    public List<String> search(String keyword) {
        while (true) {
            ConfidentialClientAdapter.StateRequestResult stateRequest = adapter.requestState();
            State state = stateRequest.state();
            SecretKey masterKey = stateRequest.masterKey();

            SearchToken searchToken = sseClientFacade.generateSearchToken(
                    masterKey,
                    state,
                    keyword
            );
            if (searchToken == null) {
                return Collections.emptyList();
            }
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
            SecretKey masterKey = stateRequest.masterKey();
            RSAPrivateKey trapdoorPrivateKey = stateRequest.trapdoorPrivateKey();

            PreparedUpdateRequest preparedUpdateRequest = sseClientFacade.prepareUpdateRequest(
                    masterKey,
                    trapdoorPrivateKey,
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
