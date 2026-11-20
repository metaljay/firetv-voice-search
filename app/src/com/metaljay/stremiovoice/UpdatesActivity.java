package com.metaljay.stremiovoice;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.IOException;

/**
 * The Updates screen, opened from the settings screen or from the "Voice Search is
 * ready" notification: the installed version, when it last checked, how often to check
 * (Daily, Weekly, Monthly, Off), Check now, the offer (Install, Not now, Skip this
 * version), the restart choice, and Go back when a previous version is kept.
 *
 * Nothing installs without a tap here. The install itself is done by the add-on's
 * service.sh (see Updater); this screen only asks and shows what it says back.
 */
public class UpdatesActivity extends Activity {

    private static final int ORANGE = 0xFFFFB84D;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private LinearLayout body;

    /** What the last action said, shown near the top; empty for nothing. */
    private String message = "";
    private int messageColor = ChooserActivity.ACCENT;
    /** Something is downloading or installing: show only the message. */
    private boolean busy = false;
    /** A check was run on this screen, so a skipped version is shown again (Check now still shows it). */
    private boolean checkedHere = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ChooserActivity.BACKGROUND);
        root.setPadding(dp(96), dp(44), dp(96), dp(32));

        TextView title = text("Updates", 34, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, dp(12), 0, 0);
        root.addView(body);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(ChooserActivity.BACKGROUND);
        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String notice = Updater.takeNotice(this);
        if (notice != null) say(notice, ORANGE);
        refresh(null);
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void say(String text, int color) {
        message = text;
        messageColor = color;
    }

    // ----- drawing -----

    /** Rebuilds the screen. Focus goes to the view tagged focusTag, else the first button. */
    private void refresh(String focusTag) {
        body.removeAllViews();
        View focus = null;

        body.addView(line("Installed: version " + Updater.installedVersion(this), 20, Color.WHITE));
        body.addView(line("Last checked: " + Updater.lastCheckText(this), 16, ChooserActivity.GREY));
        if (!message.isEmpty()) {
            TextView m = line(message, 20, messageColor);
            m.setTypeface(Typeface.DEFAULT_BOLD);
            m.setPadding(0, dp(12), 0, 0);
            body.addView(m);
        }

        if (busy) return; // only the message; the back button still works

        String staged = Updater.stagedVersion(this);
        Updater.Release offer = Updater.available(this);
        if (offer != null && Updater.isSkipped(this, offer.version) && !checkedHere) offer = null;
        String previous = Updater.previousVersion(this);

        if (staged != null) {
            // An update (or going back) is installed but needs a restart to take effect.
            boolean newer = Versions.isNewer(staged, Updater.installedVersion(this));
            body.addView(card(newer ? "Update to " + staged + " isn't finished until you restart."
                    : "Going back to " + staged + " isn't finished until you restart."));
            View now = button("Restart now", "restart", new Runnable() {
                @Override
                public void run() {
                    restartNow();
                }
            });
            View later = button("Later", "later", new Runnable() {
                @Override
                public void run() {
                    finish();
                }
            });
            body.addView(buttonRow(now, later));
            focus = now;
        } else if (offer != null) {
            final Updater.Release shown = offer;
            body.addView(card(offerText(shown)));
            View install = button("Install", "install", new Runnable() {
                @Override
                public void run() {
                    install(shown);
                }
            });
            View notNow = button("Not now", "notnow", new Runnable() {
                @Override
                public void run() {
                    finish();
                }
            });
            View skip = button("Skip this version", "skip", new Runnable() {
                @Override
                public void run() {
                    Updater.skip(UpdatesActivity.this, shown.version);
                    say("Skipped version " + shown.version + ". Check now will still show it.", ChooserActivity.GREY);
                    refresh("check");
                }
            });
            body.addView(buttonRow(install, notNow, skip));
            focus = install;
        }

        // How often to check.
        TextView freqLabel = line("Check for updates:", 18, ChooserActivity.GREY);
        freqLabel.setPadding(0, dp(20), 0, dp(8));
        body.addView(freqLabel);
        String freq = Updater.frequency(this);
        View[] choices = new View[4];
        String[] ids = {UpdateSchedule.DAILY, UpdateSchedule.WEEKLY, UpdateSchedule.MONTHLY, UpdateSchedule.OFF};
        String[] names = {"Daily", "Weekly", "Monthly", "Off"};
        for (int i = 0; i < ids.length; i++) {
            final String id = ids[i];
            choices[i] = button((id.equals(freq) ? "✓ " : "") + names[i], "freq-" + id, new Runnable() {
                @Override
                public void run() {
                    Updater.setFrequency(UpdatesActivity.this, id);
                    say("", ChooserActivity.ACCENT);
                    refresh("freq-" + id);
                }
            });
        }
        body.addView(buttonRow(choices));

        // Check now, Go back, Done.
        View check = button("Check now", "check", new Runnable() {
            @Override
            public void run() {
                checkNow();
            }
        });
        View[] bottom;
        if (previous != null && staged == null) {
            View back = button("Go back to version " + previous, "goback", new Runnable() {
                @Override
                public void run() {
                    goBack();
                }
            });
            bottom = new View[]{check, back, doneButton()};
        } else {
            bottom = new View[]{check, doneButton()};
        }
        LinearLayout bottomRow = buttonRow(bottom);
        bottomRow.setPadding(0, dp(20), 0, 0);
        body.addView(bottomRow);

        if (focus == null) focus = check;
        if (focusTag != null) {
            View tagged = body.findViewWithTag(focusTag);
            if (tagged != null) focus = tagged;
        }
        focus.requestFocus();
    }

    private String offerText(Updater.Release r) {
        StringBuilder sb = new StringBuilder();
        sb.append(ReleaseNotes.isUrgent(r.notes) ? "Urgent fix: version " : "Version ").append(r.version).append(" is ready");
        String summary = ReleaseNotes.summary(r.notes);
        if (!summary.isEmpty()) sb.append("\n\n").append(summary);
        return sb.toString();
    }

    // ----- actions (slow ones run off the main thread) -----

    private void checkNow() {
        busy = true;
        say("Checking…", ChooserActivity.ACCENT);
        refresh(null);
        new Thread(new Runnable() {
            @Override
            public void run() {
                String problem = null;
                Updater.Release found = null;
                try {
                    found = Updater.check(UpdatesActivity.this);
                } catch (IOException e) {
                    problem = Updater.isOffline(e) ? "no connection" : e.getMessage();
                }
                final String why = problem;
                final Updater.Release release = found;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        busy = false;
                        checkedHere = true;
                        if (why != null) {
                            say("Couldn't check: " + why, ORANGE);
                        } else if (release == null) {
                            say("You're up to date.", ChooserActivity.ACCENT);
                        } else {
                            say("", ChooserActivity.ACCENT);
                        }
                        refresh(release == null ? "check" : "install");
                    }
                });
            }
        }, "update-check-now").start();
    }

    private void install(final Updater.Release release) {
        busy = true;
        say("Downloading version " + release.version + "…", ChooserActivity.ACCENT);
        refresh(null);
        new Thread(new Runnable() {
            @Override
            public void run() {
                String problem = null;
                try {
                    String sha = Updater.downloadAndVerify(UpdatesActivity.this, release, null);
                    postMessage("Installing…", ChooserActivity.ACCENT);
                    String token = Updater.request(UpdatesActivity.this, "install " + sha);
                    problem = Updater.waitForResult(UpdatesActivity.this, token, 180000);
                } catch (IOException e) {
                    problem = Updater.isOffline(e) ? "no connection" : e.getMessage();
                }
                final String why = problem;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        busy = false;
                        if (why == null) {
                            say("Version " + release.version + " is installed. Restart to finish.", ChooserActivity.ACCENT);
                            refresh("restart");
                        } else {
                            say("Couldn't install: " + why + ". Your current version is unchanged.", ORANGE);
                            refresh("install");
                        }
                    }
                });
            }
        }, "update-install").start();
    }

    private void goBack() {
        final String previous = Updater.previousVersion(this);
        if (previous == null) return;
        busy = true;
        say("Getting version " + previous + " ready…", ChooserActivity.ACCENT);
        refresh(null);
        // The version being left is not offered again at once.
        Updater.skip(this, Updater.installedVersion(this));
        new Thread(new Runnable() {
            @Override
            public void run() {
                String problem;
                try {
                    String token = Updater.request(UpdatesActivity.this, "goback");
                    problem = Updater.waitForResult(UpdatesActivity.this, token, 120000);
                } catch (IOException e) {
                    problem = e.getMessage();
                }
                final String why = problem;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        busy = false;
                        if (why == null) {
                            say("Version " + previous + " is ready. Restart to go back.", ChooserActivity.ACCENT);
                            refresh("restart");
                        } else {
                            say("Couldn't go back: " + why, ORANGE);
                            refresh("goback");
                        }
                    }
                });
            }
        }, "update-goback").start();
    }

    private void restartNow() {
        busy = true;
        say("Restarting…", ChooserActivity.ACCENT);
        refresh(null);
        new Thread(new Runnable() {
            @Override
            public void run() {
                String problem;
                try {
                    String token = Updater.request(UpdatesActivity.this, "restart");
                    // The add-on answers first, then restarts; no answer in 30 seconds means it isn't there.
                    problem = Updater.waitForResult(UpdatesActivity.this, token, 30000);
                } catch (IOException e) {
                    problem = e.getMessage();
                }
                if (problem == null) return; // answered "ok": the stick is restarting
                final String why = problem;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        busy = false;
                        say("Couldn't restart: " + why, ORANGE);
                        refresh("restart");
                    }
                });
            }
        }, "update-restart").start();
    }

    private void postMessage(final String text, final int color) {
        ui.post(new Runnable() {
            @Override
            public void run() {
                say(text, color);
                refresh(null);
            }
        });
    }

    // ----- the look: the same cards, focus outline and helpers as ChooserActivity -----

    private TextView line(String s, int sp, int color) {
        TextView v = text(s, sp, color);
        v.setPadding(0, dp(4), 0, 0);
        return v;
    }

    private View card(String s) {
        TextView v = text(s, 20, Color.WHITE);
        v.setPadding(dp(28), dp(18), dp(28), dp(18));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(ChooserActivity.CARD);
        bg.setCornerRadius(dp(16));
        v.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(16);
        lp.bottomMargin = dp(12);
        v.setLayoutParams(lp);
        return v;
    }

    private LinearLayout buttonRow(View... buttons) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (View b : buttons) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(16);
            row.addView(b, lp);
        }
        return row;
    }

    private View doneButton() {
        return button("Done", "done", new Runnable() {
            @Override
            public void run() {
                finish();
            }
        });
    }

    private View button(String label, String tag, final Runnable onClick) {
        TextView b = text(label, 22, Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(36), dp(14), dp(36), dp(14));
        final GradientDrawable bg = new GradientDrawable();
        bg.setColor(ChooserActivity.CARD);
        bg.setCornerRadius(dp(16));
        b.setBackground(bg);
        b.setTag(tag);
        b.setFocusable(true);
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                bg.setStroke(hasFocus ? dp(4) : 0, Color.WHITE);
                bg.setColor(hasFocus ? ChooserActivity.ACCENT : ChooserActivity.CARD);
                ((TextView) v).setTextColor(hasFocus ? ChooserActivity.BACKGROUND : Color.WHITE);
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
