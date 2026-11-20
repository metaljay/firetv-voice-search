package com.metaljay.stremiovoice;

/**
 * Turns a GitHub release's notes into the short plain summary the TV shows
 * before the user taps Install: the first paragraph (headings skipped), with
 * Markdown marks removed, cut at about 300 characters. Notes that start with
 * "Urgent:" mean an urgent fix. Plain Java, unit-tested on the Mac.
 */
public final class ReleaseNotes {

    static final int MAX_CHARS = 300;
    private static final String URGENT = "urgent:";

    private ReleaseNotes() {}

    /** The summary to show; empty when the notes have no text. */
    public static String summary(String body) {
        String text = firstParagraph(body);
        if (text.toLowerCase().startsWith(URGENT)) {
            text = text.substring(URGENT.length()).trim();
            if (!text.isEmpty()) text = Character.toUpperCase(text.charAt(0)) + text.substring(1);
        }
        if (text.length() > MAX_CHARS) {
            int cut = text.lastIndexOf(' ', MAX_CHARS);
            if (cut < MAX_CHARS / 2) cut = MAX_CHARS;
            text = text.substring(0, cut).trim() + "…";
        }
        return text;
    }

    /** True when the notes start with "Urgent:" (after any heading). */
    public static boolean isUrgent(String body) {
        return firstParagraph(body).toLowerCase().startsWith(URGENT);
    }

    private static String firstParagraph(String body) {
        if (body == null) return "";
        StringBuilder out = new StringBuilder();
        for (String raw : body.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            String line = raw.trim();
            boolean heading = line.startsWith("#");
            if (line.isEmpty() || heading) {
                if (out.length() > 0) break; // the paragraph has ended
                continue;                    // still looking for it
            }
            line = stripMarks(line);
            if (line.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(line);
        }
        return out.toString().trim();
    }

    private static String stripMarks(String line) {
        String s = line;
        if (s.startsWith("- ") || s.startsWith("* ") || s.startsWith("+ ")) s = s.substring(2);
        return s.replace("**", "").replace("__", "").replace("`", "").trim();
    }
}
