package com.metaljay.stremiovoice;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Alexa-style bar along the bottom of the screen: a dark navy fade, white text,
 * and a row of blue-to-violet bars that ripple like a sound wave. While
 * listening the ripple spreads out from the middle; while searching it sweeps
 * across, faster. Not focusable and not touchable, so it never steals the
 * remote's buttons from whatever is on screen. Main thread only.
 */
public class ListeningBar {

    private final Context context;
    private final WindowManager windowManager;

    private FrameLayout root;
    private TextView label;
    private WaveView wave;
    private ValueAnimator ripple;

    public ListeningBar(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    public void showListening() {
        if (!ensureShown()) return;
        label.setText("Listening…");
        wave.setSearching(false);
        startRipple(1200);
    }

    public void showSearching() {
        if (!ensureShown()) return;
        label.setText("Searching…");
        wave.setSearching(true);
        startRipple(800);
    }

    /** A short notice with the wave at rest, e.g. when there is no app to search in. */
    public void showMessage(String text) {
        if (!ensureShown()) return;
        label.setText(text);
        wave.setSearching(false);
        stopRipple();
    }

    public void hide() {
        stopRipple();
        if (root != null) {
            try {
                windowManager.removeView(root);
            } catch (RuntimeException e) {
                Log.w(VoiceService.TAG, "cannot remove bar", e);
            }
            root = null;
            label = null;
            wave = null;
        }
    }

    /** Adds the bar to the screen if it is not there yet. Returns false if that fails. */
    private boolean ensureShown() {
        if (root != null) return true;
        FrameLayout view = build();
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
            windowManager.addView(view, lp);
        } catch (RuntimeException e) {
            // Most likely the overlay permission is missing. The service still works without the bar.
            Log.w(VoiceService.TAG, "cannot show bar", e);
            root = null;
            label = null;
            wave = null;
            return false;
        }
        root = view;
        view.setAlpha(0f);
        view.animate().alpha(1f).setDuration(150).start();
        return true;
    }

    private FrameLayout build() {
        float d = context.getResources().getDisplayMetrics().density;

        FrameLayout frame = new FrameLayout(context);
        frame.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x00000000, 0xB303142B, 0xE6031A33}));

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setPadding(0, (int) (36 * d), 0, (int) (18 * d));
        frame.addView(column, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        TextView text = new TextView(context);
        text.setTextColor(Color.WHITE);
        text.setTextSize(18);
        text.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        text.setGravity(Gravity.CENTER);
        column.addView(text, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        WaveView waveView = new WaveView(context, d);
        LinearLayout.LayoutParams waveParams =
                new LinearLayout.LayoutParams((int) (300 * d), (int) (30 * d));
        waveParams.topMargin = (int) (8 * d);
        column.addView(waveView, waveParams);

        label = text;
        wave = waveView;
        return frame;
    }

    private void startRipple(long cycleMs) {
        stopRipple();
        if (wave == null) return;
        ripple = ValueAnimator.ofFloat(0f, 1f);
        ripple.setDuration(cycleMs);
        ripple.setRepeatCount(ValueAnimator.INFINITE);
        ripple.setRepeatMode(ValueAnimator.RESTART);
        ripple.setInterpolator(new LinearInterpolator());
        ripple.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                if (wave != null) wave.setPhase((Float) a.getAnimatedValue());
            }
        });
        ripple.start();
    }

    private void stopRipple() {
        if (ripple != null) {
            ripple.cancel();
            ripple = null;
        }
    }

    /** A row of rounded bars whose heights follow a moving wave, painted in one blue-to-violet gradient. */
    private static class WaveView extends View {
        private static final int BARS = 31;
        private static final int[] GRADIENT = {0xFF00E5FF, 0xFF00CAFF, 0xFF2F7BFF, 0xFF7C5CFF};
        private static final float[] STOPS = {0f, 0.35f, 0.7f, 1f};

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final float density;
        private float phase;
        private boolean searching;

        WaveView(Context context, float density) {
            super(context);
            this.density = density;
        }

        void setSearching(boolean searching) {
            this.searching = searching;
            invalidate();
        }

        void setPhase(float phase) {
            this.phase = phase;
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            paint.setShader(new LinearGradient(0, 0, w, 0, GRADIENT, STOPS, Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float slot = w / BARS;
            float barW = slot * 0.55f;
            float mid = (BARS - 1) / 2f;
            float minH = 3 * density;
            float twoPi = (float) (2 * Math.PI);

            for (int i = 0; i < BARS; i++) {
                float fromMiddle = Math.abs(i - mid) / mid;          // 0 in the middle, 1 at the ends
                float position = searching ? i / (float) (BARS - 1)  // sweep left to right
                                           : fromMiddle;             // spread out from the middle
                float travel = searching ? 2f : 1.5f;
                float a = twoPi * (phase - position * travel);
                float b = twoPi * (phase * 2f - position * travel * 2f) + 1.3f;
                float wave = 0.65f * (0.5f + 0.5f * (float) Math.sin(a))
                           + 0.35f * (0.5f + 0.5f * (float) Math.sin(b));
                float envelope = 1f - 0.7f * fromMiddle * fromMiddle; // taller in the middle
                float barH = minH + (h - minH) * envelope * (0.15f + 0.85f * wave);

                float left = i * slot + (slot - barW) / 2f;
                rect.set(left, (h - barH) / 2f, left + barW, (h + barH) / 2f);
                canvas.drawRoundRect(rect, barW / 2f, barW / 2f, paint);
            }
        }
    }
}
