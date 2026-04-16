package sse.dataset.enron;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class KeywordExtractor {

    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("\\b[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b");
    private static final Pattern NON_ALPHANUMERIC_PATTERN = Pattern.compile("[^a-z0-9]+");
    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{M}+");

    private static final int MIN_TOKEN_LENGTH = 3;
    private static final int MAX_TOKEN_LENGTH = 32;

    private static final Set<String> STOPWORDS = Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(
            "the", "and", "for", "are", "with", "that", "this", "from", "have", "will", "your", "you",
            "not", "but", "all", "can", "our", "has", "was", "were", "had", "his", "her", "she", "him",
            "its", "they", "them", "their", "what", "when", "where", "who", "how", "why", "which", "while",
            "would", "could", "should", "there", "here", "than", "then", "into", "about", "after", "before",
            "between", "through", "during", "because", "been", "being", "over", "under", "some", "more",
            "most", "much", "many", "very", "just", "also", "only", "such", "each", "other", "same",
            "these", "those", "please", "thanks", "thank", "regards", "best", "hello", "dear", "attached",
            "attachment", "forwarded", "original", "message", "mailto", "subject", "from", "sent", "cc",
            "bcc", "corp", "com", "net", "www", "http", "https", "re", "fw", "fwd", "ect", "hou"
    )));

    public Set<String> extractKeywords(String subject, String body) {
        Set<String> keywords = new LinkedHashSet<String>();
        addKeywords(subject, keywords);
        addKeywords(body, keywords);
        return keywords;
    }

    private void addKeywords(String text, Set<String> keywords) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }

        String normalized = normalizeText(text);
        if (normalized.isEmpty()) {
            return;
        }

        String[] rawTokens = NON_ALPHANUMERIC_PATTERN.split(normalized);
        for (String token : rawTokens) {
            if (isKeywordCandidate(token)) {
                keywords.add(token);
            }
        }
    }

    private String normalizeText(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        normalized = URL_PATTERN.matcher(normalized).replaceAll(" ");
        normalized = EMAIL_PATTERN.matcher(normalized).replaceAll(" ");
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD);
        normalized = DIACRITICS_PATTERN.matcher(normalized).replaceAll("");
        return normalized;
    }

    private boolean isKeywordCandidate(String token) {
        if (token == null) {
            return false;
        }

        String trimmed = token.trim();
        if (trimmed.length() < MIN_TOKEN_LENGTH || trimmed.length() > MAX_TOKEN_LENGTH) {
            return false;
        }
        if (STOPWORDS.contains(trimmed)) {
            return false;
        }
        if (isDigitsOnly(trimmed)) {
            return false;
        }
        return hasLetter(trimmed);
    }

    private boolean isDigitsOnly(String token) {
        for (int i = 0; i < token.length(); i++) {
            if (!Character.isDigit(token.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean hasLetter(String token) {
        for (int i = 0; i < token.length(); i++) {
            if (Character.isLetter(token.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
