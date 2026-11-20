import com.metaljay.stremiovoice.UpdateSchedule;

/** Plain-Java test runner: prints one line per case, exits non-zero if any fail. */
public class UpdateScheduleTest {

    private static final long HOUR = 3600L * 1000;
    private static final long DAY = 24 * HOUR;
    // A time that is not near zero, as on a stick with a correct clock.
    private static final long NOW = 1_800_000_000_000L;

    private static int failures = 0;

    private static void due(String label, String freq, long last, long now, boolean expected) {
        boolean actual = UpdateSchedule.isDue(freq, last, now);
        boolean ok = actual == expected;
        if (!ok) failures++;
        System.out.println((ok ? "PASS " : "FAIL ") + label + " -> " + actual);
    }

    private static void same(String label, String expected, String actual) {
        boolean ok = expected.equals(actual);
        if (!ok) failures++;
        System.out.println((ok ? "PASS " : "FAIL ") + label + " -> " + actual);
    }

    public static void main(String[] args) {
        // Never checked: every setting except Off checks at once.
        due("never checked, weekly", "weekly", 0, NOW, true);
        due("never checked, daily", "daily", 0, NOW, true);
        due("never checked, monthly", "monthly", 0, NOW, true);

        // Off never checks, however long it has been.
        due("off, never checked", "off", 0, NOW, false);
        due("off, a year ago", "off", NOW - 365 * DAY, NOW, false);

        due("daily, 2 hours ago", "daily", NOW - 2 * HOUR, NOW, false);
        due("daily, 25 hours ago", "daily", NOW - 25 * HOUR, NOW, true);
        due("weekly, 6 days ago", "weekly", NOW - 6 * DAY, NOW, false);
        due("weekly, 8 days ago", "weekly", NOW - 8 * DAY, NOW, true);
        due("monthly, 20 days ago", "monthly", NOW - 20 * DAY, NOW, false);
        due("monthly, 31 days ago", "monthly", NOW - 31 * DAY, NOW, true);

        // The stick's clock can be wrong early in a boot; a "last check" in the future
        // must not block checks for ever.
        due("last check is in the future", "weekly", NOW + 30 * DAY, NOW, true);

        // Nothing saved yet, or an unknown value: Weekly, the default.
        due("nothing saved, 2 days ago", null, NOW - 2 * DAY, NOW, false);
        due("nothing saved, 8 days ago", null, NOW - 8 * DAY, NOW, true);
        due("unknown value, 8 days ago", "hourly", NOW - 8 * DAY, NOW, true);

        same("default is weekly", "weekly", UpdateSchedule.normalise(null));
        same("daily is kept", "daily", UpdateSchedule.normalise("daily"));
        same("unknown becomes weekly", "weekly", UpdateSchedule.normalise("hourly"));
        same("off is kept", "off", UpdateSchedule.normalise("off"));

        System.out.println(failures == 0 ? "All schedule cases pass." : failures + " schedule case(s) FAILED.");
        System.exit(failures == 0 ? 0 : 1);
    }
}
