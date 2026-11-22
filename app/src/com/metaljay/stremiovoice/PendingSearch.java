package com.metaljay.stremiovoice;

/**
 * The words waiting to be typed into an app's own search screen (TYPE_IN_APP apps), and when
 * they were set. Shared between the service that hears the words and the TiviMate helper that
 * types them. Static, because both live in this app's one process.
 */
final class PendingSearch {

    /** One waiting search. The id tells a new search from an old one. */
    static final class Item {
        /** The words to type; null while TiviMate is only getting ready (the user is still talking). */
        final String words;
        final long timeMs;
        final int id;
        /** The listener said "... channel": choose the live channel, not the film or series. */
        final boolean wantChannel;

        Item(String words, long timeMs, int id, boolean wantChannel) {
            this.words = words;
            this.timeMs = timeMs;
            this.id = id;
            this.wantChannel = wantChannel;
        }
    }

    private static Item pending;
    private static int counter;

    private PendingSearch() {}

    static synchronized void set(String words, long now, boolean wantChannel) {
        counter++;
        pending = new Item(words, now, counter, wantChannel);
    }

    /** The button went down: TiviMate may get ready, but there are no words yet. */
    static synchronized void setGettingReady(long now) {
        counter++;
        pending = new Item(null, now, counter, false);
    }

    /**
     * The words have arrived. If TiviMate was getting ready, the same search carries on (same id,
     * so the progress made while talking counts); otherwise it is a new search.
     */
    static synchronized void fill(String words, long now, boolean wantChannel) {
        if (pending != null && pending.words == null) {
            pending = new Item(words, now, pending.id, wantChannel);
        } else {
            set(words, now, wantChannel);
        }
    }

    /** Forgets a search that is only getting ready (the words were for something else, or never came). */
    static synchronized void dropGettingReady() {
        if (pending != null && pending.words == null) pending = null;
    }

    /** The waiting search if it is younger than maxAgeMs, else null (and forgotten). */
    static synchronized Item current(long now, long maxAgeMs) {
        if (pending == null) return null;
        if (now - pending.timeMs > maxAgeMs) {
            pending = null;
            return null;
        }
        return pending;
    }

    /** Forgets the waiting search, but only if it is still the one with this id. */
    static synchronized void clear(int id) {
        if (pending != null && pending.id == id) pending = null;
    }

    static synchronized void clear() {
        pending = null;
    }
}
