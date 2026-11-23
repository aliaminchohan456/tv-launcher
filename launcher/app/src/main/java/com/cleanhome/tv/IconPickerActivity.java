package com.cleanhome.tv;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Picks an icon from another app.
 *
 * First the app grid, then the icons that belong to the app that was picked.
 * The answer comes back as the spec the edit screen understands,
 * "app:package:icon" or "app:package:banner".
 */
public class IconPickerActivity extends Activity {
    public static final String EXTRA_SPEC = "spec";
    /** When this is set the screen only picks an app and returns its package. */
    public static final String EXTRA_PICK_APP = "pickApp";
    public static final String EXTRA_REPLACE_APP = "replaceApp";
    public static final String EXTRA_PACKAGE = "package";
    private static final int COLS = 4;

    private final List<Entry> entries = new ArrayList<>();
    private RecyclerView grid;
    private TextView title;
    private TextView help;
    private FocusRing ring;
    private Adapter adapter;
    private String chosenPackage;
    private boolean pickAppMode;
    private boolean replaceApp;
    private Typeface medium;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_icon_picker);
        pickAppMode = getIntent().getBooleanExtra(EXTRA_PICK_APP, false);
        grid = findViewById(R.id.picker_grid);
        title = findViewById(R.id.picker_title);
        help = findViewById(R.id.picker_help);
        ring = new FocusRing((FrameLayout) findViewById(R.id.picker_root));
        medium = outfit(500);
        title.setTypeface(outfit(600));
        help.setTypeface(outfit(400));
        TextView kicker = findViewById(R.id.picker_kicker);
        kicker.setTypeface(medium);
        if (pickAppMode) {
            replaceApp = getIntent().getBooleanExtra(EXTRA_REPLACE_APP, false);
            title.setText(replaceApp ? "Replace dock app" : "Add to dock");
            kicker.setText("DOCK");
        }
        help.setText("Finding apps…");
        grid.setLayoutManager(new GridLayoutManager(this, COLS));
        grid.setItemAnimator(null);
        adapter = new Adapter();
        grid.setAdapter(adapter);
        grid.addOnLayoutChangeListener((view, left, top, right, bottom,
                                        oldLeft, oldTop, oldRight, oldBottom) ->
                view.setClipBounds(new Rect(0, 0, right - left, bottom - top)));
        grid.getViewTreeObserver().addOnPreDrawListener(() -> {
            View focused = grid.findFocus();
            View art = focused == null ? null : focused.findViewById(R.id.art);
            if (art != null) {
                ring.moveTo(art, dp(16));
            } else {
                ring.hide();
            }
            return true;
        });
        new Thread(this::loadApps, "picker-apps").start();
    }

    /** Every app that can be launched, with the icon it would give. */
    private void loadApps() {
        final List<Entry> found = new ArrayList<>();
        if (pickAppMode) {
            Set<String> docked = new HashSet<>(Dock.load(this));
            for (HomeApp app : AppLoader.load(this)) {
                if (!docked.contains(app.packageName)) {
                    Entry entry = new Entry();
                    entry.packageName = app.packageName;
                    entry.label = app.label;
                    entry.art = app.art;
                    found.add(entry);
                }
            }
            showAppEntries(found);
            return;
        }
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        query.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        Set<String> seen = new HashSet<>();
        for (ResolveInfo info : pm.queryIntentActivities(query, 0)) {
            if (info.activityInfo == null) {
                continue;
            }
            String pkg = info.activityInfo.packageName;
            if (!seen.add(pkg)) {
                continue;
            }
            CharSequence label = info.loadLabel(pm);
            Drawable art = info.activityInfo.loadBanner(pm);
            if (art == null) {
                art = info.loadIcon(pm);
            }
            if (art == null) {
                continue;
            }
            Entry entry = new Entry();
            entry.packageName = pkg;
            entry.label = label == null ? pkg : label.toString();
            entry.art = AppLoader.toBitmap(art, 480);
            entry.spec = "app:" + pkg + ":banner";
            found.add(entry);
        }
        java.util.Collections.sort(found, (a, b) -> a.label.compareToIgnoreCase(b.label));
        showAppEntries(found);
    }

    private void showAppEntries(List<Entry> found) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            entries.clear();
            entries.addAll(found);
            adapter.notifyDataSetChanged();
            if (found.isEmpty()) {
                help.setText(pickAppMode ? "No other apps are available for the dock." : "No apps found.");
            } else if (pickAppMode) {
                help.setText(replaceApp ? "Press OK to replace with the focused app."
                        : "Press OK to add the focused app.");
            } else {
                help.setText("Choose an app to browse its artwork.");
            }
        });
    }

    /** The icons of the app that was picked, its banner and its icon. */
    private void showIconsOf(String pkg) {
        chosenPackage = pkg;
        final List<Entry> icons = new ArrayList<>();
        PackageManager pm = getPackageManager();
        Set<String> seen = new HashSet<>();
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        query.setPackage(pkg);
        for (ResolveInfo info : pm.queryIntentActivities(query, 0)) {
            if (info.activityInfo == null) {
                continue;
            }
            Drawable banner = info.activityInfo.loadBanner(pm);
            if (banner != null && seen.add("banner")) {
                Entry entry = new Entry();
                entry.packageName = pkg;
                entry.label = "Banner";
                entry.art = AppLoader.toBitmap(banner, 480);
                entry.spec = "app:" + pkg + ":banner";
                icons.add(entry);
            }
        }
        try {
            Drawable icon = pm.getApplicationIcon(pkg);
            if (icon != null && seen.add("icon")) {
                Entry entry = new Entry();
                entry.packageName = pkg;
                entry.label = "App icon";
                entry.art = AppLoader.toBitmap(icon, 480);
                entry.spec = "app:" + pkg + ":icon";
                entry.icon = true;
                icons.add(entry);
            }
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        title.setText(pickLabel(pkg));
        help.setText("Pick the picture you want to use.");
        entries.clear();
        entries.addAll(icons);
        adapter.notifyDataSetChanged();
        grid.scrollToPosition(0);
    }

    private String pickLabel(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            CharSequence label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
            if (label != null && label.length() > 0) {
                return label.toString();
            }
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        return pkg;
    }

    private void pick(Entry entry) {
        Intent result = new Intent();
        result.putExtra(EXTRA_SPEC, entry.spec);
        setResult(RESULT_OK, result);
        finish();
    }

    private static final class Entry {
        String packageName;
        String label;
        Bitmap art;
        String spec;
        boolean icon;
    }

    private final class Adapter extends RecyclerView.Adapter<Adapter.Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View item = LayoutInflater.from(parent.getContext()).inflate(R.layout.tile_grid, parent, false);
            item.setFocusable(true);
            item.setDefaultFocusHighlightEnabled(false);
            item.setPadding(0, dp(12), 0, dp(18));
            CardArtView art = item.findViewById(R.id.art);
            CardArtView.round(art, 16f / 180f);
            art.setBackgroundColor(0xFF1C1C20);
            TextView name = item.findViewById(R.id.name);
            name.setTypeface(medium);
            return new Holder(item, art, name);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Entry entry = entries.get(position);
            holder.art.setImageBitmap(entry.art);
            holder.art.setFitInside(entry.icon);
            holder.name.setText(entry.label);
            holder.itemView.setContentDescription(entry.label);
            holder.itemView.setOnFocusChangeListener(null);
            boolean focused = holder.itemView.hasFocus();
            holder.art.animate().cancel();
            holder.art.setScaleX(focused ? CardFocus.SCALE : 1f);
            holder.art.setScaleY(focused ? CardFocus.SCALE : 1f);
            holder.art.setAlpha(focused ? 1f : CardFocus.DIM);
            holder.art.setTranslationZ(focused ? dp(8) : 0f);
            holder.art.setArtFocus(focused);
            holder.name.setAlpha(focused ? 1f : 0.68f);
            holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
                holder.art.animate().cancel();
                CardFocus.apply(holder.art, hasFocus);
                holder.name.animate().alpha(hasFocus ? 1f : 0.68f)
                        .setDuration(Motion.DURATION)
                        .setInterpolator(Motion.EASING)
                        .start();
            });
            holder.itemView.setOnClickListener(v -> {
                if (pickAppMode) {
                    Intent result = new Intent();
                    result.putExtra(EXTRA_PACKAGE, entry.packageName);
                    setResult(RESULT_OK, result);
                    finish();
                    return;
                }
                if (chosenPackage == null) {
                    showIconsOf(entry.packageName);
                } else {
                    pick(entry);
                }
            });
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final CardArtView art;
            final TextView name;

            Holder(View view, CardArtView art, TextView name) {
                super(view);
                this.art = art;
                this.name = name;
            }
        }
    }

    private Typeface outfit(int weight) {
        return new Typeface.Builder(getAssets(), "fonts/outfit.ttf")
                .setFontVariationSettings("'wght' " + weight)
                .build();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
