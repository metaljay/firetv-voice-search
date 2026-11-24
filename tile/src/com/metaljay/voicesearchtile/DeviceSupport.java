package com.metaljay.voicesearchtile;

/**
 * Is this stick one Voice Search has been tried on? Plain Java (no Android classes), so it is
 * unit-tested on the Mac. A stick that isn't on the list isn't refused: the setup app just says so
 * and lets the owner choose.
 *
 * Tested: Fire TV Stick 4K Max, 2nd gen (model AFTKRT) on Fire OS 8.1.8.x; the Fire TV Stick 4K Plus
 * reported working on 8.1.8.5 (a user report, not our own test).
 */
public final class DeviceSupport {

    /** The 4K Max, 2nd gen. */
    static final String MODEL_4K_MAX_2 = "AFTKRT";
    // TODO(Opus): the 4K Plus model code isn't written down anywhere in the README or handover.
    // Fill it in (from the user's report) and the 4K Plus on 8.1.8.x counts as tested too.
    static final String MODEL_4K_PLUS = null;

    /** The Fire OS line that was tested. */
    static final String TESTED_PREFIX = "8.1.8.";

    private DeviceSupport() {}

    /** True when this model on this Fire OS version is one Voice Search was tried on. */
    public static boolean isTested(String model, String fireOsVersion) {
        if (model == null || fireOsVersion == null) return false;
        String m = model.trim();
        boolean knownModel = m.equalsIgnoreCase(MODEL_4K_MAX_2)
                || (MODEL_4K_PLUS != null && m.equalsIgnoreCase(MODEL_4K_PLUS));
        return knownModel && fireOsVersion.trim().startsWith(TESTED_PREFIX)
                && fireOsVersion.trim().length() > TESTED_PREFIX.length();
    }

    /**
     * Pulls the dotted Fire OS number out of what the system reports, which may be just "8.1.8.5" or
     * words around it ("Fire OS 8.1.8.5 (PS8185/2517)"). Returns the first run of digits and dots with
     * at least three parts, or "" when there is none.
     */
    public static String versionFrom(String raw) {
        if (raw == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+(?:\\.\\d+){2,}").matcher(raw);
        return m.find() ? m.group() : "";
    }

    /** "AFTKRT, Fire OS 8.2.1.0", or a plain fallback when a part is unknown. */
    public static String describe(String model, String fireOsVersion) {
        String m = model == null || model.trim().isEmpty() ? "unknown model" : model.trim();
        String v = fireOsVersion == null || fireOsVersion.trim().isEmpty()
                ? "unknown Fire OS version" : "Fire OS " + fireOsVersion.trim();
        return m + ", " + v;
    }
}
