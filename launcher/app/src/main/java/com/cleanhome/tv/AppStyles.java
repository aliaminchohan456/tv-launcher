package com.cleanhome.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;

import java.io.File;

/** Names and icons this home screen shows instead of the app's own. */
final class AppStyles {
    private static final String PREFS = "app-styles";

    private AppStyles() {}

    static String label(Context context, String packageName) {
        String value = prefs(context).getString(packageName + ".label", null);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    static void setLabel(Context context, String packageName, String label) {
        SharedPreferences.Editor editor = prefs(context).edit();
        if (label == null || label.trim().isEmpty()) {
            editor.remove(packageName + ".label");
        } else {
            editor.putString(packageName + ".label", label.trim());
        }
        editor.apply();
    }

    /** null means the real app art. "app:pkg:icon", "app:pkg:banner", or "file:name". */
    static String iconSpec(Context context, String packageName) {
        return prefs(context).getString(packageName + ".icon", null);
    }

    static void setIconSpec(Context context, String packageName, String spec) {
        SharedPreferences.Editor editor = prefs(context).edit();
        if (spec == null || spec.isEmpty()) {
            editor.remove(packageName + ".icon");
        } else {
            editor.putString(packageName + ".icon", spec);
        }
        editor.apply();
    }

    static void bump(Context context) {
        SharedPreferences preferences = prefs(context);
        preferences.edit().putInt("gen", preferences.getInt("gen", 0) + 1).apply();
    }

    static int generation(Context context) {
        return prefs(context).getInt("gen", 0);
    }

    static File iconFile(Context context, String fileName) {
        File dir = new File(context.getFilesDir(), "icons");
        // If the directory cannot be made, the caller still gets the path and
        // its own existence check decides what to do.
        if (!dir.isDirectory()) dir.mkdirs();
        String safeName = new File(fileName).getName();
        return new File(dir, safeName);
    }

    static Bitmap loadCustomArt(Context context, String packageName, int maxWidth) {
        String spec = iconSpec(context, packageName);
        if (spec == null || spec.isEmpty()) {
            return null;
        }
        try {
            if (spec.startsWith("file:")) {
                File file = iconFile(context, spec.substring("file:".length()));
                if (!file.isFile()) {
                    return null;
                }
                return decodeFile(file, maxWidth);
            }
            if (spec.startsWith("app:")) {
                String[] parts = spec.split(":");
                if (parts.length < 3) {
                    return null;
                }
                PackageManager pm = context.getPackageManager();
                Drawable drawable = "banner".equals(parts[2])
                        ? pm.getApplicationBanner(parts[1])
                        : pm.getApplicationIcon(parts[1]);
                return AppLoader.toBitmap(drawable, maxWidth);
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static Bitmap decodeFile(File file, int maxWidth) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        int sample = 1;
        while (bounds.outWidth / sample > maxWidth && sample < 16) {
            sample *= 2;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap decoded = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        if (decoded == null) {
            return null;
        }
        if (decoded.getWidth() <= maxWidth) {
            return decoded;
        }
        int height = Math.max(1, decoded.getHeight() * maxWidth / decoded.getWidth());
        Bitmap scaled = Bitmap.createScaledBitmap(decoded, maxWidth, height, true);
        if (scaled != decoded) {
            decoded.recycle();
        }
        return scaled;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
