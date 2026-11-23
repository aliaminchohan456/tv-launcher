package com.cleanhome.tv;

import android.content.Intent;
import android.graphics.Bitmap;

final class HomeApp {
    final String packageName;
    final String label;
    final Bitmap art;
    final Intent launch;
    final int color;

    HomeApp(String packageName, String label, Bitmap art, Intent launch, int color) {
        this.packageName = packageName;
        this.label = label;
        this.art = art;
        this.launch = launch;
        this.color = color;
    }
}
