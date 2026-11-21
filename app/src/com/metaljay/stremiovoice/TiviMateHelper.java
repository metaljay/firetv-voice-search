package com.metaljay.stremiovoice;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Types spoken words into TiviMate's own Search screen. An Android accessibility service
 * that gets events from TiviMate only (see res/xml/tivimate_helper.xml) and reads the screen
 * only while TiviMate is in front. The add-on's script switches it on only while TiviMate is the
 * app Voice Search is using.
 *
 * It acts only while a search is waiting in PendingSearch and is under MAX_AGE_MS old. On
 * each look at TiviMate's screen it does the first of these that fits:
 *   1. a search box is showing: fill it, then (once the results have settled) focus the best card, see ResultChooser;
 *   2. the menu's "Search" item is showing: click it;
 *   3. otherwise press Back (at most MAX_BACKS times, at most one per BACK_GAP_MS), which
 *      peels TiviMate's guide, then its channel list, back to the menu.
 * Never presses Back unless TiviMate is the screen in front.
 */
public class TiviMateHelper extends AccessibilityService {

    private static final String TAG = VoiceService.TAG;
    private static final String TIVIMATE = "ar.tvplayer.tv";
    private static final long MAX_AGE_MS = 10000;
    private static final int MAX_BACKS = 4;
    private static final long BACK_GAP_MS = 800;
    private static final long CLICK_GAP_MS = 1000;
    private static final long SETTLE_AFTER_CLICK_MS = 2500;
    private static final long MENU_STABLE_MS = 400;
    private static final long TICK_MS = 500;
    /** After filling the box: choose once TiviMate has been quiet this long, or at the latest after CHOOSE_CAP_MS. */
    private static final long SETTLE_MS = 700;
    private static final long CHOOSE_CAP_MS = 3000;
    private static final long CHOOSE_POLL_MS = 150;
    /** Width of TiviMate's left-hand menu on a 1920-wide screen. */
    private static final int RAIL_RIGHT = 440;

    private static TiviMateHelper instance;

    private final Handler main = new Handler(Looper.getMainLooper());
    private int seenId = -1;
    private int backs = 0;
    private long lastBack = 0;
    private long lastClick = 0;
    private long menuSince = 0;   // when the menu got the focus, if it has it now
    private boolean ticking = false;
    private long lastChangeMs = 0;   // when TiviMate's window last changed
    private int chooseToken = 0;     // tells a newer choosing from an older one

    /** Called by the service when a search has just been put in PendingSearch. */
    static void kick() {
        final TiviMateHelper h = instance;
        if (h == null) return;
        h.main.post(new Runnable() {
            @Override public void run() { h.evaluate(); }
        });
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
            return;
        }
        if (p.id != seenId) {
            // A new search. The Back and click timers carry on from the last one, so two searches
            // close together can't press Back in the middle of a screen change.
            seenId = p.id;
            backs = 0;
        }
        scheduleTick(); // events can be missed while a screen changes; look again soon

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || !TIVIMATE.contentEquals(root.getPackageName())) return;

        // 1. A search box is showing: fill it.
        AccessibilityNodeInfo box = findSearchBox(root);
        if (box != null) {
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, p.words);
            boolean ok = box.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
            Log.i(TAG, "TiviMate helper: filled the search box with \"" + p.words + "\" (" + ok + ")");
            if (ok) {
                PendingSearch.clear(p.id);
                startChoosing(p.words, p.wantChannel);
            }
            return;
        }

        // 2. The menu's Search item is showing: click it.
        // Only when the menu is really open, meaning the remote's focus is on one of its items.
        // TiviMate keeps the menu in its screen tree even while it is slid out of sight, and a
        // click on it then does nothing useful.
        // And only once that has been so for a moment: right after a Back the focus can flick
        // onto the menu while it is still sliding in, and a click then is lost.
        long now = System.currentTimeMillis();
        if (menuHasFocus(root)) {
            if (menuSince == 0) menuSince = now;
        } else {
            menuSince = 0;
        }
        AccessibilityNodeInfo item =
                menuSince != 0 && now - menuSince >= MENU_STABLE_MS ? findMenuSearch(root) : null;
        if (item != null) {
            if (now - lastClick >= CLICK_GAP_MS) {
                lastClick = now;
                boolean ok = item.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                Log.i(TAG, "TiviMate helper: clicked the menu's Search item (" + ok + ")");
            }
            return;
        }

        // 3. Neither: go back a screen, a few times at most. Not just after a click on Search:
        // that screen takes a moment to appear, and Back would undo the click.
        if (now - lastClick < SETTLE_AFTER_CLICK_MS) return;
        if (menuSince != 0) return; // the menu is opening: wait for it to settle, then click
        if (backs >= MAX_BACKS) {
            Log.w(TAG, "TiviMate helper: no Search screen after " + MAX_BACKS + " Backs, giving up");
            PendingSearch.clear(p.id);
            return;
        }
        if (now - lastBack >= BACK_GAP_MS) {
            lastBack = now;
            backs++;
            Log.i(TAG, "TiviMate helper: Back " + backs + " of " + MAX_BACKS);
            performGlobalAction(GLOBAL_ACTION_BACK);
        }
    }

    /**
     * After filling the box: TiviMate adds its result rows one at a time (Channels first, then
     * films and series), so wait until its window has been quiet for SETTLE_MS (at most
     * CHOOSE_CAP_MS), then put focus on the best card so OK plays it.
     */
    private void startChoosing(final String words, final boolean wantChannel) {
        final int token = ++chooseToken;
        final long filledAt = System.currentTimeMillis();
        lastChangeMs = filledAt;
        main.postDelayed(new Runnable() {
            @Override public void run() {
                try {
                    if (token != chooseToken) return; // a newer search took over
                    long now = System.currentTimeMillis();
                    boolean capped = now - filledAt >= CHOOSE_CAP_MS;
                    boolean quiet = now - lastChangeMs >= SETTLE_MS;
                    AccessibilityNodeInfo root = getRootInActiveWindow();
                    if (root == null || !TIVIMATE.contentEquals(root.getPackageName())) return;
                    // A newer search is waiting: its own fill-in comes first.
                    if (PendingSearch.current(now, MAX_AGE_MS) != null) return;
                    if (quiet || capped) {
                        if (chooseAndFocus(root, words, wantChannel)) return;
                        if (capped) {
                            Log.i(TAG, "TiviMate helper: no results to focus");
                            return;
                        }
                    }
                    main.postDelayed(this, CHOOSE_POLL_MS);
                } catch (RuntimeException e) {
                    Log.w(TAG, "TiviMate helper: ignored a screen it could not read", e);
                }
            }
        }, CHOOSE_POLL_MS);
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
        int pick = ResultChooser.choose(cards, words, wantChannel, Locale.getDefault().getCountry());
        if (pick < 0 && nothingFound != null) {
            // No results. The focus still has to leave the search box: left there, Amazon's
            // on-screen keyboard pops up (seen on the stick), so it goes to the message.
            nothingFound.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            Log.i(TAG, "TiviMate helper: no results");
            return true;
        }
        if (pick < 0) return false;
        ResultChooser.Card c = cards.get(pick);
        boolean ok = nodes.get(pick).performAction(AccessibilityNodeInfo.ACTION_FOCUS);
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

    /** True when the input focus is on one of the menu's items (the column at the left edge). */
    private static boolean menuHasFocus(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo f = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (f == null) return false;
        Rect r = new Rect();
        f.getBoundsInScreen(r);
        return r.left < 100 && r.right <= RAIL_RIGHT && r.top >= 150;
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
