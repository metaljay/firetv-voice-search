package com.metaljay.stremiovoice;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * Slim bar along the bottom of the screen that says "Listening..." and then
 * "Searching...". Not focusable and not touchable, so it never steals the
 * remote's buttons from whatever is on screen. Main thread only.
 */
public class ListeningBar {

    private final Context context;
    private final WindowManager windowManager;
    private TextView view;

    public ListeningBar(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    public void show(String text) {
        if (view == null) {
            TextView tv = new TextView(context);
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(26);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(24, 16, 24, 16);
            tv.setBackgroundColor(0xCC202020);

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.BOTTOM;
            try {
                windowManager.addView(tv, lp);
                view = tv;
            } catch (RuntimeException e) {
                // Most likely the overlay permission is missing. The service still works without the bar.
                Log.w(VoiceService.TAG, "cannot show bar", e);
                return;
            }
        }
        view.setText(text);
    }

    public void hide() {
        if (view != null) {
            try {
                windowManager.removeView(view);
            } catch (RuntimeException e) {
                Log.w(VoiceService.TAG, "cannot remove bar", e);
            }
            view = null;
        }
    }
}
