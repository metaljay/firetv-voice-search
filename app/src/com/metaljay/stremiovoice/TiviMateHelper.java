package com.metaljay.stremiovoice;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Types spoken words into TiviMate's own Search screen. An Android accessibility service
 * that gets events from TiviMate only (see res/xml/tivimate_helper.xml) and reads the screen
 * only while TiviMate is in front (apart from the name of the app in front when the voice button
 * goes down, to decide whether TiviMate may get ready early). The add-on's script switches it on
 * only while TiviMate is the app Voice Search is using.
 *
 * It acts only while a search is waiting in PendingSearch and is under MAX_AGE_MS old. A search
 * is either "getting ready" (the button is down, no words yet) or has words. On each look at
 * TiviMate's screen it does the first of these that fits:
 *   1. a search box is showing: with words, fill it, then focus the best card (see ResultChooser);
 *      getting ready, stop here (nothing is ever typed before the words arrive);
 *   2. the menu has the focus, the same item for two looks: getting ready, stop here ("ready");
 *      with words, click Search (clicking it with an empty box raises Amazon's keyboard, so this
 *      only ever happens when the words are in hand);
 *   3. otherwise press Back (at most MAX_BACKS times), as soon as TiviMate's screen has changed
 *      after the last one, with BACK_GAP_MS as the fallback.
 * Never presses Back unless TiviMate is the screen in front.
 *
 * While a search with words is under way a dark card covers the screen (see showCover), so the
 * user sees one change instead of TiviMate's screens flicking past.
 */
public class TiviMateHelper extends AccessibilityService {

    private static final String TAG = VoiceService.TAG;
    private static final String TIVIMATE = "ar.tvplayer.tv";
    private static final long MAX_AGE_MS = 10000;
    private static final int MAX_BACKS = 4;
    /** Fallback gap between Backs, when TiviMate's screen gives no sign of changing. */
    private static final long BACK_GAP_MS = 800;
    /** Never two Backs closer than this. */
    private static final long MIN_BACK_GAP_MS = 200;
    /** After a Back, wait for TiviMate's screen to be still this long before judging it. */
    private static final long QUIET_BEFORE_JUDGING_MS = 150;
    private static final long CLICK_GAP_MS = 1000;
    private static final long SETTLE_AFTER_CLICK_MS = 1500;
    /** The menu counts as open once the same item has kept the focus over two looks this far apart. */
    private static final long MENU_STABLE_MS = 150;
    private static final long TICK_MS = 150;
    private static final long CHOOSE_POLL_MS = 100;
    /** The cover card goes away by itself after this long. */
    private static final long COVER_CAP_MS = 6000;
    /** Width of TiviMate's left-hand menu on a 1920-wide screen. */
    private static final int RAIL_RIGHT = 440;

    private static TiviMateHelper instance;

    private final Handler main = new Handler(Looper.getMainLooper());
    private int seenId = -1;
    private int backs = 0;
    private long lastBack = 0;
    private long lastClick = 0;
    private long menuSince = 0;      // when the menu got the focus, if it has it now
    private String menuKey = null;   // which menu item has the focus
    private long menuLookAt = 0;     // when that item was first seen with it
    private boolean ready = false;   // getting ready has reached its stopping point (this search)
    private boolean ticking = false;
    private long lastNoRootLog = 0;
    private long lastChangeMs = 0;   // when TiviMate's window last changed
    private int chooseToken = 0;     // tells a newer choosing from an older one
    private int coverId = -1;        // the search the cover card is for
    private boolean sawTiviMate = false; // TiviMate has been in front since the cover went up
    private FrameLayout cover;

    /** Called by the service when a search has just been put in PendingSearch. */
    static void kick() {
        final TiviMateHelper h = instance;
        if (h == null) return;
        h.main.post(new Runnable() {
            @Override public void run() { h.evaluate(); }
        });
    }

    /** Is the helper switched on and running? */
    static boolean connected() {
        return instance != null;
    }

    /**
     * The package of the app in front, or null if it can't be told. Only the package name of the
     * active application window is read, and only when the voice button goes down.
     */
    static String frontPackage() {
        TiviMateHelper h = instance;
        if (h == null) return null;
        try {
            for (android.view.accessibility.AccessibilityWindowInfo w : h.getWindows()) {
                if (w.getType() != android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION || !w.isActive()) continue;
                AccessibilityNodeInfo r = w.getRoot();
                if (r != null && r.getPackageName() != null) return r.getPackageName().toString();
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "could not tell which app is in front", e);
        }
        return null;
    }

    /** True when TiviMate is in front and a channel is playing full screen (no menu, nothing but the video). */
    static boolean tivimatePlaying() {
        TiviMateHelper h = instance;
        if (h == null) return false;
        try {
            AccessibilityNodeInfo root = h.getRootInActiveWindow();
            return root != null && TIVIMATE.contentEquals(String.valueOf(root.getPackageName())) && isPlayingScreen(root);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** A playing channel's screen is one full-screen view with no text and nothing inside (measured on the stick). */
    private static boolean isPlayingScreen(AccessibilityNodeInfo root) {
        if (root.getChildCount() != 1) return false;
        AccessibilityNodeInfo v = root.getChild(0);
        if (v == null || v.getChildCount() != 0 || v.getText() != null) return false;
        Rect r = new Rect();
        v.getBoundsInScreen(r);
        return r.width() >= 1800 && r.height() >= 1000;
    }

    @Override
    protected void onServiceConnected() {
        instance = this;
        Log.i(TAG, "TiviMate helper connected");
        evaluate(); // a search may already be waiting
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        main.removeCallbacksAndMessages(null);
        hideCover();
        Log.i(TAG, "TiviMate helper stopped");
        super.onDestroy();
    }

    @Override
    public void onInterrupt() { }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        lastChangeMs = System.currentTimeMillis();
        evaluate();
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            ticking = false;
            evaluate();
        }
    };

    private void scheduleTick() {
        if (ticking) return;
        ticking = true;
        main.postDelayed(tick, TICK_MS);
    }

    /**
     * Looks at TiviMate's screen and acts if a search is waiting. Whatever TiviMate's screen
     * looks like, nothing is allowed to throw out of here: this runs in the app's own process,
     * and a crash would hand the voice button back to Alexa.
     */
    private void evaluate() {
        try {
            evaluateScreen();
        } catch (RuntimeException e) {
            Log.w(TAG, "TiviMate helper: ignored a screen it could not read", e);
        }
    }

    private void evaluateScreen() {
        final PendingSearch.Item p = PendingSearch.current(System.currentTimeMillis(), MAX_AGE_MS);
        if (p == null) {
            menuSince = 0;
            menuKey = null;
            return;
        }
        final boolean gettingReady = p.words == null;
        if (p.id != seenId) {
            // A new search. The Back and click timers carry on from the last one, so two searches
            // close together can't press Back in the middle of a screen change.
            seenId = p.id;
            backs = 0;
            ready = false;
            menuSince = 0;
            menuKey = null;
        }
        if (!gettingReady && coverId != p.id) showCover(p.id, p.words);
        scheduleTick(); // events can be missed while a screen changes; look again soon

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || !TIVIMATE.contentEquals(root.getPackageName())) {
            long t0 = System.currentTimeMillis();
            if (cover != null && sawTiviMate) { // TiviMate went out of front (Home, another app): drop the card
                Log.i(TAG, "TiviMate helper: TiviMate left the front, card removed");
                hideCover();
            }
            if (t0 - lastNoRootLog > 2000) {
                lastNoRootLog = t0;
                Log.i(TAG, "TiviMate helper: TiviMate's screen not readable (" + (root == null ? "no window" : root.getPackageName()) + ")");
            }
            return;
        }
        sawTiviMate = true;

        // 1. A search box is showing.
        AccessibilityNodeInfo box = findSearchBox(root);
        if (box != null) {
            if (gettingReady) {
                markReady("Search screen already open");
                return;
            }
            VoiceService.timing("box found");
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, p.words);
            boolean ok = box.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
            Log.i(TAG, "TiviMate helper: filled the search box with \"" + p.words + "\" (" + ok + ")");
            VoiceService.timing("box filled");
            if (ok) {
                PendingSearch.clear(p.id);
                startChoosing(p.words, p.wantChannel);
            }
            return;
        }

        // 2. The menu has the focus, and has kept it on the same item for two looks.
        long now = System.currentTimeMillis();
        boolean menuOpen = menuIsSettled(root, now);
        if (gettingReady) {
            if (menuOpen && findMenuSearch(root) != null) markReady("menu open, Search showing");
            if (ready) return; // stay put until the words arrive
        } else if (menuOpen) {
            AccessibilityNodeInfo item = findMenuSearch(root);
            if (item != null) {
                if (now - lastClick >= CLICK_GAP_MS) {
                    lastClick = now;
                    boolean ok = item.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    VoiceService.timing("Search clicked");
                    Log.i(TAG, "TiviMate helper: clicked the menu's Search item (" + ok + ")");
                }
                return;
            }
        }

        // 3. Neither: go back a screen, a few times at most. Not just after a click on Search:
        // that screen takes a moment to appear, and Back would undo the click.
        if (now - lastClick < SETTLE_AFTER_CLICK_MS) return;
        if (menuSince != 0) return; // the menu is opening: wait for it to settle
        if (backs >= MAX_BACKS) {
            Log.w(TAG, "TiviMate helper: no Search screen after " + MAX_BACKS + " Backs, giving up");
            PendingSearch.clear(p.id);
            hideCover();
            return;
        }
        long sinceBack = now - lastBack;
        boolean changedAndStill = lastChangeMs > lastBack && now - lastChangeMs >= QUIET_BEFORE_JUDGING_MS;
        if (sinceBack >= MIN_BACK_GAP_MS && (backs == 0 || changedAndStill || sinceBack >= BACK_GAP_MS)) {
            lastBack = now;
            backs++;
            VoiceService.timing("Back " + backs);
            Log.i(TAG, "TiviMate helper: Back " + backs + " of " + MAX_BACKS);
            performGlobalAction(GLOBAL_ACTION_BACK);
        }
    }

    private void markReady(String why) {
        if (ready) return;
        ready = true;
        VoiceService.timing("TiviMate ready: " + why);
        Log.i(TAG, "TiviMate helper: ready (" + why + "), waiting for the words");
    }

    /**
     * True once the remote's focus has been on the same menu item over two looks at least
     * MENU_STABLE_MS apart. Right after a Back the focus can flick onto the menu while it is still
     * sliding in, and a click then is lost.
     */
    private boolean menuIsSettled(AccessibilityNodeInfo root, long now) {
        String key = menuFocusKey(root);
        if (key == null) {
            menuSince = 0;
            menuKey = null;
            return false;
        }
        if (menuSince == 0) {
            menuSince = now;
            VoiceService.timing("menu focused");
        }
        if (!key.equals(menuKey)) {
            menuKey = key;
            menuLookAt = now;
            return false;
        }
        return now - menuLookAt >= MENU_STABLE_MS;
    }

    // ----- the cover card -----

    /** Shows the dark "Searching TiviMate for ..." card over everything. Not focusable, not touchable. */
    private void showCover(int id, String words) {
        coverId = id;
        sawTiviMate = false;
        hideCover();
        try {
            float d = getResources().getDisplayMetrics().density;
            FrameLayout card = new FrameLayout(this);
            card.setBackgroundColor(0xFF031A33); // the listening bar's navy
            TextView t = new TextView(this);
            t.setTextColor(Color.WHITE);
            t.setTextSize(28);
            t.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            t.setGravity(Gravity.CENTER);
            t.setText("Searching TiviMate for \u2018" + words + "\u2019\u2026");
            t.setPadding((int) (48 * d), 0, (int) (48 * d), 0);
            card.addView(t, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.OPAQUE);
            ((WindowManager) getSystemService(WINDOW_SERVICE)).addView(card, lp);
            cover = card;
            VoiceService.timing("cover card shown");
            final int mine = id;
            main.postDelayed(new Runnable() {
                @Override public void run() {
                    if (coverId == mine && cover != null) {
                        Log.i(TAG, "TiviMate helper: card removed after the time limit");
                        hideCover();
                    }
                }
            }, COVER_CAP_MS);
        } catch (RuntimeException e) {
            Log.w(TAG, "TiviMate helper: could not show the card", e);
            cover = null;
        }
    }

    private void hideCover() {
        if (cover == null) return;
        try {
            ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(cover);
        } catch (RuntimeException e) {
            Log.w(TAG, "TiviMate helper: could not remove the card", e);
        }
        cover = null;
        VoiceService.timing("cover card gone");
    }

    /**
     * After filling the box: TiviMate adds its result rows one at a time (Channels first, then
     * films and series). Choose when TiviMatePrewarm.readyToChoose says so: a Movies or Shows row
     * has cards and TiviMate has been quiet a moment, or it has been quiet longer, or the cap. Then
     * put focus on the best card so OK plays it.
     */
    private void startChoosing(final String words, final boolean wantChannel) {
        final int token = ++chooseToken;
        final long filledAt = System.currentTimeMillis();
        loggedRows = "";
        lastChangeMs = filledAt;
        main.postDelayed(new Runnable() {
            @Override public void run() {
                try {
                    if (token != chooseToken) return; // a newer search took over
                    long now = System.currentTimeMillis();
                    AccessibilityNodeInfo root = getRootInActiveWindow();
                    if (root == null || !TIVIMATE.contentEquals(root.getPackageName())) {
                        hideCover();
                        return;
                    }
                    // A newer search is waiting: its own fill-in comes first.
                    if (PendingSearch.current(now, MAX_AGE_MS) != null) return;
                    logNewRows(root);
                    boolean capped = now - filledAt >= TiviMatePrewarm.CAP_MS;
                    if (TiviMatePrewarm.readyToChoose(now - filledAt, now - lastChangeMs, movieOrShowCards(root))) {
                        if (chooseAndFocus(root, words, wantChannel)) {
                            hideCover();
                            return;
                        }
                        if (capped) {
                            Log.i(TAG, "TiviMate helper: no results to focus");
                            hideCover();
                            return;
                        }
                    }
                    main.postDelayed(this, CHOOSE_POLL_MS);
                } catch (RuntimeException e) {
                    Log.w(TAG, "TiviMate helper: ignored a screen it could not read", e);
                    hideCover();
                }
            }
        }, CHOOSE_POLL_MS);
    }

    /** True when at least one card sits under a Movies or Shows header. */
    private boolean movieOrShowCards(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> all = new ArrayList<>();
        collect(root, all);
        Rect r = new Rect();
        List<ResultChooser.Header> headers = new ArrayList<>();
        for (AccessibilityNodeInfo n : all) {
            CharSequence t = n.getText();
            if (t == null || !n.isVisibleToUser() || ResultChooser.knownRow(t.toString()) == null) continue;
            if (hasClickableAncestor(n)) continue;
            n.getBoundsInScreen(r);
            headers.add(new ResultChooser.Header(t.toString(), r.top));
        }
        for (AccessibilityNodeInfo n : all) {
            if (!isResultCard(n)) continue;
            n.getBoundsInScreen(r);
            String row = ResultChooser.rowFor(headers, r.top);
            if (row != null && !row.equals("Channels")) return true;
        }
        return false;
    }

    private String loggedRows = "";

    /** TIMING: logs each time a result row header (Channels, Movies, Shows) shows up. */
    private void logNewRows(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> all = new ArrayList<>();
        collect(root, all);
        StringBuilder sb = new StringBuilder();
        for (AccessibilityNodeInfo n : all) {
            CharSequence t = n.getText();
            if (t == null || !n.isVisibleToUser()) continue;
            String row = ResultChooser.knownRow(t.toString());
            if (row != null && !hasClickableAncestor(n) && sb.indexOf(row) < 0) sb.append(row).append(' ');
        }
        String now = sb.toString().trim();
        if (!now.equals(loggedRows)) {
            loggedRows = now;
            VoiceService.timing("rows showing: " + now);
        }
    }

    /** Gathers the visible result cards, lets ResultChooser pick one and focuses it. False if there are no cards. */
    private boolean chooseAndFocus(AccessibilityNodeInfo root, String words, boolean wantChannel) {
        List<AccessibilityNodeInfo> all = new ArrayList<>();
        collect(root, all);
        Rect r = new Rect();

        // Row headers: plain text ("Channels", "Movies", "Shows"). Text inside something clickable
        // is a card's or the menu's, not a header.
        List<ResultChooser.Header> headers = new ArrayList<>();
        for (AccessibilityNodeInfo n : all) {
            CharSequence t = n.getText();
            if (t == null || !n.isVisibleToUser() || ResultChooser.knownRow(t.toString()) == null) continue;
            if (hasClickableAncestor(n)) continue;
            n.getBoundsInScreen(r);
            headers.add(new ResultChooser.Header(t.toString(), r.top));
        }

        List<AccessibilityNodeInfo> nodes = new ArrayList<>();
        List<ResultChooser.Card> cards = new ArrayList<>();
        for (AccessibilityNodeInfo n : all) {
            if (!isResultCard(n)) continue;
            n.getBoundsInScreen(r);
            nodes.add(n);
            cards.add(new ResultChooser.Card(ResultChooser.rowFor(headers, r.top), cardTitle(n), r.top, r.left));
        }
        // TiviMate's "Nothing found" message is focusable too, but it is not a result.
        AccessibilityNodeInfo nothingFound = null;
        for (int i = cards.size() - 1; i >= 0; i--) {
            if (cards.get(i).title.trim().equalsIgnoreCase("Nothing found")) {
                nothingFound = nodes.get(i);
                cards.remove(i);
                nodes.remove(i);
            }
        }
        if (!cards.isEmpty()) {
            StringBuilder rows = new StringBuilder();
            for (ResultChooser.Card c : cards) rows.append(c.row == null ? "?" : c.row.substring(0, 1)).append(' ');
            VoiceService.timing("result cards seen: " + cards.size() + " (" + rows.toString().trim() + ")");
        }
        int pick = ResultChooser.choose(cards, words, wantChannel, Locale.getDefault().getCountry());
        if (pick < 0 && nothingFound != null) {
            // No results. The focus still has to leave the search box: left there, Amazon's
            // on-screen keyboard pops up (seen on the stick), so it goes to the message.
            nothingFound.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            VoiceService.timing("best match focused (nothing found)");
            Log.i(TAG, "TiviMate helper: no results");
            return true;
        }
        if (pick < 0) return false;
        ResultChooser.Card c = cards.get(pick);
        boolean ok = nodes.get(pick).performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        VoiceService.timing("best match focused");
        Log.i(TAG, "TiviMate helper: focused " + (c.row == null ? "(no row header)" : c.row) + ": "
                + c.title + " (" + ok + ")");
        return true;
    }

    // ----- finding things on TiviMate's screen -----

    private static boolean isSearchBox(AccessibilityNodeInfo n) {
        CharSequence cls = n.getClassName();
        if (cls == null) return false;
        String c = cls.toString();
        return c.endsWith("EditText") || c.endsWith("StreamingTextView") || n.isEditable();
    }

    private static AccessibilityNodeInfo findSearchBox(AccessibilityNodeInfo n) {
        if (n == null) return null;
        if (isSearchBox(n) && n.isVisibleToUser()) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo r = findSearchBox(n.getChild(i));
            if (r != null) return r;
        }
        return null;
    }

    /** Where the input focus is, as text, when it is on one of the menu's items (the column at the left edge); else null. */
    private static String menuFocusKey(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo f = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (f == null) return null;
        Rect r = new Rect();
        f.getBoundsInScreen(r);
        return r.left < 100 && r.right <= RAIL_RIGHT && r.top >= 150 ? r.top + "," + r.left : null;
    }

    /** The menu's Search entry: the text "Search" in the left menu, or else its first icon-only item. */
    private static AccessibilityNodeInfo findMenuSearch(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> all = new ArrayList<>();
        collect(root, all);
        Rect r = new Rect();
        for (AccessibilityNodeInfo n : all) {
            if (n.getText() != null && "Search".contentEquals(n.getText()) && n.isVisibleToUser()) {
                n.getBoundsInScreen(r);
                if (r.left < RAIL_RIGHT) {
                    AccessibilityNodeInfo c = clickableSelfOrParent(n);
                    if (c != null) return c;
                }
            }
        }
        // Menu folded to icons: the first clickable item of the left rail.
        AccessibilityNodeInfo best = null;
        int bestTop = Integer.MAX_VALUE;
        for (AccessibilityNodeInfo n : all) {
            if (!n.isClickable() || !n.isVisibleToUser()) continue;
            n.getBoundsInScreen(r);
            if (r.right <= RAIL_RIGHT && r.top >= 150 && r.top < bestTop
                    && String.valueOf(n.getClassName()).endsWith("LinearLayout")) {
                best = n;
                bestTop = r.top;
            }
        }
        return best;
    }

    private static AccessibilityNodeInfo clickableSelfOrParent(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo c = n;
        for (int i = 0; c != null && i < 4; i++) {
            if (c.isClickable()) return c;
            c = c.getParent();
        }
        return null;
    }

    /** A result card: a focusable card (not the menu, not the search box) below the search bar. */
    private static boolean isResultCard(AccessibilityNodeInfo n) {
        if (!n.isFocusable() || !n.isVisibleToUser() || isSearchBox(n)) return false;
        String c = String.valueOf(n.getClassName());
        if (c.endsWith("LinearLayout") || c.endsWith("GridView") || c.endsWith("ImageView")) return false;
        Rect r = new Rect();
        n.getBoundsInScreen(r);
        return r.top >= 250 && r.width() >= 150 && r.width() <= 450;
    }

    /** A card's title: its first text that is not a rating or "No information". */
    private static String cardTitle(AccessibilityNodeInfo card) {
        List<AccessibilityNodeInfo> inside = new ArrayList<>();
        collect(card, inside);
        for (AccessibilityNodeInfo n : inside) {
            CharSequence t = n.getText();
            if (t == null) continue;
            String s = t.toString().trim();
            if (s.isEmpty() || s.equalsIgnoreCase("No information") || s.matches("\\d+(\\.\\d+)?")) continue;
            return s;
        }
        return "";
    }

    private static boolean hasClickableAncestor(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo p = n.getParent();
        for (int i = 0; p != null && i < 3; i++) {
            if (p.isClickable()) return true;
            p = p.getParent();
        }
        return false;
    }

    private static void collect(AccessibilityNodeInfo n, List<AccessibilityNodeInfo> out) {
        if (n == null) return;
        out.add(n);
        for (int i = 0; i < n.getChildCount(); i++) collect(n.getChild(i), out);
    }
}
