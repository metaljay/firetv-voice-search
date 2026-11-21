import com.metaljay.stremiovoice.ResultChooser;
import com.metaljay.stremiovoice.ResultChooser.Card;
import com.metaljay.stremiovoice.ResultChooser.Header;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Plain-Java test runner: prints one line per case, exits non-zero if any fail. */
public class ResultChooserTest {

    private static int failures = 0;

    private static Card card(String row, String title, int top, int left) {
        return new Card(row, title, top, left);
    }

    /** Checks that choose() returns the card with this title (or -1 for null). */
    private static void check(String label, List<Card> cards, String words, boolean wantChannel, String expectedTitle) {
        int i = ResultChooser.choose(cards, words, wantChannel);
        String actual = i < 0 ? null : cards.get(i).title;
        boolean ok = expectedTitle == null ? i < 0 : expectedTitle.equals(actual);
        if (!ok) failures++;
        System.out.println((ok ? "PASS " : "FAIL ") + label + " -> " + actual
                + (ok ? "" : " (expected " + expectedTitle + ")"));
    }

    private static void checkC(String label, List<Card> cards, String words, String country, String expectedTitle) {
        int i = ResultChooser.choose(cards, words, false, country);
        String actual = i < 0 ? null : cards.get(i).title;
        boolean ok = expectedTitle.equals(actual);
        if (!ok) failures++;
        System.out.println((ok ? "PASS " : "FAIL ") + label + " -> " + actual
                + (ok ? "" : " (expected " + expectedTitle + ")"));
    }

    private static List<Card> channels(String... titles) {
        List<Card> out = new ArrayList<>();
        for (int i = 0; i < titles.length; i++) out.add(card("Channels", titles[i], 300, 50 + i * 250));
        return out;
    }

    private static void checkEq(String label, Object expected, Object actual) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        if (!ok) failures++;
        System.out.println((ok ? "PASS " : "FAIL ") + label + " -> " + actual
                + (ok ? "" : " (expected " + expected + ")"));
    }

    public static void main(String[] args) {
        // "bluey": the Channels row sits above the Shows row, and the series still wins.
        List<Card> bluey = Arrays.asList(
                card("Channels", "Bluey", 300, 50),
                card("Channels", "Kids: OnePlay", 300, 300),
                card("Shows", "Bluey (2018)", 880, 50),
                card("Shows", "Bluey Book Reads", 880, 300));
        check("bluey: series beats channel even with Channels on top", bluey, "bluey", false, "Bluey (2018)");
        check("bluey channel: the channel", bluey, "bluey", true, "Bluey");

        // "channel" with an empty Channels row falls back to films and series.
        List<Card> noChannels = Arrays.asList(
                card("Shows", "Bluey (2018)", 300, 50),
                card("Shows", "Bluey Book Reads", 300, 300));
        check("channel wanted but no Channels row", noChannels, "bluey", true, "Bluey (2018)");

        // "moana": exact matches beat "Moana 2"; between the two exact ones, TiviMate's order.
        List<Card> moana = Arrays.asList(
                card("Movies", "Moana - 2016", 300, 50),
                card("Movies", "Moana - 2026", 300, 300),
                card("Movies", "Moana 2 - 2024", 300, 550));
        check("moana: exact match, first in order", moana, "moana", false, "Moana - 2016");
        List<Card> moanaRev = Arrays.asList(
                card("Movies", "Moana 2 - 2024", 300, 50),
                card("Movies", "Moana - 2016", 300, 300));
        check("moana: exact beats starts-with even when later", moanaRev, "moana", false, "Moana - 2016");

        // Only channels: the first matching channel.
        List<Card> sky = Arrays.asList(
                card("Channels", "Sky News", 300, 50),
                card("Channels", "AU: Sky News Australia", 300, 300),
                card("Channels", "DE: Sky Sport News", 300, 550));
        check("sky news: only Channels, first match", sky, "sky news", false, "Sky News");
        List<Card> sky2 = Arrays.asList(
                card("Channels", "AR: Sky News Arabia", 300, 50),
                card("Channels", "AU: Sky News Australia", 300, 300));
        check("sky news: two starts-with matches keep TiviMate's order", sky2, "sky news", false, "AR: Sky News Arabia");
        check("sky news: nothing exact, order wins over later cards", Arrays.asList(
                card("Channels", "DE: Sky Sport News", 300, 50),
                card("Channels", "UK: Sky News HD", 300, 300)), "sky news", false, "UK: Sky News HD");

        // Prefixes and years are ignored when matching.
        check("\"UK: Sky News\" matches \"sky news\"", Arrays.asList(
                card("Channels", "UK: Sky Sports", 300, 50),
                card("Channels", "UK: Sky News", 300, 300)), "sky news", false, "UK: Sky News");
        check("\"4K: Sky News\" matches \"sky news\"", Arrays.asList(
                card("Channels", "4K: Sky Cinema", 300, 50),
                card("Channels", "4K: Sky News", 300, 300)), "sky news", false, "4K: Sky News");
        check("year in brackets ignored", Arrays.asList(
                card("Movies", "Bluey Movie (2030)", 300, 50),
                card("Movies", "Bluey (2018)", 300, 300)), "bluey", false, "Bluey (2018)");
        check("apostrophe ignored", Arrays.asList(
                card("Shows", "Schitt's Creek", 300, 50)), "schitts creek", false, "Schitt's Creek");

        // Contains beats nothing; no match at all keeps TiviMate's order.
        check("contains match", Arrays.asList(
                card("Movies", "The Moana Story", 300, 50),
                card("Movies", "Something else", 300, 300)), "moana", false, "The Moana Story");
        check("no match anywhere: first in order", Arrays.asList(
                card("Movies", "Alpha", 300, 300),
                card("Movies", "Beta", 300, 50)), "zzz", false, "Beta");

        // Films and series are compared together, top row first.
        check("Movies and Shows compared together", Arrays.asList(
                card("Movies", "Bluey Movie", 300, 50),
                card("Shows", "Bluey", 880, 50)), "bluey", false, "Bluey");
        check("Movies and Shows tie: higher row first", Arrays.asList(
                card("Movies", "Bluey", 300, 50),
                card("Shows", "Bluey", 880, 50)), "bluey", false, "Bluey");

        // Unknown headers (TiviMate in another language): the top-left card, as before.
        check("no row headers: top-left card", Arrays.asList(
                card(null, "Zweite", 880, 50),
                card(null, "Dritte", 300, 300),
                card(null, "Erste", 300, 50)), "erste", false, "Erste");
        check("no row headers: words do not matter", Arrays.asList(
                card(null, "Bluey", 880, 50),
                card(null, "Other", 300, 50)), "bluey", false, "Other");

        // Several country versions of the same channel: plain title, then the stick's country, then TiviMate's order.
        checkC("country GB: plain \"Sky News\" wins", channels("AR: Sky News Arabia", "NO: Sky News",
                "PT: Sky News", "Sky News", "UK: Sky News"), "sky news", "GB", "Sky News");
        checkC("country GB, no plain: \"UK: Sky News\" wins", channels("AR: Sky News Arabia", "NO: Sky News",
                "PT: Sky News", "UK: Sky News"), "sky news", "GB", "UK: Sky News");
        checkC("country GB, only foreign: TiviMate's order", channels("AR: Sky News Arabia", "NO: Sky News",
                "PT: Sky News"), "sky news", "GB", "NO: Sky News");
        checkC("no country: plain still wins", channels("NO: Sky News", "Sky News", "UK: Sky News"),
                "sky news", null, "Sky News");
        checkC("no country, no plain: TiviMate's order", channels("NO: Sky News", "UK: Sky News"),
                "sky news", "", "NO: Sky News");
        checkC("country GB matches the prefix EN", channels("NO: Sky News", "EN: Sky News"), "sky news", "GB", "EN: Sky News");
        checkC("country US matches only US", channels("UK: Sky News", "US: Sky News"), "sky news", "US", "US: Sky News");
        checkC("quality prefix does not lose to a foreign country", channels("NO: Sky Sports F1", "4K: Sky Sports F1"),
                "sky sports f1", "GB", "4K: Sky Sports F1");
        checkC("quality prefix does not beat the stick's country", channels("4K: Sky Sports F1", "UK: Sky Sports F1"),
                "sky sports f1", "GB", "UK: Sky Sports F1");
        checkC("HD prefix counts as neutral", channels("PT: Sky News", "HD | Sky News"), "sky news", "GB", "HD | Sky News");
        checkC("a better match still beats a better country", channels("UK: Sky News Extra", "NO: Sky News"),
                "sky news", "GB", "NO: Sky News");
        checkEq("prefix of \"UK: Sky News\"", "uk", ResultChooser.prefixOf("UK: Sky News"));
        checkEq("prefix of \"4K | Sky\"", "4k", ResultChooser.prefixOf("4K | Sky"));
        checkEq("prefix of \"Sky News\"", null, ResultChooser.prefixOf("Sky News"));
        checkEq("prefix of \"Cartoon: Network\" (too long to be a code)", null, ResultChooser.prefixOf("Cartoon: Network"));

        // No cards.
        check("no cards", new ArrayList<Card>(), "bluey", false, null);
        check("null cards", null, "bluey", false, null);

        // Which row a card is under: the nearest known header above it.
        List<Header> headers = Arrays.asList(
                new Header("Shows", 232), new Header("Channels", 820), new Header("Search history", 100));
        checkEq("row: under Shows", "Shows", ResultChooser.rowFor(headers, 310));
        checkEq("row: under Channels", "Channels", ResultChooser.rowFor(headers, 892));
        checkEq("row: above every header", null, ResultChooser.rowFor(headers, 150));
        checkEq("row: no headers", null, ResultChooser.rowFor(new ArrayList<Header>(), 300));
        checkEq("row: header in another case", "Movies", ResultChooser.rowFor(
                Arrays.asList(new Header("MOVIES", 232)), 400));
        checkEq("known row: Channels", "Channels", ResultChooser.knownRow(" channels "));
        checkEq("known row: other text", null, ResultChooser.knownRow("Search history"));

        System.out.println(failures == 0 ? "All chooser cases pass." : failures + " chooser case(s) FAILED.");
        System.exit(failures == 0 ? 0 : 1);
    }
}
