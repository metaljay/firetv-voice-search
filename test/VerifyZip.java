import com.metaljay.stremiovoice.PackageVerifier;

import java.io.File;
import java.security.KeyStore;

/**
 * Build helper (not a test): checks that a built add-on zip passes the same
 * PackageVerifier the app runs, against the certificate in the signing keystore.
 * Usage: VerifyZip <zip> <keystore> <alias> <password>. Exits non-zero if refused.
 */
public class VerifyZip {
    public static void main(String[] args) throws Exception {
        KeyStore store = KeyStore.getInstance(new File(args[1]), args[3].toCharArray());
        byte[] cert = store.getCertificate(args[2]).getEncoded();
        PackageVerifier.Result r = PackageVerifier.verify(new File(args[0]), cert);
        if (!r.ok) {
            System.err.println("Zip refused by the app's own check: " + r.reason);
            System.exit(1);
        }
        System.out.println("Zip passes the app's own check: version " + r.versionName + " (code " + r.versionCode + ")");
    }
}
