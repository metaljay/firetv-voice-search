import com.metaljay.stremiovoice.Redactor;

/** Plain-Java test runner: prints one line per case, exits non-zero if any fail. */
public class RedactorTest {

    private static int failures = 0;

    private static void check(String label, String input, String expected, String... secrets) {
        String actual = Redactor.redact(input, secrets);
        boolean ok = expected.equals(actual);
        if (!ok) failures++;
        System.out.println((ok ? "PASS " : "FAIL ") + label + ": \"" + input + "\" -> \"" + actual + "\""
                + (ok ? "" : " (expected \"" + expected + "\")"));
    }

    private static void unchanged(String label, String input) {
        check(label, input, input);
    }

    public static void main(String[] args) {
        // IPv4
        check("IPv4", "connect 192.168.1.161:5555 ok", "connect [ip]:5555 ok");
        check("two IPv4", "from 10.0.0.2 to 8.8.8.8", "from [ip] to [ip]");
        // IPv6
        check("IPv6 full", "addr 2001:0db8:85a3:0000:0000:8a2e:0370:7334 up", "addr [ip] up");
        check("IPv6 short", "addr fe80::1a2b:3c4d:5e6f:7a8b%wlan0 up", "addr [ip] up");
        check("IPv6 loopback", "bound to ::1 now", "bound to [ip] now");
        check("IPv6 prefix", "net 2a00:1450:4009:81f::/64", "net [ip]/64");
        // Times and plain colons are not addresses.
        unchanged("logcat time", "11-22 10:16:47.867 I VoiceSearch: heard");
        unchanged("clock", "at 10:16:47 it started");
        unchanged("key: value", "mode: TYPE_IN_APP");
        // MAC
        check("MAC colons", "wlan0 a4:cf:99:12:34:56 up", "wlan0 [mac] up");
        check("MAC dashes", "id A4-CF-99-12-34-56.", "id [mac].");
        // E-mail
        check("e-mail", "signed in as jane.doe+tv@example.co.uk today", "signed in as [email] today");
        // Serial numbers (only the value we are given)
        check("serial", "device G070VM1234567890 booted", "device [serial] booted", "G070VM1234567890");
        check("serial twice", "G070VM1234567890 and g070vm1234567890", "[serial] and [serial]", "G070VM1234567890");
        unchanged("short secret ignored", "abc abc");
        check("short secret ignored 2", "abc abc", "abc abc", "abc", "", null);
        // account / user keys
        check("account key", "account=jane_doe next", "account=[removed] next");
        check("user key", "user: jdoe42, other", "user: [removed], other");
        check("username key", "username=JDoe", "username=[removed]");
        unchanged("the word user in prose", "the user pressed the button");
        // Tokens
        check("GitHub token", "key github_pat_" + "A1b2C3d4E5f6G7h8I9j0K1l2M3" + " end", "key [token] end");
        check("classic token", "key ghp_" + "A1b2C3d4E5f6G7h8I9j0K1l2M3" + " end", "key [token] end");

        // What is said stays, and so do versions and plain log lines.
        unchanged("spoken words", "heard: \"peppa pig on smarttube\"");
        unchanged("search words", "search words: \"bluey\"");
        unchanged("app named", "app named: Stremio");
        unchanged("version", "Voice Search 2.5 on Fire OS 8");
        unchanged("empty", "");
        unchanged("package", "opened com.stremio.one for \"frozen\"");

        // removeSecrets only takes out the given values (so a 4-part Fire OS version survives).
        String kept = Redactor.removeSecrets("Fire OS 8.1.8.5, serial G070VM1234567890", "G070VM1234567890");
        boolean ok = "Fire OS 8.1.8.5, serial [serial]".equals(kept);
        if (!ok) failures++;
        System.out.println((ok ? "PASS " : "FAIL ") + "removeSecrets keeps 8.1.8.5 -> " + kept);

        ok = "".equals(Redactor.redact(null));
        if (!ok) failures++;
        System.out.println((ok ? "PASS " : "FAIL ") + "null -> empty");

        System.out.println(failures == 0 ? "All redaction cases pass." : failures + " redaction case(s) FAILED.");
        System.exit(failures == 0 ? 0 : 1);
    }
}
