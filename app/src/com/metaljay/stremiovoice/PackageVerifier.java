package com.metaljay.stremiovoice;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Decides whether a downloaded add-on zip is genuine before anything installs it.
 * The release zip is a signed jar (build.sh signs it with the same key that signs
 * the app). It is accepted only when:
 * <ul>
 *   <li>every file in it is signed, and nothing in it has been changed;</li>
 *   <li>nothing the signature lists is missing, and no file has been added;</li>
 *   <li>it was signed by exactly one signer, whose certificate is exactly the
 *       expected one (the app's own);</li>
 *   <li>it is complete: a Voice Search add-on with its module file, script and app;</li>
 *   <li>every name is a plain relative path (no "..", no empty parts, no leading slash),
 *       META-INF/ holds only the manifest and one signature, and the file list read the
 *       way the installer's unzip reads it (local headers, in order) is exactly the one
 *       Java reads from the central directory, with identical contents.</li>
 * </ul>
 * The last point matters: the installer drops META-INF/ by pattern, but unzip resolves
 * "/../" first and reads names from the local headers, so a file hidden under META-INF/
 * or named differently in the two places could land as a script that was never signed.
 * Magisk skips META-INF when it installs, so the signature files never reach the module.
 * Plain Java, so it is unit-tested on the Mac.
 */
public final class PackageVerifier {

    static final String MODULE_ID = "stremio_voice";
    private static final String[] REQUIRED = {
            "module.prop", "service.sh", "system/priv-app/StremioVoice/StremioVoice.apk"};

    private PackageVerifier() {}

    public static final class Result {
        public final boolean ok;
        /** Why it was refused (empty when accepted). */
        public final String reason;
        /** From the zip's own module.prop; only set when accepted. */
        public final String versionName;
        public final int versionCode;

        Result(boolean ok, String reason, String versionName, int versionCode) {
            this.ok = ok;
            this.reason = reason;
            this.versionName = versionName;
            this.versionCode = versionCode;
        }

        static Result refused(String reason) {
            return new Result(false, reason, null, 0);
        }
    }

    private static final int MAX_ENTRIES = 2000;
    private static final long MAX_UNPACKED_BYTES = 64L * 1024 * 1024;

    /** @param expectedCert the DER bytes of the one certificate that must have signed the zip */
    public static Result verify(File zip, byte[] expectedCert) {
        // Pass 1: read the zip the way the installer's unzip does, entry by entry from the
        // local headers. Every name must be safe and META-INF/ must hold only the signature.
        List<String> names = new ArrayList<>();
        List<byte[]> contents = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int manifests = 0;
        int sigFiles = 0;
        int sigBlocks = 0;
        long unpacked = 0;
        try (ZipInputStream in = new ZipInputStream(new FileInputStream(zip))) {
            for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                String name = e.getName();
                if (names.size() >= MAX_ENTRIES) return Result.refused("too many files");
                if (unsafe(name)) return Result.refused("unsafe file path: " + name);
                if (!seen.add(name)) return Result.refused("a file name is used twice: " + name);
                if (name.toUpperCase(Locale.ROOT).startsWith("META-INF")) {
                    if (!name.startsWith("META-INF/")) return Result.refused("unexpected file: " + name);
                    String rest = name.substring("META-INF/".length());
                    if (rest.isEmpty()) {
                        // the folder entry itself
                    } else if (rest.contains("/")) {
                        return Result.refused("unexpected file in META-INF: " + name);
                    } else if (rest.equals("MANIFEST.MF")) {
                        manifests++;
                    } else if (rest.endsWith(".SF")) {
                        sigFiles++;
                    } else if (rest.endsWith(".RSA") || rest.endsWith(".DSA") || rest.endsWith(".EC")) {
                        sigBlocks++;
                    } else {
                        return Result.refused("unexpected file in META-INF: " + name);
                    }
                }
                byte[] data = readFully(in, false);
                unpacked += data.length;
                if (unpacked > MAX_UNPACKED_BYTES) return Result.refused("too big when unpacked");
                names.add(name);
                contents.add(data);
            }
        } catch (IOException | RuntimeException e) {
            return Result.refused("unreadable: " + e.getMessage());
        }
        if (manifests != 1 || sigFiles != 1 || sigBlocks != 1) return Result.refused("not signed exactly once");

        // Pass 2: Java's own view (the central directory), with the signature checked.
        try (JarFile jar = new JarFile(zip, true)) {
            Manifest manifest = jar.getManifest();
            if (manifest == null) return Result.refused("not signed");
            for (String listed : manifest.getEntries().keySet()) {
                if (jar.getEntry(listed) == null) return Result.refused("a signed file is missing: " + listed);
            }

            String props = null;
            boolean[] found = new boolean[REQUIRED.length];
            int index = 0;
            for (Enumeration<JarEntry> en = jar.entries(); en.hasMoreElements(); index++) {
                JarEntry entry = en.nextElement();
                String name = entry.getName();
                if (index >= names.size() || !names.get(index).equals(name)) {
                    return Result.refused("the file list isn't the same in both places: " + name);
                }
                byte[] data = readFully(jar.getInputStream(entry)); // reading checks the signature
                if (!Arrays.equals(data, contents.get(index))) {
                    return Result.refused("the file contents aren't the same in both places: " + name);
                }
                if (entry.isDirectory() || name.startsWith("META-INF/")) continue;

                Certificate[] signers = entry.getCertificates();
                if (signers == null || signers.length == 0) return Result.refused("not signed: " + name);
                if (signers.length != 1 || !Arrays.equals(signers[0].getEncoded(), expectedCert)) {
                    return Result.refused("signed by someone else: " + name);
                }
                for (int i = 0; i < REQUIRED.length; i++) if (REQUIRED[i].equals(name)) found[i] = true;
                if ("module.prop".equals(name)) props = new String(data, "UTF-8");
            }
            if (index != names.size()) return Result.refused("the file list isn't the same in both places");
            for (int i = 0; i < REQUIRED.length; i++) {
                if (!found[i]) return Result.refused("incomplete, missing " + REQUIRED[i]);
            }
            if (!MODULE_ID.equals(prop(props, "id"))) return Result.refused("not a Voice Search add-on");
            String version = Versions.clean(prop(props, "version"));
            int code;
            try {
                code = Integer.parseInt(prop(props, "versionCode"));
            } catch (NumberFormatException e) {
                code = 0;
            }
            if (version == null || code <= 0) return Result.refused("no version in module.prop");
            return new Result(true, "", version, code);
        } catch (SecurityException e) {
            return Result.refused("changed after signing");
        } catch (IOException e) {
            return Result.refused("unreadable: " + e.getMessage());
        } catch (java.security.cert.CertificateEncodingException e) {
            return Result.refused("unreadable certificate");
        }
    }

    /** A name that isn't a plain relative path: empty, absolute, backslash, control character, or "." / ".." / empty parts. */
    static boolean unsafe(String name) {
        if (name.isEmpty() || name.startsWith("/") || name.indexOf('\\') >= 0) return true;
        for (int i = 0; i < name.length(); i++) if (name.charAt(i) < 0x20) return true;
        String path = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
        for (String part : path.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) return true;
        }
        return false;
    }

    private static String prop(String text, String key) {
        if (text == null) return null;
        for (String line : text.split("\n")) {
            line = line.trim();
            if (line.startsWith(key + "=")) return line.substring(key.length() + 1).trim();
        }
        return null;
    }

    /** Reads a stream to its end and closes it. */
    private static byte[] readFully(InputStream in) throws IOException {
        return readFully(in, true);
    }

    private static byte[] readFully(InputStream in, boolean close) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            if (close) in.close();
        }
    }
}
