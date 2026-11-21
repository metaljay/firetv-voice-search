package com.metaljay.stremiovoice;

import java.util.List;
import java.util.Locale;

/**
 * Picks which result card of TiviMate's Search screen to highlight. Pure Java, no Android
 * classes, so it runs in the plain-Java unit tests; TiviMateHelper only gathers the cards from
 * the screen and focuses the one chosen here.
 *
 * TiviMate lists results in rows under the headers "Channels", "Movies" and "Shows". Unless the
 * spoken words ended in "channel", films and series come first, then channels. Within the
 * preferred rows the card whose title best matches the words wins (exact, then starts with, then
 * contains). Cards that match equally are ranked: a title that needed no country or quality
 * prefix stripped ("Sky News") before one that did ("NO: Sky News"); then a prefix that is the
 * stick's own country before other countries (a quality prefix like "4K:" is not a foreign
 * country); then TiviMate's own order (top row first, then left to right).
 */
public final class ResultChooser {

    public static final String CHANNELS = "Channels";
    public static final String MOVIES = "Movies";
    public static final String SHOWS = "Shows";

    /** A row header on the screen. */
    public static final class Header {
        public final String text;
        public final int top;

        public Header(String text, int top) {
            this.text = text;
            this.top = top;
        }
    }

    /** One result card. row is the header it sits under ("Channels", "Movies", "Shows") or null. */
    public static final class Card {
        public final String row;
        public final String title;
        public final int top;
        public final int left;

        public Card(String row, String title, int top, int left) {
            this.row = row;
            this.title = title;
            this.top = top;
            this.left = left;
        }
    }

    private ResultChooser() {}

    /** The known row header a text is (ignoring case), or null. */
    public static String knownRow(String headerText) {
        if (headerText == null) return null;
        String t = headerText.trim();
        if (t.equalsIgnoreCase(CHANNELS)) return CHANNELS;
        if (t.equalsIgnoreCase(MOVIES)) return MOVIES;
        if (t.equalsIgnoreCase(SHOWS)) return SHOWS;
        return null;
    }

    /** The row a card at cardTop belongs to: the nearest known header at or above it, else null. */
    public static String rowFor(List<Header> headers, int cardTop) {
        Header best = null;
        for (Header h : headers) {
            if (knownRow(h.text) == null || h.top > cardTop) continue;
            if (best == null || h.top > best.top) best = h;
        }
        return best == null ? null : knownRow(best.text);
    }

    /**
     * The index in cards to highlight, or -1 when there are no cards. With no recognised row
     * on any card (TiviMate in another language), the top-left card.
     */
    public static int choose(List<Card> cards, String words, boolean wantChannel) {
        return choose(cards, words, wantChannel, null);
    }

    /**
     * As above; country is the stick's own country code (for example "GB"), or null/empty for
     * no preference. "GB" also matches the prefixes "UK" and "EN".
     */
    public static int choose(List<Card> cards, String words, boolean wantChannel, String country) {
        if (cards == null || cards.isEmpty()) return -1;
        boolean anyRow = false;
        for (Card c : cards) {
            if (c.row != null) anyRow = true;
        }
        if (!anyRow) return bestIn(cards, null, "", false, null);

        int pick;
        if (wantChannel) {
            pick = bestIn(cards, new String[]{CHANNELS}, words, true, country);
            if (pick < 0) pick = bestIn(cards, new String[]{MOVIES, SHOWS}, words, true, country);
        } else {
            pick = bestIn(cards, new String[]{MOVIES, SHOWS}, words, true, country);
            if (pick < 0) pick = bestIn(cards, new String[]{CHANNELS}, words, true, country);
        }
        return pick >= 0 ? pick : bestIn(cards, null, "", false, null);
    }

    /** Best card among those in the given rows (any card if rows is null), or -1 if there are none. */
    private static int bestIn(List<Card> cards, String[] rows, String words, boolean score, String country) {
        String w = normalise(words);
        int best = -1;
        int bestScore = -1, bestPlain = -1, bestCountry = -1;
        for (int i = 0; i < cards.size(); i++) {
            Card c = cards.get(i);
            if (rows != null && !inRows(c.row, rows)) continue;
            int s = score ? score(normalise(c.title), w) : 0;
            int plain = score && prefixOf(c.title) == null ? 1 : 0;
            int ctry = score ? countryRank(prefixOf(c.title), country) : 0;
            boolean better = best < 0
                    || s > bestScore
                    || (s == bestScore && plain > bestPlain)
                    || (s == bestScore && plain == bestPlain && ctry > bestCountry)
                    || (s == bestScore && plain == bestPlain && ctry == bestCountry
                            && before(c, cards.get(best)));
            if (better) {
                best = i;
                bestScore = s;
                bestPlain = plain;
                bestCountry = ctry;
            }
        }
        return best;
    }

    /** The leading country or quality code of a title ("UK" in "UK: Sky News"), lower case, or null. */
    public static String prefixOf(String title) {
        if (title == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^([a-z0-9]{2,3})\\s*[:|]\\s+").matcher(title.toLowerCase(Locale.ROOT).trim());
        return m.find() ? m.group(1) : null;
    }

    private static boolean isQuality(String prefix) {
        return prefix.equals("4k") || prefix.equals("hd") || prefix.equals("fhd")
                || prefix.equals("uhd") || prefix.equals("sd");
    }

    /**
     * 2 = the prefix is the stick's own country, 1 = neutral (no prefix, a quality prefix, or no
     * country preference), 0 = another country's prefix.
     */
    static int countryRank(String prefix, String country) {
        if (country == null || country.trim().isEmpty() || prefix == null || isQuality(prefix)) return 1;
        String c = country.trim().toLowerCase(Locale.ROOT);
        if (prefix.equals(c)) return 2;
        if (c.equals("gb") && (prefix.equals("uk") || prefix.equals("en"))) return 2;
        return 0;
    }

    private static boolean inRows(String row, String[] rows) {
        for (String r : rows) {
            if (r.equals(row)) return true;
        }
        return false;
    }

    /** TiviMate's order: higher on the screen first, then further left. */
    private static boolean before(Card a, Card b) {
        return a.top < b.top || (a.top == b.top && a.left < b.left);
    }

    /** 3 = exactly the words, 2 = starts with them, 1 = contains them, 0 = no match. */
    static int score(String title, String words) {
        if (words.isEmpty() || title.isEmpty()) return 0;
        if (title.equals(words)) return 3;
        if (title.startsWith(words)) return 2;
        if (title.contains(words)) return 1;
        return 0;
    }

    /**
     * Lower case, a trailing year ("(2018)", "- 2016") and a leading country or quality prefix
     * ("UK: ", "4K | ") dropped, punctuation turned into single spaces.
     */
    static String normalise(String s) {
        if (s == null) return "";
        String t = s.toLowerCase(Locale.ROOT).trim();
        t = t.replaceFirst("^[a-z0-9]{2,3}\\s*[:|]\\s+", "");
        t = t.replaceFirst("\\s*\\(\\s*(19|20)\\d\\d\\s*\\)$", "");
        t = t.replaceFirst("\\s+-\\s+(19|20)\\d\\d$", "");
        t = t.replaceAll("[^a-z0-9]+", " ").trim();
        return t;
    }
}
