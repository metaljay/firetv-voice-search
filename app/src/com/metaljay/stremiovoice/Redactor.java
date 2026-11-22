package com.metaljay.stremiovoice;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Takes out of a problem report anything that could point at a person or at this particular
 * stick: network addresses, MAC addresses, e-mail addresses, serial numbers, account and user
 * names, access tokens. What was said to the remote is left alone (the owner's choice, and the
 * report says so). Pure Java, no Android classes, so it runs in the plain-Java unit tests.
 */
public final class Redactor {

    private static final String HEX4 = "[0-9A-Fa-f]{1,4}";
    private static final String IPV6_TAIL = "(?:" + HEX4 + "(?::" + HEX4 + "){0,6})?";

    // GitHub access tokens ("github_pat_..." and the older "ghp_..." family).
    private static final Pattern TOKEN = Pattern.compile(
            "github_pat_[A-Za-z0-9_]{20,}|gh[pousr]_[A-Za-z0-9]{20,}");
    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}");
    private static final Pattern MAC = Pattern.compile(
            "(?<![0-9A-Fa-f:\\-])(?:[0-9A-Fa-f]{2}[:\\-]){5}[0-9A-Fa-f]{2}(?![0-9A-Fa-f:\\-])");
    // Eight groups, or any form with "::". Plain times such as 10:16:47 have neither.
    private static final Pattern IPV6 = Pattern.compile(
            "(?<![0-9A-Za-z:.])(?:"
            + "(?:" + HEX4 + ":){7}" + HEX4
            + "|(?:" + HEX4 + ":){1,7}:" + IPV6_TAIL
            + "|::" + IPV6_TAIL
            + ")(?:%[0-9A-Za-z]+)?(?![0-9A-Za-z:])");
    private static final Pattern IPV4 = Pattern.compile(
            "(?<![0-9.])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?![0-9])");
    // "account=jane", "user: jdoe": keeps the key, drops the value.
    private static final Pattern ACCOUNT = Pattern.compile(
            "(\\b(?:account|username|user_id|userid|user)\\s*[=:]\\s*)[^\\s,;]+", Pattern.CASE_INSENSITIVE);

    private Redactor() {}

    /** The text with every pattern above removed, and each of the given secret values (such as the serial number). */
    public static String redact(String text, String... secrets) {
        if (text == null) return "";
        String s = removeSecrets(text, secrets);
        s = TOKEN.matcher(s).replaceAll("[token]");
        s = EMAIL.matcher(s).replaceAll("[email]");
        s = MAC.matcher(s).replaceAll("[mac]");
        s = IPV6.matcher(s).replaceAll("[ip]");
        s = IPV4.matcher(s).replaceAll("[ip]");
        s = ACCOUNT.matcher(s).replaceAll("$1[removed]");
        return s;
    }

    /** Only the given secret values taken out (case ignored); values under four characters are not used. */
    public static String removeSecrets(String text, String... secrets) {
        if (text == null) return "";
        String s = text;
        if (secrets != null) {
            for (String secret : secrets) {
                if (secret == null || secret.trim().length() < 4) continue;
                s = Pattern.compile(Pattern.quote(secret.trim()), Pattern.CASE_INSENSITIVE)
                        .matcher(s).replaceAll(Matcher.quoteReplacement("[serial]"));
            }
        }
        return s;
    }
}
