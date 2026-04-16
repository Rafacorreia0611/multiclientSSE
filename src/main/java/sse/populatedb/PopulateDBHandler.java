package sse.populatedb;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.SecretKey;

import sse.dataset.enron.KeywordDocIdsEntry;
import sse.demo.client.ConfidentialClientAdapter;
import sse.domain.EncryptedUpdateTuple;
import sse.domain.InitializationMaterial;
import sse.domain.State;
import sse.domain.populatedb.BulkUpdateRequest;
import sse.facade.SseClientFacade;

public final class PopulateDBHandler {

    private static final int DEFAULT_BATCH_SIZE = 1_000;
    private static final long PROGRESS_LOG_INTERVAL_MS = 2_000L;

    private final ConfidentialClientAdapter adapter;
    private final SseClientFacade sseClientFacade;
    private final EnronReaderService enronReaderService;
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
        this.enronReaderService = new EnronReaderService();
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

        try (BufferedReader reader = enronReaderService.openReader(inputPath)) {
            String line;
            long lineNumber = 0L;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                KeywordDocIdsEntry entry = enronReaderService.parseEntry(line, lineNumber);
                if (entry == null) {
                    continue;
                }

                List<String> docIds = entry.docIds();
                for (int start = 0; start < docIds.size(); start += batchSize) {
                    int end = Math.min(start + batchSize, docIds.size());
                    List<String> batchDocIds = docIds.subList(start, end);
                    BatchResult batchResult = prepareBatch(entry.keyword(), batchDocIds, tokenGenKey, updateCounterKey,
                            currentState);

                    if (!adapter.sendBulkUpdateRequest(batchResult.request(), batchResult.updateTupleKeys())) {
                        throw new IllegalStateException(
                                "Server rejected bulk update for keyword '" + entry.keyword() + "'");
                    }

                    currentState = new State(currentState.searchCounter(), batchResult.request().encryptedUpdateCounter());
                    processedDocIds += batchDocIds.size();
                    sentBatches++;
                }

                processedKeywords++;
                long now = System.nanoTime();
                if (shouldLogProgress(lastProgressLogNanos, now)) {
                    printProgress(processedKeywords, processedDocIds, sentBatches, startTimeNanos);
                    lastProgressLogNanos = now;
                }
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

    private BatchResult prepareBatch(String keyword, List<String> docIds, SecretKey tokenGenKey,
                                     SecretKey updateCounterKey, State currentState) {
        List<EncryptedUpdateTuple> encryptedTuples = new ArrayList<EncryptedUpdateTuple>(docIds.size());
        SecretKey[] tupleKeys = new SecretKey[docIds.size()];

        for (int i = 0; i < docIds.size(); i++) {
            SecretKey tupleKey = sseClientFacade.generateTupleSecretKey();
            tupleKeys[i] = tupleKey;
            encryptedTuples.add(sseClientFacade.generateEncryptedUpdateTuple(docIds.get(i), true, tupleKey));
        }

        BulkUpdateRequest request = sseClientFacade.generateBulkUpdateRequest(
                tokenGenKey,
                updateCounterKey,
                currentState,
                keyword,
                encryptedTuples
        );
        return new BatchResult(request, tupleKeys);
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

    private static final class BatchResult {
        private final BulkUpdateRequest request;
        private final SecretKey[] updateTupleKeys;

        private BatchResult(BulkUpdateRequest request, SecretKey[] updateTupleKeys) {
            this.request = request;
            this.updateTupleKeys = updateTupleKeys;
        }

        private BulkUpdateRequest request() {
            return request;
        }

        private SecretKey[] updateTupleKeys() {
            return updateTupleKeys;
        }
    }
}
