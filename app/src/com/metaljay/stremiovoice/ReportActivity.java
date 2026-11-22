package com.metaljay.stremiovoice;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.nayuki.qrcodegen.QrCode;

/**
 * "Send a problem report": says plainly what a report holds, then shows a QR code that opens it on
 * a phone as a new issue on this project's GitHub page, for the person to check and submit with
 * their own account. ReportSender is asked first, so an automatic sender can be added later
 * without changing this screen. The explanation text is fixed wording agreed with the owner.
 */
public class ReportActivity extends Activity {

    private static final int BODY_MAX = 50000;
    private static final int QR_URL_MAX = 1400; // a QR code can't carry much more than this and still scan from a sofa
    private static final int LOG_LINES = 1500;

    static final String EXPLANATION = "Scan this with your phone to post the report on GitHub. It includes your stick model, "
            + "Fire OS version, language, app version, which supported apps are installed, and Voice Search's recent log, "
            + "including what was said to the remote. Nothing that identifies you or this stick. "
            + "You can read it on your phone and change it before you post it.";

    private LinearLayout content;
    private EditText note;
    private View send;
    private boolean working = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(ChooserActivity.BACKGROUND);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(96), dp(28), dp(96), dp(24));
        scroll.addView(content);
        setContentView(scroll);
        showQuestion();
    }

    // ----- the screens -----

    private void reset() {
        content.removeAllViews();
        TextView title = text("Send a problem report", 34, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        content.addView(title);
    }

    private void showQuestion() {
        reset();
        TextView what = text(EXPLANATION, 19, ChooserActivity.GREY);
        what.setPadding(0, dp(10), 0, dp(14));
        content.addView(what);

        TextView label = text("What went wrong? (optional, a few words is plenty)", 17, ChooserActivity.GREY);
        content.addView(label);
        note = new EditText(this);
        note.setSingleLine(true);
        note.setHint("e.g. \"Netflix did nothing\"");
        note.setHintTextColor(0xFF6B778C);
        note.setTextColor(Color.WHITE);
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        note.setInputType(InputType.TYPE_CLASS_TEXT);
        note.setImeOptions(EditorInfo.IME_ACTION_DONE);
        note.setMaxEms(40);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(4);
        content.addView(note, lp);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(16), 0, 0);
        send = button("Show the code", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startSending();
            }
        });
        buttons.addView(send);
        View cancel = button("Cancel", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        ((LinearLayout.LayoutParams) cancel.getLayoutParams()).leftMargin = dp(16);
        buttons.addView(cancel);
        content.addView(buttons);
        send.requestFocus();
    }

    private void showMessage(String headline, String detail) {
        reset();
        TextView h = text(headline, 28, ChooserActivity.ACCENT);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setPadding(0, dp(16), 0, dp(6));
        content.addView(h);
        if (detail != null) content.addView(text(detail, 19, ChooserActivity.GREY));
    }

    private void showDone(String headline, String detail) {
        showMessage(headline, detail);
        View done = button("Done", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        ((LinearLayout.LayoutParams) done.getLayoutParams()).topMargin = dp(20);
        content.addView(done);
        done.requestFocus();
    }

    /** The QR code, with the short address beside it. why: null normally, or why an automatic send failed. */
    private void showQr(String why, String title, String body) {
        reset();
        String url = ReportBuilder.fallbackUrl(ReportSender.REPORT_REPO, title, body, QR_URL_MAX);
        QrCode qr = null;
        try {
            qr = QrCode.encodeText(url, QrCode.Ecc.LOW);
        } catch (RuntimeException e) {
            Log.w(VoiceService.TAG, "report: QR code didn't fit", e);
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(12), 0, 0);
        content.addView(row);
        if (qr != null) {
            row.addView(new QrView(this, qr), new LinearLayout.LayoutParams(dp(340), dp(340)));
        }
        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setPadding(dp(32), 0, 0, 0);
        row.addView(side, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView h = text(why == null ? "Scan this with your phone" : "Couldn't send it directly: " + why + ".",
                20, ChooserActivity.ACCENT);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        side.addView(h);
        TextView how = text(qr != null
                ? "Point your phone's camera at the code. GitHub opens a new issue with the report filled in (shortened to fit); sign in to your own GitHub account, check it, then tap Submit new issue."
                : "Open the address below on your phone and describe what went wrong.", 18, ChooserActivity.GREY);
        how.setPadding(0, dp(8), 0, dp(8));
        side.addView(how);
        side.addView(text(ReportBuilder.shortAddress(ReportSender.REPORT_REPO), 20, Color.WHITE));
        View done = button("Done", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        ((LinearLayout.LayoutParams) done.getLayoutParams()).topMargin = dp(20);
        side.addView(done);
        done.requestFocus();
    }

    // ----- sending -----

    private void startSending() {
        if (working) return;
        working = true;
        final String typed = note.getText().toString();
        showMessage("Getting the report ready\u2026", "Collecting the recent log.");
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String[] titleBody = collect(app, typed);
                final ReportSender.Result r = ReportSender.send(titleBody[0], titleBody[1]);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        working = false;
                        if (isFinishing()) return;
                        if (r.posted) {
                            showDone("Sent. Thank you",
                                    r.issueNumber > 0 ? "Your report is issue #" + r.issueNumber + "." : null);
                        } else {
                            showQr(r.why, titleBody[0], titleBody[1]);
                        }
                    }
                });
            }
        }).start();
    }

    /** The report's title and body. Slow (runs logcat), so not on the main thread. */
    private static String[] collect(Context c, String typedNote) {
        String appVersion = "unknown";
        try {
            appVersion = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
            // leaves "unknown"
        }
        Targets.Target def = ChooserActivity.current(c);
        String os = getprop("ro.build.version.name");
        List<String> installed = new ArrayList<>();
        List<Targets.Target> all = new ArrayList<>();
        java.util.Collections.addAll(all, Targets.ALL);
        all.add(Targets.YOUTUBE);
        java.util.Collections.addAll(all, Targets.NO_SEARCH);
        for (Targets.Target t : all) {
            if (t.mode != Targets.Mode.AMAZON_SEARCH && ChooserActivity.isInstalled(c, t.packageName)) installed.add(t.name);
        }
        String facts = ReportBuilder.facts(
                "Voice Search", appVersion,
                "Stick model", Build.MODEL,
                "Fire OS", os.isEmpty() ? Build.DISPLAY : os,
                "Android", Build.VERSION.RELEASE,
                "Language", Locale.getDefault().toLanguageTag(),
                "Default app", def == null ? "none" : def.name,
                "Say the app's name", ChooserActivity.appNamesEnabled(c) ? "on" : "off",
                "Supported apps installed", installed.isEmpty() ? "none" : join(installed),
                "debug.alm.enable", getprop("debug.alm.enable"));
        String[] secrets = {
            getprop("ro.serialno"), getprop("ro.boot.serialno"),
            Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ANDROID_ID),
        };
        String body = ReportBuilder.body(facts, typedNote, ownLog(), moduleLog(c), secrets, BODY_MAX);
        String title = ReportBuilder.title(appVersion, Build.MODEL, os, def == null ? "none" : def.name);
        return new String[] {title, body};
    }

    private static String join(List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (String s : items) sb.append(sb.length() == 0 ? "" : ", ").append(s);
        return sb.toString();
    }

    /** The app's own recent log lines (an app can only read its own). */
    private static String ownLog() {
        try {
            Process p = new ProcessBuilder("logcat", "-d", "-v", "time", "-t", String.valueOf(LOG_LINES))
                    .redirectErrorStream(true).start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append('\n');
            }
            p.destroy();
            return sb.toString();
        } catch (IOException | RuntimeException e) {
            return "(couldn't read the log: " + e.getClass().getSimpleName() + ")\n";
        }
    }

    /** The add-on script's rolling log, written by service.sh into the shared folder. */
    private static String moduleLog(Context c) {
        File f = new File(Updater.dir(c), "module.log");
        if (!f.isFile()) return "";
        try (InputStream in = new FileInputStream(f)) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0 && bytes.size() < 200000) bytes.write(buf, 0, n);
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static String getprop(String name) {
        try {
            Process p = new ProcessBuilder("getprop", name).redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line = r.readLine();
                p.destroy();
                return line == null ? "" : line.trim();
            }
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    // ----- small helpers (the look matches ChooserActivity) -----

    private View button(String label, View.OnClickListener onClick) {
        TextView b = text(label, 22, Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(48), dp(14), dp(48), dp(14));
        final GradientDrawable bg = new GradientDrawable();
        bg.setColor(ChooserActivity.CARD);
        bg.setCornerRadius(dp(16));
        b.setBackground(bg);
        b.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        b.setFocusable(true);
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                bg.setStroke(hasFocus ? dp(4) : 0, Color.WHITE);
                bg.setColor(hasFocus ? ChooserActivity.ACCENT : ChooserActivity.CARD);
                ((TextView) v).setTextColor(hasFocus ? ChooserActivity.BACKGROUND : Color.WHITE);
            }
        });
        b.setOnClickListener(onClick);
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
