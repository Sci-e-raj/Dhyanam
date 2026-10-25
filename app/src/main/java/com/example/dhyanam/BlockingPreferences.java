package com.example.dhyanam;

import android.content.Context;
import android.content.SharedPreferences;

final class BlockingPreferences {
    private static final String FILE_NAME = "focusbuddy_settings";
    private static final String BLOCK_SHORTS_KEY = "block_shorts";

    private BlockingPreferences() {
    }

    static boolean isBlockingEnabled(Context context) {
        return preferences(context).getBoolean(BLOCK_SHORTS_KEY, false);
    }

    static void setBlockingEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(BLOCK_SHORTS_KEY, enabled).apply();
    }

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }
}
