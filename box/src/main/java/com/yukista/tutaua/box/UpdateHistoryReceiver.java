package com.yukista.tutaua.box;
import android.content.*;
import org.json.*;

public final class UpdateHistoryReceiver extends BroadcastReceiver {
    static final String ACTION="com.yukista.tutaua.box.action.UPDATE_RECORDED", PREFS="update_history_v1", KEY="events";
    @Override public void onReceive(Context context,Intent intent){if(!ACTION.equals(intent.getAction()))return;SharedPreferences prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);try{JSONArray old=new JSONArray(prefs.getString(KEY,"[]")), history=new JSONArray();history.put(new JSONObject().put("package",intent.getStringExtra("package")).put("versionName",intent.getStringExtra("versionName")).put("versionCode",intent.getLongExtra("versionCode",0)).put("success",intent.getBooleanExtra("success",false)).put("time",intent.getLongExtra("time",System.currentTimeMillis())));for(int i=0;i<old.length()&&history.length()<30;i++)history.put(old.get(i));prefs.edit().putString(KEY,history.toString()).apply();}catch(Exception ignored){}}
}
