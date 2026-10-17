package io.github.leobej.shared.util;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Mentions are written as @[Display Name](user-id), the token the editor inserts when someone is
// picked from the member search (§26). Users have no username to match on, full names are not
// unique, and the id keeps a mention correct after the person is renamed - the display text is
// only there so the raw comment stays readable.
public final class MentionParser {

    private static final Pattern MENTION = Pattern.compile(
            "@\\[([^\\]\\r\\n]{1,100})\\]\\(([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})\\)");

    private MentionParser() {}

    // In order of first appearance, each person once however often they are mentioned.
    public static Set<UUID> mentionedUserIds(String content) {
        Set<UUID> ids = new LinkedHashSet<>();
        if (content == null) {
            return ids;
        }
        Matcher matcher = MENTION.matcher(content);
        while (matcher.find()) {
            ids.add(UUID.fromString(matcher.group(2)));
        }
        return ids;
    }
}
