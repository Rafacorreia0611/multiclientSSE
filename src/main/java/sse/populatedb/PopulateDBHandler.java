package sse.populatedb;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Path;
import java.security.interfaces.RSAPrivateKey;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.SecretKey;

import sse.dataset.KeywordDocIdsEntry;
import sse.dataset.KeywordDocIdsReader;
import sse.demo.client.ConfidentialClientAdapter;
import sse.domain.setup.InitializationMaterial;
import sse.domain.state.KeywordState;
import sse.domain.id.KeywordToken;
import sse.domain.update.KeywordUpdate;
import sse.domain.update.PreparedUpdateRequest;
import sse.domain.state.State;
import sse.domain.update.UpdateOp;
import sse.facade.SseClientFacade;
import sse.vocabulary.VocabularyLoader;

public final class PopulateDBHandler {

    private static final int DEFAULT_BATCH_SIZE = 250;
    private static final long PROGRESS_LOG_INTERVAL_MS = 2_000L;

    private final ConfidentialClientAdapter adapter;
    private final SseClientFacade sseClientFacade;
    private final KeywordDocIdsReader datasetReader;
    private final int batchSize;
    private final Path vocabularyPath;

    public PopulateDBHandler(ConfidentialClientAdapter adapter) {
        this(adapter, DEFAULT_BATCH_SIZE);
    }

    public PopulateDBHandler(ConfidentialClientAdapter adapter, int batchSize) {
        this(adapter, batchSize, VocabularyLoader.DEFAULT_VOCABULARY_PATH);
    }

    public PopulateDBHandler(ConfidentialClientAdapter adapter, int batchSize, Path vocabularyPath) {
        if (adapter == null) {
            throw new IllegalArgumentException("adapter cannot be null");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be greater than zero");
        }
        if (vocabularyPath == null) {
            throw new IllegalArgumentException("vocabularyPath cannot be null");
        }
        this.adapter = adapter;
        this.sseClientFacade = new SseClientFacade();
        this.datasetReader = new KeywordDocIdsReader();
        this.batchSize = batchSize;
        this.vocabularyPath = vocabularyPath;
    }

    public PopulationSummary populate(Path inputPath) {
        boolean setupStarted = false;
        boolean completed = false;
        try (BufferedReader reader = datasetReader.openReader(inputPath)) {
            if (adapter.isInitialized()) {
                System.out.println("State already initialized.");
            } else {
                InitializationMaterial initializationMaterial = sseClientFacade.generateInitialStateData(vocabularyPath);
                if (adapter.sendInitializeStateRequest(initializationMaterial)) {
                    System.out.println("State initialized.");
                } else {
                    System.out.println("State was initialized by another client.");
                }
            }

            ConfidentialClientAdapter.StateRequestResult stateRequest = adapter.requestSetupState();
            setupStarted = true;
            State currentState = stateRequest.state();
            SecretKey masterKey = stateRequest.masterKey();
            RSAPrivateKey trapdoorPrivateKey = stateRequest.trapdoorPrivateKey();

            long processedKeywords = 0L;
            long processedDocIds = 0L;
            long sentBatches = 0L;
            long startTimeNanos = System.nanoTime();
            long lastProgressLogNanos = startTimeNanos;
            List<KeywordUpdate> pendingUpdates = new ArrayList<KeywordUpdate>();
            int pendingDocIds = 0;

            String line;
            long lineNumber = 0L;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                KeywordDocIdsEntry entry = datasetReader.parseEntry(line, lineNumber);
                if (entry == null) {
                    continue;
                }

                List<String> docIds = entry.docIds();
                int start = 0;
                while (start < docIds.size()) {
                    int availableSlots = batchSize - pendingDocIds;
                    int end = Math.min(start + availableSlots, docIds.size());
                    List<String> batchDocIds = docIds.subList(start, end);
                    pendingUpdates.add(new KeywordUpdate(
                            entry.keyword(),
                            batchDocIds,
                            UpdateOp.ADD
                    ));

                    pendingDocIds += batchDocIds.size();
                    start = end;

                    if (pendingDocIds == batchSize) {
                        SentBatch sentBatch = sendPendingBatch(
                                pendingUpdates,
                                masterKey,
                                trapdoorPrivateKey,
                                currentState
                        );
                        currentState = sentBatch.state();
                        processedDocIds += sentBatch.sentDocIds();
                        sentBatches++;
                        pendingUpdates.clear();
                        pendingDocIds = 0;

                        long now = System.nanoTime();
                        if (shouldLogProgress(lastProgressLogNanos, now)) {
                            printProgress(processedKeywords, processedDocIds, sentBatches, startTimeNanos);
                            lastProgressLogNanos = now;
                        }
                    }
                }

                processedKeywords++;
            }

            if (pendingDocIds > 0) {
                SentBatch sentBatch = sendPendingBatch(
                        pendingUpdates,
                        masterKey,
                        trapdoorPrivateKey,
                        currentState
                );
                currentState = sentBatch.state();
                processedDocIds += sentBatch.sentDocIds();
                sentBatches++;
            }

            if (!adapter.sendSetupCompleteRequest()) {
                throw new IllegalStateException("Server rejected the setup completion request");
            }
            completed = true;
            printProgress(processedKeywords, processedDocIds, sentBatches, startTimeNanos);
            System.out.println();
            return new PopulationSummary(processedKeywords, processedDocIds, sentBatches);
        } catch (IOException e) {
            throw new IllegalStateException("Failed while reading NDJSON input " + inputPath.toAbsolutePath(), e);
        } finally {
            if (setupStarted && !completed) {
                attemptSetupAbort();
            }
        }
    }

    private SentBatch sendPendingBatch(List<KeywordUpdate> pendingUpdates,
                                       SecretKey masterKey, RSAPrivateKey trapdoorPrivateKey, State currentState) {
        PreparedUpdateRequest preparedUpdateRequest = sseClientFacade.prepareUpdateRequest(
                masterKey,
                trapdoorPrivateKey,
                currentState,
                pendingUpdates
        );

        if (preparedUpdateRequest.updateToken().items().size() != preparedUpdateRequest.tupleKeys().size()) {
            throw new IllegalStateException("Update item/key count mismatch: items="
                    + preparedUpdateRequest.updateToken().items().size()
                    + ", keys=" + preparedUpdateRequest.tupleKeys().size());
        }

        SecretKey[] tupleKeys = preparedUpdateRequest.tupleKeys()
                .toArray(new SecretKey[preparedUpdateRequest.tupleKeys().size()]);
        if (!adapter.sendUpdateRequest(preparedUpdateRequest.updateToken(), tupleKeys)) {
            throw new IllegalStateException("Server rejected update batch");
        }

        Map<KeywordToken, KeywordState> nextKeywordStates =
                new LinkedHashMap<KeywordToken, KeywordState>(currentState.keywordStates());
        nextKeywordStates.putAll(preparedUpdateRequest.updateToken().updatedKeywordStates());
        return new SentBatch(
                new State(
                        nextKeywordStates,
                        currentState.encodedTrapdoorPublicKey(),
                        currentState.encryptedKeywordAddressMap()
                ),
                preparedUpdateRequest.updateToken().items().size()
        );
    }

    private boolean shouldLogProgress(long lastProgressLogNanos, long nowNanos) {
        return (nowNanos - lastProgressLogNanos) >= PROGRESS_LOG_INTERVAL_MS * 1_000_000L;
    }

    private void printProgress(long processedKeywords, long processedDocIds, long sentBatches, long startTimeNanos) {
        long elapsedSeconds = Math.max(1L, (System.nanoTime() - startTimeNanos) / 1_000_000_000L);
        String message = "\rPopulateDB progress: keywords=" + processedKeywords
                + " | docIds=" + processedDocIds
                + " | batches=" + sentBatches
                + " | elapsed=" + formatDuration(elapsedSeconds);
        System.out.print(message);
        System.out.flush();
    }

    private String formatDuration(long totalSeconds) {
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0) {
            return String.format("%dh%02dm%02ds", hours, minutes, seconds);
        }
        if (minutes > 0) {
            return String.format("%dm%02ds", minutes, seconds);
        }
        return String.format("%ds", seconds);
    }

    private void attemptSetupAbort() {
        try {
            if (!adapter.sendSetupAbortRequest()) {
                System.err.println("PopulateDB could not signal setup abort to the replicas.");
            }
        } catch (RuntimeException e) {
            System.err.println("PopulateDB failed to signal setup abort: " + e.getMessage());
        }
    }

    public static final class PopulationSummary {
        private final long processedKeywords;
        private final long processedDocIds;
        private final long sentBatches;

        PopulationSummary(long processedKeywords, long processedDocIds, long sentBatches) {
            this.processedKeywords = processedKeywords;
            this.processedDocIds = processedDocIds;
            this.sentBatches = sentBatches;
        }

        public long processedKeywords() {
            return processedKeywords;
        }

        public long processedDocIds() {
            return processedDocIds;
        }

        public long sentBatches() {
            return sentBatches;
        }
    }

    private static final class SentBatch {
        private final State state;
        private final int sentDocIds;

        private SentBatch(State state, int sentDocIds) {
            this.state = state;
            this.sentDocIds = sentDocIds;
        }

        private State state() {
            return state;
        }

        private int sentDocIds() {
            return sentDocIds;
        }
    }
}
