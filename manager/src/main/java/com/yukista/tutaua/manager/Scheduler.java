package com.yukista.tutaua.manager;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

final class Scheduler {
    private static final int HEARTBEAT = 4101, IMMEDIATE = 4102;
    private static final int DEFAULT_HEARTBEAT_SECONDS = 300;
    static void schedule(Context context) {
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        ComponentName service = new ComponentName(context, SyncJobService.class);
        jobs.cancel(HEARTBEAT);
        jobs.schedule(new JobInfo.Builder(HEARTBEAT, service).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(heartbeatMillis(context)).setBackoffCriteria(30_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .setPersisted(true).build());
        jobs.schedule(new JobInfo.Builder(IMMEDIATE, service).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(1_000L).setOverrideDeadline(30_000L).build());
    }
    static void scheduleHeartbeat(Context context) {
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        ComponentName service = new ComponentName(context, SyncJobService.class);
        jobs.cancel(HEARTBEAT);
        jobs.schedule(new JobInfo.Builder(HEARTBEAT, service).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(heartbeatMillis(context)).setBackoffCriteria(30_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .setPersisted(true).build());
    }
    private static long heartbeatMillis(Context context) {
        int seconds = new ManagerStore(context).heartbeatSeconds();
        if (seconds < 30 || seconds > 3600) seconds = DEFAULT_HEARTBEAT_SECONDS;
        return seconds * 1000L;
    }
    private Scheduler() {}
}
