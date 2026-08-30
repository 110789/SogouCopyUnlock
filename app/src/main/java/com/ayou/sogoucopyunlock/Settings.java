package com.ayou.sogoucopyunlock;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

public class Settings {

    public static final String PACKAGE_NAME = "com.ayou.sogoucopyunlock";
    public static final String PREFS_NAME = "sogou_copy_unlock_prefs";

    public static final String KEY_DEBUG = "debug_log";
    public static final String KEY_COPY_LIMIT = "feature_copy_limit";
    public static final String KEY_TOOLBAR = "feature_toolbar";
    public static final String KEY_PHRASE_LENGTH = "feature_phrase_length";
    public static final String KEY_CLIPBOARD_MOVE = "feature_clipboard_move";
    public static final String KEY_CLIPBOARD_HISTORY = "feature_clipboard_history";

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static void setValue(Context context, String key, boolean value) {
        prefs(context).edit().putBoolean(key, value).commit();
        makeReadable(context);
    }

    public static boolean getValue(Context context, String key, boolean defaultValue) {
        return prefs(context).getBoolean(key, defaultValue);
    }

    private static void makeReadable(Context context) {
        File dataDir = new File(context.getApplicationInfo().dataDir);
        dataDir.setExecutable(true, false);
        dataDir.setReadable(true, false);

        File prefsDir = new File(dataDir, "shared_prefs");
        prefsDir.setExecutable(true, false);
        prefsDir.setReadable(true, false);

        File prefsFile = new File(prefsDir, PREFS_NAME + ".xml");
        prefsFile.setReadable(true, false);
    }
}
