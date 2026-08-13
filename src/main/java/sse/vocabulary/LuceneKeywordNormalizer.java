package sse.vocabulary;

import java.io.IOException;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

public final class LuceneKeywordNormalizer implements KeywordNormalizer {

    private static final String FIELD_NAME = "keyword";

    private final EnglishAnalyzer analyzer;

    public LuceneKeywordNormalizer() {
        this.analyzer = new EnglishAnalyzer();
    }

    @Override
    public String normalize(String input) {
        if (input == null) {
            return null;
        }

        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        try (TokenStream stream = analyzer.tokenStream(FIELD_NAME, trimmed)) {
            CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            String normalized = stream.incrementToken() ? term.toString() : null;
            stream.end();
            return normalized == null || normalized.isEmpty() ? null : normalized;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to normalize keyword", e);
        }
    }
}
