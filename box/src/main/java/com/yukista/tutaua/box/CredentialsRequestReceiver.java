package com.yukista.tutaua.box;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/** Answers the Tutaua app when it starts without a session and asks for credentials. */
public final class CredentialsRequestReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Credentials.ACTION_REQUEST.equals(intent.getAction())) return;
        Bundle bundle = Credentials.available(context);
        if (bundle == null) { setResultCode(Activity.RESULT_CANCELED); return; }
        setResultExtras(bundle);
        setResultCode(Activity.RESULT_OK);
    }
}
