package com.metaljay.stremiovoice;

import java.util.Collection;

/**
 * The streaming apps voice search can open, and the rule for choosing one.
 * Pure Java, no Android classes, so it runs in the plain-Java unit tests.
 *
 * To add an app: add an entry to ALL. An app either has its own search link
 * that takes the spoken words (APP_SEARCH), or only a link that opens one
 * title by its IMDb id, in which case our pick-list finds the title first
 * (PICK_LIST).
 */
public final class Targets {

    public enum Mode {
        /** The app shows its own search results for the words. */
        APP_SEARCH,
        /** Our pick-list shows matching titles; the chosen one opens in the app. */
        PICK_LIST,
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

        Target(String id, String name, String packageName, Mode mode, String description,
               String searchLink) {
            this.id = id;
            this.name = name;
            this.packageName = packageName;
            this.mode = mode;
            this.description = description;
            this.searchLink = searchLink;
        }
    }

    public static final Target STREMIO = new Target(
            "stremio", "Stremio", "com.stremio.one", Mode.APP_SEARCH,
            "Stremio's own search results", "stremio:///search?search=");

    public static final Target NUVIO = new Target(
            "nuvio", "Nuvio", "com.nuvio.tv", Mode.PICK_LIST,
            "Pick from a row of posters, then Nuvio opens it", null);

    /** In order of preference when nothing has been chosen. */
    public static final Target[] ALL = {STREMIO, NUVIO};

    private Targets() {}

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
            if (installedPackages.contains(t.packageName)) return t;
        }
        return null;
    }

    /** The link that opens one title, for PICK_LIST apps. Type is "movie" or "series". */
    public static String titleLink(Target target, String type, String imdbId) {
        if (target == NUVIO) return "nuvio://" + type + "/" + imdbId;
        throw new IllegalArgumentException(target.id + " has no title link");
    }
}
