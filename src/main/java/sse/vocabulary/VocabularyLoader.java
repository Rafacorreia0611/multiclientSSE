package sse.vocabulary;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

public final class VocabularyLoader {

    public static final Path DEFAULT_VOCABULARY_PATH =
            java.nio.file.Paths.get("datasets", "vocabulary", "vocabulary.txt");

    private final KeywordNormalizer keywordNormalizer;

    public VocabularyLoader(KeywordNormalizer keywordNormalizer) {
        if (keywordNormalizer == null) {
            throw new IllegalArgumentException("keywordNormalizer cannot be null");
        }
        this.keywordNormalizer = keywordNormalizer;
    }

    public List<String> load(Path path) {
        validatePath(path);

        TreeSet<String> normalizedKeywords = new TreeSet<String>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String normalized = keywordNormalizer.normalize(line);
                if (normalized != null && !normalized.isEmpty() && hasLetter(normalized)) {
                    normalizedKeywords.add(normalized);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read vocabulary file " + path.toAbsolutePath(), e);
        }

        if (normalizedKeywords.isEmpty()) {
            throw new IllegalArgumentException("Vocabulary file has no usable keywords: " + path.toAbsolutePath());
        }

        return Collections.unmodifiableList(new ArrayList<String>(normalizedKeywords));
    }

    private void validatePath(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("vocabulary path cannot be null");
        }
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Vocabulary file does not exist: " + path.toAbsolutePath());
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Vocabulary path is not a regular file: " + path.toAbsolutePath());
        }
        if (!Files.isReadable(path)) {
            throw new IllegalArgumentException("Vocabulary file is not readable: " + path.toAbsolutePath());
        }
    }

    private boolean hasLetter(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isLetter(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
