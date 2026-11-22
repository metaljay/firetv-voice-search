package com.metaljay.stremiovoice;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Builds the text of a problem report: the title, the body (device facts, an optional note, the
 * recent logs) and the trimmed link a phone opens from the QR code. Pure Java, no Android
 * classes, so it runs in the plain-Java unit tests.
 */
public final class ReportBuilder {

    private static final int TITLE_MAX = 120;
    private static final String CUT_NOTE = "[... earlier lines cut ...]\n";
    /** Room kept for the headings and the cut notes when sharing the body's budget between the logs. */
    private static final int OVERHEAD = 300;

    private ReportBuilder() {}

    /** "Report: 2.5 on AFTKRT / Fire OS 8.1.8.5 / Stremio". */
    public static String title(String appVersion, String model, String osVersion, String defaultApp) {
        String t = "Report: " + known(appVersion) + " on " + known(model) + " / "
                + known(shortOs(osVersion)) + " / " + known(defaultApp);
        return t.length() <= TITLE_MAX ? t : t.substring(0, TITLE_MAX);
    }

    /** "Fire OS 8.1.8.5 (RS8185/3891)" becomes "Fire OS 8.1.8.5". */
    public static String shortOs(String raw) {
        if (raw == null) return "";
        int paren = raw.indexOf(" (");
        return (paren > 0 ? raw.substring(0, paren) : raw).trim();
    }

    private static String known(String s) {
        if (s == null) return "unknown";
        String t = s.replaceAll("\\s+", " ").trim();
        return t.isEmpty() ? "unknown" : t;
    }

    /** Markdown lines "- **key:** value" from key, value, key, value, ...; an empty value reads "(not known)". */
    public static String facts(String... keyValues) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            String v = keyValues[i + 1];
            sb.append("- **").append(keyValues[i]).append(":** ")
              .append(v == null || v.trim().isEmpty() ? "(not known)" : v.replaceAll("\\s+", " ").trim())
              .append('\n');
        }
        return sb.toString();
    }

    /**
     * The issue body. The facts are the app's own and only have the secret values taken out (they
     * hold versions such as 8.1.8.5, which the address patterns would mistake for an address); the
     * note and both logs are fully redacted. Longer than maxChars: the oldest log lines go first.
     */
    public static String body(String facts, String note, String appLog, String moduleLog,
                              String[] secrets, int maxChars) {
        String n = Redactor.redact(note == null ? "" : note.replaceAll("\\s+", " ").trim(), secrets);
        if (n.length() > 500) n = n.substring(0, 500);
        if (n.isEmpty()) n = "(not given)";
        String head = "## What went wrong\n" + n + "\n\n## Device and app\n"
                + Redactor.removeSecrets(facts, secrets) + "\n";
        String appHeading = "## Voice Search app log (newest lines)\n";
        String moduleHeading = "## Add-on script log (newest lines)\n";

        String app = Redactor.redact(appLog, secrets);
        String module = Redactor.redact(moduleLog, secrets);
        int remaining = maxChars - head.length() - appHeading.length() - moduleHeading.length() - OVERHEAD;
        if (remaining < 0) remaining = 0;
        String moduleCut = trimTail(module, remaining / 3);
        String appCut = trimTail(app, remaining - moduleCut.length());

        StringBuilder sb = new StringBuilder(head);
        // The app's log goes last: a trimmed link keeps the end of the body, and those are the newest lines.
        sb.append(moduleHeading).append(block(moduleCut, moduleCut.length() < module.length()));
        sb.append(appHeading).append(block(appCut, appCut.length() < app.length()));
        return sb.toString();
    }

    private static String block(String log, boolean cut) {
        String shown = log.trim().isEmpty() ? "(none)\n" : (cut ? CUT_NOTE : "") + (log.endsWith("\n") ? log : log + "\n");
        return "```\n" + shown + "```\n\n";
    }

    /** The last lines of text that fit in max characters (whole lines only). */
    public static String trimTail(String text, int max) {
        if (text == null) return "";
        if (text.length() <= max) return text;
        if (max <= 0) return "";
        String s = text.substring(text.length() - max);
        int nl = s.indexOf('\n');
        return nl >= 0 ? s.substring(nl + 1) : "";
    }

    /** "github.com/OWNER/repo/issues/new", to print beside the QR code. */
    public static String shortAddress(String repo) {
        return "github.com/" + repo + "/issues/new";
    }

    /**
     * A link that opens GitHub's new-issue page with the title and a trimmed body filled in, no more
     * than maxChars long (a QR code can't hold much more than a thousand-odd characters). Keeps the
     * facts at the top and the newest log lines at the end.
     */
    public static String fallbackUrl(String repo, String title, String body, int maxChars) {
        String base = "https://github.com/" + repo + "/issues/new?title=" + enc(title) + "&body=";
        int budget = Math.max(0, maxChars - base.length());
        if (enc(body).length() <= budget) return base + enc(body);

        int fence = body.indexOf("```");
        String head = (fence > 0 ? body.substring(0, fence) : "").trim();
        while (!head.isEmpty() && enc(head).length() > budget / 2) {
            int nl = head.lastIndexOf('\n');
            head = nl > 0 ? head.substring(0, nl) : head.substring(0, head.length() / 2);
        }
        String glue = "\n[... trimmed ...]\n";
        int lo = 0, hi = body.length();
        while (lo < hi) { // the most tail text that still fits
            int mid = (lo + hi + 1) >>> 1;
            if (enc(head + glue + trimTail(body, mid)).length() <= budget) lo = mid; else hi = mid - 1;
        }
        return base + enc(head + glue + trimTail(body, lo));
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e); // UTF-8 is always there
        }
    }
}
