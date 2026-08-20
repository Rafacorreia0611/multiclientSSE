package sse.demo.client;

import java.nio.file.Path;
import java.security.interfaces.RSAPrivateKey;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.crypto.SecretKey;

import sse.domain.update.EncryptedUpdateTuple;
import sse.domain.update.KeywordUpdate;
import sse.domain.update.PreparedUpdateRequest;
import sse.domain.search.SearchToken;
import sse.domain.state.KeywordBlock;
import sse.domain.state.State;
import sse.facade.SseClientFacade;
import sse.oram.ORAMAdapter;
import sse.oram.ORAMSettings;
import sse.vocabulary.VocabularyLoader;

public final class SseClientHandler {

    private static final long LOCK_RETRY_DELAY_MS = 250L;

    private final ConfidentialClientAdapter adapter;
    private final SseClientFacade sseClientFacade;
    private final ORAMSettings oramSettings;
    private final ORAMAdapter oramAdapter;
    private final SseInitCoordinator initCoordinator;

    public SseClientHandler(ConfidentialClientAdapter adapter) {
        this(adapter, VocabularyLoader.DEFAULT_VOCABULARY_PATH);
    }

    public SseClientHandler(ConfidentialClientAdapter adapter, Path vocabularyPath) {
        if (adapter == null) {
            throw new IllegalArgumentException("adapter cannot be null");
        }
        if (vocabularyPath == null) {
            throw new IllegalArgumentException("vocabularyPath cannot be null");
        }
        this.adapter = adapter;
        this.sseClientFacade = new SseClientFacade();
        this.oramSettings = ORAMSettings.defaults();
        this.oramAdapter = new ORAMAdapter(
                oramSettings,
                ORAMSettings.oramClientIdFor(adapter.clientId())
        );
        this.initCoordinator = new SseInitCoordinator(
                adapter,
                sseClientFacade,
                vocabularyPath,
                oramSettings,
                oramAdapter
        );
    }

    public void initializeState() {
        initCoordinator.initializeOrConnect();
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

            KeywordBlock keywordBlock = oramAdapter.readKeywordBlock(keywordLocation);
            SearchToken searchToken = sseClientFacade.generateSearchToken(
                    masterKey,
                    keywordBlock.keywordState(),
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

        while (true) {
            KeywordBlock oldBlock = oramAdapter.acquireKeywordLock(keywordLocation);
            if (oldBlock.locked()) {
                sleepBeforeLockRetry();
                continue;
            }

            boolean updateCommitted = false;
            try {
                PreparedUpdateRequest preparedUpdateRequest = sseClientFacade.prepareUpdateRequest(
                        masterKey,
                        trapdoorPrivateKey,
                        state,
                        oldBlock.keywordState(),
                        canonicalUpdate
                );

                SecretKey[] tupleKeys = preparedUpdateRequest.tupleKeys()
                        .toArray(new SecretKey[preparedUpdateRequest.tupleKeys().size()]);
                updateCommitted = adapter.sendUpdateRequest(preparedUpdateRequest.updateToken(), tupleKeys);
                if (updateCommitted) {
                    oramAdapter.publishKeywordState(keywordLocation, preparedUpdateRequest.keywordState());
                    return;
                }
            } finally {
                if (!updateCommitted) {
                    oramAdapter.releaseKeywordLock(keywordLocation);
                }
            }
        }
    }

    private void sleepBeforeLockRetry() {
        try {
            Thread.sleep(LOCK_RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting to retry keyword lock", e);
        }
    }

    public void close() {
        oramAdapter.close();
    }
}
