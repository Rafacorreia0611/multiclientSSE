package sse.populatedb;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.SecretKey;

import sse.dataset.KeywordDocIdsEntry;
import sse.dataset.KeywordDocIdsReader;
import sse.demo.client.ConfidentialClientAdapter;
import sse.domain.InitializationMaterial;
import sse.domain.PendingKeywordUpdates;
import sse.domain.PreparedKeywordUpdates;
import sse.domain.State;
import sse.domain.UpdateOp;
import sse.domain.UpdateToken;
import sse.facade.SseClientFacade;

public final class PopulateDBHandler {

    private static final int DEFAULT_BATCH_SIZE = 250;
    private static final long PROGRESS_LOG_INTERVAL_MS = 2_000L;

    private final ConfidentialClientAdapter adapter;
    private final SseClientFacade sseClientFacade;
    private final KeywordDocIdsReader datasetReader;
    private final int batchSize;

    public PopulateDBHandler(ConfidentialClientAdapter adapter) {
        this(adapter, DEFAULT_BATCH_SIZE);
    }

    public PopulateDBHandler(ConfidentialClientAdapter adapter, int batchSize) {
        if (adapter == null) {
            throw new IllegalArgumentException("adapter cannot be null");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be greater than zero");
        }
        this.adapter = adapter;
        this.sseClientFacade = new SseClientFacade();
        this.datasetReader = new KeywordDocIdsReader();
        this.batchSize = batchSize;
    }

    public PopulationSummary populate(Path inputPath) {
        InitializationMaterial initializationMaterial = sseClientFacade.generateInitialStateData();
        if (adapter.sendInitializeStateRequest(initializationMaterial)) {
            System.out.println("State initialized.");
        } else {
            System.out.println("State was already initialized.");
        }

        ConfidentialClientAdapter.StateRequestResult stateRequest = adapter.requestSetupState();
        State currentState = stateRequest.state();
        SecretKey tokenGenKey = stateRequest.tokenGenKey();
        SecretKey updateCounterKey = stateRequest.updateCounterKey();

        boolean completed = false;
        long processedKeywords = 0L;
        long processedDocIds = 0L;
        long sentBatches = 0L;
        long startTimeNanos = System.nanoTime();
        long lastProgressLogNanos = startTimeNanos;
        List<PendingKeywordUpdates> pendingUpdates = new ArrayList<PendingKeywordUpdates>();
        List<SecretKey> pendingTupleKeys = new ArrayList<SecretKey>();

        try (BufferedReader reader = datasetReader.openReader(inputPath)) {
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
                    int availableSlots = batchSize - pendingTupleKeys.size();
                    int end = Math.min(start + availableSlots, docIds.size());
                    List<String> batchDocIds = docIds.subList(start, end);
                    PreparedKeywordUpdates preparedUpdates = sseClientFacade.prepareKeywordUpdates(
                            entry.keyword(),
                            batchDocIds,
                            UpdateOp.ADD
                    );

                    pendingUpdates.add(preparedUpdates.pendingUpdates());
                    pendingTupleKeys.addAll(preparedUpdates.tupleKeys());
                    start = end;

                    if (pendingTupleKeys.size() == batchSize) {
                        SentBatch sentBatch = sendPendingBatch(
                                pendingUpdates,
                                pendingTupleKeys,
                                tokenGenKey,
                                updateCounterKey,
                                currentState
                        );
                        currentState = sentBatch.state();
                        processedDocIds += sentBatch.sentDocIds();
                        sentBatches++;
                        pendingUpdates.clear();
                        pendingTupleKeys.clear();

                        long now = System.nanoTime();
                        if (shouldLogProgress(lastProgressLogNanos, now)) {
                            printProgress(processedKeywords, processedDocIds, sentBatches, startTimeNanos);
                            lastProgressLogNanos = now;
                        }
                    }
                }

                processedKeywords++;
            }

            if (!pendingTupleKeys.isEmpty()) {
                SentBatch sentBatch = sendPendingBatch(
                        pendingUpdates,
                        pendingTupleKeys,
                        tokenGenKey,
                        updateCounterKey,
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
            if (!completed) {
                attemptSetupAbort();
            }
        }
    }

    private SentBatch sendPendingBatch(List<PendingKeywordUpdates> pendingUpdates, List<SecretKey> pendingTupleKeys,
                                       SecretKey tokenGenKey, SecretKey updateCounterKey, State currentState) {
        UpdateToken updateToken = sseClientFacade.generateUpdateToken(
                tokenGenKey,
                updateCounterKey,
                currentState,
                pendingUpdates
        );
        if (updateToken.items().size() != pendingTupleKeys.size()) {
            throw new IllegalStateException("Update item/key count mismatch: items="
                    + updateToken.items().size() + ", keys=" + pendingTupleKeys.size());
        }

        SecretKey[] tupleKeys = pendingTupleKeys.toArray(new SecretKey[pendingTupleKeys.size()]);
        if (!adapter.sendUpdateRequest(updateToken, tupleKeys)) {
            throw new IllegalStateException("Server rejected update batch");
        }

        return new SentBatch(
                new State(currentState.searchCounter(), updateToken.encryptedUpdateCounter()),
                updateToken.items().size()
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
