package com.metaljay.stremiovoice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Merges the catalogue's film and TV results into one row for the pick-list.
 * Pure Java, no Android classes, so it runs in the plain-Java unit tests.
 *
 * Order: by a score of the title's place in its own catalogue list (0 = most
 * popular) plus a penalty for a weaker name match: exact +0, starts with the
 * words +1, anything else +3. So popular titles stay near the front (Frozen II
 * is not buried under every obscure film called "Frozen"), while an exact name
 * wins a close call. Ties: the more popular, then the shorter (closer) title,
 * then film before TV.
 */
public final class ResultRanker {

    public static final int MAX_RESULTS = 10;

    public static final class Item {
        public final String id;      // IMDb id, e.g. tt7678620
        public final String type;    // "movie" or "series"
        public final String name;
        public final String year;    // as the catalogue gives it, e.g. "2018–"; may be empty
        public final String poster;  // URL; may be empty
        /** Position in its own catalogue list, 0 = most popular. */
        public final int rank;

        public Item(String id, String type, String name, String year, String poster, int rank) {
            this.id = id;
            this.type = type;
            this.name = name == null ? "" : name;
            this.year = year == null ? "" : year;
            this.poster = poster == null ? "" : poster;
            this.rank = rank;
        }

        @Override
        public String toString() {
            return name + " (" + year + ", " + type + ")";
        }
    }

    private ResultRanker() {}

    public static List<Item> merge(String words, List<Item> films, List<Item> shows) {
        final String wanted = normalise(words);
        List<Item> all = new ArrayList<>();
        if (films != null) all.addAll(films);
        if (shows != null) all.addAll(shows);
        // Drop entries the pick-list cannot open.
        for (int i = all.size() - 1; i >= 0; i--) {
            Item it = all.get(i);
            // Only IMDb ids ("tt" and digits) open in the apps; anything else is dropped.
            if (it.id == null || !it.id.matches("tt\\d+") || it.name.isEmpty()) all.remove(i);
        }
        Collections.sort(all, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                int c = Integer.compare(a.rank + penalty(a, wanted), b.rank + penalty(b, wanted));
                if (c != 0) return c;
                c = Integer.compare(a.rank, b.rank);
                if (c != 0) return c;
                c = Integer.compare(normalise(a.name).length(), normalise(b.name).length());
                if (c != 0) return c;
                return Integer.compare(typeOrder(a), typeOrder(b));
            }
        });
        return all.size() > MAX_RESULTS ? new ArrayList<>(all.subList(0, MAX_RESULTS)) : all;
    }

    private static int penalty(Item it, String wanted) {
        String name = normalise(it.name);
        if (wanted.isEmpty()) return 3;
        if (name.equals(wanted)) return 0;
        if (name.startsWith(wanted + " ")) return 1;
        return 3;
    }

    private static int typeOrder(Item it) {
        return "movie".equals(it.type) ? 0 : 1;
    }

    /** Lower case, letters and digits only, single spaces, no leading "the". */
    static String normalise(String s) {
        if (s == null) return "";
        String n = s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        if (n.startsWith("the ")) n = n.substring(4);
        return n;
    }
}
