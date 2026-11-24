package com.metaljay.stremiovoice;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Asking GitHub for the latest release and downloading its zip. Shared by the main app's
 * updater and the setup app, so it uses no Android classes beyond org.json and no
 * main-app-only code. Only https:// is used, unless the caller says the (root-only) test
 * file is present. A download is never trusted on its own: callers must run it through
 * {@link PackageVerifier}.
 */
public final class ReleaseFetch {

    public static final String LATEST_URL =
            "https://api.github.com/repos/metaljay/firetv-voice-search/releases/latest";
    private static final int TIMEOUT_MS = 15000;
    private static final int MAX_JSON_BYTES = 512 * 1024;
    private static final int MAX_ZIP_BYTES = 30 * 1024 * 1024;

    private ReleaseFetch() {}

    /** A failure with a sentence that is fit to show on screen. */
    public static class FetchException extends IOException {
        private static final long serialVersionUID = 1L;

        public FetchException(String message) {
            super(message);
        }
    }

    /** What the latest release says. {@code url} is null when no zip is attached. */
    public static final class Info {
        public final String version;
        public final String notes;
        public final String url;

        Info(String version, String notes, String url) {
            this.version = version;
            this.notes = notes;
            this.url = url;
        }
    }

    /** Called with bytes so far and the total (0 when unknown). */
    public interface Progress {
        void onBytes(long done, long total);
    }

    /** The latest full release (never a draft or pre-release), or null if there is none to offer. */
    public static Info fetchLatest(String address, boolean insecureOk) throws IOException {
        JSONObject o;
        try {
            o = new JSONObject(new String(get(address, insecureOk, MAX_JSON_BYTES), StandardCharsets.UTF_8));
        } catch (JSONException e) {
            throw new FetchException("GitHub's reply wasn't understood");
        }
        if (o.optBoolean("draft") || o.optBoolean("prerelease")) return null; // never offered
        String version = Versions.clean(o.optString("tag_name", ""));
        if (version == null) return null;
        // The zip is attached under its current name and the old one; the current name wins.
        List<String> names = new ArrayList<>();
        Map<String, String> urls = new HashMap<>();
        JSONArray assets = o.optJSONArray("assets");
        for (int i = 0; assets != null && i < assets.length(); i++) {
            JSONObject a = assets.optJSONObject(i);
            if (a == null) continue;
            String name = a.optString("name", "");
            names.add(name);
            urls.put(name, a.optString("browser_download_url", null));
        }
        String picked = AssetPicker.pick(names);
        return new Info(version, o.optString("body", ""), picked == null ? null : urls.get(picked));
    }

    public static void download(String address, boolean insecureOk, File to, Progress progress) throws IOException {
        URLConnection conn = open(address, insecureOk);
        long total = conn.getContentLengthLong();
        if (total > MAX_ZIP_BYTES) throw new FetchException("the download is too big");
        long done = 0;
        try (InputStream in = conn.getInputStream(); OutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                done += n;
                if (done > MAX_ZIP_BYTES) throw new FetchException("the download is too big");
                out.write(buf, 0, n);
                if (progress != null) progress.onBytes(done, total);
            }
        } catch (FetchException e) {
            throw e;
        } catch (IOException e) {
            if (isOffline(e)) throw e;
            throw new FetchException("the download was interrupted");
        } finally {
            if (conn instanceof HttpURLConnection) ((HttpURLConnection) conn).disconnect();
        }
        if (total > 0 && done != total) throw new FetchException("the download was cut short");
    }

    private static byte[] get(String address, boolean insecureOk, int max) throws IOException {
        URLConnection conn = open(address, insecureOk);
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                if (out.size() + n > max) throw new FetchException("GitHub's reply was too big");
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } finally {
            if (conn instanceof HttpURLConnection) ((HttpURLConnection) conn).disconnect();
        }
    }

    /** Only https:// is ever used, except when the caller allows plain http:// and file:// for tests. */
    private static URLConnection open(String address, boolean insecureOk) throws IOException {
        URL url = new URL(address);
        String protocol = url.getProtocol();
        if (!"https".equals(protocol) && !(insecureOk && ("http".equals(protocol) || "file".equals(protocol)))) {
            throw new FetchException("refusing a non-https address");
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
                throw new FetchException(code == 404 ? "no release found" : "GitHub answered HTTP " + code);
            }
        }
        return conn;
    }

    /** True for the failures that mean "no connection". */
    public static boolean isOffline(IOException e) {
        return e instanceof java.net.UnknownHostException
                || e instanceof java.net.ConnectException
                || e instanceof java.net.NoRouteToHostException
                || e instanceof java.net.SocketTimeoutException;
    }
}
