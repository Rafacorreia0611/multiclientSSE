package sse.vocabulary;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.TreeMap;

public final class VocabularyExporter {

    private static final String USAGE = "Usage: VocabularyExporter INPUT_VOCABULARY OUTPUT_USABLE_VOCABULARY";

    private VocabularyExporter() {
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException(USAGE);
        }

        Path inputPath = Paths.get(args[0]);
        Path outputPath = Paths.get(args[1]);

        Map<String, String> keywords = loadRepresentativeKeywords(inputPath);
        writeKeywords(outputPath, keywords);
    }

    private static Map<String, String> loadRepresentativeKeywords(Path inputPath) {
        if (!Files.exists(inputPath) || !Files.isRegularFile(inputPath) || !Files.isReadable(inputPath)) {
            throw new IllegalArgumentException("Vocabulary file is not readable: " + inputPath.toAbsolutePath());
        }

        LuceneKeywordNormalizer normalizer = new LuceneKeywordNormalizer();
        Map<String, String> normalizedToRawKeyword = new TreeMap<String, String>();

        try (BufferedReader reader = Files.newBufferedReader(inputPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String rawKeyword = line.trim();
                String normalized = normalizer.normalize(rawKeyword);
                if (normalized != null && !normalized.isEmpty() && hasLetter(normalized)
                        && !normalizedToRawKeyword.containsKey(normalized)) {
                    normalizedToRawKeyword.put(normalized, rawKeyword);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read vocabulary file " + inputPath.toAbsolutePath(), e);
        }

        if (normalizedToRawKeyword.isEmpty()) {
            throw new IllegalArgumentException("Vocabulary file has no usable keywords: " + inputPath.toAbsolutePath());
        }

        return normalizedToRawKeyword;
    }

    private static void writeKeywords(Path outputPath, Map<String, String> normalizedToRawKeyword) {
        Path parent = outputPath.toAbsolutePath().getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to create output directory " + parent, e);
            }
        }

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            for (String rawKeyword : normalizedToRawKeyword.values()) {
                writer.write(rawKeyword);
                writer.newLine();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write usable vocabulary " + outputPath.toAbsolutePath(), e);
        }
    }

    private static boolean hasLetter(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isLetter(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
