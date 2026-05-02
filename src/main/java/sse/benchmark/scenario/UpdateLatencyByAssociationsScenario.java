package sse.benchmark.scenario;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import sse.benchmark.BenchmarkConfig;
import sse.benchmark.BenchmarkOperation;
import sse.benchmark.BenchmarkResultWriter;
import sse.benchmark.BenchmarkScenario;
import sse.demo.client.ConfidentialClientAdapter;
import sse.demo.client.SseClientHandler;
import sse.domain.KeywordUpdate;
import sse.domain.UpdateOp;
import vss.facade.SecretSharingException;

public final class UpdateLatencyByAssociationsScenario implements BenchmarkScenario {

    private static final String PHASE_WARMUP = "warmup";
    private static final String PHASE_MEASURE = "measure";

    private final Gson gson;

    public UpdateLatencyByAssociationsScenario() {
        this.gson = new Gson();
    }

    @Override
    public String name() {
        return "update-latency-by-associations";
    }

    @Override
    public BenchmarkOperation operation() {
        return BenchmarkOperation.UPDATE;
    }

    @Override
    public void writeHeader(BenchmarkResultWriter resultWriter) throws IOException {
        resultWriter.writeUpdateHeader();
    }

    @Override
    public void run(BenchmarkConfig config, BenchmarkResultWriter resultWriter) throws Exception {
        validateConfig(config);

        List<UpdatePayload> payloads = loadPayloads(config.inputPath());
        List<UpdatePayload> warmupPayloads = filterPayloads(payloads, PHASE_WARMUP);
        List<UpdatePayload> measurePayloads = filterPayloads(payloads, PHASE_MEASURE);
        validatePayloadCounts(config, warmupPayloads, measurePayloads);

        ConfidentialClientAdapter adapter = createAdapter(config.clientId());
        SseClientHandler clientHandler = new SseClientHandler(adapter);
        try {
            runWarmups(clientHandler, warmupPayloads);
            runMeasurement(clientHandler, measurePayloads.get(0), resultWriter);
        } finally {
            clientHandler.close();
        }
    }

    private void validateConfig(BenchmarkConfig config) {
        if (!name().equals(config.scenarioName())) {
            throw new IllegalArgumentException("Scenario name mismatch: expected " + name()
                    + " but got " + config.scenarioName());
        }
        if (config.warmupIterations() < 0) {
            throw new IllegalArgumentException("warmupIterations must be 0 or greater");
        }
    }

    private List<UpdatePayload> loadPayloads(String inputPath) {
        Path path = Paths.get(inputPath);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("NDJSON file does not exist: " + path.toAbsolutePath());
        }

        List<UpdatePayload> payloads = new ArrayList<UpdatePayload>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            long lineNumber = 0L;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                UpdatePayload payload = parsePayload(line, lineNumber);
                if (payload != null) {
                    payloads.add(payload);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read benchmark input " + path.toAbsolutePath(), e);
        }
        return payloads;
    }

    private UpdatePayload parsePayload(String line, long lineNumber) {
        if (line == null) {
            return null;
        }

        String normalizedLine = line.trim();
        if (normalizedLine.isEmpty()) {
            return null;
        }

        try {
            RawUpdatePayload rawPayload = gson.fromJson(normalizedLine, RawUpdatePayload.class);
            if (rawPayload == null) {
                throw new IllegalArgumentException("payload is null");
            }
            return validatePayload(rawPayload, lineNumber);
        } catch (JsonParseException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid update payload at line " + lineNumber, e);
        }
    }

    private UpdatePayload validatePayload(RawUpdatePayload rawPayload, long lineNumber) {
        String phase = validatePhase(rawPayload.phase, lineNumber);
        String payloadId = validateRequiredString(rawPayload.payloadId, "payloadId", lineNumber);

        if (rawPayload.associationCount <= 0) {
            throw new IllegalArgumentException("associationCount must be greater than 0 at line " + lineNumber);
        }
        if (rawPayload.updates == null || rawPayload.updates.isEmpty()) {
            throw new IllegalArgumentException("updates cannot be null or empty at line " + lineNumber);
        }

        List<UpdateEntry> updates = new ArrayList<UpdateEntry>(rawPayload.updates.size());
        int docIdCount = 0;
        for (RawUpdateEntry rawUpdate : rawPayload.updates) {
            if (rawUpdate == null) {
                throw new IllegalArgumentException("updates cannot contain null entries at line " + lineNumber);
            }

            String keyword = validateRequiredString(rawUpdate.keyword, "keyword", lineNumber);
            if (rawUpdate.docIds == null || rawUpdate.docIds.isEmpty()) {
                throw new IllegalArgumentException("docIds cannot be null or empty at line " + lineNumber
                        + " for payload " + payloadId);
            }

            List<String> docIds = new ArrayList<String>(rawUpdate.docIds.size());
            for (String docId : rawUpdate.docIds) {
                docIds.add(validateRequiredString(docId, "docId", lineNumber));
            }
            docIdCount += docIds.size();
            updates.add(new UpdateEntry(keyword, docIds));
        }

        if (docIdCount != rawPayload.associationCount) {
            throw new IllegalArgumentException("associationCount is " + rawPayload.associationCount
                    + " but payload " + payloadId + " contains " + docIdCount + " doc IDs at line " + lineNumber);
        }

        return new UpdatePayload(phase, payloadId, rawPayload.associationCount, updates);
    }

    private String validatePhase(String rawPhase, long lineNumber) {
        String phase = validateRequiredString(rawPhase, "phase", lineNumber);
        if (!PHASE_WARMUP.equals(phase) && !PHASE_MEASURE.equals(phase)) {
            throw new IllegalArgumentException("phase must be warmup or measure at line " + lineNumber);
        }
        return phase;
    }

    private String validateRequiredString(String value, String fieldName, long lineNumber) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or empty at line " + lineNumber);
        }
        return value.trim();
    }

    private List<UpdatePayload> filterPayloads(List<UpdatePayload> payloads, String phase) {
        List<UpdatePayload> filteredPayloads = new ArrayList<UpdatePayload>();
        for (UpdatePayload payload : payloads) {
            if (phase.equals(payload.phase())) {
                filteredPayloads.add(payload);
            }
        }
        return filteredPayloads;
    }

    private void validatePayloadCounts(BenchmarkConfig config,
                                       List<UpdatePayload> warmupPayloads,
                                       List<UpdatePayload> measurePayloads) {
        if (warmupPayloads.size() != config.warmupIterations()) {
            throw new IllegalArgumentException("warmupIterations is " + config.warmupIterations()
                    + " but input contains " + warmupPayloads.size() + " warmup payloads");
        }
        if (measurePayloads.size() != 1) {
            throw new IllegalArgumentException("Input must contain exactly one measure payload, found "
                    + measurePayloads.size());
        }
    }

    private ConfidentialClientAdapter createAdapter(int clientId) {
        try {
            return new ConfidentialClientAdapter(clientId);
        } catch (SecretSharingException e) {
            throw new IllegalStateException("Failed to create benchmark client with id " + clientId, e);
        }
    }

    private void runWarmups(SseClientHandler clientHandler, List<UpdatePayload> warmupPayloads) {
        for (UpdatePayload payload : warmupPayloads) {
            clientHandler.update(toKeywordUpdates(payload));
        }
    }

    private void runMeasurement(SseClientHandler clientHandler,
                                UpdatePayload payload,
                                BenchmarkResultWriter resultWriter) throws IOException {
        List<KeywordUpdate> keywordUpdates = toKeywordUpdates(payload);

        long startTime = System.nanoTime();
        clientHandler.update(keywordUpdates);
        long endTime = System.nanoTime();

        resultWriter.writeUpdateSample(
                name(),
                operation(),
                1,
                payload.phase(),
                payload.associationCount(),
                payload.keywordCount(),
                payload.docIdCount(),
                payload.payloadId(),
                endTime - startTime
        );
    }

    private List<KeywordUpdate> toKeywordUpdates(UpdatePayload payload) {
        List<KeywordUpdate> keywordUpdates = new ArrayList<KeywordUpdate>(payload.updates().size());
        for (UpdateEntry update : payload.updates()) {
            keywordUpdates.add(new KeywordUpdate(update.keyword(), update.docIds(), UpdateOp.ADD));
        }
        return keywordUpdates;
    }

    private static final class UpdatePayload {

        private final String phase;
        private final String payloadId;
        private final int associationCount;
        private final List<UpdateEntry> updates;

        private UpdatePayload(String phase, String payloadId, int associationCount, List<UpdateEntry> updates) {
            this.phase = phase;
            this.payloadId = payloadId;
            this.associationCount = associationCount;
            this.updates = updates;
        }

        private String phase() {
            return phase;
        }

        private String payloadId() {
            return payloadId;
        }

        private int associationCount() {
            return associationCount;
        }

        private int keywordCount() {
            return updates.size();
        }

        private int docIdCount() {
            int docIdCount = 0;
            for (UpdateEntry update : updates) {
                docIdCount += update.docIds().size();
            }
            return docIdCount;
        }

        private List<UpdateEntry> updates() {
            return updates;
        }
    }

    private static final class UpdateEntry {

        private final String keyword;
        private final List<String> docIds;

        private UpdateEntry(String keyword, List<String> docIds) {
            this.keyword = keyword;
            this.docIds = docIds;
        }

        private String keyword() {
            return keyword;
        }

        private List<String> docIds() {
            return docIds;
        }
    }

    private static final class RawUpdatePayload {
        private String phase;
        private String payloadId;
        private int associationCount;
        private List<RawUpdateEntry> updates;
    }

    private static final class RawUpdateEntry {
        private String keyword;
        private List<String> docIds;
    }
}
