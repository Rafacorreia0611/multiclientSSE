package sse.dataset.enron;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.Gson;

public final class EnronCsvProcessor {

    private static final Path DEFAULT_INPUT = Paths.get("datasets", "raw", "enron", "emails.csv");
    private static final Path DEFAULT_OUTPUT = Paths.get("datasets", "processed", "enron", "keyword_to_docids.ndjson");
    private static final ProcessingMode DEFAULT_MODE = ProcessingMode.COMPACT;
    private static final int DEFAULT_TOP_K = 500;
    private static final double DEFAULT_MAX_DOC_FREQ_RATIO = 0.02d;
    private static final long PROGRESS_UPDATE_INTERVAL_MS = 2000L;

    private final KeywordExtractor keywordExtractor;
    private final Gson gson;

    public EnronCsvProcessor() {
        this.keywordExtractor = new KeywordExtractor();
        this.gson = new Gson();
    }

    public static void main(String[] args) {
        if (args.length > 5) {
            throw new IllegalArgumentException(
                    "Usage: EnronCsvProcessor [inputCsvPath] [outputNdjsonPath] [mode] [topK] [maxDocFreqRatio]");
        }

        Path input = args.length > 0 ? Paths.get(args[0]) : DEFAULT_INPUT;
        Path output = args.length > 1 ? Paths.get(args[1]) : DEFAULT_OUTPUT;
        ProcessingMode mode = args.length > 2 ? ProcessingMode.fromArg(args[2]) : DEFAULT_MODE;
        int topK = args.length > 3 ? parsePositiveInt(args[3], "topK") : DEFAULT_TOP_K;
        double maxDocFreqRatio = args.length > 4
                ? parseMaxDocFreqRatio(args[4])
                : DEFAULT_MAX_DOC_FREQ_RATIO;

        EnronCsvProcessor processor = new EnronCsvProcessor();
        ProcessingSummary summary = processor.process(input, output, mode, topK, maxDocFreqRatio);
        System.out.println("Mode: " + summary.mode().value());
        System.out.println("Processed " + summary.processedDocuments + " documents.");
        System.out.println("Skipped " + summary.skippedDocuments + " documents.");
        System.out.println("Extracted " + summary.extractedUniqueKeywords + " unique keywords before selection.");
        if (summary.mode() == ProcessingMode.COMPACT) {
            System.out.println("Filtered " + summary.filteredCommonKeywords + " too-common keywords.");
            System.out.println("Kept " + summary.writtenUniqueKeywords + " keywords after topK="
                    + summary.topK + " and maxDocFreqRatio="
                    + String.format(Locale.ROOT, "%.4f", summary.maxDocFreqRatio) + ".");
        } else {
            System.out.println("Wrote " + summary.writtenUniqueKeywords + " unique keywords.");
        }
        System.out.println("Generated " + summary.totalKeywordAssignments + " keyword-to-doc assignments.");
        System.out.println("Wrote NDJSON output to " + output.toAbsolutePath());
    }

    public ProcessingSummary process(Path inputPath, Path outputPath) {
        return process(inputPath, outputPath, ProcessingMode.FULL, DEFAULT_TOP_K, DEFAULT_MAX_DOC_FREQ_RATIO);
    }

    public ProcessingSummary process(Path inputPath, Path outputPath,
                                     ProcessingMode mode, int topK, double maxDocFreqRatio) {
        validateInputPath(inputPath);
        ensureParentDirectory(outputPath);
        validateModeSettings(mode, topK, maxDocFreqRatio);

        Map<String, List<String>> keywordToDocIds = new LinkedHashMap<String, List<String>>();
        int processedDocuments = 0;
        int skippedDocuments = 0;
        long totalKeywordAssignments = 0L;
        long inputSizeBytes = inputSizeBytes(inputPath);
        long startTimeNanos = System.nanoTime();
        long lastProgressUpdateNanos = startTimeNanos;

        try (CountingInputStream countingInputStream = new CountingInputStream(Files.newInputStream(inputPath));
             InputStreamReader inputStreamReader = new InputStreamReader(countingInputStream, StandardCharsets.UTF_8);
             PushbackReader reader = new PushbackReader(new BufferedReader(inputStreamReader), 2)) {
            validateHeader(readNextRow(reader));

            CsvRow row;
            while ((row = readNextRow(reader)) != null) {
                String docId = normalizeDocId(row.file);
                if (docId == null) {
                    skippedDocuments++;
                    continue;
                }

                ParsedMessage parsedMessage = parseMessage(row.message);
                LinkedHashSet<String> keywords =
                        new LinkedHashSet<String>(keywordExtractor.extractKeywords(parsedMessage.subject, parsedMessage.body));
                if (keywords.isEmpty()) {
                    skippedDocuments++;
                    continue;
                }

                for (String keyword : keywords) {
                    List<String> docIds = keywordToDocIds.get(keyword);
                    if (docIds == null) {
                        docIds = new ArrayList<String>();
                        keywordToDocIds.put(keyword, docIds);
                    }
                    docIds.add(docId);
                    totalKeywordAssignments++;
                }
                processedDocuments++;

                long now = System.nanoTime();
                if (shouldReportProgress(lastProgressUpdateNanos, now)) {
                    printProgress(processedDocuments, skippedDocuments, countingInputStream.bytesRead(),
                            inputSizeBytes, startTimeNanos);
                    lastProgressUpdateNanos = now;
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to process Enron CSV at " + inputPath.toAbsolutePath(), e);
        }

        int extractedUniqueKeywords = keywordToDocIds.size();
        int filteredCommonKeywords = 0;
        List<Map.Entry<String, List<String>>> selectedEntries = new ArrayList<Map.Entry<String, List<String>>>();

        if (mode == ProcessingMode.FULL) {
            selectedEntries.addAll(keywordToDocIds.entrySet());
        } else {
            for (Map.Entry<String, List<String>> entry : keywordToDocIds.entrySet()) {
                double docFrequencyRatio = processedDocuments == 0
                        ? 0.0d
                        : (double) entry.getValue().size() / (double) processedDocuments;
                if (docFrequencyRatio > maxDocFreqRatio) {
                    filteredCommonKeywords++;
                    continue;
                }
                selectedEntries.add(entry);
            }

            Collections.sort(selectedEntries, new Comparator<Map.Entry<String, List<String>>>() {
                @Override
                public int compare(Map.Entry<String, List<String>> left, Map.Entry<String, List<String>> right) {
                    int byDocFrequency = Integer.compare(right.getValue().size(), left.getValue().size());
                    if (byDocFrequency != 0) {
                        return byDocFrequency;
                    }
                    return left.getKey().compareTo(right.getKey());
                }
            });

            if (selectedEntries.size() > topK) {
                selectedEntries = new ArrayList<Map.Entry<String, List<String>>>(selectedEntries.subList(0, topK));
            }
        }

        writeNdjson(outputPath, selectedEntries);
        printProgress(processedDocuments, skippedDocuments, inputSizeBytes, inputSizeBytes, startTimeNanos);
        System.out.println();
        return new ProcessingSummary(
                processedDocuments,
                skippedDocuments,
                extractedUniqueKeywords,
                selectedEntries.size(),
                filteredCommonKeywords,
                totalKeywordAssignments,
                mode,
                topK,
                maxDocFreqRatio
        );
    }

    private static int parsePositiveInt(String value, String argumentName) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException(argumentName + " must be a positive integer");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(argumentName + " must be a positive integer: " + value, e);
        }
    }

    private static double parseMaxDocFreqRatio(String value) {
        try {
            double parsed = Double.parseDouble(value);
            if (parsed <= 0.0d || parsed > 1.0d) {
                throw new IllegalArgumentException("maxDocFreqRatio must be in the range (0, 1]");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("maxDocFreqRatio must be a decimal number: " + value, e);
        }
    }

    private long inputSizeBytes(Path inputPath) {
        try {
            return Files.size(inputPath);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to determine input file size for " + inputPath.toAbsolutePath(), e);
        }
    }

    private void validateModeSettings(ProcessingMode mode, int topK, double maxDocFreqRatio) {
        if (mode == null) {
            throw new IllegalArgumentException("mode cannot be null");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be a positive integer");
        }
        if (maxDocFreqRatio <= 0.0d || maxDocFreqRatio > 1.0d) {
            throw new IllegalArgumentException("maxDocFreqRatio must be in the range (0, 1]");
        }
    }

    private void validateInputPath(Path inputPath) {
        if (inputPath == null) {
            throw new IllegalArgumentException("Input CSV path cannot be null");
        }
        if (!Files.exists(inputPath) || !Files.isRegularFile(inputPath)) {
            throw new IllegalArgumentException("Input CSV file does not exist: " + inputPath.toAbsolutePath());
        }
    }

    private void ensureParentDirectory(Path outputPath) {
        if (outputPath == null) {
            throw new IllegalArgumentException("Output NDJSON path cannot be null");
        }
        Path parent = outputPath.toAbsolutePath().getParent();
        if (parent == null) {
            throw new IllegalArgumentException("Output NDJSON path must have a parent directory: " + outputPath);
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create output directory " + parent, e);
        }
    }

    private void validateHeader(CsvRow header) {
        if (header == null) {
            throw new IllegalArgumentException("Enron CSV is empty");
        }
        if (!"file".equals(header.file) || !"message".equals(header.message)) {
            throw new IllegalArgumentException("Unexpected Enron CSV header. Expected \"file\",\"message\"");
        }
    }

    private CsvRow readNextRow(PushbackReader reader) throws IOException {
        String file = readQuotedField(reader);
        if (file == null) {
            return null;
        }
        expectSeparator(reader, ',');
        String message = readQuotedField(reader);
        expectRowTerminator(reader);
        return new CsvRow(file, message);
    }

    private String readQuotedField(PushbackReader reader) throws IOException {
        int start = readSkippingLeadingRowTerminators(reader);
        if (start == -1) {
            return null;
        }
        if (start != '"') {
            throw new IOException("Malformed CSV: expected '\"' at the start of a field");
        }

        StringBuilder builder = new StringBuilder();
        while (true) {
            int current = reader.read();
            if (current == -1) {
                throw new IOException("Malformed CSV: unexpected end of file inside quoted field");
            }
            if (current == '"') {
                int next = reader.read();
                if (next == '"') {
                    builder.append('"');
                    continue;
                }
                if (next != -1) {
                    reader.unread(next);
                }
                return builder.toString();
            }
            builder.append((char) current);
        }
    }

    private int readSkippingLeadingRowTerminators(PushbackReader reader) throws IOException {
        while (true) {
            int current = reader.read();
            if (current == '\r') {
                int next = reader.read();
                if (next != '\n' && next != -1) {
                    reader.unread(next);
                }
                continue;
            }
            if (current == '\n') {
                continue;
            }
            return current;
        }
    }

    private void expectSeparator(Reader reader, char expected) throws IOException {
        int current = reader.read();
        if (current != expected) {
            throw new IOException("Malformed CSV: expected '" + expected + "' separator");
        }
    }

    private void expectRowTerminator(PushbackReader reader) throws IOException {
        int current = reader.read();
        if (current == -1) {
            return;
        }
        if (current == '\n') {
            return;
        }
        if (current == '\r') {
            int next = reader.read();
            if (next != '\n' && next != -1) {
                reader.unread(next);
            }
            return;
        }
        throw new IOException("Malformed CSV: expected row terminator after message field");
    }

    private String normalizeDocId(String file) {
        if (file == null) {
            return null;
        }
        String normalized = file.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private ParsedMessage parseMessage(String rawMessage) {
        if (rawMessage == null || rawMessage.isEmpty()) {
            return new ParsedMessage("", "");
        }

        int bodyStart = findBodyStart(rawMessage);
        String headersSection = bodyStart >= 0 ? rawMessage.substring(0, bodyStart) : rawMessage;
        String body = bodyStart >= 0 ? rawMessage.substring(bodyStart) : "";
        String subject = extractHeaderValue(headersSection, "Subject");

        return new ParsedMessage(subject, body.trim());
    }

    private int findBodyStart(String rawMessage) {
        int unixSeparator = rawMessage.indexOf("\n\n");
        int windowsSeparator = rawMessage.indexOf("\r\n\r\n");
        if (unixSeparator < 0) {
            return windowsSeparator >= 0 ? windowsSeparator + 4 : -1;
        }
        if (windowsSeparator < 0) {
            return unixSeparator + 2;
        }
        return Math.min(unixSeparator + 2, windowsSeparator + 4);
    }

    private String extractHeaderValue(String headersSection, String headerName) {
        String[] lines = headersSection.split("\\r?\\n");
        StringBuilder value = null;
        String normalizedHeaderName = headerName.toLowerCase();

        for (String line : lines) {
            if (line.startsWith(" ") || line.startsWith("\t")) {
                if (value != null) {
                    value.append(' ').append(line.trim());
                }
                continue;
            }

            int separatorIndex = line.indexOf(':');
            if (separatorIndex <= 0) {
                value = null;
                continue;
            }

            String currentHeader = line.substring(0, separatorIndex).trim().toLowerCase();
            if (normalizedHeaderName.equals(currentHeader)) {
                value = new StringBuilder(line.substring(separatorIndex + 1).trim());
            } else {
                value = null;
            }
        }

        return value == null ? "" : value.toString().trim();
    }

    private void writeNdjson(Path outputPath, List<Map.Entry<String, List<String>>> selectedEntries) {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, List<String>> entry : selectedEntries) {
                writer.write(gson.toJson(new KeywordDocIdsEntry(entry.getKey(), entry.getValue())));
                writer.newLine();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write NDJSON output to " + outputPath.toAbsolutePath(), e);
        }
    }

    private boolean shouldReportProgress(long lastProgressUpdateNanos, long nowNanos) {
        return (nowNanos - lastProgressUpdateNanos) >= PROGRESS_UPDATE_INTERVAL_MS * 1_000_000L;
    }

    private void printProgress(int processedDocuments, int skippedDocuments, long bytesRead,
                               long inputSizeBytes, long startTimeNanos) {
        double progress = inputSizeBytes <= 0 ? 0.0 : Math.min(1.0, (double) bytesRead / (double) inputSizeBytes);
        long elapsedSeconds = Math.max(1L, (System.nanoTime() - startTimeNanos) / 1_000_000_000L);
        long remainingSeconds;
        if (progress <= 0.0) {
            remainingSeconds = -1L;
        } else if (progress >= 1.0) {
            remainingSeconds = 0L;
        } else {
            remainingSeconds = Math.max(0L, Math.round((elapsedSeconds / progress) - elapsedSeconds));
        }

        String etaText = remainingSeconds < 0 ? "estimating..." : formatDuration(remainingSeconds);
        String message = String.format(
                Locale.ROOT,
                "\rProcessing Enron CSV: %6.2f%% | processed=%d | skipped=%d | elapsed=%s | remaining=%s",
                progress * 100.0,
                processedDocuments,
                skippedDocuments,
                formatDuration(elapsedSeconds),
                etaText
        );
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

    public static final class ProcessingSummary {
        private final int processedDocuments;
        private final int skippedDocuments;
        private final int extractedUniqueKeywords;
        private final int writtenUniqueKeywords;
        private final int filteredCommonKeywords;
        private final long totalKeywordAssignments;
        private final ProcessingMode mode;
        private final int topK;
        private final double maxDocFreqRatio;

        private ProcessingSummary(int processedDocuments, int skippedDocuments,
                                  int extractedUniqueKeywords, int writtenUniqueKeywords,
                                  int filteredCommonKeywords, long totalKeywordAssignments,
                                  ProcessingMode mode, int topK, double maxDocFreqRatio) {
            this.processedDocuments = processedDocuments;
            this.skippedDocuments = skippedDocuments;
            this.extractedUniqueKeywords = extractedUniqueKeywords;
            this.writtenUniqueKeywords = writtenUniqueKeywords;
            this.filteredCommonKeywords = filteredCommonKeywords;
            this.totalKeywordAssignments = totalKeywordAssignments;
            this.mode = mode;
            this.topK = topK;
            this.maxDocFreqRatio = maxDocFreqRatio;
        }

        public int processedDocuments() {
            return processedDocuments;
        }

        public int skippedDocuments() {
            return skippedDocuments;
        }

        public int uniqueKeywords() {
            return writtenUniqueKeywords;
        }

        public int extractedUniqueKeywords() {
            return extractedUniqueKeywords;
        }

        public int writtenUniqueKeywords() {
            return writtenUniqueKeywords;
        }

        public int filteredCommonKeywords() {
            return filteredCommonKeywords;
        }

        public long totalKeywordAssignments() {
            return totalKeywordAssignments;
        }

        public ProcessingMode mode() {
            return mode;
        }

        public int topK() {
            return topK;
        }

        public double maxDocFreqRatio() {
            return maxDocFreqRatio;
        }
    }

    public enum ProcessingMode {
        FULL,
        COMPACT;

        private static ProcessingMode fromArg(String value) {
            if (value == null) {
                throw new IllegalArgumentException("mode cannot be null");
            }

            String normalized = value.trim().toLowerCase(Locale.ROOT);
            if ("full".equals(normalized)) {
                return FULL;
            }
            if ("compact".equals(normalized)) {
                return COMPACT;
            }
            throw new IllegalArgumentException("mode must be either 'full' or 'compact': " + value);
        }

        private String value() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private static final class CsvRow {
        private final String file;
        private final String message;

        private CsvRow(String file, String message) {
            this.file = file;
            this.message = message;
        }
    }

    private static final class ParsedMessage {
        private final String subject;
        private final String body;

        private ParsedMessage(String subject, String body) {
            this.subject = subject;
            this.body = body;
        }
    }

    private static final class CountingInputStream extends InputStream {
        private final InputStream delegate;
        private long bytesRead;

        private CountingInputStream(InputStream delegate) {
            this.delegate = delegate;
            this.bytesRead = 0L;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value != -1) {
                bytesRead++;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = delegate.read(buffer, offset, length);
            if (count > 0) {
                bytesRead += count;
            }
            return count;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        private long bytesRead() {
            return bytesRead;
        }
    }
}
