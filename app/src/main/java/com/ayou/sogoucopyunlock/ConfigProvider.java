package com.ayou.sogoucopyunlock;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

public class ConfigProvider extends ContentProvider {

    public static final String AUTHORITY = "com.ayou.sogoucopyunlock.provider";

    private static final String[] KEYS = {
            Settings.KEY_DEBUG,
            Settings.KEY_COPY_LIMIT,
            Settings.KEY_TOOLBAR,
            Settings.KEY_PHRASE_LENGTH,
            Settings.KEY_CLIPBOARD_MOVE,
            Settings.KEY_CLIPBOARD_HISTORY,
    };

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                         String[] selectionArgs, String sortOrder) {
        SharedPreferences prefs = Settings.prefs(getContext());
        MatrixCursor cursor = new MatrixCursor(KEYS);
        Object[] row = new Object[KEYS.length];
        for (int i = 0; i < KEYS.length; i++) {
            boolean defaultValue = !Settings.KEY_DEBUG.equals(KEYS[i]);
            row[i] = prefs.getBoolean(KEYS[i], defaultValue) ? 1 : 0;
        }
        cursor.addRow(row);
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.item/vnd." + AUTHORITY + ".config";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
