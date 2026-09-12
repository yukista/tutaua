package com.yukista.tutaua.manager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class ConfigurationReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if ("com.yukista.tutaua.manager.action.CONFIG_CHANGED".equals(intent.getAction()))
            Scheduler.schedule(context);
    }
}
