package com.yukista.tutaua.box;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import java.util.Map;

public final class ConfigProvider extends ContentProvider {
    public static final Uri URI = Uri.parse("content://com.yukista.tutaua.box.config/config");

    @Override public boolean onCreate() { return true; }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        MatrixCursor cursor = new MatrixCursor(new String[]{"key", "value"});
        if (getContext() == null || !"config".equals(uri.getLastPathSegment())) return cursor;
        for (Map.Entry<String, String> entry : BoxConfig.values(getContext()).entrySet())
            cursor.addRow(new Object[]{entry.getKey(), entry.getValue()});
        return cursor;
    }

    @Override public String getType(Uri uri) { return "vnd.android.cursor.dir/vnd.tutaua.config"; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
