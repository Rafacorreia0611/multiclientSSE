package sse.populatedb;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import sse.dataset.enron.KeywordDocIdsEntry;

public final class EnronReaderService {

    private final Gson gson;

    public EnronReaderService() {
        this.gson = new Gson();
    }

    public BufferedReader openReader(Path inputPath) {
        if (inputPath == null) {
            throw new IllegalArgumentException("inputPath cannot be null");
        }
        if (!Files.exists(inputPath) || !Files.isRegularFile(inputPath)) {
            throw new IllegalArgumentException("NDJSON file does not exist: " + inputPath.toAbsolutePath());
        }

        try {
            return Files.newBufferedReader(inputPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to open NDJSON file " + inputPath.toAbsolutePath(), e);
        }
    }

    public KeywordDocIdsEntry parseEntry(String line, long lineNumber) {
        if (line == null) {
            return null;
        }

        String normalizedLine = line.trim();
        if (normalizedLine.isEmpty()) {
            return null;
        }

        try {
            KeywordDocIdsEntry entry = gson.fromJson(normalizedLine, KeywordDocIdsEntry.class);
            if (entry == null) {
                throw new IllegalArgumentException("entry is null");
            }
            validateEntry(entry);
            return entry;
        } catch (JsonParseException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid NDJSON entry at line " + lineNumber, e);
        }
    }

    private void validateEntry(KeywordDocIdsEntry entry) {
        if (entry.keyword() == null || entry.keyword().trim().isEmpty()) {
            throw new IllegalArgumentException("keyword cannot be null or empty");
        }
        if (entry.docIds() == null || entry.docIds().isEmpty()) {
            throw new IllegalArgumentException("docIds cannot be null or empty");
        }
        for (String docId : entry.docIds()) {
            if (docId == null || docId.trim().isEmpty()) {
                throw new IllegalArgumentException("docIds cannot contain null or empty values");
            }
        }
    }
}
