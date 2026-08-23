package io.github.leobej.shared.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class SlugGenerator {

    private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_DASHES = Pattern.compile("^-+|-+$");
    private static final int MAX_LENGTH = 60;
    private static final String FALLBACK = "workspace";

    private SlugGenerator() {}

    // "Acme Tech!" -> "acme-tech", "Ünïcode" -> "unicode"
    public static String slugify(String input) {
        if (input == null || input.isBlank()) {
            return FALLBACK;
        }
        String withoutAccents = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String slug = NON_SLUG.matcher(withoutAccents.toLowerCase(Locale.ROOT)).replaceAll("-");
        slug = EDGE_DASHES.matcher(slug).replaceAll("");
        if (slug.length() > MAX_LENGTH) {
            slug = EDGE_DASHES.matcher(slug.substring(0, MAX_LENGTH)).replaceAll("");
        }
        return slug.isBlank() ? FALLBACK : slug;
    }

    // Collision handling: acme-tech, acme-tech-2, acme-tech-3 ...
    public static String withSuffix(String base, int attempt) {
        return base + "-" + attempt;
    }
}
