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

        if (failures > 0) {
            System.out.println(failures + " test(s) FAILED");
            System.exit(1);
        }
        System.out.println("All tests passed");
    }
}
