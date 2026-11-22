import com.metaljay.stremiovoice.TiviMatePrewarm;

/** Plain-Java test runner: prints one line per case, exits non-zero if any fail. */
public class TiviMatePrewarmTest {

    private static int failures = 0;

    private static void check(String label, boolean actual, boolean expected) {
        boolean ok = actual == expected;
        if (!ok) failures++;
        System.out.println((ok ? "PASS " : "FAIL ") + label + " -> " + actual);
    }

    public static void main(String[] args) {
        String[] fronts = {
            "com.amazon.tv.launcher", "com.metaljay.stremiovoice", "com.metaljay.voicesearchtile",
            "ar.tvplayer.tv", "com.netflix.ninja", "com.stremio.one", null
        };
        for (String front : fronts) {
            for (boolean playing : new boolean[] {false, true}) {
                String name = front + (playing ? " playing" : " idle");
                // Always: any screen.
                check("always, " + name, TiviMatePrewarm.shouldPrewarm("always", front, playing), true);
                // Idle (default), and any unknown or missing setting, behave the same.
                boolean safe = front != null && (front.equals("com.amazon.tv.launcher")
                        || front.equals("com.metaljay.stremiovoice") || front.equals("com.metaljay.voicesearchtile")
                        || (front.equals("ar.tvplayer.tv") && !playing));
                for (String mode : new String[] {"idle", null, "nonsense"}) {
                    check(mode + ", " + name, TiviMatePrewarm.shouldPrewarm(mode, front, playing), safe);
                }
            }
        }
        check("normalise always", "always".equals(TiviMatePrewarm.normalise("always")), true);
        check("normalise null -> idle", "idle".equals(TiviMatePrewarm.normalise(null)), true);
        check("normalise junk -> idle", "idle".equals(TiviMatePrewarm.normalise("x")), true);

        // Settle rule.
        check("just filled, nothing yet", TiviMatePrewarm.readyToChoose(100, 100, false), false);
        check("rows seen, quiet 299", TiviMatePrewarm.readyToChoose(900, 299, true), false);
        check("rows seen, quiet 300", TiviMatePrewarm.readyToChoose(900, 300, true), true);
        check("no rows, quiet 300", TiviMatePrewarm.readyToChoose(900, 300, false), false);
        check("no rows, quiet 699", TiviMatePrewarm.readyToChoose(1500, 699, false), false);
        check("no rows, quiet 700", TiviMatePrewarm.readyToChoose(1500, 700, false), true);
        check("cap at 3000, still changing", TiviMatePrewarm.readyToChoose(3000, 10, false), true);
        check("2999, still changing", TiviMatePrewarm.readyToChoose(2999, 10, false), false);
        check("rows seen but still changing", TiviMatePrewarm.readyToChoose(500, 50, true), false);

        if (failures > 0) {
            System.out.println(failures + " FAILED");
            System.exit(1);
        }
        System.out.println("TiviMatePrewarmTest: all passed");
    }
}
