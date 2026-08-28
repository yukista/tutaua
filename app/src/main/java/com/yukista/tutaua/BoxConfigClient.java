package com.yukista.tutaua;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

final class BoxConfigClient {
    private static final Uri URI = Uri.parse("content://com.yukista.tutaua.box.config/config");
    private BoxConfigClient() {}

    static String get(Context context, String key) {
        try (Cursor cursor = context.getContentResolver().query(URI, null, null, null, null)) {
            if (cursor == null) return "";
            int keyColumn = cursor.getColumnIndex("key");
            int valueColumn = cursor.getColumnIndex("value");
            while (cursor.moveToNext()) if (key.equals(cursor.getString(keyColumn)))
                return cursor.getString(valueColumn);
        } catch (RuntimeException ignored) { }
        return "";
    }
}
