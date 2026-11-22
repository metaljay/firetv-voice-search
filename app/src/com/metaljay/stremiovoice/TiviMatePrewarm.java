package com.metaljay.stremiovoice;

/**
 * The decisions for getting TiviMate ready while the user is still talking, and for when to choose
 * a result. Plain Java (no Android code), so it is tested on the Mac.
 */
public final class TiviMatePrewarm {

    /** Setting values (prefs "voice_search", key "tivimate_prewarm"). */
    public static final String IDLE = "idle";
    public static final String ALWAYS = "always";

    /** Amazon's home screen. */
    static final String AMAZON_HOME = "com.amazon.tv.launcher";
    static final String TIVIMATE = "ar.tvplayer.tv";
    /** Voice Search's own screens (the settings app and the tile app). */
    static final String OWN_APP = "com.metaljay.stremiovoice";
    static final String OWN_TILE = "com.metaljay.voicesearchtile";

    /** Choose once TiviMate has been quiet this long and a Movies or Shows row has cards... */
    public static final long QUIET_WITH_ROWS_MS = 300;
    /** ...or this long whatever is showing... */
    public static final long QUIET_ANY_MS = 700;
    /** ...or after this long since the box was filled, at the latest. */
    public static final long CAP_MS = 3000;

    private TiviMatePrewarm() {}

    /**
     * Should TiviMate get ready now, before the words arrive?
     * ALWAYS: from any screen. IDLE (and any unknown setting): only from Amazon's home screen, Voice
     * Search's own screens, or TiviMate itself when no channel is playing full screen. A front app
     * that isn't known (null) never counts as safe.
     */
    public static boolean shouldPrewarm(String mode, String frontPackage, boolean tivimatePlaying) {
        if (ALWAYS.equals(mode)) return true;
        if (frontPackage == null) return false;
        switch (frontPackage) {
            case AMAZON_HOME:
            case OWN_APP:
            case OWN_TILE:
                return true;
            case TIVIMATE:
                return !tivimatePlaying;
            default:
                return false;
        }
    }

    /** Has the setting a value we know? Anything else is treated as IDLE. */
    public static String normalise(String mode) {
        return ALWAYS.equals(mode) ? ALWAYS : IDLE;
    }

    /**
     * After the search box is filled: is it time to choose the best result?
     * sinceFillMs: time since the box was filled; quietMs: time since TiviMate's screen last
     * changed; rowsSeen: a Movies or Shows row has cards.
     */
    public static boolean readyToChoose(long sinceFillMs, long quietMs, boolean rowsSeen) {
        if (sinceFillMs >= CAP_MS) return true;
        if (rowsSeen && quietMs >= QUIET_WITH_ROWS_MS) return true;
        return quietMs >= QUIET_ANY_MS;
    }
}
