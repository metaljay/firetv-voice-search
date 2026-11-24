package com.metaljay.voicesearchtile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * Runs one command as root through Magisk's {@code su}, with a time limit. The first use raises
 * Magisk's own permission prompt on the TV, so callers only run it after the user pressed a button
 * that said so. Never run on the main thread.
 */
final class Root {

    private Root() {}

    static final class Result {
        /** su isn't on this stick at all. */
        final boolean missing;
        /** su didn't finish in time (for example the prompt was left unanswered). */
        final boolean timedOut;
        final int exit;
        final String output;

        Result(boolean missing, boolean timedOut, int exit, String output) {
            this.missing = missing;
            this.timedOut = timedOut;
            this.exit = exit;
            this.output = output;
        }

        boolean ok() {
            return !missing && !timedOut && exit == 0;
        }
    }

    /** Runs {@code su -c command}; stdout and stderr come back together. */
    static Result run(String command, long timeoutSeconds) {
        Process p = null;
        for (String su : new String[]{"su", "/product/bin/su"}) {
            try {
                p = new ProcessBuilder(su, "-c", command).redirectErrorStream(true).start();
                break;
            } catch (IOException e) {
                // try the next place
            }
        }
        if (p == null) return new Result(true, false, -1, "");
        final StringBuilder out = new StringBuilder();
        final InputStream in = p.getInputStream();
        Thread reader = new Thread(new Runnable() {
            @Override
            public void run() {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        synchronized (out) {
                            if (out.length() < 16 * 1024) out.append(line).append('\n');
                        }
                    }
                } catch (IOException e) {
                    // the process ended
                }
            }
        }, "su-reader");
        reader.setDaemon(true);
        reader.start();
        try {
            if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                p.destroy();
                return new Result(false, true, -1, text(out));
            }
            reader.join(2000);
        } catch (InterruptedException e) {
            p.destroy();
            Thread.currentThread().interrupt();
            return new Result(false, true, -1, text(out));
        }
        return new Result(false, false, p.exitValue(), text(out));
    }

    private static String text(StringBuilder out) {
        synchronized (out) {
            return out.toString();
        }
    }

    /** True when {@code su -c id} really ran as root. */
    static boolean isRoot(Result r) {
        return r.ok() && r.output.contains("uid=0");
    }
}
