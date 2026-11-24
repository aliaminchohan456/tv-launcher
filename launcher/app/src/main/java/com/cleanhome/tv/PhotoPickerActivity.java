package com.cleanhome.tv;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Size;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Picks a picture out of the TV's own media library.
 *
 * The system picture picker is useless on this set: the only activity that
 * advertises GET_CONTENT for images is a disabled stub, so the chooser answers
 * "You don't have an app that can do this". This screen lists the images that
 * MediaStore knows about instead, so "Add photo" always has something to show.
 */
public class PhotoPickerActivity extends Activity {
    public static final String EXTRA_URI = "uri";

    private static final int COLS = 4;
    private static final int MAX = 200;

    private final List<Uri> uris = new ArrayList<>();
    private final List<Bitmap> thumbs = new ArrayList<>();
    private Adapter adapter;
    private FocusRing ring;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_picker);
        ring = new FocusRing((FrameLayout) findViewById(R.id.photo_root));
        ((TextView) findViewById(R.id.photo_kicker)).setTypeface(outfit(500));
        ((TextView) findViewById(R.id.photo_title)).setTypeface(outfit(600));
        ((TextView) findViewById(R.id.photo_empty)).setTypeface(outfit(400));
        RecyclerView grid = findViewById(R.id.photo_grid);
        grid.setLayoutManager(new GridLayoutManager(this, COLS));
        grid.setItemAnimator(null);
        adapter = new Adapter();
        grid.setAdapter(adapter);
        new Thread(this::load, "photos").start();
    }

    private void load() {
        List<Uri> found = new ArrayList<>();
        List<Bitmap> art = new ArrayList<>();
        try (Cursor cursor = getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Images.Media._ID},
                null, null,
                MediaStore.Images.Media.DATE_ADDED + " DESC")) {
            if (cursor != null) {
                while (cursor.moveToNext() && found.size() < MAX) {
                    Uri uri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0));
                    found.add(uri);
                    art.add(thumbnail(uri));
                }
            }
        } catch (Exception ignored) {
        }
        final List<Uri> resultUris = found;
        final List<Bitmap> resultThumbs = art;
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            uris.clear();
            uris.addAll(resultUris);
            thumbs.clear();
            thumbs.addAll(resultThumbs);
            adapter.notifyDataSetChanged();
            TextView empty = findViewById(R.id.photo_empty);
            empty.setVisibility(uris.isEmpty() ? View.VISIBLE : View.GONE);
            focusFirst();
        });
    }

    private Bitmap thumbnail(Uri uri) {
        try {
            Bitmap thumb = getContentResolver().loadThumbnail(uri, new Size(320, 180), null);
            if (thumb != null) {
                return thumb;
            }
        } catch (Exception ignored) {
        }
        // The system thumbnailer refuses some files. Read the picture ourselves.
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) {
                return null;
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, bounds);
            int sample = 1;
            while (bounds.outWidth / sample > 320 && sample < 16) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            try (InputStream again = getContentResolver().openInputStream(uri)) {
                return again == null ? null : BitmapFactory.decodeStream(again, null, opts);
            }
        } catch (Exception ex) {
            return null;
        }
    }

    /** The first tile takes focus so the D-pad works without a touch screen. */
    private void focusFirst() {
        RecyclerView grid = findViewById(R.id.photo_grid);
        grid.post(() -> {
            View first = grid.getChildAt(0);
            if (first != null) {
                first.requestFocus();
            }
        });
    }

    private void pick(Uri uri) {
        setResult(RESULT_OK, new Intent().putExtra(EXTRA_URI, uri));
        finish();
    }

    private final class Adapter extends RecyclerView.Adapter<Adapter.Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            CardArtView view = new CardArtView(PhotoPickerActivity.this);
            int width = dp(180);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(width, dp(101));
            lp.setMargins(dp(8), dp(8), dp(8), dp(8));
            view.setLayoutParams(lp);
            view.setScaleType(ImageView.ScaleType.CENTER_CROP);
            view.setBackgroundColor(0xFF2A2A2E);
            view.setFocusable(true);
            view.setClickable(true);
            CardArtView.round(view, 16f / 180f);
            CardFocus.apply(view, false);
            view.setOnFocusChangeListener((v, hasFocus) -> {
                v.animate().cancel();
                CardFocus.apply(v, hasFocus);
                if (hasFocus) {
                    ring.moveTo(v, dp(16));
                } else {
                    v.post(() -> {
                        if (getCurrentFocus() == null) {
                            ring.hide();
                        }
                    });
                }
            });
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Bitmap bitmap = position < thumbs.size() ? thumbs.get(position) : null;
            holder.image.setImageBitmap(bitmap);
            boolean focused = holder.image.hasFocus();
            holder.image.animate().cancel();
            CardFocus.apply(holder.image, focused);
            if (focused) {
                ring.moveTo(holder.image, dp(16));
            }
            Uri uri = position < uris.size() ? uris.get(position) : null;
            holder.image.setOnClickListener(v -> {
                if (uri != null) {
                    pick(uri);
                }
            });
        }

        @Override
        public int getItemCount() {
            return uris.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final CardArtView image;

            Holder(View view) {
                super(view);
                image = (CardArtView) view;
            }
        }
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

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
