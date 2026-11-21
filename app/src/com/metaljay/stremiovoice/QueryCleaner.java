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
        "on stremio", "in stremio", "on nuvio", "in nuvio",
        // WuPlay, and how speech-to-text may spell it.
        "on wuplay", "in wuplay", "on wu play", "in wu play", "on woo play", "in woo play",
        // TiviMate, and how speech-to-text may spell it.
        "on tivimate", "in tivimate", "on tivi mate", "in tivi mate",
        "on tv mate", "in tv mate", "on tivo mate", "in tivo mate",
        "please",
    };

    // Spoken words (after clean()) that open the Voice Search settings screen instead of searching.
    private static final String[] SETTINGS_PHRASES = {
        "voice search settings", "voice search setting", "voice search setup", "voice search set up",
        "voice settings", "voice setting",
    };

    private QueryCleaner() {}

    /** True if the cleaned words ask for the settings screen, e.g. "voice search settings". */
    public static boolean isSettingsRequest(String cleaned) {
        if (cleaned == null) return false;
        String s = cleaned.trim();
        if (s.startsWith("open ")) s = s.substring(5).trim();
        for (String phrase : SETTINGS_PHRASES) {
            if (s.equals(phrase)) return true;
        }
        return false;
    }

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
            if (changed) continue;
            // "sky news channel" -> "sky news": TiviMate's search won't find the extra word.
            // Only when other words come before it, so "channel 4" and "channel" are left alone.
            if (s.endsWith(" channel")) {
                s = tidy(s.substring(0, s.length() - " channel".length()));
                changed = true;
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
