package com.cleanhome.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.format.DateFormat;

final class ClockPreferences {
    private ClockPreferences() {}

    static boolean is24Hour(Context context) {
        int format = prefs(context).getInt("format", 0);
        return format == 0 ? DateFormat.is24HourFormat(context) : format == 2;
    }

    static String formatLabel(Context context) {
        switch (prefs(context).getInt("format", 0)) {
            case 1: return "12-hour";
            case 2: return "24-hour";
            default: return "Follow TV setting";
        }
    }

    static void cycleFormat(Context context) {
        SharedPreferences preferences = prefs(context);
        int next = (preferences.getInt("format", 0) + 1) % 3;
        preferences.edit().putInt("format", next).apply();
    }

    enum Background { AMBIENT, MEADOW, EARTH, DUSK }

    static Background background(Context context) {
        SharedPreferences settings = prefs(context);
        Background initial = settings.getBoolean("daily_earth", true)
                ? Background.AMBIENT : Background.DUSK;
        String saved = settings.getString("background", initial.name());
        if ("LANDSCAPE".equals(saved)) return Background.AMBIENT;
        try {
            return Background.valueOf(saved);
        } catch (RuntimeException badValue) {
            // A stale or hand-edited preference must never keep the dream
            // from starting. Put it back to the default instead of crashing.
            return initial;
        }
    }

    static void cycleBackground(Context context) {
        Background[] modes = Background.values();
        Background next = modes[(background(context).ordinal() + 1) % modes.length];
        prefs(context).edit().putString("background", next.name()).apply();
    }

    static boolean opensOnBack(Context context) {
        return prefs(context).getBoolean("back_opens_clock", false);
    }

    static void setOpensOnBack(Context context, boolean enabled) {
        prefs(context).edit().putBoolean("back_opens_clock", enabled).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("clock_display", Context.MODE_PRIVATE);
    }
}
