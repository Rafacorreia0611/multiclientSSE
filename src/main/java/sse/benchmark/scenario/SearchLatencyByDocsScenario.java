package sse.benchmark.scenario;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import sse.benchmark.BenchmarkBucket;
import sse.benchmark.BenchmarkConfig;
import sse.benchmark.BenchmarkOperation;
import sse.benchmark.BenchmarkResultWriter;
import sse.benchmark.BenchmarkScenario;
import sse.dataset.KeywordDocIdsEntry;
import sse.dataset.KeywordDocIdsReader;
import sse.demo.client.ConfidentialClientAdapter;
import sse.demo.client.SSEClientHandler;
import vss.facade.SecretSharingException;

public final class SearchLatencyByDocsScenario implements BenchmarkScenario {

    @Override
    public String name() {
        return "search-latency-by-docs";
    }

    @Override
    public BenchmarkOperation operation() {
        return BenchmarkOperation.SEARCH;
    }

    @Override
    public void writeHeader(BenchmarkResultWriter resultWriter) throws IOException {
        resultWriter.writeSearchHeader();
    }

    @Override
    public void run(BenchmarkConfig config, BenchmarkResultWriter resultWriter) throws Exception {
        validateConfig(config);

        List<KeywordDocIdsEntry> entries = loadEntries(config.inputPath());
        Map<BenchmarkBucket, List<KeywordDocIdsEntry>> entriesByBucket = groupEntriesByBucket(entries, config.buckets());
        validateEntries(entriesByBucket, config);

        ConfidentialClientAdapter adapter = createAdapter(config.clientId());
        SSEClientHandler clientHandler = new SSEClientHandler(adapter);
        try {
            runWarmup(clientHandler, entriesByBucket, config);
            runMeasurements(clientHandler, entriesByBucket, config, resultWriter);
        } finally {
            clientHandler.close();
            adapter.close();
        }
    }

    private void validateConfig(BenchmarkConfig config) {
        if (!name().equals(config.scenarioName())) {
            throw new IllegalArgumentException("Scenario name mismatch: expected " + name()
                    + " but got " + config.scenarioName());
        }
        if (config.buckets().isEmpty()) {
            throw new IllegalArgumentException("At least one benchmark bucket must be configured");
        }
        if (config.measurementIterations() <= 0) {
            throw new IllegalArgumentException("measurementIterations must be greater than 0");
        }
    }

    private List<KeywordDocIdsEntry> loadEntries(String inputPath) {
        KeywordDocIdsReader datasetReader = new KeywordDocIdsReader();
        Path path = Paths.get(inputPath);
        List<KeywordDocIdsEntry> entries = new ArrayList<KeywordDocIdsEntry>();

        java.io.BufferedReader reader = datasetReader.openReader(path);
        try {
            String line;
            long lineNumber = 0L;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                KeywordDocIdsEntry entry = datasetReader.parseEntry(line, lineNumber);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to read benchmark input " + path.toAbsolutePath(), e);
        } finally {
            try {
                reader.close();
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Failed to close benchmark input " + path.toAbsolutePath(), e);
            }
        }

        return entries;
    }

    private Map<BenchmarkBucket, List<KeywordDocIdsEntry>> groupEntriesByBucket(List<KeywordDocIdsEntry> entries,
                                                                                List<BenchmarkBucket> buckets) {
        Map<BenchmarkBucket, List<KeywordDocIdsEntry>> entriesByBucket =
                new LinkedHashMap<BenchmarkBucket, List<KeywordDocIdsEntry>>();
        for (BenchmarkBucket bucket : buckets) {
            entriesByBucket.put(bucket, new ArrayList<KeywordDocIdsEntry>());
        }

        for (KeywordDocIdsEntry entry : entries) {
            BenchmarkBucket bucket = findBucket(entry, buckets);
            if (bucket == null) {
                throw new IllegalArgumentException("Keyword '" + entry.keyword() + "' with " + entry.docIds().size()
                        + " docs does not fit any configured benchmark bucket");
            }
            entriesByBucket.get(bucket).add(entry);
        }
        return entriesByBucket;
    }

    private void validateEntries(Map<BenchmarkBucket, List<KeywordDocIdsEntry>> entriesByBucket, BenchmarkConfig config) {
        int requiredEntriesPerBucket = config.warmupIterations() + config.measurementIterations();
        for (Map.Entry<BenchmarkBucket, List<KeywordDocIdsEntry>> bucketEntries : entriesByBucket.entrySet()) {
            int availableEntries = bucketEntries.getValue().size();
            if (availableEntries < requiredEntriesPerBucket) {
                throw new IllegalArgumentException("Bucket " + bucketEntries.getKey().label() + " requires at least "
                        + requiredEntriesPerBucket + " keywords, but found " + availableEntries);
            }
        }
    }

    private ConfidentialClientAdapter createAdapter(int clientId) {
        try {
            return new ConfidentialClientAdapter(clientId);
        } catch (SecretSharingException e) {
            throw new IllegalStateException("Failed to create benchmark client with id " + clientId, e);
        }
    }

    private void runWarmup(SSEClientHandler clientHandler,
                           Map<BenchmarkBucket, List<KeywordDocIdsEntry>> entriesByBucket,
                           BenchmarkConfig config) {
        for (Map.Entry<BenchmarkBucket, List<KeywordDocIdsEntry>> bucketEntries : entriesByBucket.entrySet()) {
            List<KeywordDocIdsEntry> entries = bucketEntries.getValue();
            for (int i = 0; i < config.warmupIterations(); i++) {
                KeywordDocIdsEntry entry = entries.get(i);
                List<String> results = clientHandler.search(entry.keyword());
                validateResultCount(entry, results);
            }
        }
    }

    private void runMeasurements(SSEClientHandler clientHandler,
                                 Map<BenchmarkBucket, List<KeywordDocIdsEntry>> entriesByBucket,
                                 BenchmarkConfig config,
                                 BenchmarkResultWriter resultWriter) throws Exception {
        int run = 1;
        for (Map.Entry<BenchmarkBucket, List<KeywordDocIdsEntry>> bucketEntries : entriesByBucket.entrySet()) {
            BenchmarkBucket bucket = bucketEntries.getKey();
            List<KeywordDocIdsEntry> entries = bucketEntries.getValue();
            int startIndex = config.warmupIterations();
            int endIndex = startIndex + config.measurementIterations();

            for (int i = startIndex; i < endIndex; i++) {
                KeywordDocIdsEntry entry = entries.get(i);

                long freshStartTime = System.nanoTime();
                List<String> freshResults = clientHandler.search(entry.keyword());
                long freshEndTime = System.nanoTime();
                validateResultCount(entry, freshResults);
                resultWriter.writeSearchSample(
                        name(),
                        operation(),
                        run++,
                        entry.keyword(),
                        entry.docIds().size(),
                        bucket.label(),
                        "fresh",
                        freshEndTime - freshStartTime
                );

                long cachedStartTime = System.nanoTime();
                List<String> cachedResults = clientHandler.search(entry.keyword());
                long cachedEndTime = System.nanoTime();
                validateResultCount(entry, cachedResults);
                resultWriter.writeSearchSample(
                        name(),
                        operation(),
                        run++,
                        entry.keyword(),
                        entry.docIds().size(),
                        bucket.label(),
                        "cached",
                        cachedEndTime - cachedStartTime
                );
            }
        }
    }

    private BenchmarkBucket findBucket(KeywordDocIdsEntry entry, List<BenchmarkBucket> buckets) {
        int docCount = entry.docIds().size();
        for (BenchmarkBucket bucket : buckets) {
            if (bucket.contains(docCount)) {
                return bucket;
            }
        }
        return null;
    }

    private void validateResultCount(KeywordDocIdsEntry entry, List<String> results) {
        if (results == null) {
            throw new IllegalStateException("Search returned null for keyword '" + entry.keyword() + "'");
        }
        int expectedCount = entry.docIds().size();
        if (results.size() != expectedCount) {
            throw new IllegalStateException("Search returned " + results.size() + " results for keyword '"
                    + entry.keyword() + "', expected " + expectedCount);
        }
    }
}
