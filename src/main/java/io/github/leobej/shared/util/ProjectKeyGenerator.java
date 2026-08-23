package io.github.leobej.shared.util;

import java.util.ArrayList;
import java.util.List;

// Project keys are the visible half of every issue identifier (KEY-42), so they are short,
// uppercase and stable: 2-10 characters, first one a letter.
public final class ProjectKeyGenerator {

    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 10;
    private static final String FALLBACK = "PRJ";

    private ProjectKeyGenerator() {}

    // "Simplira Backend" -> "SB", "Backend" -> "BACKEND", "3M" -> "PRJ" (must start with a letter)
    public static String fromName(String name) {
        List<String> words = wordsOf(name);
        if (words.isEmpty()) {
            return FALLBACK;
        }
        String candidate;
        if (words.size() > 1) {
            StringBuilder initials = new StringBuilder();
            for (String word : words) {
                if (initials.length() == MAX_LENGTH) {
                    break;
                }
                initials.append(word.charAt(0));
            }
            candidate = initials.toString();
        } else {
            candidate = words.get(0);
        }
        candidate = truncate(candidate);
        return isValid(candidate) ? candidate : FALLBACK;
    }

    public static String normalize(String key) {
        return key == null ? null : key.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public static boolean isValid(String key) {
        if (key == null || key.length() < MIN_LENGTH || key.length() > MAX_LENGTH) {
            return false;
        }
        if (!Character.isLetter(key.charAt(0)) || !isUpperAscii(key.charAt(0))) {
            return false;
        }
        for (int i = 1; i < key.length(); i++) {
            char c = key.charAt(i);
            if (!isUpperAscii(c) && !Character.isDigit(c)) {
                return false;
            }
        }
        return true;
    }

    // Collision handling: SB, SB2, SB3 ... trimming the base so the result still fits.
    public static String withSuffix(String base, int attempt) {
        String suffix = Integer.toString(attempt);
        String trimmed = base.length() + suffix.length() > MAX_LENGTH
                ? base.substring(0, MAX_LENGTH - suffix.length())
                : base;
        return trimmed + suffix;
    }

    private static List<String> wordsOf(String name) {
        List<String> words = new ArrayList<>();
        if (name == null) {
            return words;
        }
        StringBuilder current = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                current.append(Character.toUpperCase(c));
            } else if (current.length() > 0) {
                words.add(current.toString());
                current.setLength(0);
            }
        }
        if (current.length() > 0) {
            words.add(current.toString());
        }
        return words;
    }

    private static String truncate(String value) {
        return value.length() > MAX_LENGTH ? value.substring(0, MAX_LENGTH) : value;
    }

    private static boolean isUpperAscii(char c) {
        return c >= 'A' && c <= 'Z';
    }
}
