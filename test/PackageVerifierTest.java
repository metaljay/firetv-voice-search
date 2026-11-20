import com.metaljay.stremiovoice.PackageVerifier;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Plain-Java test runner. Makes two throwaway signing keys and a set of signed
 * (and deliberately damaged) add-on zips in build/verifier-test/ at test time,
 * then checks that PackageVerifier accepts only a complete zip signed with the
 * expected key. Never touches debug.keystore.
 */
public class PackageVerifierTest {

    private static final File DIR = new File("build/verifier-test");
    private static final String PASS = "testpass";
    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        deleteTree(DIR);
        if (!DIR.mkdirs()) throw new IOException("cannot create " + DIR);

        File goodKs = makeKey("good");
        File wrongKs = makeKey("wrong");
        byte[] goodCert = certOf(goodKs);

        Map<String, byte[]> files = baseFiles();

        // 1. A good zip passes, and reports its own version.
        File good = sign(plainZip("good", files), goodKs, "good", "SIGNER");
        PackageVerifier.Result r = PackageVerifier.verify(good, goodCert);
        expect("good zip is accepted", r.ok, true);
        expect("good zip reports version name", "2.3.1".equals(r.versionName), true);
        expect("good zip reports version code", r.versionCode == 8, true);

        // 2. A changed file fails.
        Map<String, byte[]> changed = new LinkedHashMap<>(files);
        changed.put("service.sh", "#!/system/bin/sh\necho evil\n".getBytes(StandardCharsets.UTF_8));
        File tampered = rewriteKeepingSignature(good, "tampered", changed, null);
        expectRefused("a changed file", PackageVerifier.verify(tampered, goodCert));

        // 3. An added, unsigned file fails.
        File added = rewriteKeepingSignature(good, "added", files, "extra.sh");
        expectRefused("an added unsigned file", PackageVerifier.verify(added, goodCert));

        // 4. A file removed from a signed zip fails.
        Map<String, byte[]> fewer = new LinkedHashMap<>(files);
        fewer.remove("tile/VoiceSearchTile.apk");
        File removed = rewriteKeepingSignature(good, "removed", fewer, null);
        expectRefused("a removed file", PackageVerifier.verify(removed, goodCert));

        // 5. A zip signed with another key fails.
        File wrong = sign(plainZip("wrong", files), wrongKs, "wrong", "SIGNER");
        expectRefused("a zip signed with another key", PackageVerifier.verify(wrong, goodCert));

        // 6. A zip signed with both keys fails: an extra signer is not allowed.
        File both = sign(copy(good, "both"), wrongKs, "wrong", "OTHER");
        expectRefused("a zip with an extra signer", PackageVerifier.verify(both, goodCert));

        // 7. An unsigned zip fails.
        File unsigned = plainZip("unsigned", files);
        expectRefused("an unsigned zip", PackageVerifier.verify(unsigned, goodCert));

        // 8. Something that is not a zip fails.
        File junk = new File(DIR, "junk.zip");
        Files.write(junk.toPath(), "not a zip at all".getBytes(StandardCharsets.UTF_8));
        expectRefused("a file that is not a zip", PackageVerifier.verify(junk, goodCert));
        expectRefused("a missing file", PackageVerifier.verify(new File(DIR, "nope.zip"), goodCert));

        // 9. Signed correctly, but not an add-on (no module.prop): fails.
        Map<String, byte[]> noProp = new LinkedHashMap<>(files);
        noProp.remove("module.prop");
        File noModule = sign(plainZip("noprop", noProp), goodKs, "good", "SIGNER");
        expectRefused("a signed zip without module.prop", PackageVerifier.verify(noModule, goodCert));

        // 10. Signed correctly, but for another add-on: fails.
        Map<String, byte[]> otherId = new LinkedHashMap<>(files);
        otherId.put("module.prop", moduleProp("some_other_module", "2.3.1", 8));
        File otherModule = sign(plainZip("otherid", otherId), goodKs, "good", "SIGNER");
        expectRefused("a signed zip for another add-on", PackageVerifier.verify(otherModule, goodCert));

        // 11. Signed correctly, but an entry name climbs out of the folder: fails.
        Map<String, byte[]> climbing = new LinkedHashMap<>(files);
        climbing.put("../escape.sh", "x".getBytes(StandardCharsets.UTF_8));
        File climb = sign(plainZip("climb", climbing), goodKs, "good", "SIGNER");
        expectRefused("a signed zip with a path that climbs out", PackageVerifier.verify(climb, goodCert));

        System.out.println(failures == 0 ? "All verifier cases pass." : failures + " verifier case(s) FAILED.");
        System.exit(failures == 0 ? 0 : 1);
    }

    // ----- checks -----

    private static void expect(String label, boolean actual, boolean expected) {
        boolean ok = actual == expected;
        if (!ok) failures++;
        System.out.println((ok ? "PASS " : "FAIL ") + label);
    }

    private static void expectRefused(String label, PackageVerifier.Result r) {
        boolean ok = !r.ok && r.reason != null && !r.reason.isEmpty();
        if (!ok) failures++;
        System.out.println((ok ? "PASS " : "FAIL ") + "refuses " + label + (r.ok ? "" : " (" + r.reason + ")"));
    }

    // ----- building test material -----

    private static Map<String, byte[]> baseFiles() {
        Map<String, byte[]> m = new LinkedHashMap<>();
        m.put("module.prop", moduleProp("stremio_voice", "2.3.1", 8));
        m.put("post-fs-data.sh", "#!/system/bin/sh\nexit 0\n".getBytes(StandardCharsets.UTF_8));
        m.put("service.sh", "#!/system/bin/sh\nexit 0\n".getBytes(StandardCharsets.UTF_8));
        m.put("system/priv-app/StremioVoice/StremioVoice.apk", new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
        m.put("tile/VoiceSearchTile.apk", new byte[]{9, 8, 7, 6, 5, 4, 3, 2});
        return m;
    }

    private static byte[] moduleProp(String id, String version, int code) {
        return ("id=" + id + "\nname=Fire TV Voice Search\nversion=" + version + "\nversionCode=" + code
                + "\nauthor=test\ndescription=test\n").getBytes(StandardCharsets.UTF_8);
    }

    private static File plainZip(String name, Map<String, byte[]> files) throws IOException {
        File f = new File(DIR, name + ".zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(f))) {
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                out.putNextEntry(new ZipEntry(e.getKey()));
                out.write(e.getValue());
                out.closeEntry();
            }
        }
        return f;
    }

    private static File copy(File from, String name) throws IOException {
        File to = new File(DIR, name + ".zip");
        Files.copy(from.toPath(), to.toPath());
        return to;
    }

    /**
     * Rebuilds a signed zip with the given contents but the ORIGINAL signature files
     * (what an attacker without the key can do): changed, added or removed files.
     */
    private static File rewriteKeepingSignature(File signed, String name, Map<String, byte[]> files,
                                                String extraEntry) throws IOException {
        File f = new File(DIR, name + ".zip");
        try (ZipFile in = new ZipFile(signed);
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(f))) {
            Enumeration<? extends ZipEntry> en = in.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.getName().startsWith("META-INF/")) {
                    out.putNextEntry(new ZipEntry(e.getName()));
                    out.write(readAll(in.getInputStream(e)));
                    out.closeEntry();
                }
            }
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                out.putNextEntry(new ZipEntry(e.getKey()));
                out.write(e.getValue());
                out.closeEntry();
            }
            if (extraEntry != null) {
                out.putNextEntry(new ZipEntry(extraEntry));
                out.write("#!/system/bin/sh\necho added\n".getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return f;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try (InputStream i = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = i.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private static File makeKey(String alias) throws Exception {
        File ks = new File(DIR, alias + ".p12");
        run(tool("keytool"), "-genkeypair", "-keystore", ks.getPath(), "-storepass", PASS, "-keypass", PASS,
                "-alias", alias, "-keyalg", "RSA", "-keysize", "2048", "-validity", "3650",
                "-dname", "CN=Test " + alias);
        return ks;
    }

    private static byte[] certOf(File ks) throws Exception {
        KeyStore store = KeyStore.getInstance(ks, PASS.toCharArray());
        List<String> aliases = new ArrayList<>();
        for (Enumeration<String> e = store.aliases(); e.hasMoreElements(); ) aliases.add(e.nextElement());
        Certificate c = store.getCertificate(aliases.get(0));
        return c.getEncoded();
    }

    /** Signs the zip in place with jarsigner, as build.sh does for the release zip. */
    private static File sign(File zip, File ks, String alias, String sigFile) throws Exception {
        run(tool("jarsigner"), "-keystore", ks.getPath(), "-storepass", PASS, "-keypass", PASS,
                "-digestalg", "SHA-256", "-sigalg", "SHA256withRSA", "-sigfile", sigFile,
                zip.getPath(), alias);
        return zip;
    }

    private static String tool(String name) {
        return new File(new File(System.getProperty("java.home"), "bin"), name).getPath();
    }

    private static void run(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String output = new String(readAll(p.getInputStream()), StandardCharsets.UTF_8);
        if (p.waitFor() != 0) throw new IOException(String.join(" ", cmd) + " failed:\n" + output);
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }
}
