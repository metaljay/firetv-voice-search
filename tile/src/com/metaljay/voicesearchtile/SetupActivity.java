package com.metaljay.voicesearchtile;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.metaljay.stremiovoice.PackageVerifier;
import com.metaljay.stremiovoice.QrView;
import com.metaljay.stremiovoice.ReleaseFetch;
import com.metaljay.stremiovoice.Versions;

import io.nayuki.qrcodegen.QrCode;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;

/**
 * The Voice Search setup screens. The add-on that does the real work is a Magisk module, so this
 * ordinary app walks the owner through putting it in: explains what root is needed for, asks Magisk
 * (only after a button press that says so), warns, checks the stick, downloads the add-on from
 * GitHub, proves it is genuine (signed by this app's own key), and hands it to Magisk's installer.
 * It can also remove the add-on again. Nothing is installed without a press on "Install".
 */
public class SetupActivity extends Activity {

    private static final int BACKGROUND = 0xFF0B0F1A;
    private static final int CARD = 0xFF161D2E;
    private static final int ACCENT = 0xFF00CAFF;
    private static final int GREY = 0xFFA9B4C8;
    private static final int ORANGE = 0xFFFFB84D;

    static final String ROOT_GUIDE = "https://github.com/metaljay/firetv-voice-search#root-your-fire-tv";
    private static final String MODULE_DIR = "/data/adb/modules/stremio_voice";
    private static final String TEMP_ZIP = "/data/local/tmp/voice_search_setup.zip";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private LinearLayout body;
    /** Something is running: Back is ignored until it finishes. */
    private boolean busy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);
        root.setPadding(dp(96), dp(36), dp(96), dp(28));

        TextView title = text("Voice Search setup", 34, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, dp(10), 0, 0);
        root.addView(body);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);
        scroll.addView(root);
        setContentView(scroll);

        showIntro();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (!busy) super.onBackPressed();
    }

    // ----- the screens -----

    private void showIntro() {
        clear();
        body.addView(line("Voice Search lets you hold the voice button on your remote and say what to watch. "
                + "It needs a rooted Fire TV with Magisk.", 20, Color.WHITE));
        body.addView(line("When you press the button below, Magisk will ask whether to allow Voice Search to use root. "
                + "Choose Grant on the TV.", 20, GREY));
        View go = button("Set up Voice Search", new Runnable() {
            @Override public void run() { checkRoot(); }
        });
        body.addView(row(go, button("Close", closeAction())));
        go.requestFocus();
    }

    private void showNoRoot(boolean askedAndRefused) {
        clear();
        TextView head = line("Voice Search needs a rooted Fire TV with Magisk.", 24, Color.WHITE);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        body.addView(head);
        body.addView(line("Without root it can't:", 20, Color.WHITE));
        body.addView(line("• hear you (Alexa's speech-to-text is for system apps only)", 20, Color.WHITE));
        body.addView(line("• take over the voice button", 20, Color.WHITE));
        body.addView(line("• send Netflix, iPlayer and others to Amazon's search", 20, Color.WHITE));
        body.addView(line("• hand the button back to Alexa if it stops", 20, Color.WHITE));

        LinearLayout qrRow = new LinearLayout(this);
        qrRow.setOrientation(LinearLayout.HORIZONTAL);
        qrRow.setGravity(Gravity.CENTER_VERTICAL);
        qrRow.setPadding(0, dp(14), 0, 0);
        try {
            qrRow.addView(new QrView(this, QrCode.encodeText(ROOT_GUIDE, QrCode.Ecc.MEDIUM)),
                    new LinearLayout.LayoutParams(dp(230), dp(230)));
        } catch (RuntimeException e) {
            // The address is short and fixed, so this can't happen.
        }
        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setPadding(dp(28), 0, 0, 0);
        qrRow.addView(side, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        side.addView(line("Scan with your phone for how to root your stick and install Magisk.", 20, Color.WHITE));
        if (askedAndRefused) {
            TextView why = line("Magisk didn't allow it, or didn't answer in time. If you chose Deny by mistake, try again.", 18, ORANGE);
            why.setPadding(0, dp(12), 0, 0);
            side.addView(why);
        }
        View first;
        if (askedAndRefused) {
            first = button("Try again", new Runnable() {
                @Override public void run() { checkRoot(); }
            });
            side.addView(spaced(row(first, button("Close", closeAction()))));
        } else {
            first = button("Close", closeAction());
            side.addView(spaced(row(first)));
        }
        body.addView(qrRow);
        first.requestFocus();
    }

    private void showWarning() {
        clear();
        TextView head = line("Before you install", 24, Color.WHITE);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        body.addView(head);
        body.addView(card("• It takes over the voice button. Saying \"Alexa\" still gets Alexa.\n\n"
                + "• It installs a system app through Magisk.\n\n"
                + "• To undo it: use the Remove button in Voice Search, or Magisk's safe mode, "
                + "or delete /data/adb/modules/stremio_voice."));
        View install = button("Install", new Runnable() {
            @Override public void run() { deviceCheck(); }
        });
        body.addView(row(install, button("Cancel", closeAction())));
        install.requestFocus();
    }

    private void showUntested(final String described) {
        clear();
        TextView head = line("Not tested on this device", 24, ORANGE);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        body.addView(head);
        body.addView(card("Not tested on this device (" + described + "). It may not work, or the stick may fail to "
                + "start; Magisk's safe mode is the way back."));
        View cont = button("Continue", new Runnable() {
            @Override public void run() { install(); }
        });
        body.addView(row(button("Cancel", closeAction()), cont));
        cont.requestFocus();
        // Cancel is first in the row so a stray double press cancels; Continue is the deliberate move.
    }

    private void showBusy(String message) {
        clear();
        busy = true;
        body.addView(line(message, 22, ACCENT));
    }

    private void showInstalled() {
        busy = false;
        clear();
        TextView head = line("Installed.", 26, ACCENT);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        body.addView(head);
        body.addView(line("Voice Search starts after the restart.", 20, Color.WHITE));
        View now = button("Restart now", new Runnable() {
            @Override public void run() { restartNow(); }
        });
        body.addView(row(now, button("Later", closeAction())));
        now.requestFocus();
    }

    private void showFailed(String why, boolean canRetry) {
        busy = false;
        clear();
        TextView head = line("Nothing was installed.", 24, ORANGE);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        body.addView(head);
        body.addView(line(why, 20, Color.WHITE));
        View first;
        if (canRetry) {
            first = button("Try again", new Runnable() {
                @Override public void run() { showIntro(); }
            });
            body.addView(row(first, button("Close", closeAction())));
        } else {
            first = button("Close", closeAction());
            body.addView(row(first));
        }
        first.requestFocus();
    }

    /** The add-on's folder is there but its app isn't running (switched off in Magisk, or waiting for a restart). */
    private void showExisting() {
        clear();
        TextView head = line("Voice Search is already in Magisk", 24, Color.WHITE);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        body.addView(head);
        body.addView(card("The add-on is installed but isn't running. It may be switched off in Magisk's Modules list, "
                + "or waiting for a restart. You can restart to try it, or remove it."));
        View restart = button("Restart now", new Runnable() {
            @Override public void run() { restartNow(); }
        });
        View remove = button("Remove", new Runnable() {
            @Override public void run() { removeAddOn(); }
        });
        body.addView(row(restart, remove, button("Close", closeAction())));
        restart.requestFocus();
    }

    private void showRemoved() {
        busy = false;
        clear();
        TextView head = line("Voice Search will be removed at the next restart.", 24, ACCENT);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        body.addView(head);
        body.addView(line("Alexa gets the button back after a restart. After that you can uninstall the "
                + "Voice Search app from Fire TV Settings, Applications.", 20, Color.WHITE));
        View now = button("Restart now", new Runnable() {
            @Override public void run() { restartNow(); }
        });
        body.addView(row(now, button("Later", closeAction())));
        now.requestFocus();
    }

    // ----- the work (never on the main thread) -----

    /** Asks Magisk: runs "su -c id". Only called from a button that said Magisk would ask. */
    private void checkRoot() {
        showBusy("Asking Magisk for permission. Choose Grant on the TV…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Root.Result r = Root.run("id", 30);
                if (r.missing) {
                    post(new Runnable() { @Override public void run() { busy = false; showNoRoot(false); } });
                } else if (!Root.isRoot(r)) {
                    post(new Runnable() { @Override public void run() { busy = false; showNoRoot(true); } });
                } else {
                    final Root.Result folder = Root.run("[ -d " + MODULE_DIR + " ] && echo yes || echo no", 20);
                    final boolean exists = folder.ok() && folder.output.contains("yes");
                    post(new Runnable() {
                        @Override public void run() {
                            busy = false;
                            if (exists) showExisting(); else showWarning();
                        }
                    });
                }
            }
        }, "setup-root-check").start();
    }

    private void deviceCheck() {
        String model = Build.MODEL == null ? "" : Build.MODEL;
        String version = osVersion();
        if (DeviceSupport.isTested(model, version)) {
            install();
        } else {
            showUntested(DeviceSupport.describe(model, version));
        }
    }

    /**
     * Fire OS's version: the first of these properties that holds a dotted number. Read with getprop
     * (no root needed): ro.build.version.name (Fire OS's own "Fire OS 8.1.8.5"-style name), then
     * ro.build.display.id, then ro.build.version.fireos.
     */
    private static String osVersion() {
        for (String key : new String[]{"ro.build.version.name", "ro.build.display.id", "ro.build.version.fireos"}) {
            String v = DeviceSupport.versionFrom(getprop(key));
            if (!v.isEmpty()) return v;
        }
        return "";
    }

    private static String getprop(String key) {
        try {
            Process p = new ProcessBuilder("getprop", key).redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"))) {
                String line = r.readLine();
                return line == null ? "" : line.trim();
            }
        } catch (IOException e) {
            return "";
        }
    }

    private void install() {
        showBusy("Looking for the latest Voice Search…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    installNow();
                } catch (final IOException e) {
                    final String why = ReleaseFetch.isOffline(e)
                            ? "There's no connection to GitHub. Check the internet and try again."
                            : "Couldn't finish: " + e.getMessage() + ".";
                    post(new Runnable() { @Override public void run() { showFailed(why, true); } });
                } catch (RuntimeException e) {
                    post(new Runnable() { @Override public void run() { showFailed("Something went wrong (" + e + ").", true); } });
                }
            }
        }, "setup-install").start();
    }

    private void installNow() throws IOException {
        ReleaseFetch.Info info = ReleaseFetch.fetchLatest(ReleaseFetch.LATEST_URL, false);
        if (info == null) throw new IOException("GitHub has no release to install");
        if (info.url == null) throw new IOException("the release has no add-on file");
        say("Downloading Voice Search " + info.version + "…");
        File zip = new File(getCacheDir(), "voice_search.zip");
        zip.delete();
        try {
            ReleaseFetch.download(info.url, false, zip, new ReleaseFetch.Progress() {
                private int last = -1;
                @Override public void onBytes(long done, long total) {
                    int pct = total > 0 ? (int) (done * 100 / total) : (int) (done / 1024 / 1024);
                    if (pct == last) return;
                    last = pct;
                    say(total > 0 ? "Downloading… " + pct + "%" : "Downloading… " + pct + " MB");
                }
            });
            say("Checking the download is genuine…");
            PackageVerifier.Result v = PackageVerifier.verify(zip, ownCertificate());
            if (!v.ok) throw new IOException("the download isn't genuine (" + v.reason + ")");
            if (!info.version.equals(v.versionName) || Versions.clean(v.versionName) == null) {
                throw new IOException("the download is version " + v.versionName + ", not " + info.version);
            }
            String sha = sha256(zip);
            say("Installing through Magisk…");
            // Root copies the checked file somewhere Magisk's installer can read, makes sure it is still
            // byte-for-byte the file that was checked, installs it, and cleans up. The exit code is
            // Magisk's own.
            String script = "cp '" + zip.getAbsolutePath() + "' " + TEMP_ZIP + " && chmod 644 " + TEMP_ZIP
                    + " && [ \"$(sha256sum " + TEMP_ZIP + " | cut -d' ' -f1)\" = " + sha + " ]"
                    + " && magisk --install-module " + TEMP_ZIP + "; rc=$?; rm -f " + TEMP_ZIP + "; exit $rc";
            Root.Result r = Root.run(script, 180);
            if (r.missing) throw new IOException("root disappeared");
            if (r.timedOut) throw new IOException("Magisk didn't finish in time");
            if (r.exit != 0) throw new IOException("Magisk's installer failed (exit " + r.exit + ")");
        } finally {
            zip.delete();
        }
        post(new Runnable() { @Override public void run() { showInstalled(); } });
    }

    private void removeAddOn() {
        showBusy("Removing…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Root.Result r = Root.run("touch " + MODULE_DIR + "/remove", 30);
                post(new Runnable() {
                    @Override public void run() {
                        if (r.ok()) showRemoved();
                        else showFailed("Couldn't remove it (Magisk said no, or the add-on folder is gone).", false);
                    }
                });
            }
        }, "setup-remove").start();
    }

    private void restartNow() {
        showBusy("Restarting…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Root.Result r = Root.run("reboot", 30);
                if (r.ok()) return; // the stick is going down
                post(new Runnable() {
                    @Override public void run() { showFailed("Couldn't restart. Restart the stick from Fire TV Settings.", false); }
                });
            }
        }, "setup-restart").start();
    }

    private byte[] ownCertificate() throws IOException {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
            return info.signingInfo.getApkContentsSigners()[0].toByteArray();
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            throw new IOException("couldn't read this app's own signature");
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

    // ----- small helpers -----

    private Runnable closeAction() {
        return new Runnable() {
            @Override public void run() { finish(); }
        };
    }

    private void post(Runnable r) {
        ui.post(r);
    }

    /** Updates the progress line on the busy screen. */
    private void say(final String message) {
        post(new Runnable() {
            @Override public void run() {
                if (busy) showBusyText(message);
            }
        });
    }

    private void showBusyText(String message) {
        clear();
        body.addView(line(message, 22, ACCENT));
    }

    private void clear() {
        body.removeAllViews();
    }

    // ----- the look: the same cards and focus outline as the main app's screens -----

    private TextView line(String s, int sp, int color) {
        TextView v = text(s, sp, color);
        v.setPadding(0, dp(6), 0, 0);
        return v;
    }

    private View card(String s) {
        TextView v = text(s, 20, Color.WHITE);
        v.setPadding(dp(28), dp(18), dp(28), dp(18));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(16));
        v.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        lp.bottomMargin = dp(12);
        v.setLayoutParams(lp);
        return v;
    }

    private LinearLayout row(View... buttons) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(14), 0, 0);
        for (View b : buttons) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(16);
            row.addView(b, lp);
        }
        return row;
    }

    private View spaced(View v) {
        v.setPadding(0, dp(12), 0, 0);
        return v;
    }

    private View button(String label, final Runnable onClick) {
        TextView b = text(label, 22, Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(36), dp(14), dp(36), dp(14));
        final GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(16));
        b.setBackground(bg);
        b.setFocusable(true);
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                bg.setStroke(hasFocus ? dp(4) : 0, Color.WHITE);
                bg.setColor(hasFocus ? ACCENT : CARD);
                ((TextView) v).setTextColor(hasFocus ? BACKGROUND : Color.WHITE);
            }
        });
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onClick.run();
            }
        });
        return b;
    }

    private TextView text(String s, int sp, int color) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(color);
        return v;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
