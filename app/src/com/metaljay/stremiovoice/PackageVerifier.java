package com.metaljay.stremiovoice;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Decides whether a downloaded add-on zip is genuine before anything installs it.
 * The release zip is a signed jar (build.sh signs it with the same key that signs
 * the app). It is accepted only when:
 * <ul>
 *   <li>every file in it is signed, and nothing in it has been changed;</li>
 *   <li>nothing the signature lists is missing, and no file has been added;</li>
 *   <li>it was signed by exactly one signer, whose certificate is exactly the
 *       expected one (the app's own);</li>
 *   <li>it is complete: a Voice Search add-on with its module file, script and app,
 *       and no file path that climbs out of the folder.</li>
 * </ul>
 * Magisk skips META-INF when it installs, so the signature files never reach the
 * module. Plain Java, so it is unit-tested on the Mac.
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

    /** @param expectedCert the DER bytes of the one certificate that must have signed the zip */
    public static Result verify(File zip, byte[] expectedCert) {
        try (JarFile jar = new JarFile(zip, true)) {
            Manifest manifest = jar.getManifest();
            if (manifest == null) return Result.refused("not signed");
            for (String listed : manifest.getEntries().keySet()) {
                if (jar.getEntry(listed) == null) return Result.refused("a signed file is missing: " + listed);
            }

            String props = null;
            boolean[] found = new boolean[REQUIRED.length];
            for (Enumeration<JarEntry> en = jar.entries(); en.hasMoreElements(); ) {
                JarEntry entry = en.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || name.startsWith("META-INF/")) continue;
                if (name.startsWith("/") || name.contains("\\") || Arrays.asList(name.split("/")).contains("..")) {
                    return Result.refused("unsafe file path: " + name);
                }

                byte[] data = readAll(jar.getInputStream(entry)); // reading checks the signature
                Certificate[] signers = entry.getCertificates();
                if (signers == null || signers.length == 0) return Result.refused("not signed: " + name);
                if (signers.length != 1 || !Arrays.equals(signers[0].getEncoded(), expectedCert)) {
                    return Result.refused("signed by someone else: " + name);
                }
                for (int i = 0; i < REQUIRED.length; i++) if (REQUIRED[i].equals(name)) found[i] = true;
                if ("module.prop".equals(name)) props = new String(data, "UTF-8");
            }
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

    private static String prop(String text, String key) {
        if (text == null) return null;
        for (String line : text.split("\n")) {
            line = line.trim();
            if (line.startsWith(key + "=")) return line.substring(key.length() + 1).trim();
        }
        return null;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            in.close();
        }
    }
}
