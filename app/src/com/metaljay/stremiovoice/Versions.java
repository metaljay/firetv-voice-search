package com.metaljay.stremiovoice;

/**
 * Version names such as "2.3" or a release tag such as "v2.3.1": dot-separated
 * whole numbers, nothing else. Anything that isn't that (a pre-release tag like
 * "2.3.1-rc1", "latest", empty) is not a version, so it is never offered.
 * Plain Java, no Android classes, so it is unit-tested on the Mac.
 */
public final class Versions {

    private Versions() {}

    /** "v2.3.1" becomes "2.3.1"; null if it isn't a plain dotted version. */
    public static String clean(String tag) {
        if (tag == null) return null;
        String s = tag.trim();
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        return parse(s) == null ? null : s;
    }

    /** True when {@code candidate} is a higher version than {@code installed}. */
    public static boolean isNewer(String candidate, String installed) {
        int[] a = parse(clean(candidate));
        int[] b = parse(clean(installed));
        if (a == null || b == null) return false;
        int n = Math.max(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) return x > y;
        }
        return false;
    }

    private static int[] parse(String s) {
        if (s == null || s.isEmpty()) return null;
        String[] parts = s.split("\\.", -1);
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.isEmpty() || p.length() > 6) return null;
            for (int c = 0; c < p.length(); c++) {
                if (p.charAt(c) < '0' || p.charAt(c) > '9') return null;
            }
            out[i] = Integer.parseInt(p);
        }
        return out;
    }
}
