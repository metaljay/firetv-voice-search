package com.metaljay.stremiovoice;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Updates from inside Voice Search. The app (no root) checks GitHub for a newer
 * release, downloads it, proves it is genuine (PackageVerifier) and hands it to the
 * add-on's service.sh, which runs as root and does the install, the restart and
 * going back. The two talk through small files in this app's own private folder
 * (files/update/): the app writes "request", service.sh writes "status".
 *
 * Only GitHub is contacted, and nothing is sent but the request itself.
 */
final class Updater {

    private static final String PREFS = "voice_search";
    static final String KEY_FREQ = "update_freq";
    static final String KEY_LAST = "last_check";
    static final String KEY_SKIPPED = "skipped_version";
    private static final String KEY_AVAIL_VERSION = "avail_version";
    private static final String KEY_AVAIL_NOTES = "avail_notes";
    private static final String KEY_AVAIL_URL = "avail_url";
    private static final String KEY_NOTIFIED = "notified_version";
    private static final String KEY_NOTICE_SHOWN = "notice_shown";

    private static final String LATEST_URL =
            "https://api.github.com/repos/metaljay/firetv-voice-search/releases/latest";
    private static final int TIMEOUT_MS = 15000;
    private static final int MAX_JSON_BYTES = 512 * 1024;
    private static final int MAX_ZIP_BYTES = 30 * 1024 * 1024;

    private static final String CHANNEL_ID = "voice_search_updates";
    private static final int NOTIFICATION_ID = 2;
    private static final long BANNER_MS = 12000;

    private Updater() {}

    /** What the latest release says. */
    static final class Release {
        final String version;
        final String notes;
        final String url;

        Release(String version, String notes, String url) {
            this.version = version;
            this.notes = notes;
            this.url = url;
        }
    }

    /** A failure with a sentence that is fit to show on screen. */
    static final class UpdateException extends IOException {
        private static final long serialVersionUID = 1L;

        UpdateException(String message) {
            super(message);
        }
    }

    // ----- settings (the existing voice_search prefs; existing keys are never touched) -----

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String frequency(Context c) {
        return UpdateSchedule.normalise(prefs(c).getString(KEY_FREQ, null));
    }

    static void setFrequency(Context c, String freq) {
        prefs(c).edit().putString(KEY_FREQ, UpdateSchedule.normalise(freq)).apply();
    }

    static long lastCheck(Context c) {
        return prefs(c).getLong(KEY_LAST, 0);
    }

    static String lastCheckText(Context c) {
        long t = lastCheck(c);
        if (t <= 0) return "never";
        return new SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(new Date(t));
    }

    static boolean isSkipped(Context c, String version) {
        return version != null && version.equals(prefs(c).getString(KEY_SKIPPED, null));
    }

    static void skip(Context c, String version) {
        prefs(c).edit().putString(KEY_SKIPPED, version).apply();
    }

    static String installedVersion(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }

    // ----- the check -----

    /** The newer release found by the last check, or null. Forgets one that is already installed. */
    static Release available(Context c) {
        SharedPreferences p = prefs(c);
        String v = p.getString(KEY_AVAIL_VERSION, null);
        if (v == null) return null;
        if (!Versions.isNewer(v, installedVersion(c))) {
            p.edit().remove(KEY_AVAIL_VERSION).remove(KEY_AVAIL_NOTES).remove(KEY_AVAIL_URL).apply();
            return null;
        }
        return new Release(v, p.getString(KEY_AVAIL_NOTES, ""), p.getString(KEY_AVAIL_URL, null));
    }

    /**
     * Asks GitHub for the latest full release. On success records the time and the offer
     * (or clears it) and returns the newer release, or null when up to date. On failure
     * throws and records nothing.
     */
    static Release check(Context c) throws IOException {
        Release latest = fetchLatest(c);
        SharedPreferences.Editor e = prefs(c).edit().putLong(KEY_LAST, System.currentTimeMillis());
        Release newer = null;
        if (latest != null && Versions.isNewer(latest.version, installedVersion(c))) {
            newer = latest;
            e.putString(KEY_AVAIL_VERSION, latest.version)
                    .putString(KEY_AVAIL_NOTES, latest.notes)
                    .putString(KEY_AVAIL_URL, latest.url);
        } else {
            e.remove(KEY_AVAIL_VERSION).remove(KEY_AVAIL_NOTES).remove(KEY_AVAIL_URL);
        }
        e.apply();
        return newer;
    }

    /** The scheduled background check: only when due, quiet when it fails, tells the user once per version. */
    static void scheduledCheck(Context c) {
        if (!UpdateSchedule.isDue(frequency(c), lastCheck(c), System.currentTimeMillis())) return;
        try {
            Release r = check(c);
            if (r != null && !isSkipped(c, r.version) && !r.version.equals(prefs(c).getString(KEY_NOTIFIED, null))) {
                showBanner(c, r);
                notifyAvailable(c, r);
                prefs(c).edit().putString(KEY_NOTIFIED, r.version).apply();
            }
        } catch (IOException e) {
            Log.i(VoiceService.TAG, "scheduled update check failed quietly: " + e);
        }
    }

    private static Release fetchLatest(Context c) throws IOException {
        String address = LATEST_URL;
        boolean testing = testing(c);
        if (testing) address = new String(readFile(testFile(c)), StandardCharsets.UTF_8).trim();
        JSONObject o;
        try {
            o = new JSONObject(new String(get(address, testing, MAX_JSON_BYTES), StandardCharsets.UTF_8));
        } catch (JSONException e) {
            throw new UpdateException("GitHub's reply wasn't understood");
        }
        if (o.optBoolean("draft") || o.optBoolean("prerelease")) return null; // never offered
        String version = Versions.clean(o.optString("tag_name", ""));
        if (version == null) return null;
        // The zip is attached under its current name and the old one; the current name wins.
        java.util.List<String> names = new java.util.ArrayList<>();
        java.util.Map<String, String> urls = new java.util.HashMap<>();
        JSONArray assets = o.optJSONArray("assets");
        for (int i = 0; assets != null && i < assets.length(); i++) {
            JSONObject a = assets.optJSONObject(i);
            if (a == null) continue;
            String name = a.optString("name", "");
            names.add(name);
            urls.put(name, a.optString("browser_download_url", null));
        }
        String picked = AssetPicker.pick(names);
        String url = picked == null ? null : urls.get(picked);
        if (url == null) {
            if (!Versions.isNewer(version, installedVersion(c))) return null;
            throw new UpdateException("the release has no " + AssetPicker.NEW_NAME);
        }
        return new Release(version, o.optString("body", ""), url);
    }

    // ----- download and proof -----

    /** Called with bytes so far and the total (0 when unknown). */
    interface Progress {
        void onBytes(long done, long total);
    }

    /**
     * Downloads the release zip, then refuses it unless it is genuine (signed by this app's
     * own key, complete, unchanged) and really is the newer version. Returns its SHA-256.
     * Nothing is installed here, and a failure leaves nothing behind.
     */
    static String downloadAndVerify(Context c, Release release, Progress progress) throws IOException {
        File dir = dir(c);
        File part = new File(dir, "update.zip.part");
        File zip = new File(dir, "update.zip");
        part.delete();
        zip.delete();
        try {
            download(release.url, testing(c), part, progress);
            PackageVerifier.Result r = PackageVerifier.verify(part, ownCertificate(c));
            if (!r.ok) throw new UpdateException("the download isn't genuine (" + r.reason + ")");
            if (!release.version.equals(r.versionName)) {
                throw new UpdateException("the download is version " + r.versionName + ", not " + release.version);
            }
            if (!Versions.isNewer(r.versionName, installedVersion(c))) {
                throw new UpdateException("the download isn't newer than this version");
            }
            if (!part.renameTo(zip)) throw new UpdateException("couldn't save the download");
            return sha256(zip);
        } finally {
            part.delete();
        }
    }

    private static byte[] ownCertificate(Context c) throws IOException {
        try {
            PackageInfo info = c.getPackageManager().getPackageInfo(
                    c.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
            return info.signingInfo.getApkContentsSigners()[0].toByteArray();
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            throw new UpdateException("couldn't read this app's own signature");
        }
    }

    private static void download(String address, boolean testing, File to, Progress progress) throws IOException {
        URLConnection conn = open(address, testing);
        long total = conn.getContentLengthLong();
        if (total > MAX_ZIP_BYTES) throw new UpdateException("the download is too big");
        long done = 0;
        try (InputStream in = conn.getInputStream(); OutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                done += n;
                if (done > MAX_ZIP_BYTES) throw new UpdateException("the download is too big");
                out.write(buf, 0, n);
                if (progress != null) progress.onBytes(done, total);
            }
        } catch (UpdateException e) {
            throw e;
        } catch (IOException e) {
            if (isOffline(e)) throw e;
            throw new UpdateException("the download was interrupted");
        } finally {
            if (conn instanceof HttpURLConnection) ((HttpURLConnection) conn).disconnect();
        }
        if (total > 0 && done != total) throw new UpdateException("the download was cut short");
    }

    private static byte[] get(String address, boolean testing, int max) throws IOException {
        URLConnection conn = open(address, testing);
        try (InputStream in = conn.getInputStream()) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                if (out.size() + n > max) throw new UpdateException("GitHub's reply was too big");
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } finally {
            if (conn instanceof HttpURLConnection) ((HttpURLConnection) conn).disconnect();
        }
    }

    /** Only https:// is ever used, except when the root-only test file is present. */
    private static URLConnection open(String address, boolean testing) throws IOException {
        URL url = new URL(address);
        String protocol = url.getProtocol();
        if (!"https".equals(protocol) && !(testing && ("http".equals(protocol) || "file".equals(protocol)))) {
            throw new UpdateException("refusing a non-https address");
        }
        URLConnection conn = url.openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        if (conn instanceof HttpURLConnection) {
            conn.setRequestProperty("User-Agent", "FireTV-VoiceSearch");
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            int code = ((HttpURLConnection) conn).getResponseCode();
            if (code != 200) {
                ((HttpURLConnection) conn).disconnect();
                throw new UpdateException(code == 404 ? "no release found" : "GitHub answered HTTP " + code);
            }
        }
        return conn;
    }

    /** True for the failures that mean "no connection". */
    static boolean isOffline(IOException e) {
        return e instanceof java.net.UnknownHostException
                || e instanceof java.net.ConnectException
                || e instanceof java.net.NoRouteToHostException
                || e instanceof java.net.SocketTimeoutException;
    }

    // ----- the files shared with the add-on's service.sh -----

    /** This app's private update folder; the add-on (root) reads and writes here too. */
    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "update");
        if (!d.isDirectory() && !d.mkdirs()) Log.w(VoiceService.TAG, "cannot create " + d);
        return d;
    }

    /**
     * Testing only: a file holding an address to use instead of GitHub's. Root writes it for tests,
     * but the app (and any code running as it) can write it too, so it is not a secret. It protects
     * nothing by itself: a download from any address must still pass {@link PackageVerifier}, and
     * plain http:// and file:// addresses are accepted only while this file exists.
     */
    private static File testFile(Context c) {
        return new File(dir(c), "test_url");
    }

    private static boolean testing(Context c) {
        return testFile(c).isFile();
    }

    /** Hands one line to service.sh ("install <sha256> <token>", "restart <token>", ...). Returns the token. */
    static String request(Context c, String command) throws IOException {
        String token = Long.toString(System.currentTimeMillis());
        File tmp = new File(dir(c), "request.tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write((command + " " + token + "\n").getBytes(StandardCharsets.UTF_8));
        }
        if (!tmp.renameTo(new File(dir(c), "request"))) throw new UpdateException("couldn't reach the add-on");
        return token;
    }

    /** What service.sh last wrote, as key to value; empty when it hasn't answered. */
    static Map<String, String> status(Context c) {
        Map<String, String> m = new HashMap<>();
        try {
            for (String line : new String(readFile(new File(dir(c), "status")), StandardCharsets.UTF_8).split("\n")) {
                int eq = line.indexOf('=');
                if (eq > 0) m.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
            }
        } catch (IOException e) {
            // Not written yet.
        }
        return m;
    }

    /**
     * Waits for service.sh to finish the request with this token. Returns null on success,
     * or the reason it failed (including that it never answered).
     */
    static String waitForResult(Context c, String token, long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            Map<String, String> s = status(c);
            if (token.equals(s.get("last_token"))) {
                if ("ok".equals(s.get("result"))) return null;
                String why = s.get("error");
                return why == null || why.isEmpty() ? "the add-on couldn't do it" : why;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "interrupted";
            }
        }
        return "the add-on didn't answer. Is it switched on in Magisk?";
    }

    /** The version waiting for a restart (an update or a going back), or null. */
    static String stagedVersion(Context c) {
        String v = status(c).get("staged");
        return v == null || v.isEmpty() ? null : v;
    }

    /** The kept previous version that "Go back" would restore, or null. */
    static String previousVersion(Context c) {
        String v = status(c).get("prev");
        return v == null || v.isEmpty() ? null : v;
    }

    /** The one-time "went back because it didn't start" message; null once it has been shown. */
    static String takeNotice(Context c) {
        Map<String, String> s = status(c);
        String n = s.get("notice");
        if (n == null || n.isEmpty()) {
            // Cleared by the add-on: forget that it was shown, so a later one with the same words shows too.
            if (!s.isEmpty()) prefs(c).edit().remove(KEY_NOTICE_SHOWN).apply();
            return null;
        }
        if (n.equals(prefs(c).getString(KEY_NOTICE_SHOWN, null))) return null;
        prefs(c).edit().putString(KEY_NOTICE_SHOWN, n).apply();
        // The version that failed to start isn't offered again at once (the same as after Go back).
        String failed = s.get("notice_version");
        if (failed != null && !failed.isEmpty()) skip(c, failed);
        try {
            request(c, "ack");
        } catch (IOException e) {
            Log.w(VoiceService.TAG, "couldn't clear the notice", e);
        }
        return n;
    }

    /** The one line the settings screen shows about updates, or null. */
    static String settingsLine(Context c) {
        String staged = stagedVersion(c);
        if (staged != null) {
            String what = Versions.isNewer(staged, installedVersion(c)) ? "Update to " : "Going back to ";
            return what + staged + " isn't finished until you restart.";
        }
        Release r = available(c);
        if (r != null && !isSkipped(c, r.version)) return "Update available: " + r.version;
        return null;
    }

    // ----- telling the user -----

    /**
     * Fire OS only files app notifications in its Notification Center, a page nobody opens
     * unprompted, and shows no pop-up (tested on the stick). So the news also goes up as a
     * short banner along the bottom of the screen, the same bar as "Listening". It can't be
     * tapped, so it says where to go.
     */
    static void showBanner(final Context c, Release r) {
        final String text = (ReleaseNotes.isUrgent(r.notes) ? "Urgent fix: Voice Search " : "Voice Search ")
                + r.version + " is ready. Open the Voice Search tile, then Updates.";
        final Handler main = new Handler(Looper.getMainLooper());
        main.post(new Runnable() {
            @Override
            public void run() {
                final ListeningBar bar = new ListeningBar(c);
                bar.showMessage(text);
                main.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        bar.hide();
                    }
                }, BANNER_MS);
            }
        });
    }

    /** A lasting card in Fire OS's Notification Center; selecting it opens the Updates screen. */
    static void notifyAvailable(Context c, Release r) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, "Voice Search updates", NotificationManager.IMPORTANCE_DEFAULT));
        Intent open = new Intent(c, UpdatesActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent tap = PendingIntent.getActivity(c, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String text = ReleaseNotes.isUrgent(r.notes) ? "Urgent fix. Select to read more." : "Select to read what's new.";
        Notification n = new Notification.Builder(c, CHANNEL_ID)
                .setContentTitle("Voice Search " + r.version + " is ready")
                .setContentText(text)
                .setSmallIcon(Icon.createWithResource("android", android.R.drawable.stat_notify_sync))
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build();
        nm.notify(NOTIFICATION_ID, n);
    }

    // ----- small helpers -----

    private static byte[] readFile(File f) throws IOException {
        try (FileInputStream in = new FileInputStream(f)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private static String sha256(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}
