package com.metaljay.stremiovoice;

/**
 * How often the scheduled update check runs: daily, weekly (the default),
 * monthly or off. The background job wakes every few hours; this says whether
 * a check is actually due. Plain Java, unit-tested on the Mac.
 */
public final class UpdateSchedule {

    public static final String DAILY = "daily";
    public static final String WEEKLY = "weekly";
    public static final String MONTHLY = "monthly";
    public static final String OFF = "off";

    private static final long DAY_MS = 24L * 3600 * 1000;

    private UpdateSchedule() {}

    /** The saved choice, or Weekly when nothing (or something unknown) was saved. */
    public static String normalise(String saved) {
        if (DAILY.equals(saved) || WEEKLY.equals(saved) || MONTHLY.equals(saved) || OFF.equals(saved)) {
            return saved;
        }
        return WEEKLY;
    }

    /**
     * Whether the scheduled job should check now. A last check of 0 means never.
     * A last check in the future (the stick's clock was wrong at the time) counts as due.
     */
    public static boolean isDue(String saved, long lastCheckMillis, long nowMillis) {
        String freq = normalise(saved);
        if (OFF.equals(freq)) return false;
        if (lastCheckMillis <= 0 || lastCheckMillis > nowMillis) return true;
        long interval = DAILY.equals(freq) ? DAY_MS : MONTHLY.equals(freq) ? 30 * DAY_MS : 7 * DAY_MS;
        return nowMillis - lastCheckMillis >= interval;
    }
}
