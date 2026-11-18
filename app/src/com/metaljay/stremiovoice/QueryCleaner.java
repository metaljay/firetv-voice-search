package com.metaljay.stremiovoice;

import java.util.Locale;

/**
 * Turns what was said ("Alexa, play Peppa Pig please") into search words
 * ("peppa pig"). Pure function, no Android classes, so it runs in the
 * plain-Java unit tests.
 */
public final class QueryCleaner {

    // Longest first, so "search for" is tried before "search".
    private static final String[] LEAD_INS = {
        "i want to watch", "search for", "look for", "show me", "put on",
        "can you", "please", "alexa", "search", "watch", "find", "play",
    };

    private static final String[] TAILS = {
        "on stremio", "in stremio", "on nuvio", "in nuvio", "please",
    };

    private QueryCleaner() {}

    public static String clean(String spoken) {
        if (spoken == null) return "";
        String s = spoken.toLowerCase(Locale.ROOT);
        // Commas and similar marks are spoken-sentence punctuation, not part of a title.
        s = s.replaceAll("[,;:!?]", " ");
        s = tidy(s);

        boolean changed = true;
        while (changed && !s.isEmpty()) {
            changed = false;
            for (String lead : LEAD_INS) {
                if (s.equals(lead)) {
                    return "";
                }
                if (s.startsWith(lead + " ")) {
                    s = tidy(s.substring(lead.length()));
                    changed = true;
                    break;
                }
            }
            if (changed) continue;
            for (String tail : TAILS) {
                if (s.equals(tail)) {
                    return "";
                }
                if (s.endsWith(" " + tail)) {
                    s = tidy(s.substring(0, s.length() - tail.length()));
                    changed = true;
                    break;
                }
            }
        }
        return s;
    }

    /** Trim, collapse runs of spaces, drop trailing punctuation and stray leading dots. */
    private static String tidy(String s) {
        s = s.trim().replaceAll("\\s+", " ");
        s = s.replaceAll("[\\s.\\-'\"]+$", "");
        s = s.replaceAll("^[\\s.\\-'\"]+", "");
        return s;
    }
}
