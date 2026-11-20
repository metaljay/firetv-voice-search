import com.metaljay.stremiovoice.Versions;

/** Plain-Java test runner: prints one line per case, exits non-zero if any fail. */
public class VersionsTest {

    private static int failures = 0;

    private static void newer(String candidate, String installed, boolean expected) {
        boolean actual = Versions.isNewer(candidate, installed);
        boolean ok = actual == expected;
        if (!ok) failures++;
        System.out.println((ok ? "PASS " : "FAIL ") + "\"" + candidate + "\" newer than \"" + installed
                + "\" -> " + actual);
    }

    private static void clean(String tag, String expected) {
        String actual = Versions.clean(tag);
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        if (!ok) failures++;
        System.out.println((ok ? "PASS " : "FAIL ") + "clean \"" + tag + "\" -> " + actual);
    }

    public static void main(String[] args) {
        newer("v2.3.1", "2.3", true);       // a release tag against the installed name
        newer("2.3", "2.3", false);          // the same version
        newer("2.3.0", "2.3", false);        // a missing part counts as zero
        newer("2.3", "2.3.1", false);        // older
        newer("2.10", "2.9", true);          // numbers, not text
        newer("3.0", "2.99.99", true);
        newer("V2.4", "2.3", true);          // upper-case v
        newer("2.3.1-rc1", "2.3", false);    // a pre-release tag is never offered
        newer("latest", "2.3", false);       // not a version
        newer("", "2.3", false);
        newer(null, "2.3", false);
        newer("2.4", "garbage", false);      // can't tell what is installed: offer nothing

        clean("v2.3.1", "2.3.1");
        clean(" 2.3 ", "2.3");
        clean("2.3.1-rc1", null);
        clean("nightly", null);
        clean(null, null);

        System.out.println(failures == 0 ? "All version cases pass." : failures + " version case(s) FAILED.");
        System.exit(failures == 0 ? 0 : 1);
    }
}
