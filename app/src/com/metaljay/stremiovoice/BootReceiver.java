package com.metaljay.stremiovoice;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Starts the voice service when the stick finishes booting. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.i(VoiceService.TAG, "boot completed, starting service");
            try {
                context.startForegroundService(new Intent(context, VoiceService.class));
            } catch (RuntimeException e) {
                Log.w(VoiceService.TAG, "could not start service at boot", e);
            }
        }
    }
}
