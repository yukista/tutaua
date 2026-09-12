package com.yukista.tutaua.manager;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import java.util.HashMap;
import java.util.Map;

final class BoxConfigClient {
    static Map<String, String> read(Context context) {
        Map<String, String> values = new HashMap<>();
        try (Cursor cursor = context.getContentResolver().query(
                Uri.parse("content://com.yukista.tutaua.box.config/config"), null, null, null, null)) {
            if (cursor != null) while (cursor.moveToNext())
                values.put(cursor.getString(cursor.getColumnIndexOrThrow("key")),
                        cursor.getString(cursor.getColumnIndexOrThrow("value")));
        } catch (RuntimeException ignored) { }
        return values;
    }
    private BoxConfigClient() { }
}
