package com.metaljay.stremiovoice;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
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

import java.io.IOException;
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
    // The app the voice button would really search in right now (the saved choice, or the fallback
    // when nothing is saved or the saved app is gone). The add-on's script reads this, not
    // "target", to switch the TiviMate helper on and off. "none" when no supported app is installed.
    private static final String KEY_ACTIVE_TARGET = "active_target";
    // Whether saying "on <app>" picks the app (default on). Off: the name is dropped and the default is used.
    private static final String KEY_APP_NAMES = "app_names";

    // TiviMate only: get it ready while the user talks. "idle" (default) or "always", see TiviMatePrewarm.
    static final String KEY_TIVIMATE_PREWARM = "tivimate_prewarm";

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
        context.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_TARGET, id).commit();
        publishActiveTarget(context);
    }

    static boolean appNamesEnabled(Context context) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_APP_NAMES, true);
    }

    static void setAppNamesEnabled(Context context, boolean on) {
        context.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_APP_NAMES, on).commit();
    }

    static String tivimatePrewarm(Context context) {
        return TiviMatePrewarm.normalise(
                context.getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_TIVIMATE_PREWARM, null));
    }

    static void setTivimatePrewarm(Context context, String mode) {
        context.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_TIVIMATE_PREWARM, mode).commit();
    }

    /** Writes the app the next search would use, for the add-on's script. Call when it may have changed. */
    static void publishActiveTarget(Context context) {
        publishActiveTarget(context, null);
    }

    /**
     * Same, but with an app that was just reached by name: the script then sees that app (so the
     * TiviMate helper is on while a by-name TiviMate search is under way), until the next call
     * without one puts the default back.
     */
    static void publishActiveTarget(Context context, Targets.Target byName) {
        Targets.Target t = byName != null ? byName : current(context);
        context.getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_ACTIVE_TARGET, t == null ? "none" : t.id).commit();
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
        root.setPadding(dp(96), dp(28), dp(96), dp(24));

        TextView title = text("Voice Search", 34, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView intro = text("Hold the voice button and say a title, or a title and \"on\" an app. Results open in:", 18, GREY);
        intro.setPadding(0, dp(4), 0, dp(12));
        root.addView(intro);

        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        root.addView(rows);

        // Bottom: a row of buttons (Done, Updates, report), and under it which app has the voice
        // button, update news and how to come back.
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(0, dp(8), 0, 0);
        root.addView(bottom);

        done = doneButton();
        bottom.addView(done);
        bottom.addView(updatesButton());
        bottom.addView(supportButton());
        bottom.addView(reportButton());
        bottom.addView(removeButton());

        LinearLayout notes = new LinearLayout(this);
        notes.setOrientation(LinearLayout.VERTICAL);
        notes.setPadding(0, dp(10), 0, 0);
        root.addView(notes);

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

        rows.addView(namesRow());
        // The apps sit two to a line, so all of them (and the buttons below) fit without scrolling.
        LinearLayout pair = null;
        for (int i = 0; i < Targets.ALL.length; i++) {
            final Targets.Target t = Targets.ALL[i];
            boolean installed = isInstalled(this, t.packageName);
            boolean selected = t == current;
            View row = row(t, installed, selected);
            if (i % 2 == 0) {
                pair = new LinearLayout(this);
                pair.setOrientation(LinearLayout.HORIZONTAL);
                pair.setBaselineAligned(false);
                LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                plp.bottomMargin = dp(6);
                rows.addView(pair, plp);
            } else {
                ((LinearLayout.LayoutParams) row.getLayoutParams()).leftMargin = dp(10);
            }
            pair.addView(row);
            if (installed && (t.id.equals(focusId) || (focusId == null && selected))) focus = row;
        }
        if (Targets.ALL.length % 2 == 1) { // an empty half keeps the last app the same width as the others
            View gap = new View(this);
            LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(0, 1, 1f);
            glp.leftMargin = dp(10);
            pair.addView(gap, glp);
        }
        if (current == Targets.TIVIMATE) rows.addView(prewarmRow()); // TiviMate's own setting
        if (focus == null && rows.getChildCount() > 1) focus = rows.getChildAt(1); // the first app line
        if (focus != null) focus.requestFocus();

        saved.setText(current == null ? "Install Stremio, Nuvio, WuPlay, TiviMate or SmartTube to use voice search."
                : "The voice button searches in " + current.name + ".");
    }

    private View row(final Targets.Target t, boolean installed, boolean selected) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(6), dp(16), dp(6));
        // Half the width each (the caller puts two in a line); as tall as the taller of the two.
        row.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        final GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(16));
        row.setBackground(bg);

        TextView mark = text(selected ? "✓" : "", 26, ACCENT);
        mark.setTypeface(Typeface.DEFAULT_BOLD);
        mark.setWidth(dp(36));
        row.addView(mark);

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text(t.name, 22, Color.WHITE));
        words.addView(text(installed ? t.description : "Not installed", 14, GREY));
        row.addView(words, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

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

    /** The "Say the app's name" switch, above the app rows. A press flips it and saves at once. */
    private View namesRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(28), dp(5), dp(28), dp(5));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        row.setLayoutParams(lp);

        final GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(16));
        row.setBackground(bg);

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text("Say the app's name", 24, Color.WHITE));
        words.addView(text("\"Bluey on Netflix\" opens that app's results; off, the app below is always used", 16, GREY));
        row.addView(words, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        final TextView value = text(appNamesEnabled(this) ? "On" : "Off", 24, ACCENT);
        value.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(value);

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
                boolean on = !appNamesEnabled(ChooserActivity.this);
                setAppNamesEnabled(ChooserActivity.this, on);
                value.setText(on ? "On" : "Off");
                saved.setText("Saved \u2713  " + (on ? "Saying an app's name now picks it."
                        : "The app's name is now ignored; the app below is always used."));
            }
        });
        return row;
    }

    /** TiviMate only: "Get TiviMate ready while I talk", When nothing is playing / Always. A press flips it. */
    private View prewarmRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(28), dp(5), dp(28), dp(5));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        row.setLayoutParams(lp);

        final GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(16));
        row.setBackground(bg);

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text("Get TiviMate ready while I talk", 24, Color.WHITE));
        words.addView(text("Always leaves a playing channel as soon as you press the button", 16, GREY));
        row.addView(words, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        final TextView value = text(prewarmLabel(tivimatePrewarm(this)), 24, ACCENT);
        value.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(value);

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
                String now = TiviMatePrewarm.ALWAYS.equals(tivimatePrewarm(ChooserActivity.this))
                        ? TiviMatePrewarm.IDLE : TiviMatePrewarm.ALWAYS;
                setTivimatePrewarm(ChooserActivity.this, now);
                value.setText(prewarmLabel(now));
                saved.setText("Saved \u2713  TiviMate gets ready while you talk: " + prewarmLabel(now) + ".");
            }
        });
        return row;
    }

    private static String prewarmLabel(String mode) {
        return TiviMatePrewarm.ALWAYS.equals(mode) ? "Always" : "When nothing is playing";
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

    /** "Support": opens the screen with the Buy Me a Coffee code. */
    private View supportButton() {
        View b = actionButton("\u2615 Support", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ChooserActivity.this, SupportActivity.class));
            }
        });
        ((LinearLayout.LayoutParams) b.getLayoutParams()).leftMargin = dp(16);
        return b;
    }

    /** "Send a problem report": opens the report screen. */
    private View reportButton() {
        View b = actionButton("Send a problem report", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ChooserActivity.this, ReportActivity.class));
            }
        });
        ((LinearLayout.LayoutParams) b.getLayoutParams()).leftMargin = dp(16);
        return b;
    }

    /** "Remove Voice Search": asks first, then has the add-on take itself away at the next restart. */
    private View removeButton() {
        View b = actionButton("Remove Voice Search", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(ChooserActivity.this)
                        .setTitle("Remove Voice Search?")
                        .setMessage("Remove Voice Search? Alexa gets the button back after a restart.")
                        .setPositiveButton("Remove", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                remove();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
        ((LinearLayout.LayoutParams) b.getLayoutParams()).leftMargin = dp(16);
        return b;
    }

    /** Sends the "remove" request (the add-on does it as root); no root is needed here. */
    private void remove() {
        updateNote.setText("Removing\u2026");
        updateNote.setVisibility(View.VISIBLE);
        new Thread(new Runnable() {
            @Override
            public void run() {
                String problem;
                try {
                    String token = Updater.request(ChooserActivity.this, "remove");
                    problem = Updater.waitForResult(ChooserActivity.this, token, 30000);
                } catch (IOException e) {
                    problem = e.getMessage();
                }
                final String why = problem;
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || isDestroyed()) return;
                        if (why != null) {
                            updateNote.setText("Couldn't remove it: " + why);
                            return;
                        }
                        updateNote.setText("Voice Search will be removed at the next restart.");
                        new AlertDialog.Builder(ChooserActivity.this)
                                .setTitle("Voice Search will be removed")
                                .setMessage("Restart the stick to finish. After that you can uninstall the Voice Search "
                                        + "app from Fire TV Settings, Applications.")
                                .setPositiveButton("Restart now", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d, int which) {
                                        restart();
                                    }
                                })
                                .setNegativeButton("Later", null)
                                .show();
                    }
                });
            }
        }, "remove-request").start();
    }

    /** Asks the add-on to restart the stick (the same request the Updates screen uses). */
    private void restart() {
        updateNote.setText("Restarting\u2026");
        new Thread(new Runnable() {
            @Override
            public void run() {
                String problem;
                try {
                    String token = Updater.request(ChooserActivity.this, "restart");
                    problem = Updater.waitForResult(ChooserActivity.this, token, 30000);
                } catch (IOException e) {
                    problem = e.getMessage();
                }
                if (problem == null) return; // answered "ok": the stick is restarting
                final String why = problem;
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        updateNote.setText("Couldn't restart: " + why);
                    }
                });
            }
        }, "remove-restart").start();
    }

    private View actionButton(String label, View.OnClickListener onClick) {
        TextView b = text(label, 22, Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(24), dp(12), dp(24), dp(12));
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
