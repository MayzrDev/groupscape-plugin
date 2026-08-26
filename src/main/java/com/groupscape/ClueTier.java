package com.groupscape;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts a clue scroll's tier from RuneLite's {@code LootReceived.getName()} for a clue
 * casket reward (e.g. "Clue Scroll (Elite)"), which arrives as {@code LootRecordType.EVENT}
 * like any other chest source - there's no dedicated clue event type in the RuneLite API.
 */
public final class ClueTier {
    private ClueTier() {
    }

    private static final Pattern CLUE_PATTERN = Pattern.compile("^Clue Scroll \\((\\w+)\\)$");

    /**
     * @return the tier lowercased ("beginner".."master"), or {@code null} if {@code eventName}
     * isn't a clue casket reward.
     */
    public static String extractTier(String eventName) {
        if (eventName == null) {
            return null;
        }
        Matcher matcher = CLUE_PATTERN.matcher(eventName);
        return matcher.matches() ? matcher.group(1).toLowerCase() : null;
    }
}
