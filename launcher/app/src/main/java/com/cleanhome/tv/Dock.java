package com.cleanhome.tv;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * The dock, and the settings the user changed about it.
 *
 * The list starts as the one below and the user can change it from the
 * launcher's own settings screen.
 */
public final class Dock {
    private Dock() {}

    public static final String[] DEFAULT = {
            "com.netflix.ninja",
            "io.gh.reisxd.tizentube.cobalt",
            "com.nuvio.app",
            "app.netmirror.newtv"
    };

    /** The three buttons in the top right corner. */
    public static final String BUTTON_INPUTS = "inputs";
    public static final String BUTTON_WIFI = "wifi";
    public static final String BUTTON_SETTINGS = "settings";

    private static final String PREFS = "dock";
    private static final String KEY_LIST = "packages";
    private static final String KEY_HIDDEN = "hidden";

    /** The dock as the user left it. */
    public static List<String> load(Context context) {
        SharedPreferences prefs = prefs(context);
        String saved = prefs.getString(KEY_LIST, null);
        List<String> list = new ArrayList<>();
        if (saved != null && !saved.isEmpty()) {
            for (String pkg : saved.split("\n")) {
                String trimmed = pkg.trim();
                if (!trimmed.isEmpty() && !list.contains(trimmed)) {
                    list.add(trimmed);
                }
            }
        }
        if (saved == null) {
            for (String pkg : DEFAULT) {
                list.add(pkg);
            }
        }
        return list;
    }

    public static List<String> loadAvailable(Context context) {
        List<String> packages = load(context);
        packages.removeIf(pkg -> {
            try {
                context.getPackageManager().getApplicationInfo(pkg, 0);
                return false;
            } catch (android.content.pm.PackageManager.NameNotFoundException missing) {
                return true;
            }
        });
        return packages;
    }

    public static void save(Context context, List<String> packages) {
        StringBuilder text = new StringBuilder();
        for (String pkg : packages) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(pkg);
        }
        prefs(context).edit().putString(KEY_LIST, text.toString()).apply();
    }

    /** Whether the user hid one of the buttons in the top right corner. */
    public static boolean isHidden(Context context, String button) {
        return prefs(context).getStringSet(KEY_HIDDEN, new java.util.HashSet<>()).contains(button);
    }

    public static void setHidden(Context context, String button, boolean hide) {
        SharedPreferences prefs = prefs(context);
        java.util.Set<String> hidden = new java.util.HashSet<>(
                prefs.getStringSet(KEY_HIDDEN, new java.util.HashSet<>()));
        if (hide) {
            hidden.add(button);
        } else {
            hidden.remove(button);
        }
        prefs.edit().putStringSet(KEY_HIDDEN, hidden).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
