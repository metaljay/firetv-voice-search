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

    // "on stremio", "in smart tube", ... for every app that can be named (longest first), then "please".
    private static final String[] TAILS = buildTails();

    private static String[] buildTails() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String name : Targets.spokenNames()) {
            out.add("on " + name);
            out.add("in " + name);
        }
        out.add("please");
        return out.toArray(new String[0]);
    }

    // Spoken words (after clean()) that open the Voice Search settings screen instead of searching.
    private static final String[] SETTINGS_PHRASES = {
        "voice search settings", "voice search setting", "voice search setup", "voice search set up",
        "voice settings", "voice setting",
    };

    private QueryCleaner() {}

    /**
     * True if the spoken words ended in "channel" (so the TiviMate trim drops a word): the
     * listener wants the live channel, not the film or series of that name.
     */
    public static boolean endsWithChannel(String spoken) {
        return !clean(spoken, false).equals(clean(spoken, true));
    }

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

    /** True only for a bare "Alexa" (with "hey" / "ok" and punctuation around it): the listener wants Alexa herself. */
    public static boolean isAlexaRequest(String spoken) {
        if (spoken == null) return false;
        String s = normalise(spoken);
        for (String hello : new String[] {"hey", "ok", "okay", "hi", "hello"}) {
            if (s.startsWith(hello + " ")) {
                s = normalise(s.substring(hello.length()));
                break;
            }
        }
        return s.equals("alexa");
    }

    /** Lower case, spoken-sentence punctuation removed, spaces tidied. */
    static String normalise(String spoken) {
        String s = spoken.toLowerCase(Locale.ROOT);
        s = s.replaceAll("[,;:!?]", " ");
        return tidy(s);
    }

    /** Tidies the spoken words for any app except TiviMate. */
    public static String clean(String spoken) {
        return clean(spoken, false);
    }

    /**
     * Tidies the spoken words. forTiviMate also drops a trailing "channel" ("sky news channel"
     * becomes "sky news"), because TiviMate's search wouldn't find the extra word; other apps
     * get it kept ("weather channel").
     */
    public static String clean(String spoken, boolean forTiviMate) {
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
            // TiviMate only: "sky news channel" -> "sky news". Only when other words come before
            // it, so "channel 4" and "channel" are left alone.
            if (forTiviMate && s.endsWith(" channel")) {
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
