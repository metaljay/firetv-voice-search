package com.metaljay.stremiovoice;

import java.util.List;

/**
 * Picks the add-on zip out of a release's attached files. Pure Java, so it runs in the
 * plain-Java unit tests.
 *
 * Releases attach the same zip under two names. "voice_search.zip" is the current name.
 * "stremio_voice.zip" is the old one and stays attached for good, because sticks on 2.3
 * and earlier only look for that name.
 */
public final class AssetPicker {

    static final String NEW_NAME = "voice_search.zip";
    static final String OLD_NAME = "stremio_voice.zip";

    private AssetPicker() {}

    /** The name to download: the new name if attached, else the old one, else null. */
    public static String pick(List<String> names) {
        if (names == null) return null;
        if (names.contains(NEW_NAME)) return NEW_NAME;
        if (names.contains(OLD_NAME)) return OLD_NAME;
        return null;
    }
}
