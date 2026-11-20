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
 * the words come back we open Stremio's search for them.
 *
 * Everything here runs on the main thread, as the speech recogniser requires.
 */
public class VoiceService extends Service {

    static final String TAG = "StremioVoice";

    private static final String ACTION_KEY_INFO = "com.amazon.tv.alm.action.KEY_INFO";
    private static final String EXTRA_KEY_EVENT = "KeyEvent";
    private static final ComponentName SPEECH_SERVICE = new ComponentName(
            "com.amazon.vizzini", "com.amazon.vizzini.voiceservice.SpeechToTextService");
    private static final String CHANNEL_ID = "stremio_voice";
    private static final int NOTIFICATION_ID = 1;

    /** How long to wait for words after the button is released. */
    private static final long RESULT_TIMEOUT_MS = 5000;
    /** Safety net in case the button-up broadcast never arrives. */
    private static final long MAX_HOLD_MS = 20000;
    /** How long the bar stays up after Stremio has been asked to open. */
    private static final long BAR_LINGER_MS = 3000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private ListeningBar bar;
    private SpeechRecognizer recognizer;

    /** Counts button presses, so late callbacks from an older press can be ignored. */
    private int session = 0;
    private boolean active = false;   // a press is being handled
    private boolean released = false; // its button-up has arrived

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
        Updater.dir(this); // makes the folder the add-on's script shares with the app
        UpdateJob.schedule(this); // the scheduled update check; does nothing if already scheduled
        Log.i(TAG, "service started");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
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
        } catch (RuntimeException ignored) {
            // Not registered; nothing to undo.
        }
        main.removeCallbacksAndMessages(null);
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
            endSession(true);
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
                    endSession(true);
                }
            }
        }, RESULT_TIMEOUT_MS);
    }

    /** Stop handling the current press. If cancel is true, also abandon the recogniser's work. */
    private void endSession(boolean cancel) {
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
        bar.hide();
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
            endSession(false);
            return;
        }
        String heard = candidates.get(0);
        Log.i(TAG, "heard: \"" + heard + "\"");
        String words = QueryCleaner.clean(heard);
        Log.i(TAG, "search words: \"" + words + "\"");
        if (words.isEmpty()) {
            endSession(false);
            return;
        }
        final int mine = session;
        main.removeCallbacksAndMessages(null);
        if (QueryCleaner.isSettingsRequest(words)) {
            openSettings();
            bar.hide();
        } else {
            showTargetResult(openTarget(words));
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

    /** Opens the chosen app's results for the words. Returns the app, or null if none is installed. */
    private Targets.Target openTarget(String words) {
        Targets.Target target = ChooserActivity.current(this);
        if (target == null) {
            Log.w(TAG, "no supported app installed");
            return null;
        }
        Intent open;
        if (target.mode == Targets.Mode.APP_SEARCH) {
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
            endSession(false);
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
