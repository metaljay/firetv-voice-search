package com.metaljay.stremiovoice;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.KeyEvent;

import java.util.ArrayList;

/**
 * Foreground service. The system sends a broadcast when the remote's voice
 * button goes down and up (the diversion switch must be on). On button-down
 * we start Alexa's speech-to-text service, on button-up we stop it, and when
 * the words come back we open the chosen app's search for them.
 *
 * Everything here runs on the main thread, as the speech recogniser requires.
 */
public class VoiceService extends Service {

    static final String TAG = "VoiceSearch";

    /**
     * Test entry: root sends this (extra "words") to run the same path as spoken words, without
     * the remote. The service is not exported, so only root and the system can send it:
     * su -c am start-foreground-service -n com.metaljay.stremiovoice/.VoiceService
     *      -a com.metaljay.stremiovoice.SEARCH --es words "sky news"
     */
    static final String ACTION_SEARCH = "com.metaljay.stremiovoice.SEARCH";
    static final String EXTRA_WORDS = "words";

    private static final String ACTION_KEY_INFO = "com.amazon.tv.alm.action.KEY_INFO";
    private static final String EXTRA_KEY_EVENT = "KeyEvent";
    private static final ComponentName SPEECH_SERVICE = new ComponentName(
            "com.amazon.vizzini", "com.amazon.vizzini.voiceservice.SpeechToTextService");
    // Keeps its original id on purpose: renaming it would leave a stale channel on sticks that update.
    private static final String CHANNEL_ID = "stremio_voice";
    private static final int NOTIFICATION_ID = 1;

    /** How long to wait for words after the button is released. */
    private static final long RESULT_TIMEOUT_MS = 5000;
    /** Safety net in case the button-up broadcast never arrives. */
    private static final long MAX_HOLD_MS = 20000;
    /** How long the bar stays up after the chosen app has been asked to open. */
    private static final long BAR_LINGER_MS = 3000;
    /** TiviMate's main screen: its only screen another app may open. */
    private static final String TIVIMATE_MAIN = "ar.tvplayer.tv.ui.MainActivity";
    /** Shown when nothing usable was heard. */
    private static final String MSG_NOT_CAUGHT = "Didn't catch that. Try again";

    /** How long the TiviMate helper stays on after TiviMate was reached by name (it is not the default). */
    private static final long BY_NAME_HELPER_MS = 60000;

    private final Handler main = new Handler(Looper.getMainLooper());
    /** Separate from main, whose callbacks are cleared on every press: puts the default back for the script. */
    private final Handler later = new Handler(Looper.getMainLooper());
    private final Runnable republishDefault = new Runnable() {
        @Override
        public void run() {
            ChooserActivity.publishActiveTarget(VoiceService.this);
        }
    };
    private ListeningBar bar;
    private SpeechRecognizer recognizer;

    /** Counts button presses, so late callbacks from an older press can be ignored. */
    private int session = 0;
    private boolean active = false;   // a press is being handled
    private boolean released = false; // its button-up has arrived

    /** An app was installed, removed or replaced: the app the button searches in may have changed. */
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Uri data = intent.getData();
            String pkg = data == null ? null : data.getSchemeSpecificPart();
            for (Targets.Target t : Targets.ALL) {
                if (t.packageName.equals(pkg)) {
                    ChooserActivity.publishActiveTarget(VoiceService.this);
                    Targets.Target now = ChooserActivity.current(VoiceService.this);
                    Log.i(TAG, "a supported app changed (" + pkg + "), now searching in " + (now == null ? "nothing" : now.name));
                    return;
                }
            }
        }
    };

    private final BroadcastReceiver keyReceiver = new BroadcastReceiver() {
        @Override
        @SuppressWarnings("deprecation") // the typed overload only exists from Android 13; the stick runs 11
        public void onReceive(Context context, Intent intent) {
            KeyEvent event = intent.getParcelableExtra(EXTRA_KEY_EVENT);
            if (event == null) {
                Log.w(TAG, "KEY_INFO without a KeyEvent");
                return;
            }
            Log.i(TAG, "KEY_INFO keycode=" + event.getKeyCode() + " action=" + event.getAction()
                    + " repeat=" + event.getRepeatCount());
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) onButtonDown(event);
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                onButtonUp();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        bar = new ListeningBar(this);
        startInForeground();
        registerReceiver(keyReceiver, new IntentFilter(ACTION_KEY_INFO));
        IntentFilter packages = new IntentFilter();
        packages.addAction(Intent.ACTION_PACKAGE_ADDED);
        packages.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packages.addAction(Intent.ACTION_PACKAGE_REPLACED);
        packages.addDataScheme("package");
        registerReceiver(packageReceiver, packages);
        ChooserActivity.publishActiveTarget(this); // right after boot, and after any update

        Updater.dir(this); // makes the folder the add-on's script shares with the app
        UpdateJob.schedule(this); // the scheduled update check; does nothing if already scheduled
        Log.i(TAG, "service started");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_SEARCH.equals(intent.getAction())) {
            String words = intent.getStringExtra(EXTRA_WORDS);
            Log.i(TAG, "test search: \"" + words + "\"");
            if (words != null) {
                ArrayList<String> heard = new ArrayList<>();
                heard.add(words);
                handleResult(heard);
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "service stopped");
        try {
            unregisterReceiver(keyReceiver);
            unregisterReceiver(packageReceiver);
        } catch (RuntimeException ignored) {
            // Not registered; nothing to undo.
        }
        main.removeCallbacksAndMessages(null);
        later.removeCallbacksAndMessages(null);
        destroyRecognizer();
        bar.hide();
        super.onDestroy();
    }

    private void startInForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, "Voice Search", NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Voice Search")
                .setContentText("Listening for the voice button")
                .setSmallIcon(Icon.createWithResource("android", android.R.drawable.ic_btn_speak_now))
                .build();
        startForeground(NOTIFICATION_ID, n);
    }

    // ----- one press of the voice button -----

    private void onButtonDown(KeyEvent event) {
        if (active) endSession(true);
        active = true;
        released = false;
        final int mine = ++session;
        main.removeCallbacksAndMessages(null);

        bar.showListening();
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (session == mine && active && !released) {
                    Log.w(TAG, "button-up never arrived, stopping");
                    onButtonUp();
                }
            }
        }, MAX_HOLD_MS);

        try {
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(this, SPEECH_SERVICE);
                recognizer.setRecognitionListener(new Listener());
            }
            Intent request = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(EXTRA_KEY_EVENT, event)
                    .setPackage(SPEECH_SERVICE.getPackageName());
            recognizer.startListening(request);
            Log.i(TAG, "startListening called");
        } catch (RuntimeException e) {
            Log.e(TAG, "could not start listening", e);
            endSession(true, "Voice service not available");
        }
    }

    private void onButtonUp() {
        if (!active || released) return;
        released = true;
        final int mine = session;
        bar.showSearching();
        try {
            recognizer.stopListening();
            Log.i(TAG, "stopListening called");
        } catch (RuntimeException e) {
            Log.e(TAG, "could not stop listening", e);
            endSession(true);
            return;
        }
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (session == mine && active) {
                    Log.w(TAG, "no result within " + RESULT_TIMEOUT_MS + " ms, giving up");
                    endSession(true, "No answer from the voice service");
                }
            }
        }, RESULT_TIMEOUT_MS);
    }

    /** Stop handling the current press. If cancel is true, also abandon the recogniser's work. */
    private void endSession(boolean cancel) {
        endSession(cancel, null);
    }

    /**
     * Stop handling the current press. With a message, the bar shows it for a moment before
     * hiding, so a press that went nowhere is not silent; without one, the bar hides at once.
     */
    private void endSession(boolean cancel, String message) {
        active = false;
        released = false;
        main.removeCallbacksAndMessages(null);
        if (cancel && recognizer != null) {
            try {
                recognizer.cancel();
            } catch (RuntimeException e) {
                Log.w(TAG, "cancel failed", e);
            }
        }
        if (message == null) {
            bar.hide();
            return;
        }
        bar.showMessage(message);
        final int mine = session;
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (session == mine && !active) bar.hide();
            }
        }, BAR_LINGER_MS);
    }

    /** What the bar says when the speech service reports an error. */
    private static String errorMessage(int code) {
        switch (code) {
            case SpeechRecognizer.ERROR_NO_MATCH:
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return MSG_NOT_CAUGHT;
            default:
                return "Voice service error: " + errorName(code);
        }
    }

    private void destroyRecognizer() {
        if (recognizer != null) {
            try {
                recognizer.destroy();
            } catch (RuntimeException e) {
                Log.w(TAG, "destroy failed", e);
            }
            recognizer = null;
        }
    }

    private void handleResult(ArrayList<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            Log.i(TAG, "result was empty");
            endSession(false, MSG_NOT_CAUGHT);
            return;
        }
        String heard = candidates.get(0);
        Log.i(TAG, "heard: \"" + heard + "\"");

        // The app to search in: the one named ("... on Stremio") if saying names is on and the app is
        // installed, else the default. A named app that isn't installed is said so in the bar.
        Targets.Target target = ChooserActivity.current(this);
        String note = null;
        Targets.Target openOnly = null; // a named app with no search: it is opened, nothing is searched
        Targets.Target named = ChooserActivity.appNamesEnabled(this) ? Targets.named(heard) : null;
        if (named != null) {
            if (ChooserActivity.isInstalled(this, named.packageName)) {
                if (named.mode == Targets.Mode.OPEN_ONLY) {
                    openOnly = named;
                } else {
                    target = named;
                }
                Log.i(TAG, "app named: " + named.name);
            } else {
                note = named.name + " isn't installed" + (target == null ? "" : ", searching in " + target.name);
                Log.i(TAG, note);
            }
        }

        boolean forTiviMate = target == Targets.TIVIMATE;
        String words = QueryCleaner.clean(heard, forTiviMate);
        boolean wantChannel = forTiviMate && QueryCleaner.endsWithChannel(heard);
        Log.i(TAG, "search words: \"" + words + "\"");
        if (words.isEmpty()) {
            endSession(false, MSG_NOT_CAUGHT);
            return;
        }
        final int mine = session;
        main.removeCallbacksAndMessages(null);
        if (QueryCleaner.isSettingsRequest(words)) {
            openSettings();
            bar.hide();
        } else if (openOnly != null) {
            bar.showMessage(openApp(openOnly) ? openOnly.name + " has no search, opened it for you"
                    : "Couldn't open " + openOnly.name);
        } else {
            Targets.Target opened = openTarget(target, words, wantChannel);
            if (note != null && opened != null) {
                bar.showMessage(note);
            } else {
                showTargetResult(opened);
            }
        }
        active = false;
        released = false;
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (session == mine && !active) bar.hide();
            }
        }, BAR_LINGER_MS);
    }

    private void showTargetResult(Targets.Target target) {
        if (target == null) {
            bar.showMessage("No app to search in");
        } else if (target.mode == Targets.Mode.PICK_LIST) {
            bar.hide(); // the pick-list shows its own "Searching for …"
        } else {
            bar.showSearching();
        }
    }

    /** "Voice Search settings": opens the screen for choosing the app, from whatever screen is showing. */
    private void openSettings() {
        Intent open = new Intent(this, ChooserActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        try {
            startActivity(open);
            Log.i(TAG, "opened settings");
        } catch (RuntimeException e) {
            Log.e(TAG, "could not open settings", e);
        }
    }

    /** Opens an app on its home screen (for apps with no search). False if it couldn't be opened. */
    private boolean openApp(Targets.Target app) {
        Intent open = getPackageManager().getLeanbackLaunchIntentForPackage(app.packageName);
        if (open == null) open = getPackageManager().getLaunchIntentForPackage(app.packageName);
        if (open == null) {
            Log.w(TAG, "no launcher entry for " + app.name);
            return false;
        }
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(open);
            Log.i(TAG, "opened " + app.name + " (no search)");
            return true;
        } catch (RuntimeException e) {
            Log.e(TAG, "could not open " + app.name, e);
            return false;
        }
    }

    /** Opens the app's results for the words. Returns the app, or null if there is none to open. */
    private Targets.Target openTarget(Targets.Target target, String words, boolean wantChannel) {
        if (target == null) {
            Log.w(TAG, "no supported app installed");
            return null;
        }
        Intent open;
        if (target.mode == Targets.Mode.TYPE_IN_APP) {
            // No link for this app: open its main screen; the TiviMate helper (an accessibility
            // service limited to TiviMate) then takes the waiting words to its Search screen.
            open = new Intent().setComponent(new ComponentName(target.packageName, TIVIMATE_MAIN));
            PendingSearch.set(words, System.currentTimeMillis(), wantChannel);
            // The script keeps the TiviMate helper on only while TiviMate is the published app. If it
            // was reached by name, publish it for a minute, then put the default back.
            later.removeCallbacks(republishDefault);
            if (target != ChooserActivity.current(this)) {
                ChooserActivity.publishActiveTarget(this, target);
                later.postDelayed(republishDefault, BY_NAME_HELPER_MS);
            }
        } else if (target.mode == Targets.Mode.APP_SEARCH) {
            Uri uri = Uri.parse(target.searchLink + Uri.encode(words));
            open = new Intent(Intent.ACTION_VIEW, uri).setPackage(target.packageName);
        } else {
            open = new Intent(this, ResultsActivity.class)
                    .putExtra(ResultsActivity.EXTRA_WORDS, words)
                    .putExtra(ResultsActivity.EXTRA_TARGET, target.id)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        }
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(open);
            if (target.mode == Targets.Mode.TYPE_IN_APP) TiviMateHelper.kick();
            Log.i(TAG, "opened " + target.name + " for \"" + words + "\"");
        } catch (RuntimeException e) {
            Log.e(TAG, "could not open " + target.name, e);
        }
        return target;
    }

    private static String errorName(int code) {
        switch (code) {
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "NETWORK_TIMEOUT";
            case SpeechRecognizer.ERROR_NETWORK: return "NETWORK";
            case SpeechRecognizer.ERROR_AUDIO: return "AUDIO";
            case SpeechRecognizer.ERROR_SERVER: return "SERVER";
            case SpeechRecognizer.ERROR_CLIENT: return "CLIENT";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "SPEECH_TIMEOUT";
            case SpeechRecognizer.ERROR_NO_MATCH: return "NO_MATCH";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "RECOGNIZER_BUSY";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "INSUFFICIENT_PERMISSIONS";
            default: return "UNKNOWN";
        }
    }

    private class Listener implements RecognitionListener {
        @Override public void onReadyForSpeech(Bundle params) { Log.i(TAG, "recogniser: ready for speech"); }
        @Override public void onBeginningOfSpeech() { Log.i(TAG, "recogniser: speech began"); }
        @Override public void onRmsChanged(float rmsdB) { }
        @Override public void onBufferReceived(byte[] buffer) { }
        @Override public void onEndOfSpeech() { Log.i(TAG, "recogniser: speech ended"); }
        @Override public void onEvent(int eventType, Bundle params) { }

        @Override
        public void onPartialResults(Bundle partialResults) {
            ArrayList<String> p = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            Log.i(TAG, "recogniser: partial " + p);
        }

        @Override
        public void onError(int error) {
            Log.w(TAG, "recogniser error " + error + " (" + errorName(error) + ")");
            // After an error the connection may be dead; start fresh next time.
            // Only a press still waiting gets a message: a late error from an old press stays quiet.
            endSession(false, active ? errorMessage(error) : null);
            destroyRecognizer();
        }

        @Override
        public void onResults(Bundle results) {
            if (!active) {
                Log.i(TAG, "result arrived with no press waiting, ignored");
                return;
            }
            handleResult(results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION));
        }
    }
}
