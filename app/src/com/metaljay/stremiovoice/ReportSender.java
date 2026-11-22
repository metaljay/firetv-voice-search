package com.metaljay.stremiovoice;

/**
 * The one place a problem report is handed on. Today nothing is sent from the stick: the report
 * screen shows a QR code, and the person submits the report from their phone with their own GitHub
 * account (ReportBuilder.fallbackUrl). If an automatic sender is added later (for example through
 * a GitHub App), it goes in {@link #send}, and the screen needs no change: it shows the QR code
 * whenever send says the report was not posted.
 */
final class ReportSender {

    /** Where reports go: this project's own Issues page. The one place to change it. */
    static final String REPORT_REPO = "metaljay/firetv-voice-search";

    /** What came of an attempt to post the report. */
    static final class Result {
        final boolean posted;
        final int issueNumber;
        /** Why it was not posted, for the screen; null when it was, or when no attempt is made. */
        final String why;

        Result(boolean posted, int issueNumber, String why) {
            this.posted = posted;
            this.issueNumber = issueNumber;
            this.why = why;
        }
    }

    private ReportSender() {}

    /** Tries to post the report. Blocks, so never call it on the main thread. Not posted for now. */
    static Result send(String title, String body) {
        return new Result(false, 0, null);
    }
}
