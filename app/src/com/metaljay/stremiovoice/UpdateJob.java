package com.metaljay.stremiovoice;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

/**
 * The scheduled update check. Android wakes this about every six hours, only while
 * a network is up. Whether a check is actually due (Daily, Weekly, Monthly, Off) is
 * decided by {@link UpdateSchedule}; a failed check is quiet and records nothing.
 */
public class UpdateJob extends JobService {

    private static final int JOB_ID = 1001;
    private static final long EVERY_MS = 6L * 3600 * 1000;

    /** Makes sure the job is scheduled (it survives restarts). Safe to call any time. */
    static void schedule(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null || scheduler.getPendingJob(JOB_ID) != null) return;
        int result = scheduler.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(context, UpdateJob.class))
                .setPeriodic(EVERY_MS)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build());
        Log.i(VoiceService.TAG, "update check scheduled: " + (result == JobScheduler.RESULT_SUCCESS ? "ok" : "refused"));
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Updater.scheduledCheck(UpdateJob.this);
                } catch (RuntimeException e) {
                    Log.w(VoiceService.TAG, "update check crashed", e);
                } finally {
                    jobFinished(params, false);
                }
            }
        }, "update-check").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false; // nothing to retry: the next run is a few hours away
    }
}
