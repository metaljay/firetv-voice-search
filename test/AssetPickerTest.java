import com.metaljay.stremiovoice.AssetPicker;

import java.util.Arrays;
import java.util.Collections;

/** Plain-Java test runner: prints one line per case, exits non-zero if any fail. */
public class AssetPickerTest {

    private static int failures = 0;

    private static void check(String label, String expected, String... names) {
        String actual = AssetPicker.pick(Arrays.asList(names));
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        if (!ok) failures++;
        System.out.println((ok ? "PASS " : "FAIL ") + label + " -> " + actual
                + (ok ? "" : " (expected " + expected + ")"));
    }

    public static void main(String[] args) {
        check("both names: the new one", "voice_search.zip", "stremio_voice.zip", "voice_search.zip");
        check("both names, other order", "voice_search.zip", "voice_search.zip", "stremio_voice.zip");
        check("only the old name", "stremio_voice.zip", "notes.txt", "stremio_voice.zip");
        check("only the new name", "voice_search.zip", "voice_search.zip");
        check("neither", null, "notes.txt", "source.zip");
        check("no files", null);
        boolean ok = AssetPicker.pick(Collections.<String>emptyList()) == null && AssetPicker.pick(null) == null;
        if (!ok) failures++;
        System.out.println((ok ? "PASS " : "FAIL ") + "empty and null lists -> none");

        System.out.println(failures == 0 ? "All asset cases pass." : failures + " asset case(s) FAILED.");
        System.exit(failures == 0 ? 0 : 1);
    }
}
