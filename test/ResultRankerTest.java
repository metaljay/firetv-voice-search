import com.metaljay.stremiovoice.ResultRanker;
import com.metaljay.stremiovoice.ResultRanker.Item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Plain-Java test runner: prints one line per case, exits non-zero if any fail.
 * The fixtures are the catalogue's real top three answers, taken on 18 Nov 2026.
 */
public class ResultRankerTest {

    private static int failures = 0;

    private static List<Item> list(String type, String... nameYearId) {
        List<Item> out = new ArrayList<>();
        for (int i = 0; i < nameYearId.length; i += 3) {
            out.add(new Item(nameYearId[i + 2], type, nameYearId[i], nameYearId[i + 1], "", i / 3));
        }
        return out;
    }

    private static void check(String label, boolean ok, List<Item> got) {
        if (!ok) failures++;
        System.out.println((ok ? "PASS " : "FAIL ") + label + (ok ? "" : "  got " + got));
    }

    private static int indexOf(List<Item> items, String id) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id.equals(id)) return i;
        }
        return -1;
    }

    public static void main(String[] args) {
        List<Item> r;

        r = ResultRanker.merge("bluey",
                list("movie", "Bluey's Big Play", "2025", "tt38133965",
                        "Bluey at the Cinema: Playdates with Friends", "2026", "tt41568963",
                        "Bluey At The Cinema: Let's Play Chef Collection", "2025", "tt36868516"),
                list("series", "Bluey", "2018", "tt7678620",
                        "Bluey Minisodes", "2024", "tt32328599",
                        "Bluey", "1976", "tt0073966"));
        check("bluey -> Bluey (2018, TV) first", r.get(0).id.equals("tt7678620"), r);

        r = ResultRanker.merge("frozen",
                list("movie", "Frozen", "2013", "tt2294629",
                        "Frozen", "2010", "tt1323045",
                        "Frozen II", "2019", "tt4520988"),
                list("series", "Frozen Planet", "2011", "tt2092588",
                        "Frozen Planet II", "2022", "tt9805678",
                        "Frozen", "2022", "tt23791244"));
        check("frozen -> Frozen (2013) first", r.get(0).id.equals("tt2294629"), r);
        check("frozen -> Frozen II still in the row", indexOf(r, "tt4520988") >= 0, r);

        r = ResultRanker.merge("spongebob",
                list("movie", "The SpongeBob SquarePants Movie", "2004", "tt0345950",
                        "The SpongeBob Movie: Search for SquarePants", "2025", "tt23572848",
                        "The SpongeBob Movie: Sponge Out of Water", "2015", "tt2279373"),
                list("series", "SpongeBob SquarePants", "1999", "tt0206512",
                        "Kamp Koral: SpongeBob's Under Years", "2021", "tt10436284",
                        "SpongeBob DocuPants", "2020", "tt12762562"));
        check("spongebob -> SpongeBob SquarePants (TV) first", r.get(0).id.equals("tt0206512"), r);

        r = ResultRanker.merge("moana",
                list("movie", "Moana", "2026", "tt27419466",
                        "Moana", "2016", "tt3521164",
                        "Moana 2", "2024", "tt13622970"),
                list("series", "Moana", "2009", "tt1465102",
                        "Moana Warriors", "2026", "tt41343164",
                        "Moana's Place", "1984", "tt5483418"));
        int m2016 = indexOf(r, "tt3521164");
        int m2026 = indexOf(r, "tt27419466");
        check("moana -> Moana 2016 and 2026 both in the first three",
                m2016 >= 0 && m2016 < 3 && m2026 >= 0 && m2026 < 3, r);

        r = ResultRanker.merge("toy story",
                list("movie", "Toy Story", "1995", "tt0114709",
                        "Toy Story 5", "2026", "tt29355505",
                        "Toy Story 3", "2010", "tt0435761"),
                list("series", "Toy Story Treats", "1996", "tt1716710",
                        "Toy World Saga: The Real Story", "2023", "tt32677145",
                        "What's Your Toy Story?", "", "tt24852274"));
        check("toy story -> Toy Story (1995) first", r.get(0).id.equals("tt0114709"), r);

        r = ResultRanker.merge("paw patrol",
                list("movie", "PAW Patrol: The Dino Movie", "2026", "tt29356163",
                        "PAW Patrol: The Movie", "2021", "tt11832046",
                        "PAW Patrol: The Mighty Movie", "2023", "tt15837338"),
                list("series", "PAW Patrol", "2013", "tt3121722",
                        "PAW Patrol", "2017", "tt8467618",
                        "Paw Patrol: Rocky's Garage", "2024", "tt32874804"));
        check("paw patrol -> PAW Patrol (2013, TV) first", r.get(0).id.equals("tt3121722"), r);

        // Edge cases.
        r = ResultRanker.merge("xqzv", Collections.<Item>emptyList(), Collections.<Item>emptyList());
        check("no results -> empty", r.isEmpty(), r);

        r = ResultRanker.merge("bluey", null, null);
        check("missing lists -> empty", r.isEmpty(), r);

        List<Item> many = new ArrayList<>();
        for (int i = 0; i < 15; i++) many.add(new Item("tt" + i, "movie", "Film " + i, "", "", i));
        r = ResultRanker.merge("film", many, null);
        check("at most " + ResultRanker.MAX_RESULTS + " results", r.size() == ResultRanker.MAX_RESULTS, r);

        List<Item> broken = new ArrayList<>();
        broken.add(new Item("", "movie", "No id", "", "", 0));
        broken.add(new Item("tt1", "movie", "", "", "", 1));
        broken.add(new Item("tt2", "movie", "Good", "", "", 2));
        broken.add(new Item("kitsu:123", "movie", "Good too", "", "", 3));
        r = ResultRanker.merge("good", broken, null);
        check("entries without an IMDb id or a name are dropped", r.size() == 1 && r.get(0).id.equals("tt2"), r);

        System.out.println(failures == 0 ? "All ranker cases pass." : failures + " ranker case(s) FAILED.");
        System.exit(failures == 0 ? 0 : 1);
    }
}
