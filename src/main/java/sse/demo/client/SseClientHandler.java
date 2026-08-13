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
        String normalizedKeyword = sseClientFacade.normalizeKeyword(keyword);
        if (normalizedKeyword == null || normalizedKeyword.isEmpty()) {
            throw new IllegalArgumentException("keyword cannot be normalized: " + keyword);
        }

        while (true) {
            ConfidentialClientAdapter.StateRequestResult stateRequest = adapter.requestState();
            State state = stateRequest.state();
            SecretKey masterKey = stateRequest.masterKey();
            Integer keywordLocation = sseClientFacade.resolveKeywordLocation(masterKey, state, normalizedKeyword);
            if (keywordLocation == null) {
                throw new IllegalArgumentException("keyword is outside the vocabulary: " + keyword);
            }

            SearchToken searchToken = sseClientFacade.generateSearchToken(
                    masterKey,
                    state,
                    normalizedKeyword
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

    public void update(KeywordUpdate update) {
        if (update == null) {
            throw new IllegalArgumentException("update cannot be null");
        }
        String normalizedKeyword = sseClientFacade.normalizeKeyword(update.keyword());
        if (normalizedKeyword == null || normalizedKeyword.isEmpty()) {
            throw new IllegalArgumentException("keyword cannot be normalized: " + update.keyword());
        }

        while (true) {
            ConfidentialClientAdapter.StateRequestResult stateRequest = adapter.requestState();
            State state = stateRequest.state();
            SecretKey masterKey = stateRequest.masterKey();
            RSAPrivateKey trapdoorPrivateKey = stateRequest.trapdoorPrivateKey();
            Integer keywordLocation = sseClientFacade.resolveKeywordLocation(masterKey, state, normalizedKeyword);
            if (keywordLocation == null) {
                throw new IllegalArgumentException("keyword is outside the vocabulary: " + update.keyword());
            }
            KeywordUpdate canonicalUpdate = new KeywordUpdate(
                    normalizedKeyword,
                    update.docIds(),
                    update.operation()
            );

            PreparedUpdateRequest preparedUpdateRequest = sseClientFacade.prepareUpdateRequest(
                    masterKey,
                    trapdoorPrivateKey,
                    state,
                    canonicalUpdate
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
