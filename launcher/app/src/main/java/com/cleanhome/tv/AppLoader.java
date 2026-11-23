package com.cleanhome.tv;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class AppLoader {
    private AppLoader() {}

    static List<HomeApp> load(Context context) {
        PackageManager pm = context.getPackageManager();
        String self = context.getPackageName();
        Set<String> seen = new HashSet<>();
        List<HomeApp> apps = new ArrayList<>();
        collect(context, pm, self, seen, apps, Intent.CATEGORY_LEANBACK_LAUNCHER);
        collect(context, pm, self, seen, apps, Intent.CATEGORY_LAUNCHER);
        Collections.sort(apps, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return apps;
    }

    private static void collect(Context context, PackageManager pm, String self, Set<String> seen,
                                List<HomeApp> apps, String category) {
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(category);
        List<ResolveInfo> found = pm.queryIntentActivities(query, 0);
        for (ResolveInfo info : found) {
            if (info.activityInfo == null) {
                continue;
            }
            String pkg = info.activityInfo.packageName;
            if (self.equals(pkg) || !seen.add(pkg)) {
                continue;
            }
            if ("com.android.tv.settings".equals(pkg) || "com.android.settings".equals(pkg)) {
                continue;
            }
            CharSequence label = info.loadLabel(pm);
            Drawable art = info.activityInfo.loadBanner(pm);
            if (art == null) {
                art = info.loadIcon(pm);
            }
            Bitmap bitmap = toBitmap(art, 480);
            Bitmap custom = AppStyles.loadCustomArt(context, pkg, 480);
            if (custom != null) {
                bitmap = custom;
            }
            Intent launch = new Intent(Intent.ACTION_MAIN);
            launch.addCategory(category);
            launch.setComponent(new ComponentName(pkg, info.activityInfo.name));
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            String name = label == null ? pkg : label.toString();
            name = tidyName(pkg, name);
            String customName = AppStyles.label(context, pkg);
            if (customName != null) {
                name = customName;
            }
            apps.add(new HomeApp(pkg, name, bitmap, launch, sample(bitmap)));
        }
    }

    /** A few apps ship a name with one word stuck to the next one. */
    private static String tidyName(String pkg, String name) {
        if ("app.netmirror.newtv".equals(pkg) && "NetMirrorTV".equals(name)) {
            return "NetMirror TV";
        }
        return name;
    }

    static Bitmap toBitmap(Drawable drawable, int maxWidth) {
        if (drawable == null) {
            return null;
        }
        int w = drawable.getIntrinsicWidth();
        int h = drawable.getIntrinsicHeight();
        if (w <= 0 || h <= 0) {
            w = maxWidth;
            h = maxWidth * 9 / 16;
        }
        if (w > maxWidth) {
            h = Math.max(1, h * maxWidth / w);
            w = maxWidth;
        }
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, w, h);
        drawable.draw(canvas);
        return bitmap;
    }

    /** Average of the colourful pixels. Near-black and near-white are skipped. */
    static int sample(Bitmap bitmap) {
        if (bitmap == null) {
            return 0xFF6E6E73;
        }
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int stepX = Math.max(1, w / 16);
        int stepY = Math.max(1, h / 16);
        long r = 0;
        long g = 0;
        long b = 0;
        int n = 0;
        for (int y = 0; y < h; y += stepY) {
            for (int x = 0; x < w; x += stepX) {
                int c = bitmap.getPixel(x, y);
                if (Color.alpha(c) < 160) {
                    continue;
                }
                int rr = Color.red(c);
                int gg = Color.green(c);
                int bb = Color.blue(c);
                int max = Math.max(rr, Math.max(gg, bb));
                int min = Math.min(rr, Math.min(gg, bb));
                if (max < 36 || (max - min) < 16) {
                    continue;
                }
                r += rr;
                g += gg;
                b += bb;
                n++;
            }
        }
        if (n == 0) {
            return 0xFF6E6E73;
        }
        return lift(Color.rgb((int) (r / n), (int) (g / n), (int) (b / n)));
    }

    /**
     * A dark app would give a black screen, so the colour is lifted until it
     * can light the panel, and a washed out colour is pushed away from grey.
     * The wash has to read for every app in the dock, not only for the bright
     * ones.
     */
    private static int lift(int rgb) {
        float[] hsv = new float[3];
        Color.colorToHSV(rgb, hsv);
        float saturation = hsv[1];
        float value = hsv[2];
        // Only a colour that already has a hue gets more of one, otherwise a
        // grey app would come out in whatever colour the hue happens to be.
        if (saturation > 0.10f) {
            saturation = Math.max(saturation, 0.45f);
        }
        value = Math.max(value, 0.40f);
        hsv[1] = saturation;
        hsv[2] = value;
        return Color.HSVToColor(hsv);
    }
}
