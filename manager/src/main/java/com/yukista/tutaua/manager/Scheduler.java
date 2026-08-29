package com.yukista.tutaua.manager;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

final class Scheduler {
    private static final int PERIODIC = 4101, IMMEDIATE = 4102;
    static void schedule(Context context) {
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        ComponentName service = new ComponentName(context, SyncJobService.class);
        jobs.schedule(new JobInfo.Builder(PERIODIC, service).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(15 * 60_000L).setPersisted(true).build());
        jobs.schedule(new JobInfo.Builder(IMMEDIATE, service).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(1_000L).setOverrideDeadline(30_000L).build());
    }
    private Scheduler() {}
}
