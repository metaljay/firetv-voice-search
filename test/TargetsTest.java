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
        String tivimate = Targets.TIVIMATE.packageName;
        check("saved TiviMate, all four installed", Targets.TIVIMATE,
                Targets.choose("tivimate", installed(stremio, nuvio, wuplay, tivimate)));
        check("nothing saved, only TiviMate", Targets.TIVIMATE,
                Targets.choose(null, installed(tivimate)));
        check("saved TiviMate, TiviMate removed", Targets.NUVIO,
                Targets.choose("tivimate", installed(nuvio)));
        check("nothing saved, Nuvio and TiviMate (Nuvio first)", Targets.NUVIO,
                Targets.choose(null, installed(nuvio, tivimate)));
        boolean typed = Targets.TIVIMATE.mode == Targets.Mode.TYPE_IN_APP
                && "ar.tvplayer.tv".equals(tivimate) && "tivimate".equals(Targets.TIVIMATE.id);
        if (!typed) failures++;
        System.out.println((typed ? "PASS " : "FAIL ") + "TiviMate entry (type-in mode)");
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

        // ----- 2.5: saying the app's name -----
        check("named: play bluey on stremio", Targets.STREMIO, Targets.named("play bluey on stremio"));
        check("named: bluey on netflix (no-search app)", Targets.NETFLIX, Targets.named("bluey on netflix"));
        check("named: once upon a time in hollywood (no app)", null, Targets.named("once upon a time in hollywood"));
        check("named: bluey (no app)", null, Targets.named("bluey"));
        check("named: sky news channel on tivi mate", Targets.TIVIMATE, Targets.named("sky news channel on tivi mate"));
        check("named: bluey on smart tube", Targets.SMARTTUBE, Targets.named("bluey on smart tube"));
        check("named: peppa pig on SmartTube please", Targets.SMARTTUBE, Targets.named("Peppa Pig on SmartTube, please"));
        check("named: bluey in nuvio", Targets.NUVIO, Targets.named("bluey in nuvio"));
        check("named: moana on woo play", Targets.WUPLAY, Targets.named("moana on woo play"));
        check("named: bluey on youtube", Targets.YOUTUBE, Targets.named("bluey on youtube"));
        check("named: bluey on you tube", Targets.YOUTUBE, Targets.named("bluey on you tube"));
        check("named: bluey on disney plus", Targets.DISNEY, Targets.named("bluey on disney plus"));
        check("named: bluey on bbc iplayer", Targets.IPLAYER, Targets.named("bluey on bbc iplayer"));
        check("named: bluey on iplayer", Targets.IPLAYER, Targets.named("bluey on iplayer"));
        check("named: bluey on all 4", Targets.CHANNEL4, Targets.named("bluey on all 4"));
        check("named: bluey on channel 4", Targets.CHANNEL4, Targets.named("bluey on channel 4"));
        check("named: bluey on my 5", Targets.MY5, Targets.named("bluey on my 5"));
        check("named: bluey on channel five", Targets.MY5, Targets.named("bluey on channel five"));
        check("named: bluey on prime video", Targets.PRIME, Targets.named("bluey on prime video"));
        check("named: bluey on amazon", Targets.PRIME, Targets.named("bluey on amazon"));
        check("named: watch channel 4 (no \"on\")", null, Targets.named("watch channel 4"));
        check("named: the disney channel (no \"on\")", null, Targets.named("the disney channel"));
        check("named: empty", null, Targets.named(""));
        check("named: null", null, Targets.named(null));

        boolean order = Arrays.equals(Targets.ALL,
                new Target[] {Targets.STREMIO, Targets.NUVIO, Targets.WUPLAY, Targets.TIVIMATE, Targets.SMARTTUBE, Targets.AMAZON});
        if (!order) failures++;
        System.out.println((order ? "PASS " : "FAIL ") + "ALL order = settings order, YouTube not in it yet");

        boolean smart = Targets.SMARTTUBE.mode == Targets.Mode.APP_SEARCH
                && "org.smarttube.stable".equals(Targets.SMARTTUBE.packageName)
                && "https://www.youtube.com/results?search_query=".equals(Targets.SMARTTUBE.searchLink)
                && Targets.YOUTUBE.mode == Targets.Mode.APP_SEARCH
                && "com.amazon.firetv.youtube".equals(Targets.YOUTUBE.packageName);
        if (!smart) failures++;
        System.out.println((smart ? "PASS " : "FAIL ") + "SmartTube and YouTube entries");

        boolean open = true;
        for (Target t : Targets.NO_SEARCH) {
            open &= t.mode == Targets.Mode.OPEN_ONLY && t.packageName != null && t.spokenNames.length > 0;
        }
        if (!open) failures++;
        System.out.println((open ? "PASS " : "FAIL ") + "every no-search app opens only, with a package and a spoken name");

        // No spoken name may belong to two apps, or "on X" would be ambiguous.
        java.util.Set<String> seen = new HashSet<>();
        boolean unique = true;
        java.util.List<Target> everyone = new java.util.ArrayList<>(Arrays.asList(Targets.ALL));
        everyone.add(Targets.YOUTUBE);
        everyone.addAll(Arrays.asList(Targets.NO_SEARCH));
        for (Target t : everyone) {
            for (String n : t.spokenNames) {
                if (!seen.add(n)) { unique = false; System.out.println("duplicate spoken name: " + n); }
                if (!n.equals(n.toLowerCase(java.util.Locale.ROOT))) unique = false;
            }
        }
        if (!unique) failures++;
        System.out.println((unique ? "PASS " : "FAIL ") + "spoken names are unique and lower case");

        check("saved SmartTube, SmartTube installed", Targets.SMARTTUBE,
                Targets.choose("smarttube", installed(stremio, Targets.SMARTTUBE.packageName)));
        check("nothing saved, only SmartTube", Targets.SMARTTUBE,
                Targets.choose(null, installed(Targets.SMARTTUBE.packageName)));
        check("nothing saved, Stremio and SmartTube (Stremio first)", Targets.STREMIO,
                Targets.choose(null, installed(stremio, Targets.SMARTTUBE.packageName)));

        String launcher = Targets.AMAZON.packageName;
        check("saved Amazon search, launcher present", Targets.AMAZON,
                Targets.choose("amazon", installed(stremio, launcher)));
        check("nothing saved, only the launcher (Amazon is never the automatic pick)", null,
                Targets.choose(null, installed(launcher)));
        check("nothing saved, Nuvio and the launcher", Targets.NUVIO,
                Targets.choose(null, installed(nuvio, launcher)));
        check("named: bluey on amazon search", Targets.AMAZON, Targets.named("bluey on amazon search"));
        boolean amz = Targets.AMAZON.mode == Targets.Mode.AMAZON_SEARCH
                && "com.amazon.tv.launcher".equals(launcher) && "amazon".equals(Targets.AMAZON.id);
        if (!amz) failures++;
        System.out.println((amz ? "PASS " : "FAIL ") + "Amazon search entry");

        System.out.println(failures == 0 ? "All target cases pass." : failures + " target case(s) FAILED.");
        System.exit(failures == 0 ? 0 : 1);
    }
}
