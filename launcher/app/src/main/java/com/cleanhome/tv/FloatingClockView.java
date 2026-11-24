package com.cleanhome.tv;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class FloatingClockView extends FrameLayout {
    private final FoxMascotView companion;
    private final TextView clock, colon, minutes, meridiem;
    private final LinearLayout clockRow;
    private final boolean ambient;
    private final FoxMeadowView meadow;
    private final FoxSkyView sky;
    private final android.animation.ObjectAnimator pulse;
    private final android.view.View warmGlow;
    private final TextView date;
    private final LinearLayout face;
    private final android.widget.ImageView photo;
    private final TextView credit;
    private final boolean preview;
    private final TextView hint;
    private String requested;
    private int generation;
    private int previewHour = -1;
    private int previewMinute;

    boolean stepPreviewHour(int delta) {
        if (meadow == null) return false;
        if (delta != 0) {
            if (previewHour < 0) {
                java.time.LocalTime now = java.time.LocalTime.now();
                previewHour = now.getHour();
                previewMinute = now.getMinute();
            }
            previewHour = Math.floorMod(previewHour + delta, 24);
            showHint();
            removeCallbacks(tick);
            updateTime();
        }
        return true;
    }

    private final Runnable tick = this::updateTime;
    private final Runnable hideHint = this::hideHint;

    FloatingClockView(Context context) { this(context, false); }

    FloatingClockView(Context context, boolean preview) {
        super(context);
        this.preview = preview;
        ClockPreferences.Background mode = ClockPreferences.background(context);
        meadow = mode == ClockPreferences.Background.MEADOW ? new FoxMeadowView(context) : null;
        ambient = meadow != null || mode == ClockPreferences.Background.AMBIENT;
        GradientDrawable dusk = new GradientDrawable();
        dusk.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        dusk.setGradientCenter(.27f, .68f);
        dusk.setColors(new int[]{0xFF433548, 0xFF202636, 0xFF0A101B, 0xFF05070B});
        dusk.setDither(true);
        if (meadow == null) setBackground(dusk);

        photo = new android.widget.ImageView(context);
        photo.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        photo.setColorFilter(0x53000000, android.graphics.PorterDuff.Mode.SRC_ATOP);
        addView(photo, new LayoutParams(-1, -1));
        warmGlow = new android.view.View(context);
        warmGlow.setBackgroundColor(0xff98b9b2);
        warmGlow.setAlpha(0f);
        sky = new FoxSkyView(context);
        if (meadow != null) {
            addView(meadow, new LayoutParams(-1, -1));
        } else if (ambient) {
            addView(warmGlow, new LayoutParams(-1, -1));
            addView(sky, new LayoutParams(-1, -1));
        }
        face = new LinearLayout(context);
        face.setOrientation(LinearLayout.VERTICAL);
        face.setGravity(Gravity.CENTER);
        clock = label(ambient ? 108 : 94, 700, 0xFFE9E6E2);
        colon = label(108, 700, 0xfff5f0e8);
        minutes = label(108, 700, 0xfff5f0e8);
        minutes.setFontFeatureSettings("tnum");
        meridiem = label(34, 500, 0xfff5f0e8);
        meridiem.setAlpha(.65f);
        colon.setText(":");
        pulse = android.animation.ObjectAnimator.ofFloat(colon, "alpha", 1f, .42f);
        pulse.setDuration(1300);
        pulse.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        pulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        clock.setFontFeatureSettings("tnum");
        date = label(ambient ? 22 : 18, 420, ambient ? 0xb3f5f0e8 : 0xFFC5C1C8);
        date.setLetterSpacing(.04f);
        credit = label(14f, 480, 0xFFEBE8E2);
        credit.setVisibility(GONE);
        for (TextView line : new TextView[]{clock, date, credit}) {
            line.setGravity(Gravity.CENTER);
            line.setShadowLayer(5f, 0f, 2f, 0xAA000000);
        }
        companion = new FoxMascotView(context);
        companion.setVisibility(mode == ClockPreferences.Background.AMBIENT ? VISIBLE : GONE);
        LinearLayout.LayoutParams stage = new LinearLayout.LayoutParams(dp(164), dp(124));
        stage.bottomMargin = dp(10);
        face.addView(companion, stage);
        clockRow = new LinearLayout(context);
        clockRow.setBaselineAligned(true);
        clockRow.setLayoutDirection(LAYOUT_DIRECTION_LTR);
        clockRow.addView(clock, -2, -2);
        if (ambient) {
            for (TextView digits : new TextView[]{clock, colon, minutes}) {
                digits.setShadowLayer(dp(12), 0, dp(3), 0x2e000000);
            }
            clockRow.addView(colon, -2, -2);
            clockRow.addView(minutes, -2, -2);
            LinearLayout.LayoutParams suffix = new LinearLayout.LayoutParams(-2, -2);
            suffix.leftMargin = dp(10);
            clockRow.addView(meridiem, suffix);
        }
        // Cache the large, soft-shadowed glyphs until their text or colors change.
        // The colon can then breathe by compositing its own small texture.
        if (meadow != null) {
            for (TextView label : new TextView[]{clock, colon, minutes, meridiem, date}) {
                label.setLayerType(LAYER_TYPE_HARDWARE, null);
            }
        }
        face.addView(clockRow, -2, -2);
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(-2, -2);
        if (ambient) dateParams.topMargin = dp(12);
        face.addView(date, dateParams);
        face.addView(credit, -2, -2);
        addView(face, new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        if (preview) {
            hint = label(15f, 460, 0xd8f2ede4);
            hint.setText(meadow != null ? "LEFT / RIGHT HOURS   OK GREET   BACK EXIT"
                    : ambient ? "OK GREET   BACK EXIT" : "BACK EXIT");
            hint.setGravity(Gravity.CENTER);
            hint.setLetterSpacing(.14f);
            hint.setShadowLayer(dp(6), 0, dp(2), 0xaa000000);
            hint.setAlpha(0f);
            addView(hint, new LayoutParams(-2, -2,
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        } else {
            hint = null;
        }
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void showHint() {
        if (hint == null) return;
        removeCallbacks(hideHint);
        hint.animate().cancel();
        hint.setAlpha(.88f);
        postDelayed(hideHint, 4500L);
    }

    private void hideHint() {
        if (hint != null) hint.animate().alpha(0f).setDuration(900L).start();
    }

    private TextView label(float size, int weight, int color) {
        TextView label = new TextView(getContext());
        label.setSingleLine(true);
        label.setIncludeFontPadding(false);
        label.setTextColor(color);
        label.setTextSize(size);
        label.setTypeface(outfit(weight));
        return label;
    }

    private Typeface outfit(int weight) {
        try {
            return new Typeface.Builder(getContext().getAssets(), "fonts/outfit.ttf")
                    .setFontVariationSettings("'wght' " + weight)
                    .build();
        } catch (RuntimeException ex) {
            return Typeface.create("sans-serif-light", Typeface.NORMAL);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (meadow == null && getBackground() instanceof GradientDrawable) {
            ((GradientDrawable) getBackground()).setGradientRadius(Math.max(1f, w * .78f));
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (ambient) pulse.start();
        if (hint != null) showHint();
        updateTime();
    }

    @Override
    protected void onDetachedFromWindow() {
        pulse.cancel();
        warmGlow.animate().cancel();
        removeCallbacks(tick);
        removeCallbacks(hideHint);
        if (hint != null) hint.animate().cancel();
        generation++;
        requested = null;
        photo.animate().cancel();
        photo.setImageDrawable(null);
        super.onDetachedFromWindow();
    }

    private void updateTime() {
        long now = System.currentTimeMillis();
        boolean use24 = ClockPreferences.is24Hour(getContext());
        java.time.LocalTime time = previewHour >= 0
                ? java.time.LocalTime.of(previewHour, previewMinute)
                : java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalTime();
        Date instant = new Date(now);
        String pattern = ambient ? (use24 ? "H" : "h") : (use24 ? "H:mm" : "h:mm a");
        clock.setText(ambient ? time.format(java.time.format.DateTimeFormatter.ofPattern(pattern))
                : new SimpleDateFormat(pattern, Locale.getDefault()).format(instant));
        if (ambient) {
            minutes.setText(time.format(java.time.format.DateTimeFormatter.ofPattern("mm")));
            meridiem.setText(time.format(java.time.format.DateTimeFormatter.ofPattern("a")));
            meridiem.setVisibility(use24 ? GONE : VISIBLE);
        }
        String calendar = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEE MMMM d");
        date.setText(new SimpleDateFormat(calendar, Locale.getDefault()).format(new Date(now)));
        java.time.LocalDate day = java.time.LocalDate.now();
        ClockPreferences.Background mode = ClockPreferences.background(getContext());
        if (ambient) {
            updateAmbient(time);
        } else if (mode == ClockPreferences.Background.EARTH && !day.toString().equals(requested)) {
            requested = day.toString();
            int token = ++generation;
            Context app = getContext().getApplicationContext();
            int sample = getResources().getDisplayMetrics().widthPixels <= 1920 ? 2 : 1;
            EarthEdition.WORKER.execute(() -> {
                try {
                    EarthEdition edition = EarthEdition.load(app, day, sample);
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        if (token != generation || !isAttachedToWindow()) return;
                        photo.setAlpha(0f);
                        photo.setImageBitmap(edition.image);
                        photo.animate().alpha(1f).setDuration(800).start();
                        credit.setText(edition.place + " · NASA / MODIS");
                        credit.setVisibility(VISIBLE);
                    });
                } catch (java.io.IOException e) {
                    android.util.Log.w("EarthEdition", "Background unavailable", e);
                }
            });
        }
        postDelayed(tick, 60_000 - now % 60_000);
    }

    // Night, dawn, noon, sunset and late night. Interpolate on the clock tick
    // so the light evolves without running a full-screen animation every frame.
    private static final int[] HOURS = {0, 4, 7, 13, 18, 22, 24};
    private static final int[][] LIGHT = {
            {0xff202942, 0xff131a30, 0xff0b1021, 0xff050810},
            {0xff202942, 0xff131a30, 0xff0b1021, 0xff050810},
            {0xff916955, 0xff5b4654, 0xff2c2b40, 0xff121724},
            {0xff46787f, 0xff324e65, 0xff1b2c43, 0xff0a1725},
            {0xff895346, 0xff5e384e, 0xff30273f, 0xff131423},
            {0xff202942, 0xff131a30, 0xff0b1021, 0xff050810},
            {0xff202942, 0xff131a30, 0xff0b1021, 0xff050810}
    };

    private void updateAmbient(java.time.LocalTime time) {
        // Meadow owns the mascot's period: setMeadowHour() derives it from the
        // preview hour. A wall-clock refresh here would put 07/11/18 back in the
        // real hour's period and silently drop their choreography in preview.
        if (meadow == null) companion.refreshTimePeriodFromClock();
        sky.setHour(time.getHour());
        int color = time.getHour() < 5 || time.getHour() >= 20 ? 0xffddd8d0 : 0xfff5f0e8;
        clock.setTextColor(color);
        minutes.setTextColor(color);
        colon.setTextColor(color);
        meridiem.setTextColor(color);
        if (meadow != null) {
            boolean brightSky = time.getHour() >= 5 && time.getHour() < 17;
            date.setTextColor(brightSky ? 0xe0f8f5f0 : 0xb3f5f0e8);
            date.setShadowLayer(brightSky ? dp(8) : 5f, 0, brightSky ? dp(2) : 2f,
                    brightSky ? 0x48000000 : 0xaa000000);
            meadow.refreshForTime(time, previewHour >= 0);
            return;
        }
        float hour = time.getHour() + time.getMinute() / 60f;
        int slot = 0;
        while (hour >= HOURS[slot + 1]) slot++;
        float progress = (hour - HOURS[slot]) / (HOURS[slot + 1] - HOURS[slot]);
        progress = progress * progress * (3f - 2f * progress);
        int[] palette = new int[4];
        for (int i = 0; i < palette.length; i++) {
            palette[i] = androidx.core.graphics.ColorUtils.blendARGB(
                    LIGHT[slot][i], LIGHT[slot + 1][i], progress);
        }
        if (getBackground() instanceof GradientDrawable) {
            GradientDrawable glow = (GradientDrawable) getBackground();
            glow.setColors(palette);
            glow.setGradientCenter(.32f + .13f * (float) Math.sin(hour * Math.PI / 12), .65f);
        }
    }

    void setDreamRunning(boolean running) {
        if (meadow != null) {
            if (running) meadow.startAnimationLoop(); else meadow.stopAnimationLoop();
        }
    }

    boolean handleGreeting(android.view.KeyEvent event) {
        if (!ambient) return false;
        int key = event.getKeyCode();
        if (key != android.view.KeyEvent.KEYCODE_DPAD_CENTER && key != android.view.KeyEvent.KEYCODE_ENTER
                && key != android.view.KeyEvent.KEYCODE_NUMPAD_ENTER) return false;
        if (event.getAction() == android.view.KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            if (meadow != null) {
                meadow.triggerOkInteraction();
                showHint();
                return true;
            }
            companion.triggerOkInteraction();
            showHint();
            if (sky.isMidday()) {
                warmGlow.animate().alpha(.08f).setDuration(450).withEndAction(() ->
                        warmGlow.animate().alpha(0f).setDuration(550).start()).start();
            }
        }
        return true;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        // Anchor the digits themselves so the optional character and credit don't shift the time.
        int clockCenter = clockRow.getTop() + clock.getTop() + clock.getHeight() / 2;
        int desiredTop = Math.round(getHeight() * (ambient && meadow == null ? .46f : .33f)) - clockCenter;
        int faceTop = Math.max(0, Math.min(desiredTop, getHeight() - face.getHeight()));
        face.offsetTopAndBottom(faceTop - face.getTop());
    }
}
