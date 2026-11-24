package com.cleanhome.tv;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class EditAppActivity extends Activity {
    static final String EXTRA_PACKAGE = "package";
    private static final int PHOTO = 22;
    private static final int PICKER = 23;

    private String packageName;
    private String selectedSpec;
    private boolean focusPlaced;
    private ImageView preview;
    private EditText nameField;
    private Bitmap standardArt;
    private FocusRing ring;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // The name field is the first thing that takes the focus, so the window
        // would open the keyboard by itself. The field is focused but the
        // keyboard stays away until the user asks for it.
        getWindow().setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        packageName = getIntent().getStringExtra(EXTRA_PACKAGE);
        if (packageName == null || packageName.isEmpty()) {
            finish();
            return;
        }
        setContentView(R.layout.activity_edit);
        preview = findViewById(R.id.edit_preview);
        nameField = findViewById(R.id.edit_name);
        ring = new FocusRing((android.widget.FrameLayout) findViewById(R.id.edit_root));
        Typeface thin = outfit(300);
        Typeface text = outfit(420);
        ((TextView) findViewById(R.id.edit_heading)).setTypeface(thin);
        ((TextView) findViewById(R.id.edit_kicker)).setTypeface(text);
        ((TextView) findViewById(R.id.edit_name_label)).setTypeface(text);
        ((TextView) findViewById(R.id.edit_name_help)).setTypeface(text);
        ((TextView) findViewById(R.id.edit_own_label)).setTypeface(text);
        ((TextView) findViewById(R.id.edit_other_label)).setTypeface(text);
        ((TextView) findViewById(R.id.edit_save)).setTypeface(text);
        nameField.setTypeface(thin);
        round(preview, 16f / 200f);
        String custom = AppStyles.label(this, packageName);
        String real = realLabel();
        showName(custom != null ? custom : real);
        findViewById(R.id.edit_reset).setOnClickListener(v -> resetToOriginal());
        findViewById(R.id.edit_from_gallery).setOnClickListener(v -> openGallery());
        findViewById(R.id.edit_other_apps).setOnClickListener(v -> openIconPicker());
        findViewById(R.id.edit_save).setOnClickListener(v -> save());
        nameField.setShowSoftInputOnFocus(false);
        // The name is the first thing anyone wants to change, so the field
        // takes the focus when the screen opens. The keyboard stays shut, the
        // setShowSoftInputOnFocus above is what does that.
        nameField.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                // Put the cursor at the end of the name, not in front of it.
                nameField.setSelection(nameField.getText().length());
            }
        });
        nameField.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) {
                return false;
            }
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(nameField.getWindowToken(), 0);
                }
                LinearLayout row = findViewById(R.id.own_icons);
                // The row holds wrappers, the picture that takes the focus is
                // inside the first one.
                for (int i = 0; row != null && i < row.getChildCount(); i++) {
                    View child = row.getChildAt(i);
                    View target = child;
                    if (child instanceof android.widget.FrameLayout
                            && ((android.widget.FrameLayout) child).getChildCount() > 0) {
                        target = ((android.widget.FrameLayout) child).getChildAt(0);
                    }
                    if (target.requestFocus()) {
                        return true;
                    }
                }
                return false;
            }
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(nameField, 0);
                }
                return true;
            }
            return false;
        });
        new Thread(this::loadChoices, "edit-icons").start();
    }

    private void loadChoices() {
        PackageManager pm = getPackageManager();
        Bitmap standard = standardArt(pm, packageName);
        Bitmap custom = AppStyles.loadCustomArt(this, packageName, 480);
        String currentSpec = AppStyles.iconSpec(this, packageName);
        List<Choice> own = new ArrayList<>();
        addArt(own, pm, packageName, "icon");
        addArt(own, pm, packageName, "banner");
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            selectedSpec = currentSpec == null ? "" : currentSpec;
            standardArt = standard;
            Bitmap showing = custom != null ? custom : standard;
            if (showing != null) {
                preview.setImageBitmap(showing);
            }
            LinearLayout row = findViewById(R.id.own_icons);
            addChoiceView(row, new Choice(standard, ""));
            for (Choice choice : own) {
                addChoiceView(row, choice);
            }
            applyBadge();
        });
    }

    /** The same picture the home screen uses before any custom icon. */
    private Bitmap standardArt(PackageManager pm, String pkg) {
        Drawable art = null;
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent query = new Intent(Intent.ACTION_MAIN);
            query.addCategory(category);
            query.setPackage(pkg);
            List<ResolveInfo> found = pm.queryIntentActivities(query, 0);
            if (found.isEmpty() || found.get(0).activityInfo == null) {
                continue;
            }
            art = found.get(0).activityInfo.loadBanner(pm);
            if (art == null) {
                try {
                    art = pm.getApplicationBanner(pkg);
                } catch (PackageManager.NameNotFoundException ignored) {
                }
            }
            if (art == null) {
                art = found.get(0).loadIcon(pm);
            }
            if (art != null) {
                break;
            }
        }
        if (art == null) {
            try {
                art = pm.getApplicationIcon(pkg);
            } catch (PackageManager.NameNotFoundException ignored) {
                return null;
            }
        }
        return AppLoader.toBitmap(art, 480);
    }

    private void addArt(List<Choice> into, PackageManager pm, String pkg, String kind) {
        try {
            Drawable drawable = "banner".equals(kind)
                    ? pm.getApplicationBanner(pkg)
                    : pm.getApplicationIcon(pkg);
            Bitmap bitmap = AppLoader.toBitmap(drawable, 320);
            if (bitmap != null) {
                into.add(new Choice(bitmap, "app:" + pkg + ":" + kind));
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * One of the pictures of this app. It is a card like the ones in the dock
     * and the grid, the same picture class, the same focus ring and the same
     * dimming.
     */
    private void addChoiceView(LinearLayout row, Choice choice) {
        if (choice.bitmap == null) {
            return;
        }
        android.widget.FrameLayout tile = new android.widget.FrameLayout(this);
        LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(dp(148), dp(84));
        tileParams.setMarginEnd(dp(8));
        tile.setLayoutParams(tileParams);
        tile.setTag(choice.spec);

        CardArtView art = new CardArtView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setImageBitmap(choice.bitmap);
        CardArtView.round(art, 16f / 148f);
        art.setFocusable(true);
        art.setClickable(true);
        art.setContentDescription(choice.spec.isEmpty() ? "Current icon" : "Icon");
        art.setOnClickListener(v -> choose(choice.spec, choice.bitmap));
        watchFocus(art);
        tile.addView(art, new android.widget.FrameLayout.LayoutParams(dp(148), dp(84)));

        // The badge that marks the picture in use.
        ImageView badge = new ImageView(this);
        badge.setImageResource(R.drawable.badge_selected);
        badge.setVisibility(View.GONE);
        android.widget.FrameLayout.LayoutParams badgeParams =
                new android.widget.FrameLayout.LayoutParams(dp(22), dp(22));
        badgeParams.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
        badgeParams.setMargins(0, dp(4), dp(8), 0);
        tile.addView(badge, badgeParams);

        row.addView(tile);
    }

    private void watchFocus(CardArtView art) {
        art.setOnFocusChangeListener((v, hasFocus) -> {
            CardFocus.apply(v, hasFocus);
            if (hasFocus) {
                ring.moveTo(v, dp(16));
            } else {
                // The focus left the pictures for a button, the ring goes too.
                v.post(() -> {
                    if (!(getCurrentFocus() instanceof CardArtView)) {
                        ring.hide();
                    }
                });
            }
        });
    }

    /** Marks the picture that is in use. */
    private void applyBadge() {
        LinearLayout row = findViewById(R.id.own_icons);
        if (row == null) {
            return;
        }
        String chosen = selectedSpec == null ? "" : selectedSpec;
        for (int i = 0; i < row.getChildCount(); i++) {
            View tile = row.getChildAt(i);
            if (!(tile instanceof android.widget.FrameLayout)) {
                continue;
            }
            android.widget.FrameLayout frame = (android.widget.FrameLayout) tile;
            if (frame.getChildCount() < 2) {
                continue;
            }
            Object spec = frame.getTag();
            boolean current = spec != null && spec.toString().equals(chosen);
            frame.getChildAt(1).setVisibility(current ? View.VISIBLE : View.GONE);
        }
        // Mark the source button when the chosen art came from another app or the gallery.
        TextView otherApps = findViewById(R.id.edit_other_apps);
        if (otherApps != null) {
            boolean foreign = chosen.startsWith("app:")
                    && !chosen.startsWith("app:" + packageName + ":");
            otherApps.setCompoundDrawablesWithIntrinsicBounds(null, null,
                    foreign ? getDrawable(R.drawable.badge_selected) : null, null);
        }
        TextView fromGallery = findViewById(R.id.edit_from_gallery);
        if (fromGallery != null) {
            boolean fromFile = chosen.startsWith("file:");
            fromGallery.setCompoundDrawablesWithIntrinsicBounds(null, null,
                    fromFile ? getDrawable(R.drawable.badge_selected) : null, null);
        }
    }

    private void choose(String spec, Bitmap bitmap) {
        selectedSpec = spec;
        if (bitmap != null) {
            preview.setImageBitmap(bitmap);
        }
        applyBadge();
    }

    /** Opens the screen that lists every app, so an icon can be taken from one. */
    private void openIconPicker() {
        try {
            startActivityForResult(new Intent(this, IconPickerActivity.class), PICKER);
        } catch (Exception ex) {
            Toast.makeText(this, "The icon picker could not open", Toast.LENGTH_SHORT).show();
        }
    }

    /** The picture behind a spec, so the preview can show it. */
    private Bitmap artForSpec(String spec) {
        if (spec == null || spec.isEmpty()) {
            return standardArt;
        }
        if (spec.startsWith("app:")) {
            String[] parts = spec.split(":");
            if (parts.length >= 3) {
                PackageManager pm = getPackageManager();
                try {
                    Drawable drawable = "banner".equals(parts[2])
                            ? pm.getApplicationBanner(parts[1])
                            : pm.getApplicationIcon(parts[1]);
                    return AppLoader.toBitmap(drawable, 480);
                } catch (Exception ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * The name field takes the focus as soon as the screen opens, so nobody
     * has to walk down to it. The keyboard does not come up, that is the
     * setShowSoftInputOnFocus on the field.
     */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && !focusPlaced) {
            focusPlaced = true;
            nameField.requestFocus();
        }
    }

    private void openGallery() {
        String permission = photoPermission();
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{permission}, PHOTO);
            return;
        }
        startPhotoPicker();
    }

    private void startPhotoPicker() {
        try {
            startActivityForResult(new Intent(this, PhotoPickerActivity.class), PHOTO);
        } catch (Exception ex) {
            Toast.makeText(this, "The picture picker could not open", Toast.LENGTH_SHORT).show();
        }
    }

    private static String photoPermission() {
        return android.os.Build.VERSION.SDK_INT >= 33
                ? "android.permission.READ_MEDIA_IMAGES"
                : "android.permission.READ_EXTERNAL_STORAGE";
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != PHOTO) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startPhotoPicker();
        } else {
            Toast.makeText(this, "Without photo access there is nothing to pick",
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICKER) {
            if (resultCode != RESULT_OK || data == null) {
                return;
            }
            String spec = data.getStringExtra(IconPickerActivity.EXTRA_SPEC);
            if (spec == null || spec.isEmpty()) {
                return;
            }
            selectedSpec = spec;
            Bitmap art = artForSpec(spec);
            if (art != null) {
                preview.setImageBitmap(art);
            }
            applyBadge();
            return;
        }
        if (requestCode != PHOTO || resultCode != RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getParcelableExtra(PhotoPickerActivity.EXTRA_URI);
        if (uri == null) {
            return;
        }
        new Thread(() -> {
            Bitmap bitmap = copyGallery(uri);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (bitmap == null) {
                    Toast.makeText(this, "Could not load that picture", Toast.LENGTH_SHORT).show();
                    return;
                }
                preview.setImageBitmap(bitmap);
                selectedSpec = "file:icon_" + packageName.replace('.', '_') + ".png";
                applyBadge();
            });
        }, "edit-gallery").start();
    }

    private Bitmap copyGallery(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) {
                return null;
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, bounds);
            int sample = 1;
            while (bounds.outWidth / sample > 480 && sample < 16) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap decoded;
            try (InputStream again = getContentResolver().openInputStream(uri)) {
                if (again == null) {
                    return null;
                }
                decoded = BitmapFactory.decodeStream(again, null, opts);
            }
            if (decoded == null) {
                return null;
            }
            String fileName = "icon_" + packageName.replace('.', '_') + ".png";
            File file = AppStyles.iconFile(this, fileName);
            try (FileOutputStream out = new FileOutputStream(file)) {
                decoded.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            return decoded;
        } catch (Exception ex) {
            return null;
        }
    }

    /** The app's own name, ignoring any name this home screen gave it. */
    private String realLabel() {
        try {
            CharSequence real = getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(packageName, 0));
            if (real != null && real.length() > 0) {
                return real.toString();
            }
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        return packageName;
    }

    private void showName(String value) {
        nameField.setText(value);
        ((TextView) findViewById(R.id.edit_heading)).setText(value);
    }

    /** Drops the custom name and the custom icon so the app looks like itself again. */
    private void resetToOriginal() {
        String spec = AppStyles.iconSpec(this, packageName);
        if (spec != null && spec.startsWith("file:")) {
            deleteIconFile(spec.substring("file:".length()));
        }
        // A picture that was picked but never saved still leaves a file behind.
        deleteIconFile("icon_" + packageName.replace('.', '_') + ".png");
        AppStyles.setLabel(this, packageName, null);
        AppStyles.setIconSpec(this, packageName, null);
        AppStyles.bump(this);

        selectedSpec = "";
        showName(realLabel());
        if (standardArt != null) {
            preview.setImageBitmap(standardArt);
        }
        applyBadge();
        Toast.makeText(this, "Back to the original name and icon", Toast.LENGTH_SHORT).show();
    }

    private void deleteIconFile(String fileName) {
        File file = AppStyles.iconFile(this, fileName);
        if (file.isFile() && !file.delete()) {
            Toast.makeText(this, "Could not remove the saved photo", Toast.LENGTH_SHORT).show();
        }
    }

    private void save() {
        AppStyles.setLabel(this, packageName, nameField.getText().toString());
        if (selectedSpec != null) {
            AppStyles.setIconSpec(this, packageName, selectedSpec);
        }
        AppStyles.bump(this);
        finish();
    }

    private Typeface outfit(int weight) {
        try {
            return new Typeface.Builder(getAssets(), "fonts/outfit.ttf")
                    .setFontVariationSettings("'wght' " + weight)
                    .build();
        } catch (RuntimeException ex) {
            return Typeface.create("sans-serif-light", Typeface.NORMAL);
        }
    }

    /** One picture and the spec that says where it came from. */
    static final class Choice {
        final Bitmap bitmap;
        final String spec;

        Choice(Bitmap bitmap, String spec) {
            this.bitmap = bitmap;
            this.spec = spec;
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static void round(View view, float fraction) {
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), v.getWidth() * fraction);
            }
        });
        view.setClipToOutline(true);
    }
}
