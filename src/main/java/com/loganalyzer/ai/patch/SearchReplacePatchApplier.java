package com.loganalyzer.ai.patch;

import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Applies LLM search/replace patches to source text (Approach 3).
 * Exact match first; then newline-normalized unique match.
 */
@Component
public class SearchReplacePatchApplier {

    private static final Pattern LINE_NUMBER_PREFIX = Pattern.compile("(?m)^\\s*\\d+\\s*\\|\\s?");

    public Optional<String> apply(String original, String search, String replace) {
        if (!StringUtils.hasText(original) || !StringUtils.hasText(search)) {
            return Optional.empty();
        }
        String replacement = replace == null ? "" : replace;
        String needle = stripLineNumberPrefixes(search);

        Optional<String> hit = replaceUnique(original, needle, replacement);
        if (hit.isPresent()) {
            return hit;
        }

        String normOriginal = normalizeNewlines(original);
        String normNeedle = normalizeNewlines(needle);
        String normReplace = normalizeNewlines(replacement);
        Optional<String> normalized = replaceUnique(normOriginal, normNeedle, normReplace);
        if (normalized.isPresent()) {
            return normalized;
        }

        // Soft: collapse runs of spaces/tabs but keep newlines — unique match only
        return replaceUniqueFlexibleWs(normOriginal, normNeedle, normReplace);
    }

    private static Optional<String> replaceUnique(String haystack, String needle, String replacement) {
        int first = haystack.indexOf(needle);
        if (first < 0) {
            return Optional.empty();
        }
        int second = haystack.indexOf(needle, first + needle.length());
        if (second >= 0) {
            return Optional.empty();
        }
        return Optional.of(
            haystack.substring(0, first) + replacement + haystack.substring(first + needle.length()));
    }

    private static Optional<String> replaceUniqueFlexibleWs(String haystack, String needle, String replacement) {
        if (!StringUtils.hasText(needle)) {
            return Optional.empty();
        }
        StringBuilder regex = new StringBuilder();
        String[] parts = needle.split("\\s+", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                regex.append("\\s+");
            }
            if (!parts[i].isEmpty()) {
                regex.append(Pattern.quote(parts[i]));
            }
        }
        Pattern pattern = Pattern.compile(regex.toString());
        var matcher = pattern.matcher(haystack);
        if (!matcher.find()) {
            return Optional.empty();
        }
        int start = matcher.start();
        int end = matcher.end();
        if (matcher.find()) {
            return Optional.empty(); // ambiguous
        }
        return Optional.of(haystack.substring(0, start) + replacement + haystack.substring(end));
    }

    static String stripLineNumberPrefixes(String text) {
        return LINE_NUMBER_PREFIX.matcher(text).replaceAll("");
    }

    private static String normalizeNewlines(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }
}
