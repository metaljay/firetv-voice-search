package com.metaljay.stremiovoice;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * The pick-list for apps that cannot search for words themselves (Nuvio, WuPlay).
 * Looks the spoken words up in Cinemeta, the free catalogue Stremio and Nuvio
 * both use, shows one row of posters, and opens the chosen title in the app.
 * Back closes it.
 */
public class ResultsActivity extends Activity {

    static final String EXTRA_WORDS = "words";
    static final String EXTRA_TARGET = "target";

    private static final String CATALOGUE = "https://v3-cinemeta.strem.io/catalog/";
    private static final int TIMEOUT_MS = 5000;
    private static final long MESSAGE_CLOSE_MS = 6000;

    private static final LruCache<String, Bitmap> POSTERS = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    private final Handler main = new Handler(Looper.getMainLooper());
    private ExecutorService pool;
    private String words;
    private Targets.Target target;
    private boolean opened = false;

    private TextView heading;
    private TextView hint;
    private TextView message;
    private LinearLayout row;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        words = getIntent().getStringExtra(EXTRA_WORDS);
        target = Targets.byId(getIntent().getStringExtra(EXTRA_TARGET));
        if (TextUtils.isEmpty(words) || target == null || target.mode != Targets.Mode.PICK_LIST) {
            Log.w(VoiceService.TAG, "results opened without words or a pick-list app");
            finish();
            return;
        }
        pool = Executors.newFixedThreadPool(4);
        buildScreen();
        search();
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        if (pool != null) pool.shutdownNow();
        // The app's process runs all the time (the voice service), so don't keep posters around.
        POSTERS.evictAll();
        super.onDestroy();
    }

    // ----- layout -----

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ChooserActivity.BACKGROUND);
        root.setPadding(dp(56), dp(40), 0, dp(24));
        root.setClipChildren(false);

        heading = text("Searching for “" + words + "”…", 28, Color.WHITE);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        heading.setPadding(dp(16), 0, 0, 0);
        root.addView(heading);

        hint = text("Choose one to open in " + target.name + ". Back to close.", 17,
                ChooserActivity.GREY);
        hint.setPadding(dp(16), dp(6), 0, dp(12));
        root.addView(hint);

        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipToPadding(false);
        scroller.setClipChildren(false);
        scroller.setPadding(dp(16), dp(16), dp(56), dp(16));
        row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        scroller.addView(row);
        root.addView(scroller);

        message = text("", 22, ChooserActivity.GREY);
        message.setPadding(dp(16), dp(32), dp(56), 0);
        root.addView(message);

        setContentView(root);
    }

    private void showResults(List<ResultRanker.Item> items) {
        heading.setText("Results for “" + words + "”");
        View first = null;
        for (ResultRanker.Item item : items) {
            View card = card(item);
            row.addView(card);
            if (first == null) first = card;
        }
        if (first != null) first.requestFocus();
    }

    private void showMessage(String text) {
        heading.setText("“" + words + "”");
        hint.setText("Back to close.");
        message.setText(text);
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }, MESSAGE_CLOSE_MS);
    }

    private View card(final ResultRanker.Item item) {
        final int width = dp(136);
        final int posterHeight = dp(204);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width + dp(12), LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(20);
        card.setLayoutParams(lp);
        card.setPadding(dp(6), dp(6), dp(6), dp(6));
        card.setFocusable(true);

        final GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(dp(14));
        ring.setColor(Color.TRANSPARENT);
        card.setBackground(ring);

        FrameLayout posterBox = new FrameLayout(this);
        GradientDrawable placeholder = new GradientDrawable();
        placeholder.setColor(ChooserActivity.CARD);
        placeholder.setCornerRadius(dp(10));
        posterBox.setBackground(placeholder);
        posterBox.setClipToOutline(true);
        posterBox.setLayoutParams(new LinearLayout.LayoutParams(width, posterHeight));

        TextView initials = text(item.name, 18, ChooserActivity.GREY);
        initials.setGravity(Gravity.CENTER);
        initials.setPadding(dp(12), 0, dp(12), 0);
        posterBox.addView(initials, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        posterBox.addView(poster, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        card.addView(posterBox);
        loadPoster(item.poster, poster);

        TextView name = text(item.name, 16, Color.WHITE);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setPadding(0, dp(8), 0, 0);
        card.addView(name);

        String kind = "series".equals(item.type) ? "TV" : "Film";
        String year = item.year.replace("–", "").trim();
        card.addView(text(year.isEmpty() ? kind : year + " · " + kind, 13, ChooserActivity.GREY));

        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                ring.setStroke(hasFocus ? dp(4) : 0, Color.WHITE);
                v.animate().scaleX(hasFocus ? 1.06f : 1f).scaleY(hasFocus ? 1.06f : 1f)
                        .setDuration(120).start();
            }
        });
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open(item);
            }
        });
        return card;
    }

    private void open(ResultRanker.Item item) {
        if (opened) return; // a quick double press must not open the app twice
        opened = true;
        Uri uri = Uri.parse(Targets.titleLink(target, item.type, item.id));
        Intent intent = new Intent(Intent.ACTION_VIEW, uri)
                .setPackage(target.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
            Log.i(VoiceService.TAG, "opened " + uri);
        } catch (RuntimeException e) {
            Log.e(VoiceService.TAG, "could not open " + target.name, e);
        }
        finish();
    }

    // ----- catalogue -----

    private void search() {
        pool.execute(new Runnable() {
            @Override
            public void run() {
                // This screen shares a process with the voice service: never let an error escape.
                try {
                    searchNow();
                } catch (RuntimeException e) {
                    Log.w(VoiceService.TAG, "search stopped: " + e);
                }
            }
        });
    }

    private void searchNow() {
        VoiceService.timing("Cinemeta request start");
        Future<List<ResultRanker.Item>> films = pool.submit(fetch("movie"));
        Future<List<ResultRanker.Item>> shows = pool.submit(fetch("series"));
        List<ResultRanker.Item> filmList = get(films);
        List<ResultRanker.Item> showList = get(shows);
        final boolean offline = filmList == null && showList == null;
        VoiceService.timing("Cinemeta answer");
        final List<ResultRanker.Item> merged = ResultRanker.merge(words, filmList, showList);
        Log.i(VoiceService.TAG, "pick-list for \"" + words + "\": " + merged.size() + " results"
                + (offline ? " (catalogue unreachable)" : ""));
        main.post(new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || isDestroyed()) return;
                if (offline) {
                    showMessage("Couldn't reach the catalogue. Check the internet.");
                } else if (merged.isEmpty()) {
                    showMessage("Nothing found. Try saying it another way.");
                } else {
                    showResults(merged);
                }
                VoiceService.timing("list shown");
            }
        });
    }

    private static List<ResultRanker.Item> get(Future<List<ResultRanker.Item>> f) {
        try {
            return f.get(TIMEOUT_MS * 2L, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            Log.w(VoiceService.TAG, "catalogue request failed: " + e);
            return null;
        }
    }

    private Callable<List<ResultRanker.Item>> fetch(final String type) {
        return new Callable<List<ResultRanker.Item>>() {
            @Override
            public List<ResultRanker.Item> call() throws Exception {
                String q = URLEncoder.encode(words, "UTF-8").replace("+", "%20");
                String json = new String(download(CATALOGUE + type + "/top/search=" + q + ".json"), "UTF-8");
                JSONArray metas = new JSONObject(json).optJSONArray("metas");
                List<ResultRanker.Item> out = new ArrayList<>();
                if (metas == null) return out;
                for (int i = 0; i < metas.length(); i++) {
                    JSONObject m = metas.optJSONObject(i);
                    if (m == null) continue;
                    out.add(new ResultRanker.Item(
                            m.optString("id", ""), type, m.optString("name", ""),
                            m.optString("releaseInfo", ""), m.optString("poster", ""), i));
                }
                return out;
            }
        };
    }

    private void loadPoster(final String url, final ImageView into) {
        if (TextUtils.isEmpty(url) || !url.startsWith("https://")) return;
        Bitmap cached = POSTERS.get(url);
        if (cached != null) {
            into.setImageBitmap(cached);
            return;
        }
        pool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] data = download(url);
                    final Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                    if (bitmap == null) return;
                    POSTERS.put(url, bitmap);
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            if (!isDestroyed()) into.setImageBitmap(bitmap);
                        }
                    });
                } catch (Exception e) {
                    Log.w(VoiceService.TAG, "poster failed: " + e);
                }
            }
        });
    }

    private static byte[] download(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setRequestProperty("User-Agent", "FireTV-VoiceSearch");
        try {
            if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            c.disconnect();
        }
    }

    // ----- small helpers -----

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
