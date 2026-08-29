package com.yukista.tutaua.manager;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

final class Scheduler {
    private static final int HEARTBEAT = 4101, IMMEDIATE = 4102;
    static void schedule(Context context) {
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        ComponentName service = new ComponentName(context, SyncJobService.class);
        jobs.cancel(HEARTBEAT);
        jobs.schedule(new JobInfo.Builder(HEARTBEAT, service).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(5 * 60_000L).setPersisted(true).build());
        jobs.schedule(new JobInfo.Builder(IMMEDIATE, service).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(1_000L).setOverrideDeadline(30_000L).build());
    }
    static void scheduleHeartbeat(Context context) {
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        ComponentName service = new ComponentName(context, SyncJobService.class);
        jobs.cancel(HEARTBEAT);
        jobs.schedule(new JobInfo.Builder(HEARTBEAT, service).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(5 * 60_000L).setPersisted(true).build());
    }
    private Scheduler() {}
}
