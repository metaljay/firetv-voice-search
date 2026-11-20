import com.metaljay.stremiovoice.ReleaseNotes;

/** Plain-Java test runner: prints one line per case, exits non-zero if any fail. */
public class ReleaseNotesTest {

    private static int failures = 0;

    private static void summary(String label, String body, String expected) {
        String actual = ReleaseNotes.summary(body);
        boolean ok = expected.equals(actual);
        if (!ok) failures++;
        System.out.println((ok ? "PASS " : "FAIL ") + label + " -> \"" + actual + "\""
                + (ok ? "" : " (expected \"" + expected + "\")"));
    }

    private static void urgent(String label, String body, boolean expected) {
        boolean actual = ReleaseNotes.isUrgent(body);
        boolean ok = actual == expected;
        if (!ok) failures++;
        System.out.println((ok ? "PASS " : "FAIL ") + label + " -> " + actual);
    }

    public static void main(String[] args) {
        summary("first paragraph only",
                "Updates now install from inside Voice Search.\n\nMore detail here.\n\n- a\n- b",
                "Updates now install from inside Voice Search.");
        summary("windows line endings",
                "First paragraph, two\r\nlines.\r\n\r\nSecond.",
                "First paragraph, two lines.");
        summary("heading lines are skipped",
                "## What's new\n\nVoice Search can update itself.\n\nOther things.",
                "Voice Search can update itself.");
        summary("bold and code marks removed",
                "**Faster** search with `Nuvio`.",
                "Faster search with Nuvio.");
        summary("bullet marker removed",
                "- Fixes the listening bar.\n- Another.",
                "Fixes the listening bar. Another.");
        summary("empty notes", "", "");
        summary("null notes", null, "");
        summary("only a heading", "# Release 2.3", "");

        // Long paragraphs are cut at a word, about 300 characters, with an ellipsis.
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 80; i++) longText.append("word").append(i).append(' ');
        String cut = ReleaseNotes.summary(longText.toString());
        boolean ok = cut.length() <= 301 && cut.endsWith("…") && !cut.endsWith(" …")
                && cut.startsWith("word0 word1 ");
        if (!ok) failures++;
        System.out.println((ok ? "PASS " : "FAIL ") + "long text is cut at a word (" + cut.length() + " chars)");

        // Urgent: the notes start with "Urgent:"; the summary then drops that word.
        urgent("urgent at the start", "Urgent: fixes a crash at boot.", true);
        urgent("urgent, other case", "urgent: fixes a crash.", true);
        urgent("urgent after a heading", "# 2.3.2\n\nUrgent: fixes a crash.", true);
        urgent("not urgent", "Fixes a crash. Not urgent.", false);
        urgent("urgent in the middle", "A fix. Urgent: no.", false);
        urgent("null notes", null, false);
        summary("urgent word is dropped from the summary",
                "Urgent: fixes a crash at boot.\n\nMore.",
                "Fixes a crash at boot.");

        System.out.println(failures == 0 ? "All release-note cases pass." : failures + " release-note case(s) FAILED.");
        System.exit(failures == 0 ? 0 : 1);
    }
}
