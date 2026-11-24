package com.cleanhome.tv;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.Choreographer;
import android.widget.FrameLayout;
import androidx.core.graphics.ColorUtils;
import java.time.LocalTime;

/** 24-Hour Living World landscape on a shared 960x540 artboard. Child draws the refined Fox Cub. */
final class FoxMeadowView extends FrameLayout implements Choreographer.FrameCallback {
    private final FoxMascotView fox;
    private final FoxHourlySchedule.State state = new FoxHourlySchedule.State();
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Path shape = new Path();
    private final Path[] ridges = {new Path(), new Path(), new Path()};
    private final Path crescent = new Path();
    private final Shader sunlight = halo(0xfff1af), moonlight = halo(0xb4cceb), firelight = halo(0xffa256);
    private Shader soil;
    // The opaque sky only varies vertically. A filtered strip keeps the gradient
    // off the GPU's per-pixel shader path while preserving the original stops.
    private final Bitmap skyStrip = Bitmap.createBitmap(64, 870, Bitmap.Config.ARGB_8888);
    private final Canvas skyCanvas = new Canvas(skyStrip);
    private final RectF skyBounds = new RectF(0, 0, 960, 435);
    private boolean paletteReady;
    private final Bitmap foreground = Bitmap.createBitmap(960, 145, Bitmap.Config.ARGB_8888);
    // Rasterize distant ridges at the output pixel scale. The middle strip still sways
    // above the sun and below the pine trees every frame.
    private Bitmap farStrip, middleStrip;
    private final RectF farBounds = new RectF(-10, 314, 970, 444);
    private final RectF middleBounds = new RectF(-10, 348, 970, 490);
    private float stripScale;
    private final Canvas stripCanvas = new Canvas();
    private final Canvas foregroundCanvas = new Canvas(foreground);
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Path[] clouds = {new Path(), new Path(), new Path()};
    private final float[] starX = new float[30], starY = new float[30];
    private final float[] starRadius = new float[30], starPhase = new float[30];
    private final GrassBlade[] blades;

    private int grassColor, windColor;
    private boolean daytimeGrass;
    private final Path windStreak = new Path();
    private final Path birdWing = new Path();
    private final Shader insectGlow = halo(0xc4de83);
    private static final float[] FIREFLY_X = {318, 705, 451, 174, 824};
    private static final float[] FIREFLY_Y = {438, 436, 415, 447, 413};
    // Dew catches the first light on the taller grass tips. Static so the loop
    // stays allocation-free; the glints drift a few units above their blade.
    private static final float[] DEW_X = {12, 101, 242, 376, 504, 655, 807, 927};
    private static final float[] DEW_Y = {476, 509, 478, 499, 449, 453, 507, 478};
    private boolean enabled = true;
    // Trees in the same wind phase share one cached silhouette and draw call.
    private final Path[] pineGrove = new Path[8];
    private final Choreographer choreographer = Choreographer.getInstance();
    private static final long MIN_FRAME_NS = 14_000_000L;
    private boolean looping, visible, sawLastEveningMinute;
    private long lastFrameNanos = -1L;
    private final long epoch = SystemClock.uptimeMillis();
    private long frameMs = epoch, hourStartMs = epoch, greeting = -10000;
    private float unit, left, top;

    private static final float[] GRASS_X = {12, 101, 242, 376, 504, 655, 807, 927};
    private static final float[] GRASS_Y = {485, 521, 488, 509, 459, 463, 519, 488};
    private static final float[] GRASS_SCALE = {.9f, 1.3f, .76f, 1.1f, .7f, .85f, 1.2f, 1f};

    FoxMeadowView(Context context) {
        super(context);
        setWillNotDraw(false);
        fox = new FoxMascotView(context);
        fox.setMeadow();
        addView(fox);
        skyStrip.setHasAlpha(false);
        skyCanvas.scale(1, 2);
        foreground.setDensity(Bitmap.DENSITY_NONE);
        foregroundCanvas.translate(0, -400);
        buildClouds();
        // A tapered brush mark follows the crest of the moving grass.
        windStreak.moveTo(-34, 1);
        windStreak.cubicTo(-17, 2, -8, -3, 10, -2);
        windStreak.quadTo(24, -2, 34, -5);
        windStreak.cubicTo(15, 0, 2, -2, -10, 1);
        windStreak.quadTo(-25, 3, -34, 1);
        windStreak.close();
        birdWing.moveTo(0, 1);
        birdWing.quadTo(-4, -4, -10, -3);
        birdWing.quadTo(-5, -2, -1, 2);
        birdWing.close();
        for (int i = 0; i < starX.length; i++) {
            starX[i] = (37 + i * 173) % 950;
            starY[i] = 18 + (i * 79) % 275;
            if (starX[i] > 290 && starX[i] < 680 && starY[i] > 96) starY[i] *= .30f;
            starRadius[i] = i % 4 == 0 ? 1.35f : .75f;
            starPhase[i] = i * 1.7f;
        }
        int bladeCount = 0;
        for (int g = 0; g < GRASS_X.length; g++) bladeCount += 3 + g % 4;
        blades = new GrassBlade[bladeCount];
        int index = 0;
        for (int g = 0; g < GRASS_X.length; g++) {
            for (int b = 0; b < 3 + g % 4; b++) blades[index++] = new GrassBlade(g, b);
        }
        ridge(ridges[0], 373, 320, 413, 345, 327, 390, 444);
        ridge(ridges[1], 402, 353, 435, 383, 365, 410, 490);
        ridge(ridges[2], 468, 484, 442, 443, 447, 474, 550);
        for (int i = 0; i < pineGrove.length; i++) pineGrove[i] = new Path();
        for (int i = 0; i < 38; i++) {
            float x = i < 22 ? -15 + i * 12 : 782 + (i - 22) * 12;
            float base = 428 + 9 * sin(i * .43f);
            float height = 22 + 12 * sin(i * 2.3f) + 13 * (1 + sin(i * .57f));
            Path grove = pineGrove[i % pineGrove.length];
            grove.addPath(makePine(height), x, base);
            grove.addRect(x - 1.2f, base - height * .5f, x + 1.2f, base, Path.Direction.CW);
        }
        crescent.moveTo(9, -17);
        crescent.cubicTo(-18, -21, -22, 14, -2, 18);
        crescent.quadTo(10, 21, 18, 12);
        crescent.cubicTo(-2, 16, -12, -8, 9, -17);
        crescent.close();
        refreshForTime(LocalTime.now(), false);
    }

    private static void ridge(Path p, int a, int b, int c, int d, int e, int f, int bottom) {
        p.moveTo(-10, a); p.cubicTo(100, b, 180, b, 300, c);
        p.cubicTo(408, c + 10, 493, d, 618, d);
        p.cubicTo(762, d - 3, 830, e, 970, f); p.lineTo(970, bottom); p.lineTo(-10, bottom); p.close();
    }

    private static Shader halo(int rgb) {
        return new RadialGradient(0, 0, 1, new int[]{0x99000000 | rgb, 0x37000000 | rgb, rgb},
                new float[]{0, .42f, 1}, Shader.TileMode.CLAMP);
    }

    void refreshForTime(LocalTime time, boolean preview) {
        if (preview) fox.cancelBedtimeTransition();
        if (paletteReady && time.getHour() == state.hour && time.getMinute() == state.minute) return;
        boolean bedtime = !preview && looping && sawLastEveningMinute && state.hour == 21
                && state.minute == 59 && time.getHour() == 22 && time.getMinute() == 0;
        sawLastEveningMinute = false;
        if (!paletteReady || time.getHour() != state.hour) {
            if (!bedtime) greeting = -10000L;
            hourStartMs = SystemClock.uptimeMillis();
        }
        FoxHourlySchedule.evaluate(time.getHour(), time.getMinute(), state);
        color(-1);
        ink.setShader(new LinearGradient(0, 0, 0, 424,
                new int[]{state.colors[0], state.colors[1], state.colors[2]},
                new float[]{0, .65f, 1}, Shader.TileMode.CLAMP));
        skyCanvas.drawRect(0, 0, skyStrip.getWidth(), 435, ink);
        ink.setShader(null);
        paletteReady = true;
        soil = new LinearGradient(0, 444, 0, 540,
                state.colors[6], ColorUtils.blendARGB(state.colors[6], 0xff101923, .48f),
                Shader.TileMode.CLAMP);
        grassColor = ColorUtils.blendARGB(state.colors[6], state.colors[2], .18f);
        daytimeGrass = state.hour >= 5 && state.hour < 17;
        if (daytimeGrass) grassColor = ColorUtils.blendARGB(grassColor, state.colors[2], .08f);
        windColor = ColorUtils.blendARGB(state.colors[6],
                state.darkRoomSafe ? 0xffa2b5ca : 0xffefdcaf, .60f);
        refreshForeground();
        if (farStrip != null) refreshRidges();
        fox.setMeadowHour(state.hour, state.foxPose, hourStartMs, bedtime);
        invalidate();
    }

    void triggerOkInteraction() {
        long now = SystemClock.uptimeMillis();
        if (now - greeting < 2100L) return;
        greeting = now;
        fox.triggerOkInteraction();
        invalidate();
    }

    @Override public void doFrame(long frameTimeNanos) {
        if (!looping) return;
        if (lastFrameNanos < 0 || frameTimeNanos - lastFrameNanos >= MIN_FRAME_NS) {
            lastFrameNanos = frameTimeNanos;
            frameMs = frameTimeNanos / 1_000_000L;
            sawLastEveningMinute = state.hour == 21 && state.minute == 59;
            fox.syncMeadowFrame(frameTimeNanos);
            invalidate();
        }
        choreographer.postFrameCallback(this);
    }

    private void updateFrameRegistration() {
        boolean run = enabled && visible && isAttachedToWindow();
        if (run == looping) return;
        looping = run;
        lastFrameNanos = -1L;
        if (run) {
            choreographer.postFrameCallback(this);
        } else {
            choreographer.removeFrameCallback(this);
            sawLastEveningMinute = false;
            fox.cancelBedtimeTransition();
        }
    }

    void startAnimationLoop() { enabled = true; updateFrameRegistration(); }
    void stopAnimationLoop() { enabled = false; updateFrameRegistration(); }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        visible = isShown() && getWindowVisibility() == VISIBLE;
        updateFrameRegistration();
    }

    @Override public void onVisibilityAggregated(boolean visible) {
        super.onVisibilityAggregated(visible);
        this.visible = visible;
        updateFrameRegistration();
    }

    @Override protected void onDetachedFromWindow() {
        visible = false;
        updateFrameRegistration();
        super.onDetachedFromWindow();
    }

    @Override protected void onMeasure(int w, int h) {
        super.onMeasure(w, h);
        unit = Math.max(getMeasuredWidth() / 960f, getMeasuredHeight() / 540f);
        left = (getMeasuredWidth() - 960 * unit) / 2;
        top = (getMeasuredHeight() - 540 * unit) / 2;
        if (unit > 0 && unit != stripScale) {
            stripScale = unit;
            farStrip = newStrip(farBounds);
            middleStrip = newStrip(middleBounds);
            refreshRidges();
        }
        fox.measure(MeasureSpec.makeMeasureSpec(Math.round(154 * unit), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.round(112 * unit), MeasureSpec.EXACTLY));
    }

    private Bitmap newStrip(RectF bounds) {
        return Bitmap.createBitmap((int) Math.ceil(bounds.width() * stripScale),
                (int) Math.ceil(bounds.height() * stripScale), Bitmap.Config.ARGB_8888);
    }

    private void refreshRidges() {
        rasterizeRidge(farStrip, farBounds, ridges[0], state.colors[3]);
        rasterizeRidge(middleStrip, middleBounds, ridges[1], state.colors[4]);
    }

    private void rasterizeRidge(Bitmap target, RectF bounds, Path path, int tint) {
        target.eraseColor(Color.TRANSPARENT);
        stripCanvas.setBitmap(target);
        stripCanvas.save();
        stripCanvas.scale(target.getWidth() / bounds.width(), target.getHeight() / bounds.height());
        stripCanvas.translate(-bounds.left, -bounds.top);
        color(tint);
        stripCanvas.drawPath(path, ink);
        stripCanvas.restore();
        stripCanvas.setBitmap(null);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int x = Math.round(left + 541 * unit), y = Math.round(top + 339 * unit);
        fox.layout(x, y, x + fox.getMeasuredWidth(), y + fox.getMeasuredHeight());
    }

    private static float sin(float a) { return (float) Math.sin(a); }
    private void color(int argb) { ink.setShader(null); ink.setColor(argb); }
    private void glow(Canvas c, Shader shader, float x, float y, float rx, float ry, int alpha) {
        if (alpha <= 1) return;
        c.save(); c.translate(x, y); c.scale(rx, ry);
        color(-1); ink.setShader(shader); ink.setAlpha(Math.min(255, alpha));
        c.drawCircle(0, 0, 1, ink); c.restore(); ink.setShader(null);
    }

    @Override protected void onDraw(Canvas c) {
        float t = (frameMs - epoch) / 1000f;
        float ok = (frameMs - greeting) / 2500f;
        c.save(); c.translate(left, top); c.scale(unit, unit);

        // 1. Sky
        c.drawBitmap(skyStrip, null, skyBounds, bitmapPaint);

        // 2. Celestial Heavens (Stars, Sun arc, Moon arc, Clouds, Birds, Meteors)
        heavens(c, t, ok);

        // 3. Far & Mid Parallax Hills + 38 Swaying Pines
        c.drawBitmap(farStrip, null, farBounds, bitmapPaint);
        c.save(); c.translate(sin(t * .045f) * 1.5f, 0);
        c.drawBitmap(middleStrip, null, middleBounds, bitmapPaint); c.restore();
        float wind = state.cloudSpeed;
        color(state.colors[5]);
        for (int i = 0; i < pineGrove.length; i++) {
            c.save();
            c.translate(0, 428);
            c.skew(.026f * sin(t * wind * .66f + i), 0);
            c.translate(0, -428);
            c.drawPath(pineGrove[i], ink);
            c.restore();
        }
        c.drawBitmap(foreground, 0f, 400f, bitmapPaint);

        // 5. Hourly Meadow Grass, Lantern / Campfire / Embers & Interactive Particles
        meadow(c, t, ok);

        c.restore();
    }

    private void refreshForeground() {
        foreground.eraseColor(Color.TRANSPARENT);
        color(state.colors[5]); foregroundCanvas.drawOval(-95, 410, 320, 508, ink); foregroundCanvas.drawOval(747, 409, 1060, 500, ink);

        // 4. Foreground Cozy Meadow Hill + Rim Light
        color(-1); ink.setShader(soil); foregroundCanvas.drawPath(ridges[2], ink);
        ink.setShader(null);
        ink.setColor(state.darkRoomSafe ? 0x358faed3 : 0x50ffdfab);
        ink.setStyle(Paint.Style.STROKE); ink.setStrokeWidth(1.3f);
        foregroundCanvas.drawPath(ridges[2], ink); ink.setStyle(Paint.Style.FILL);
    }

    private void buildClouds() {
        for (int i = 0; i < clouds.length; i++) {
            Path shape = clouds[i];
            if (i == 0) {
                shape.moveTo(-88, 5); shape.cubicTo(-56, 4, -45, -11, -22, -9);
                shape.cubicTo(-6, -24, 8, -20, 23, -10); shape.cubicTo(45, -13, 54, 2, 87, 3);
                shape.cubicTo(56, 7, -38, 10, -88, 5);
            } else if (i == 1) {
                shape.moveTo(-55, 5); shape.quadTo(-52, -9, -37, -7);
                shape.cubicTo(-37, -35, -7, -43, 5, -25); shape.quadTo(23, -31, 30, -12);
                shape.quadTo(51, -15, 61, 4); shape.cubicTo(39, 10, -24, 6, -55, 5);
            } else {
                shape.moveTo(-70, 5); shape.quadTo(-62, -4, -42, -3);
                shape.quadTo(-28, -20, -9, -14); shape.cubicTo(9, -12, 14, -37, 37, -23);
                shape.quadTo(46, -19, 48, -9); shape.quadTo(65, -10, 73, 4);
                shape.cubicTo(44, 11, -42, 7, -70, 5);
            }
            shape.close();
        }
    }

    private void heavens(Canvas c, float t, float ok) {
        int hr = state.hour;

        // Twinkling stars (emerge smoothly at 19:00, fade out by 05:30)
        if (state.starAlpha > 0.01f) {
            int starCount = (hr == 1 || hr == 2 || hr == 23) ? 30 : 24;
            for (int i = 0; i < starCount; i++) {
                float pulse = 82 + 52 * sin(t * .58f + starPhase[i]);
                // Only the large stars get a halo. The small ones stay
                // pin-sharp, so the night sky keeps its depth without noise.
                if ((i & 3) == 0 && state.starAlpha > .3f) {
                    glow(c, moonlight, starX[i], starY[i], 6.5f, 6.5f,
                            Math.round(state.starAlpha * (25 + 11 * sin(t * .58f + starPhase[i]))));
                }
                color(0xffccd7ec);
                ink.setAlpha(Math.round(state.starAlpha * pulse));
                c.drawCircle(starX[i], starY[i], starRadius[i], ink);
            }
        }

        // Crescent Moon moving across the night sky
        if (state.showMoon && state.moonAlpha > 0.01f) {
            glow(c, moonlight, state.moonX, state.moonY, 56, 56, Math.round(80 * state.moonAlpha));
            c.save(); c.translate(state.moonX, state.moonY);
            color(0xffdddac7); ink.setAlpha(Math.round(255 * state.moonAlpha));
            c.drawPath(crescent, ink); c.restore();
        }

        // Night shooting star (periodic + on OK during dark-room hours)
        if (state.darkRoomSafe) {
            float periodSec = (hr == 3 || hr == 23) ? 26f : 34f;
            boolean okMeteor = ok >= 0f && ok < 1f && (hr == 0 || hr == 3 || hr == 22 || hr == 23);
            float flight = okMeteor ? ok : (t % periodSec) / 2f;
            if (flight >= 0f && flight < 1f) {
                float mx = (hr % 2 == 0 ? 670 : 710) + 160 * flight;
                float my = 42 + 44 * flight + 11 * flight * flight;
                color(0xffced9ee); ink.setAlpha((int) (130 * sin(flight * 3.14159f)));
                ink.setStrokeWidth(1.2f); c.drawLine(mx - 26, my - 9, mx, my, ink);
                c.drawCircle(mx, my, 1.5f, ink);
            }
        }

        // Sun moving smoothly across the daytime sky
        if (state.showSun && state.sunAlpha > 0.01f) {
            boolean lowSun = state.sunY > 260f;
            glow(c, sunlight, state.sunX, state.sunY,
                    (85 + 2 * sin(t)), lowSun ? 64 : 84, Math.round(195 * state.sunAlpha));
            if (lowSun) {
                glow(c, firelight, state.sunX, 354, 260, 18, Math.round(145 * state.sunAlpha));
            }
            // Subtle midday sun-shimmer on OK at 12:00 / 14:00 (never at night)
            if (ok >= 0f && ok < 1f && (hr == 12 || hr == 14)) {
                glow(c, sunlight, state.sunX, state.sunY, 180, 140, (int) (55 * sin(ok * 3.14159f)));
            }
            int sunTint = (hr >= 17) ? 0xffffb46b : (hr <= 7 ? 0xffffd894 : 0xffffe8b0);
            color(sunTint); ink.setAlpha(Math.round(255 * state.sunAlpha));
            c.drawCircle(state.sunX, state.sunY, state.sunRadius, ink);
        }

        // 3 Asymmetrical Clouds (kept safely in upper sky above digits)
        int cloudBaseColor = (hr >= 17 && hr <= 19) ? 0x00efb097 : (state.darkRoomSafe ? 0x007b92ba : 0x00e4e3dc);
        int cloudAlphaInt = Math.round(154 * state.cloudAlpha);
        for (int i = 0; i < state.cloudCount; i++) {
            float cx = (i * 253 + t * state.cloudSpeed * (1 + i * .14f) + 127) % 1100 - 70;
            float cy = 38 + (i * 43) % 58;
            c.save(); c.translate(cx, cy); c.scale(.6f + i * .13f, .65f);
            color(cloudBaseColor | (cloudAlphaInt << 24));
            c.drawPath(clouds[i % clouds.length], ink); c.restore();
        }

        // Morning & Late-Morning Birds (ambient glide at 06..09, right-sky launch on OK at 06, 07, 09)
        boolean birdHour = hr >= 6 && hr <= 9;
        boolean okBirds = ok >= 0f && ok < 1f && (hr == 6 || hr == 7 || hr == 9);
        float fly = birdHour ? (t % 32f) / 6f : 2f;
        if (hr == 9) fly = ((Math.max(0L, frameMs - hourStartMs) % 18000L) - 3300L) / 6000f;
        if (okBirds) fly = ok;
        if (fly >= 0f && fly < 1f) {
            for (int i = 0; i < 3; i++) {
                float bx = okBirds ? 538 + 240 * fly + i * 14 : 90 + 210 * fly + i * 17;
                float by = okBirds ? 238 - 130 * fly + (i % 2) * 10 : 140 - 65 * fly + (i % 2) * 10;
                float opacity = sin(fly * 3.14159f);
                if (hr == 9 && !okBirds) {
                    // The lead bird passes overhead on the second turn. Its followers enter later.
                    float flightSeconds = fly * 6f - i * .17f;
                    if (flightSeconds < 0f) continue;
                    bx = 192f + flightSeconds * (142f + 14f * flightSeconds);
                    if (bx > 971f) continue;
                    by = 88f - 32f * Math.min(1f, flightSeconds / 4f) + (i % 2) * 8f;
                    opacity = Math.min(1f, flightSeconds / .20f);
                }
                float wing = okBirds ? 6.75f : 5.2f;
                boolean trackingFlock = hr == 9 && !okBirds;
                if (trackingFlock) wing *= 1.15f;
                color(0xff484653); ink.setAlpha((int) ((trackingFlock ? 240 : 200) * opacity));
                ink.setStyle(Paint.Style.STROKE); ink.setStrokeWidth(1.5f);
                shape.rewind();
                shape.moveTo(bx - wing, by + sin(t * 6 + i) * 3);
                shape.quadTo(bx - 2, by - 1, bx, by + 2);
                shape.quadTo(bx + 2, by - 1, bx + wing, by + sin(t * 6 + i) * 3);
                c.drawPath(shape, ink); ink.setStyle(Paint.Style.FILL);
            }
        }
        if (hr >= 10 && hr <= 16) drawGlidingPair(c, t);
    }

    private void drawGlidingPair(Canvas c, float t) {
        // Stay in the upper sky, with a long glide between brief wingbeats.
        float passage = (t + 2f) % 32f;
        if (passage >= 17f) return;
        color(0xb4414653);
        for (int i = 0; i < 2; i++) {
            float x = -34f + 62f * passage - 29f * i;
            float y = 70f + 12f * sin(passage * .24f) + 9f * i;
            float beat = (passage + i * .31f) % 4.6f;
            float flex = beat < 1.35f ? sin(beat * 9.31f) * sin(beat * 2.327f) : 0f;
            float size = i == 0 ? .95f : .75f;
            c.save();
            c.translate(x, y);
            c.rotate(-6f + 4f * sin(passage * .42f + i));
            c.scale(size, size * (.65f + 1.05f * flex));
            c.drawPath(birdWing, ink);
            c.scale(-1f, 1f);
            c.drawPath(birdWing, ink);
            c.restore();
        }
    }

    private static Path makePine(float h) {
        Path pine = new Path();
        pine.moveTo(0, -h);
        for (int i = 1; i <= 3; i++) {
            float v = i / 3f;
            pine.lineTo(h * .22f * v, -h + h * .85f * v);
            pine.lineTo(h * .09f * v, -h + h * .8f * v);
        }
        for (int i = 3; i >= 1; i--) {
            float v = i / 3f;
            pine.lineTo(-h * .22f * v, -h + h * .85f * v);
            pine.lineTo(-h * .08f * v, -h + h * .57f * v);
        }
        pine.close();
        return pine;
    }

    private static final class GrassBlade {
        final float scale, phase, x, y, height;
        final boolean fork, flower, pale;
        GrassBlade(int group, int blade) {
            scale = GRASS_SCALE[group];
            phase = group * 4.7f + blade * 2.4f;
            x = GRASS_X[group] + scale * (blade * 4f + 3 * sin(phase));
            y = GRASS_Y[group] + scale * 3 * sin(phase * 1.3f);
            height = scale * (4 + 1.8f * sin(phase));
            fork = (blade + group) % 2 == 0;
            flower = blade == 2;
            pale = group % 2 == 0;
        }
    }

    private void meadow(Canvas c, float t, float ok) {
        int hr = state.hour;
        boolean showWildflowers = state.propMode == FoxHourlySchedule.PropMode.WILDFLOWERS;
        float wind = state.cloudSpeed;

        float travel = (t * (state.darkRoomSafe ? .70f : 1f) + 1.8f) % 13f;
        float crestX = -130f + travel * 104f;
        drawWind(c, crestX);
        for (GrassBlade blade : blades) {
            float tipX = blade.x - 2 + blade.scale * (1 + 2 * sin(t * .8f * wind + blade.phase));
            float distance = Math.abs(blade.x - crestX);
            float height = blade.height;
            if (daytimeGrass) {
                // The leading streak travels at 104 units/s. Peak bend lands at its center.
                float bend = Math.max(0f, 1f - distance / (104f * .175f));
                tipX += blade.scale * 9.35f * bend * bend * (3f - 2f * bend);
                height = Math.max(blade.fork ? 7f : 0f, height * 1.5f);
            } else {
                float gust = Math.max(0f, 1f - distance / 84f);
                tipX += blade.scale * gust * gust * (state.darkRoomSafe ? 3.4f : 5.5f);
            }
            color(grassColor);
            ink.setStrokeWidth(blade.scale + (daytimeGrass ? .4f : 0f));
            c.drawLine(blade.x, blade.y, tipX, blade.y - height, ink);
            if (blade.fork) c.drawLine(blade.x, blade.y, tipX + 5, blade.y - height * .72f, ink);
            if (showWildflowers && blade.flower) {
                color(hr >= 15 ? 0xfff5cb8c : (blade.pale ? 0xffe7d5a8 : 0xfff7e6e0));
                c.drawCircle(tipX, blade.y - height, 1.7f * blade.scale, ink);
            }
        }

        if (state.propMode == FoxHourlySchedule.PropMode.MORNING_DEW) drawMorningDew(c, t);

        // Ground Prop by Hour
        if (state.propMode == FoxHourlySchedule.PropMode.CAMPFIRE_BRIGHT
                || state.propMode == FoxHourlySchedule.PropMode.CAMPFIRE_EMBERS) {
            campfire(c, t, ok, state.propMode == FoxHourlySchedule.PropMode.CAMPFIRE_EMBERS);
            drawNightFireflies(c, t);
            return;
        }

        if (state.propMode == FoxHourlySchedule.PropMode.LANTERN_WARMING
                || state.propMode == FoxHourlySchedule.PropMode.LANTERN_GLOW) {
            float basePower = state.propMode == FoxHourlySchedule.PropMode.LANTERN_GLOW ? 1.0f : 0.55f;
            float boost = (ok >= 0f && ok < 1f) ? sin(ok * 3.14159f) : 0f;
            glow(c, firelight, 533, 433, 35 * basePower + 14 * boost, 31 * basePower + 12 * boost,
                    Math.round((155 * basePower) + 35 * boost));
            color(0xff493b36); c.drawRoundRect(525, 422, 541, 450, 3, 3, ink);
            color(basePower > 0.7f ? 0xffffc57c : 0xffd99c62); c.drawRoundRect(528, 428, 538, 442, 1, 1, ink);
            color(0xff483638); c.drawRect(532, 428, 534, 442, ink); c.drawRect(523, 423, 543, 426, ink);
        }

        // Ambient Meadow Particles + OK Burst (dew motes / drifting seeds / fireflies)
        boolean isFirefly = state.fireflyAlpha > 0.1f;
        boolean isSeed = showWildflowers;
        int count = isFirefly ? (hr == 17 ? 2 : 6) : 5;
        int extraCount = (ok >= 0f && ok < 1f) ? 5 : 0;
        for (int i = 0; i < count + extraCount; i++) {
            boolean extra = i >= count;
            float p = extra ? ok : (t * (.055f * wind) + i * .217f) % 1;
            float x = extra ? 507 + (i - count) * 22 + p * (56 * wind) : 55 + (i * 193) % 820 + 44 * p * wind;
            float y = extra ? 444 - 48 * p + (i % 2) * 3 : 447 - 40 * p + 8 * sin(t * .65f + i);
            float a = sin(p * 3.14159f) * (isFirefly ? state.fireflyAlpha : 1f);
            if (isFirefly) glow(c, firelight, x, y, 6.5f, 6.5f, (int) (190 * a));
            color(isSeed ? 0xfff1f0df : 0xffffd487); ink.setAlpha((int) (210 * a));
            c.drawCircle(x, y, isSeed ? 1.8f : 1.4f, ink);
            if (isSeed) { ink.setStrokeWidth(.7f); c.drawLine(x, y, x - 3, y + 4, ink); }
        }
    }

    private void drawMorningDew(Canvas c, float t) {
        // A low band of haze behind the pines, then individual beads lifting
        // off the grass. Both use the cached daylight halo: no shader allocations.
        glow(c, sunlight, 480, 388, 320, 22, 24);
        glow(c, sunlight, 220, 402, 170, 15, 15);
        for (int i = 0; i < DEW_X.length; i++) {
            float p = (t * .11f + i * .19f) % 1f;
            float alpha = sin(p * 3.14159f) * (55 + 35 * sin(t * .9f + i));
            if (alpha <= 1f) continue;
            float x = DEW_X[i] + 1.5f * sin(t + i);
            float y = DEW_Y[i] - 4f * p;
            glow(c, sunlight, x, y, 6.5f, 5f, Math.round(alpha));
            color(0xfff8e8c8);
            ink.setAlpha(Math.round(115 + 85 * sin(p * 3.14159f)));
            c.drawCircle(x, y, 1.1f, ink);
        }
    }

    private void drawWind(Canvas c, float crestX) {
        if (crestX < -40 || crestX > 1110) return;
        float height = 488f - 7f * sin(crestX * .0055f);
        color(windColor);
        for (int i = 0; i < 3; i++) {
            float x = crestX - i * 41f;
            float fade = Math.min(1f, Math.min((x + 36f) / 100f, (996f - x) / 100f));
            if (fade <= 0f) continue;
            ink.setAlpha(Math.round(fade * (state.darkRoomSafe ? 76f : 105f) * (1f - .2f * i)));
            c.save();
            c.translate(x, height + i * 5f);
            c.scale(1f - .2f * i, 1f);
            c.drawPath(windStreak, ink);
            c.restore();
        }
    }

    private void drawNightFireflies(Canvas c, float t) {
        int count = state.hour <= 4 ? 3 : FIREFLY_X.length;
        for (int i = 0; i < count; i++) {
            float phase = t * (.76f + .06f * i) + 2.17f * i;
            float pulse = Math.max(0f, sin(phase));
            if (pulse < .05f) continue;
            float x = FIREFLY_X[i] + 13f * sin(phase * .59f) + 4f * sin(phase * 1.22f);
            float y = FIREFLY_Y[i] + 6f * sin(phase * .87f + i) + 2f * sin(phase * 1.88f);
            float radius = 5f + 4f * pulse;
            glow(c, insectGlow, x, y, radius, radius, Math.round(145f * pulse));
            color(0xffd8eaa2);
            ink.setAlpha(Math.round((state.hour <= 4 ? 158 : 202) * pulse));
            c.drawCircle(x, y, 1.35f + .45f * pulse, ink);
        }
    }

    private void campfire(Canvas c, float t, float ok, boolean lowEmbersMode) {
        c.save(); c.translate(527, 447);
        float flick = sin(t * 3) * 1.4f + sin(t * 5.3f) * .7f;
        float okBoost = (ok >= 0f && ok < 1f) ? sin(ok * 3.14159f) : 0f;
        float power = lowEmbersMode ? 0.48f : 1.0f;

        // Ground pool + local warm fire glow (soft & dim during 00..04)
        glow(c, firelight, 5, -4, Math.min(92f, (94 + flick) * (lowEmbersMode ? .72f : 1f)), 21, Math.round(170 * power));
        glow(c, firelight, 0, -10, 36 * (lowEmbersMode ? 0.7f : 1f), 34 * (lowEmbersMode ? 0.7f : 1f),
                Math.round((120 + 25 * okBoost) * power));

        // Logs & river stones
        color(0xff513830); c.save(); c.rotate(13); c.drawRoundRect(-14, -3, 13, 2, 2, 2, ink);
        c.rotate(-25); c.drawRoundRect(-13, -3, 14, 2, 2, 2, ink); c.restore();
        for (int i = 0; i < 3; i++) { color(0xff59606a); c.drawOval(-17 + i * 13, 1, -10 + i * 13, 5 + i % 2, ink); }

        if (lowEmbersMode) {
            // 00:00..04:59 — Gentle pulsing glowing coals + tiny cozy ember lick
            color(0xffb8522e); c.drawOval(-8, -4, 8, 2, ink);
            color(0xffe88542); c.drawOval(-5, -3, 5, 1, ink);
            flame(c, 5.2f, 9 + flick * .6f + okBoost * 2.5f, flick * .5f, 0xffd97c45);
        } else {
            // 20:00..23:59 — Full cozy 3-layer crackling campfire
            flame(c, 10, 22 + flick + okBoost * 2.5f, flick, 0xffcc7545);
            flame(c, 7, 16 - flick + okBoost * 1.8f, -flick, 0xfff3a75c);
            flame(c, 3.6f, 9 + flick * .4f, 1, 0xffffda89);
        }

        // Rising embers (more sparse in late night, subtle puff on OK)
        int baseEmbers = lowEmbersMode ? 3 : 5;
        int extraEmbers = (ok >= 0f && ok < 1f) ? 3 : 0;
        for (int i = 0; i < baseEmbers + extraEmbers; i++) {
            boolean extra = i >= baseEmbers;
            float p = extra ? ok : (t * (lowEmbersMode ? .19f : .27f) + i * .21f) % 1;
            color(0xffffb46d); ink.setAlpha((int) ((lowEmbersMode ? 130 : 173) * sin(p * 3.14159f)));
            c.drawCircle((i - 2) * 2.2f + p * 5 + sin(t + i) * 2, -11 - 28 * p, 1.2f - p * .6f, ink);
        }
        c.restore();
    }

    private void flame(Canvas c, float r, float h, float drift, int tint) {
        color(tint); shape.rewind(); shape.moveTo(-r, 0);
        shape.cubicTo(-r * 1.2f, -h * .42f, drift + 3, -h * .57f, drift, -h);
        shape.cubicTo(r * .3f, -h * .72f, r * 1.6f, -h * .22f, r, 0);
        shape.quadTo(0, 4, -r, 0); c.drawPath(shape, ink);
    }
}
