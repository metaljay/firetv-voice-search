import com.metaljay.stremiovoice.QueryCleaner;

/** Plain-Java test runner: prints one line per case, exits non-zero if any fail. */
public class QueryCleanerTest {

    private static int failures = 0;

    private static void check(String spoken, String expected) {
        String actual = QueryCleaner.clean(spoken);
        boolean ok = expected.equals(actual);
        if (!ok) failures++;
        System.out.println((ok ? "PASS " : "FAIL ") + "\"" + spoken + "\" -> \"" + actual + "\""
                + (ok ? "" : " (expected \"" + expected + "\")"));
    }

    private static void tivimate(String spoken, String expected) {
        String actual = QueryCleaner.clean(spoken, true);
        boolean ok = expected.equals(actual);
        if (!ok) failures++;
        System.out.println((ok ? "PASS " : "FAIL ") + "TiviMate: \"" + spoken + "\" -> \"" + actual + "\""
                + (ok ? "" : " (expected \"" + expected + "\")"));
    }

    private static void channel(String spoken, boolean expected) {
        boolean actual = QueryCleaner.endsWithChannel(spoken);
        boolean ok = actual == expected;
        if (!ok) failures++;
        System.out.println((ok ? "PASS " : "FAIL ") + "ends with \"channel\"? \"" + spoken + "\" -> " + actual);
    }

    private static void settings(String spoken, boolean expected) {
        boolean actual = QueryCleaner.isSettingsRequest(QueryCleaner.clean(spoken));
        boolean ok = actual == expected;
        if (!ok) failures++;
        System.out.println((ok ? "PASS " : "FAIL ") + "settings? \"" + spoken + "\" -> " + actual);
    }

    private static void alexa(String spoken, boolean expected) {
        boolean actual = QueryCleaner.isAlexaRequest(spoken);
        boolean ok = actual == expected;
        if (!ok) failures++;
        System.out.println((ok ? "PASS " : "FAIL ") + "Alexa request? \"" + spoken + "\" -> " + actual);
    }

    public static void main(String[] args) {
        // The cases from the brief.
        check("play Peppa Pig", "peppa pig");
        check("Play peppa pig.", "peppa pig");
        check("watch Frozen on Stremio", "frozen");
        check("search for paw patrol please", "paw patrol");
        check("show me Bluey", "bluey");
        check("Bluey", "bluey");
        check("Alexa play Bluey", "bluey");
        check("Playmobil the movie", "playmobil the movie");
        check("Finding Nemo", "finding nemo");
        check("play", "");
        check("", "");

        // Extra safety cases.
        check(null, "");
        check("   ", "");
        check("Alexa, play Bluey!", "bluey");
        check("can you please play Paw Patrol in Stremio", "paw patrol");
        check("I want to watch Inception", "inception");
        check("put on the lion king", "the lion king");
        check("look for Toy Story", "toy story");
        check("search Frozen", "frozen");
        check("find Finding Nemo", "finding nemo");
        check("play  Peppa   Pig  ", "peppa pig");
        check("please", "");
        check("on stremio", "");

        // Nuvio (2.0).
        check("Frozen on Nuvio", "frozen");
        check("play Bluey in Nuvio please", "bluey");
        check("on nuvio", "");

        // WuPlay (2.2), including likely speech-to-text spellings.
        check("Frozen on WuPlay", "frozen");
        check("play Bluey in wu play please", "bluey");
        check("Moana on woo play", "moana");
        check("on wuplay", "");

        // TiviMate (2.4), including likely speech-to-text spellings.
        check("Sky News on TiviMate", "sky news");
        check("play Moana in tivimate please", "moana");
        check("Sky News on tivi mate", "sky news");
        check("BBC One on tv mate", "bbc one");
        check("Bluey in tivo mate", "bluey");
        check("on tivimate", "");
        // The trailing "channel" is dropped for TiviMate only.
        tivimate("sky news channel", "sky news");
        tivimate("play Sky News channel on TiviMate", "sky news");
        tivimate("channel 4", "channel 4");
        tivimate("channel", "channel");
        tivimate("the history channel", "the history");
        tivimate("Bluey", "bluey");
        check("sky news channel", "sky news channel");
        check("weather channel", "weather channel");
        check("play the weather channel on Stremio", "the weather channel");
        check("channel 4", "channel 4");

        // Whether the TiviMate trim dropped a trailing "channel" (the listener wants the live channel).
        channel("bluey channel", true);
        channel("Sky News channel", true);
        channel("play Sky News channel on TiviMate", true);
        channel("sky news channel please", true);
        channel("bluey", false);
        channel("channel 4", false);
        channel("channel", false);
        channel("the channel hopper", false);
        channel("", false);
        channel(null, false);

        // Spoken settings (2.0): checked on the cleaned words, as the service does.
        settings("Voice Search settings", true);
        settings("Alexa, open voice search settings please", true);
        settings("voice settings", true);
        settings("Voice search setup", true);
        settings("play voice search settings", true);
        settings("Bluey", false);
        settings("voice search", false);
        settings("The Voice", false);
        settings("", false);

        // 2.5: every app's name is dropped from the words, whichever app it is.
        check("bluey on smart tube", "bluey");
        check("Peppa Pig on SmartTube please", "peppa pig");
        check("play bluey on youtube", "bluey");
        check("bluey on you tube", "bluey");
        check("bluey on netflix", "bluey");
        check("play Bluey on Disney Plus", "bluey");
        check("bluey on bbc iplayer", "bluey");
        check("bluey on all 4", "bluey");
        check("bluey on my 5", "bluey");
        check("bluey on prime video", "bluey");
        check("bluey on amazon", "bluey");
        check("once upon a time in hollywood", "once upon a time in hollywood");
        check("on netflix", "");
        tivimate("sky news channel on tivi mate", "sky news");
        tivimate("bluey on smart tube", "bluey");

        // 2.5: a bare "Alexa" hands the next press to Alexa.
        alexa("Alexa", true);
        alexa("alexa.", true);
        alexa("hey alexa", true);
        alexa("Hey, Alexa!", true);
        alexa("OK Alexa", true);
        alexa("  ALEXA  ", true);
        alexa("alexa play bluey", false);
        alexa("alexa what's the weather", false);
        alexa("bluey", false);
        alexa("alexandra", false);
        alexa("", false);
        alexa(null, false);

        if (failures > 0) {
            System.out.println(failures + " test(s) FAILED");
            System.exit(1);
        }
        System.out.println("All tests passed");
    }
}
