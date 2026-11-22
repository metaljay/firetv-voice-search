import com.metaljay.stremiovoice.ReportBuilder;

/** Plain-Java test runner: prints one line per case, exits non-zero if any fail. */
public class ReportBuilderTest {

    private static int failures = 0;

    private static void check(String label, boolean ok, String detail) {
        if (!ok) failures++;
        System.out.println((ok ? "PASS " : "FAIL ") + label + (detail.isEmpty() ? "" : " -> " + detail));
    }

    private static String lines(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= n; i++) sb.append("11-22 10:16:").append(String.format("%02d", i % 60))
                .append(" I VoiceSearch: line number ").append(i).append('\n');
        return sb.toString();
    }

    public static void main(String[] args) {
        // Title
        String t = ReportBuilder.title("2.5", "AFTKRT", "Fire OS 8.1.8.5 (RS8185/3891)", "Stremio");
        check("title format", "Report: 2.5 on AFTKRT / Fire OS 8.1.8.5 / Stremio".equals(t), t);
        t = ReportBuilder.title(null, "", null, null);
        check("title with nothing known", "Report: unknown on unknown / unknown / unknown".equals(t), t);
        t = ReportBuilder.title("2.5", "AF\nTKRT", "Fire OS 8", "Stremio");
        check("title has no line breaks", t.indexOf('\n') < 0, t);
        t = ReportBuilder.title("2.5", new String(new char[300]).replace('\0', 'x'), "Fire OS 8", "Stremio");
        check("title is capped", t.length() <= 120, String.valueOf(t.length()));
        check("short OS keeps the version only", "Fire OS 8.1.8.5".equals(ReportBuilder.shortOs("Fire OS 8.1.8.5 (RS8185/3891)")),
                ReportBuilder.shortOs("Fire OS 8.1.8.5 (RS8185/3891)"));

        // Facts
        String f = ReportBuilder.facts("Model", "AFTKRT", "Language", "en-GB", "Empty", "", "Nothing", null);
        check("facts lines", f.contains("- **Model:** AFTKRT") && f.contains("- **Language:** en-GB")
                && f.contains("- **Empty:** (not known)") && f.contains("- **Nothing:** (not known)"), f.replace('\n', '|'));

        // Body: shape, redaction, the note, the serial
        String body = ReportBuilder.body(f, "it did nothing, mail me at a@b.com", lines(3) + "from 192.168.1.5\n",
                "module line G070VM1234567890\n", new String[] {"G070VM1234567890"}, 50000);
        check("body has the facts", body.contains("- **Model:** AFTKRT"), "");
        check("body has the note, redacted", body.contains("it did nothing, mail me at [email]"), "");
        check("body has the app log, redacted", body.contains("line number 3") && body.contains("from [ip]")
                && !body.contains("192.168"), "");
        check("body has the module log, serial removed", body.contains("module line [serial]")
                && !body.contains("G070VM1234567890"), "");
        check("no note says so", ReportBuilder.body(f, "", "x\n", "", new String[0], 50000).contains("(not given)"), "");
        check("spoken words survive", ReportBuilder.body(f, "", "heard: \"peppa pig\"\n", "", new String[0], 50000)
                .contains("heard: \"peppa pig\""), "");

        // Body: trimmed to the limit, newest log lines kept
        String big = lines(5000);
        String trimmed = ReportBuilder.body(f, "", big, lines(500), new String[0], 20000);
        check("big body is trimmed to the limit", trimmed.length() <= 20000, String.valueOf(trimmed.length()));
        check("newest app line kept", trimmed.contains("line number 5000"), "");
        check("oldest app line dropped", !trimmed.contains("line number 1\n"), "");
        check("says lines were cut", trimmed.contains("earlier lines cut"), "");
        check("module log still present when the app log is huge", trimmed.contains("line number 500"), "");
        String small = ReportBuilder.body(f, "", lines(3), "", new String[0], 50000);
        check("small body is not cut", !small.contains("earlier lines cut"), "");

        // trimTail
        String tt = ReportBuilder.trimTail("aaa\nbbb\nccc\nddd\n", 9);
        check("trimTail keeps whole lines from the end", tt.endsWith("ddd\n") && !tt.contains("aaa"), tt.replace('\n', '|'));

        // Fallback link for the QR code
        String url = ReportBuilder.fallbackUrl("OWNER/voice-search-reports", "Report: 2.5 on X / Y / Z", trimmed, 1400);
        check("fallback link starts at the repo's new-issue page",
                url.startsWith("https://github.com/OWNER/voice-search-reports/issues/new?title="), url.substring(0, 60));
        check("fallback link fits the budget", url.length() <= 1400, String.valueOf(url.length()));
        check("fallback link carries the title", url.contains("title=Report%3A%202.5%20on%20X%20%2F%20Y%20%2F%20Z"), "");
        check("fallback link has the facts", url.contains("Model"), "");
        check("fallback link has the newest log line", url.contains("5000"), "");
        check("fallback link has no raw spaces or newlines", url.indexOf(' ') < 0 && url.indexOf('\n') < 0, "");
        String shortUrl = ReportBuilder.fallbackUrl("OWNER/r", "T", "short body", 1400);
        check("short body goes in whole", shortUrl.endsWith("body=short%20body"), shortUrl);
        check("short address", "github.com/OWNER/voice-search-reports/issues/new".equals(
                ReportBuilder.shortAddress("OWNER/voice-search-reports")), ReportBuilder.shortAddress("OWNER/voice-search-reports"));

        System.out.println(failures == 0 ? "All report cases pass." : failures + " report case(s) FAILED.");
        System.exit(failures == 0 ? 0 : 1);
    }
}
