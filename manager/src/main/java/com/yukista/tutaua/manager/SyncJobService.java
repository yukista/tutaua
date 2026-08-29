package com.yukista.tutaua.manager;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.app.Activity;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SyncJobService extends JobService {
    @Override public boolean onStartJob(JobParameters parameters) {
        Executors.newSingleThreadExecutor().execute(() -> {
            boolean retry = false;
            try { synchronize(); }
            catch (Exception error) { retry = true; Log.e("TUTAUA_MANAGER", "sync ERROR " + error.getClass().getSimpleName(), error); }
            jobFinished(parameters, retry);
            Scheduler.scheduleHeartbeat(this);
        });
        return true;
    }
    @Override public boolean onStopJob(JobParameters parameters) {
        Scheduler.scheduleHeartbeat(this);
        return true;
    }

    private void synchronize() throws Exception {
        ManagerStore store = new ManagerStore(this); if (!store.enrolled()) return;
        JSONObject response = ControlClient.checkIn(this, store);
        if (!response.isNull("desiredConfig")) {
            JSONObject config = response.getJSONObject("desiredConfig");
            int version = response.getInt("desiredConfigVersion");
            if (!applyConfiguration(version, config)) throw new IllegalStateException("Box rejected configuration");
            store.configuration(version, config.toString());
        }
        JSONArray commands = response.optJSONArray("commands");
        if (commands != null) for (int index = 0; index < commands.length(); index++) {
            JSONObject command = commands.getJSONObject(index);
            try { ControlClient.commandResult(store, command.getString("id"), true, CommandExecutor.run(this, command)); }
            catch (Exception error) { ControlClient.commandResult(store, command.getString("id"), false,
                    new JSONObject().put("error", error.getClass().getSimpleName())); }
        }
        JSONArray updates = response.optJSONArray("updates");
        if (updates != null) for (int index = 0; index < updates.length(); index++) {
            JSONObject release = updates.getJSONObject(index);
            UpdateInstaller.install(this, store, release);
        }
        Log.i("TUTAUA_MANAGER", "sync SUCCESS");
    }

    private boolean applyConfiguration(int version, JSONObject config) throws InterruptedException {
        Intent apply = new Intent("com.yukista.tutaua.box.action.APPLY_REMOTE_CONFIG")
                .setPackage("com.yukista.tutaua.box").putExtra("version", version).putExtra("config", config.toString());
        CountDownLatch completed = new CountDownLatch(1); AtomicBoolean accepted = new AtomicBoolean(false);
        BroadcastReceiver result = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                accepted.set(getResultCode() == Activity.RESULT_OK); completed.countDown();
            }
        };
        sendOrderedBroadcast(apply, null, result, null, Activity.RESULT_CANCELED, null, null);
        return completed.await(10, TimeUnit.SECONDS) && accepted.get();
    }
}
