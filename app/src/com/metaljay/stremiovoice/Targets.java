package com.metaljay.stremiovoice;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The streaming apps voice search can open, and the rule for choosing one.
 * Pure Java, no Android classes, so it runs in the plain-Java unit tests.
 *
 * To add an app: add an entry to ALL. An app either has its own search link
 * that takes the spoken words (APP_SEARCH), or only a link that opens one
 * title by its IMDb id, in which case our pick-list finds the title first
 * (PICK_LIST), or has no link at all but has a search screen of its own, in
 * which case the words are typed into that screen (TYPE_IN_APP).
 *
 * Every app also has the names it can be called by when spoken ("on smart tube"). Apps with no
 * search at all (Netflix, YouTube and friends) are in NO_SEARCH: they can be named, but never chosen as
 * the default, and they are only ever opened.
 */
public final class Targets {

    public enum Mode {
        /** The app shows its own search results for the words. */
        APP_SEARCH,
        /** Our pick-list shows matching titles; the chosen one opens in the app. */
        PICK_LIST,
        /** No search link: the words are typed into the app's own search screen. */
        TYPE_IN_APP,
        /** The app has no search: it can only be opened on its home screen. */
        OPEN_ONLY,
        /** Amazon's own results screen, which lists matches from all the user's apps. */
        AMAZON_SEARCH,
    }

    public static final class Target {
        /** Saved in the settings; never change an existing one. */
        public final String id;
        public final String name;
        public final String packageName;
        public final Mode mode;
        /** One line for the settings screen. */
        public final String description;
        /** APP_SEARCH only: the search link, to which the URL-encoded words are appended. */
        public final String searchLink;
        /** What the listener may call it, lower case as speech-to-text writes it ("smart tube"). */
        public final String[] spokenNames;

        Target(String id, String name, String packageName, Mode mode, String description,
               String searchLink, String... spokenNames) {
            this.id = id;
            this.name = name;
            this.packageName = packageName;
            this.mode = mode;
            this.description = description;
            this.searchLink = searchLink;
            this.spokenNames = spokenNames;
        }
    }

    public static final Target STREMIO = new Target(
            "stremio", "Stremio", "com.stremio.one", Mode.APP_SEARCH,
            "Films and series", "stremio:///search?search=",
            "stremio");

    public static final Target NUVIO = new Target(
            "nuvio", "Nuvio", "com.nuvio.tv", Mode.PICK_LIST,
            "Films and series", null,
            "nuvio");

    public static final Target WUPLAY = new Target(
            "wuplay", "WuPlay", "app.wuplay.androidtv", Mode.PICK_LIST,
            "Films and series", null,
            "wuplay", "wu play", "woo play");

    public static final Target TIVIMATE = new Target(
            "tivimate", "TiviMate", "ar.tvplayer.tv", Mode.TYPE_IN_APP,
            "Channels, films and series", null,
            "tivimate", "tivi mate", "tv mate", "tivo mate");

    public static final Target SMARTTUBE = new Target(
            "smarttube", "SmartTube", "org.smarttube.stable", Mode.APP_SEARCH,
            "YouTube videos", "https://www.youtube.com/results?search_query=",
            "smarttube", "smart tube");

    /**
     * Amazon's own search results screen (in the home-screen app). Needs the platform permission
     * SHOW_SEARCH_RESULTS, which is on the add-on's system whitelist. Never the automatic fallback:
     * it is there only when chosen, or when a no-search app is named.
     */
    public static final Target AMAZON = new Target(
            "amazon", "Amazon search", "com.amazon.tv.launcher", Mode.AMAZON_SEARCH,
            "Searches all your apps", null,
            "amazon search");

    // Apps with no search link: opened on their home screen (see NO_SEARCH).
    public static final Target NETFLIX = new Target(
            "netflix", "Netflix", "com.netflix.ninja", Mode.OPEN_ONLY, "", null,
            "netflix");
    public static final Target DISNEY = new Target(
            "disney", "Disney+", "com.disney.disneyplus", Mode.OPEN_ONLY, "", null,
            "disney plus", "disney+", "disney");
    public static final Target IPLAYER = new Target(
            "iplayer", "BBC iPlayer", "uk.co.bbc.iplayer", Mode.OPEN_ONLY, "", null,
            "bbc iplayer", "bbc i player", "iplayer", "i player");
    public static final Target CHANNEL4 = new Target(
            "channel4", "Channel 4", "com.channel4.ondemand", Mode.OPEN_ONLY, "", null,
            "channel 4", "channel four", "all 4", "all four");
    public static final Target MY5 = new Target(
            "my5", "My5", "com.mobileiq.demand5", Mode.OPEN_ONLY, "", null,
            "my5", "my 5", "channel 5", "channel five");
    /**
     * Amazon's own YouTube app. Stick test (22 Nov): its search link lands on the last-used
     * profile's home page, which has no search box, so the words are lost; it is treated as an
     * app with no search (Amazon's results, else just opened).
     */
    public static final Target YOUTUBE = new Target(
            "youtube", "YouTube", "com.amazon.firetv.youtube", Mode.OPEN_ONLY, "", null,
            "youtube", "you tube");
    public static final Target PRIME = new Target(
            "prime", "Prime Video", "com.amazon.firebat", Mode.OPEN_ONLY, "", null,
            "amazon prime video", "prime video", "amazon prime", "amazon");

    /** In order of preference when nothing has been chosen. */
    public static final Target[] ALL = {STREMIO, NUVIO, WUPLAY, TIVIMATE, SMARTTUBE, AMAZON};

    /** Apps that can be named but have no search: they are only opened. Never the default. */
    public static final Target[] NO_SEARCH = {NETFLIX, DISNEY, IPLAYER, CHANNEL4, MY5, YOUTUBE, PRIME};

    /** Every app that can be named: the settings list, then the no-search apps. */
    private static final List<Target> NAMEABLE = new ArrayList<>();
    /** Each spoken name with its app, longest name first, so "amazon prime video" beats "amazon". */
    private static final List<Object[]> NAMES = new ArrayList<>();

    static {
        Collections.addAll(NAMEABLE, ALL);
        Collections.addAll(NAMEABLE, NO_SEARCH);
        for (Target t : NAMEABLE) {
            for (String n : t.spokenNames) NAMES.add(new Object[] {n, t});
        }
        Collections.sort(NAMES, new Comparator<Object[]>() {
            @Override
            public int compare(Object[] a, Object[] b) {
                return ((String) b[0]).length() - ((String) a[0]).length();
            }
        });
    }

    private Targets() {}

    /** Every spoken name of every app, as the trailing words that should be dropped from a search. */
    static List<String> spokenNames() {
        List<String> out = new ArrayList<>();
        for (Object[] n : NAMES) out.add((String) n[0]);
        return out;
    }

    /**
     * The app named at the end of what was said ("bluey on netflix" -> Netflix), or null if none is.
     * Only a trailing "on <name>" or "in <name>" with at least one word before it counts, after
     * lead-ins ("put on", "play", ...) have been taken off the front. So a title that merely contains
     * a name ("once upon a time in hollywood") is left alone, and so is "put on channel 5".
     */
    public static Target named(String spoken) {
        if (spoken == null) return null;
        String s = QueryCleaner.normalise(spoken);
        boolean trimmed = true;
        while (trimmed) {
            trimmed = false;
            if (s.endsWith(" please")) {
                s = QueryCleaner.normalise(s.substring(0, s.length() - " please".length()));
                trimmed = true;
            }
        }
        s = QueryCleaner.stripLeadIns(s);
        for (Object[] n : NAMES) {
            String name = (String) n[0];
            if (s.endsWith(" on " + name) || s.endsWith(" in " + name)) {
                return (Target) n[1];
            }
        }
        return null;
    }

    public static Target byId(String id) {
        if (id == null) return null;
        for (Target t : ALL) {
            if (t.id.equals(id)) return t;
        }
        return null;
    }

    /**
     * The app to search in: the saved choice if it is installed, otherwise the
     * first installed app in ALL, otherwise null.
     */
    public static Target choose(String savedId, Collection<String> installedPackages) {
        Target saved = byId(savedId);
        if (saved != null && installedPackages.contains(saved.packageName)) return saved;
        for (Target t : ALL) {
            if (t.mode == Mode.AMAZON_SEARCH) continue; // only when chosen, never by default
            if (installedPackages.contains(t.packageName)) return t;
        }
        return null;
    }

    /** The link that opens one title, for PICK_LIST apps. Type is "movie" or "series". */
    public static String titleLink(Target target, String type, String imdbId) {
        if (target == NUVIO) return "nuvio://" + type + "/" + imdbId;
        if (target == WUPLAY) return "wuplay://" + type + "/" + imdbId;
        throw new IllegalArgumentException(target.id + " has no title link");
    }
}
