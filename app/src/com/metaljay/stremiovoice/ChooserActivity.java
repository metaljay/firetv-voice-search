package com.metaljay.stremiovoice;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.HashSet;
import java.util.Set;

/**
 * The Voice Search settings screen: choose which app the voice button
 * searches in. Opened from the small "Voice Search" tile app (Amazon's home
 * screen hides system apps such as this one) or by saying "Voice Search
 * settings". The choice is saved at once and read on every
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
    private TextView saved;
    private TextView updateNote;
    private View done;

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
        root.setPadding(dp(96), dp(44), dp(96), dp(32));

        TextView title = text("Voice Search", 34, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView intro = text("Hold the voice button and say a title. Results open in:", 18, GREY);
        intro.setPadding(0, dp(8), 0, dp(20));
        root.addView(intro);

        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        root.addView(rows);

        // Bottom row: the Done button, and beside it which app has the button, and how to come back.
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(0, dp(8), 0, 0);
        root.addView(bottom);

        done = doneButton();
        bottom.addView(done);
        bottom.addView(updatesButton());

        LinearLayout notes = new LinearLayout(this);
        notes.setOrientation(LinearLayout.VERTICAL);
        notes.setPadding(dp(32), 0, 0, 0);
        bottom.addView(notes, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // Which app has the voice button; starts with "Saved" once a choice is made.
        saved = text("", 20, ACCENT);
        saved.setTypeface(Typeface.DEFAULT_BOLD);
        notes.addView(saved);

        // Update news: "Update available", "isn't finished until you restart", or a went-back notice.
        updateNote = text("", 18, ACCENT);
        updateNote.setPadding(0, dp(4), 0, 0);
        notes.addView(updateNote);

        TextView hint = text("To come back here, hold the voice button and say \"Voice settings\".", 15, GREY);
        hint.setPadding(0, dp(4), 0, 0);
        notes.addView(hint);

        // Scrolls if a smaller screen can't fit it all; the remote's focus scrolls it.
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);
        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh(null);
        String notice = Updater.takeNotice(this);
        String line = notice != null ? notice : Updater.settingsLine(this);
        updateNote.setText(line == null ? "" : line);
        updateNote.setVisibility(line == null ? View.GONE : View.VISIBLE);
    }

    /** Rebuilds the rows. Focus goes to the row for focusId, else the current choice. */
    private void refresh(String focusId) {
        rows.removeAllViews();
        Targets.Target current = current(this);
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

        saved.setText(current == null ? "Install Stremio, Nuvio or WuPlay to use voice search."
                : "The voice button searches in " + current.name + ".");
    }

    private View row(final Targets.Target t, boolean installed, boolean selected) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(28), dp(14), dp(28), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
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
                saved.setText("Saved \u2713  The voice button now searches in " + t.name + ".");
                done.requestFocus(); // one more press closes the screen
            }
        });
        return row;
    }

    /** "Done": closes the screen, back to where it was opened from (Home, from the tile). */
    private View doneButton() {
        return actionButton("Done", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    /** "Updates": opens the Updates screen. */
    private View updatesButton() {
        View b = actionButton("Updates", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ChooserActivity.this, UpdatesActivity.class));
            }
        });
        ((LinearLayout.LayoutParams) b.getLayoutParams()).leftMargin = dp(16);
        return b;
    }

    private View actionButton(String label, View.OnClickListener onClick) {
        TextView b = text(label, 22, Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(48), dp(14), dp(48), dp(14));
        final GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(16));
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        b.setLayoutParams(lp);
        b.setFocusable(true);
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                bg.setStroke(hasFocus ? dp(4) : 0, Color.WHITE);
                bg.setColor(hasFocus ? ACCENT : CARD);
                ((TextView) v).setTextColor(hasFocus ? BACKGROUND : Color.WHITE);
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
