import com.metaljay.stremiovoice.Targets;
import com.metaljay.stremiovoice.Targets.Target;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Plain-Java test runner: prints one line per case, exits non-zero if any fail. */
public class TargetsTest {

    private static int failures = 0;

    private static Set<String> installed(String... packages) {
        return new HashSet<>(Arrays.asList(packages));
    }

    private static void check(String label, Target expected, Target actual) {
        boolean ok = expected == actual;
        if (!ok) failures++;
        System.out.println((ok ? "PASS " : "FAIL ") + label + " -> " + name(actual)
                + (ok ? "" : " (expected " + name(expected) + ")"));
    }

    private static String name(Target t) {
        return t == null ? "none" : t.name;
    }

    public static void main(String[] args) {
        String stremio = Targets.STREMIO.packageName;
        String nuvio = Targets.NUVIO.packageName;

        check("saved Nuvio, both installed", Targets.NUVIO,
                Targets.choose("nuvio", installed(stremio, nuvio)));
        check("saved Stremio, both installed", Targets.STREMIO,
                Targets.choose("stremio", installed(stremio, nuvio)));
        check("saved Nuvio, Nuvio removed", Targets.STREMIO,
                Targets.choose("nuvio", installed(stremio)));
        check("nothing saved, both installed (first in list)", Targets.STREMIO,
                Targets.choose(null, installed(stremio, nuvio)));
        check("nothing saved, only Nuvio", Targets.NUVIO,
                Targets.choose(null, installed(nuvio)));
        check("unknown saved id, only Nuvio", Targets.NUVIO,
                Targets.choose("kodi", installed(nuvio)));
        String wuplay = Targets.WUPLAY.packageName;
        check("saved WuPlay, all three installed", Targets.WUPLAY,
                Targets.choose("wuplay", installed(stremio, nuvio, wuplay)));
        check("nothing saved, only WuPlay", Targets.WUPLAY,
                Targets.choose(null, installed(wuplay)));
        check("saved WuPlay, WuPlay removed", Targets.NUVIO,
                Targets.choose("wuplay", installed(nuvio)));
        check("nothing installed", null,
                Targets.choose("stremio", Collections.<String>emptySet()));

        String link = Targets.titleLink(Targets.NUVIO, "series", "tt7678620");
        boolean ok = "nuvio://series/tt7678620".equals(link);
        if (!ok) failures++;
        System.out.println((ok ? "PASS " : "FAIL ") + "Nuvio title link -> " + link);

        link = Targets.titleLink(Targets.WUPLAY, "movie", "tt3521164");
        ok = "wuplay://movie/tt3521164".equals(link);
        if (!ok) failures++;
        System.out.println((ok ? "PASS " : "FAIL ") + "WuPlay title link -> " + link);

        System.out.println(failures == 0 ? "All target cases pass." : failures + " target case(s) FAILED.");
        System.exit(failures == 0 ? 0 : 1);
    }
}
