package com.metaljay.stremiovoice;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashSet;
import java.util.Set;

/**
 * The "Voice Search" screen in the Fire TV apps row: choose which app the
 * voice button searches in. The choice is saved at once and read on every
 * search, so nothing needs restarting. Also holds the small helpers the
 * service uses to read that choice.
 */
public class ChooserActivity extends Activity {

    private static final String PREFS = "voice_search";
    private static final String KEY_TARGET = "target";

    static final int BACKGROUND = 0xFF0B0F1A;
    static final int CARD = 0xFF161D2E;
    static final int ACCENT = 0xFF00CAFF;
    static final int GREY = 0xFFA9B4C8;

    private LinearLayout rows;
    private TextView status;

    // ----- helpers shared with the service -----

    static String savedTargetId(Context context) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_TARGET, null);
    }

    static void saveTargetId(Context context, String id) {
        context.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_TARGET, id).apply();
    }

    static boolean isInstalled(Context context, String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    static Set<String> installedTargets(Context context) {
        Set<String> out = new HashSet<>();
        for (Targets.Target t : Targets.ALL) {
            if (isInstalled(context, t.packageName)) out.add(t.packageName);
        }
        return out;
    }

    /** The app the next search will use, or null if no supported app is installed. */
    static Targets.Target current(Context context) {
        return Targets.choose(savedTargetId(context), installedTargets(context));
    }

    // ----- the screen -----

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);
        root.setPadding(dp(96), dp(64), dp(96), dp(48));

        TextView title = text("Voice Search", 34, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView intro = text("Hold the voice button and say a title. Results open in:", 18, GREY);
        intro.setPadding(0, dp(12), 0, dp(28));
        root.addView(intro);

        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        root.addView(rows);

        status = text("", 16, GREY);
        status.setPadding(0, dp(32), 0, 0);
        root.addView(status);

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh(null);
    }

    /** Rebuilds the rows. Focus goes to the row for focusId, else the current choice. */
    private void refresh(String focusId) {
        rows.removeAllViews();
        Targets.Target current = current(this);
        boolean chosenByHand = Targets.byId(savedTargetId(this)) == current && current != null;
        View focus = null;

        for (final Targets.Target t : Targets.ALL) {
            boolean installed = isInstalled(this, t.packageName);
            boolean selected = t == current;
            View row = row(t, installed, selected);
            rows.addView(row);
            if (installed && (t.id.equals(focusId) || (focusId == null && selected))) focus = row;
        }
        if (focus == null && rows.getChildCount() > 0) focus = rows.getChildAt(0);
        if (focus != null) focus.requestFocus();

        String app = current == null ? "none: install Stremio or Nuvio"
                : current.name + (chosenByHand ? "" : " (chosen automatically)");
        status.setText("Searching in: " + app + "\nVoice button: "
                + (serviceRunning() ? "ready" : "not running"));
    }

    private View row(final Targets.Target t, boolean installed, boolean selected) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(28), dp(20), dp(28), dp(20));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(16);
        row.setLayoutParams(lp);

        final GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(16));
        row.setBackground(bg);

        TextView mark = text(selected ? "✓" : "", 28, ACCENT);
        mark.setTypeface(Typeface.DEFAULT_BOLD);
        mark.setWidth(dp(56));
        row.addView(mark);

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text(t.name, 24, Color.WHITE));
        words.addView(text(installed ? t.description : "Not installed", 16, GREY));
        row.addView(words);

        if (!installed) {
            row.setAlpha(0.4f);
            row.setFocusable(false);
            return row;
        }
        row.setFocusable(true);
        row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                bg.setStroke(hasFocus ? dp(4) : 0, Color.WHITE);
            }
        });
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveTargetId(ChooserActivity.this, t.id);
                refresh(t.id);
            }
        });
        return row;
    }

    @SuppressWarnings("deprecation") // still returns this app's own services, which is all we need
    private boolean serviceRunning() {
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo s : am.getRunningServices(50)) {
            if (VoiceService.class.getName().equals(s.service.getClassName())) return true;
        }
        return false;
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
