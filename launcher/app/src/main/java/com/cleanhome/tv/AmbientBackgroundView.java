package com.cleanhome.tv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.Random;

/**
 * The ambient colour behind the home screen.
 *
 * A radial gradient that spreads almost to the far edge of the panel, so the
 * falloff is wide and soft instead of a small bright blob.
 *
 * The gradient itself is painted once into a small mask, and the mask is
 * stretched over the screen at draw time. Rebuilding a radial gradient on
 * every frame of a colour fade is what used to cost this screen its frames;
 * a stretched mask is one plain picture, and the colour comes from a tint.
 * The mask is twice the screen, so the glow can drift anywhere it likes
 * without its edge ever showing.
 *
 * A light grain goes over the top. A gradient on its own still shows rings on
 * an eight bit panel; the grain is what takes them away.
 */
public class AmbientBackgroundView extends View {
    private static final int STOPS = 12;
    private static final int GRAIN_SIZE = 64;
    private static final int PEAK_ALPHA = 0x74;
    private static final long FADE_MS = 420L;
    /** How many screen pixels one mask pixel covers. */
    private static final float MASK_DOWNSCALE = 5f;
    /** The mask is this many times the screen, so its edge never shows. */
    private static final float MASK_OVERSCAN = 2f;
    private static final DecelerateInterpolator EASING = new DecelerateInterpolator(1.4f);

    private final Paint washPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint grainPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF washRect = new RectF();

    private int color = 0xE50914;
    private int fromColor = color;
    private int toColor = color;
    private float fromX = 0.32f;
    private float toX = 0.32f;
    private float centerX = 0.32f;
    private float fromY = 0.42f;
    private float toY = 0.42f;
    private float centerY = 0.42f;

    private Bitmap mask;
    private float maskScaleX = MASK_DOWNSCALE;
    private float maskScaleY = MASK_DOWNSCALE;
    private PorterDuffColorFilter tint;
    private int tintColor = -1;

    private Bitmap noise;
    private ValueAnimator animator;

    public AmbientBackgroundView(Context context) {
        this(context, null);
    }

    public AmbientBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        // Dithering on a full screen shader makes this panel fall off the
        // hardware path entirely. The grain already hides the banding.
        washPaint.setDither(false);
        grainPaint.setDither(false);
    }

    /**
     * Moves the glow to a new colour and a new place on the screen.
     * The centre is a fraction of the width and of the height, 0 is the left
     * or the top edge and 1 the right or the bottom one.
     */
    public void animateTo(int rgb, float newCenterX) {
        animateTo(rgb, newCenterX, centerY);
    }

    /**
     * Moves the glow to a new colour and a new place on the screen, in both
     * directions at once. The grid needs that, it is two dimensional.
     */
    public void animateTo(int rgb, float newCenterX, float newCenterY) {
        int next = rgb & 0x00FFFFFF;
        float clampedX = Math.max(0f, Math.min(1f, newCenterX));
        float clampedY = Math.max(0f, Math.min(1f, newCenterY));
        if (next == toColor && Math.abs(clampedX - toX) < 0.001f
                && Math.abs(clampedY - toY) < 0.001f) {
            return;
        }
        fromColor = color;
        fromX = centerX;
        fromY = centerY;
        toColor = next;
        toX = clampedX;
        toY = clampedY;
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(FADE_MS);
        animator.setInterpolator(EASING);
        animator.addUpdateListener(value -> {
            float t = (float) value.getAnimatedValue();
            color = mix(fromColor, toColor, t);
            centerX = fromX + (toX - fromX) * t;
            centerY = fromY + (toY - fromY) * t;
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        // A fade left running would hold the screen's bitmap after it is gone.
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        buildMask();
    }

    /**
     * Paints the falloff once. The colour is not part of it: the mask is
     * white, and the tint puts the colour on at draw time.
     */
    private void buildMask() {
        int w = getWidth();
        int h = getHeight();
        if (w < 2 || h < 2) {
            return;
        }
        int maskW = Math.max(2, Math.round(w * MASK_OVERSCAN / MASK_DOWNSCALE));
        int maskH = Math.max(2, Math.round(h * MASK_OVERSCAN / MASK_DOWNSCALE));
        int[] colors = new int[STOPS];
        float[] stops = new float[STOPS];
        for (int i = 0; i < STOPS; i++) {
            float t = i / (float) (STOPS - 1);
            float fade = (1f - t) * (1f - t);
            colors[i] = (Math.round(PEAK_ALPHA * fade) << 24) | 0x00FFFFFF;
            stops[i] = t;
        }
        colors[STOPS - 1] = 0x00FFFFFF;
        Bitmap next = Bitmap.createBitmap(maskW, maskH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(next);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new RadialGradient(
                maskW / 2f,
                maskH / 2f,
                Math.max(w, h) * 1.02f / MASK_DOWNSCALE,
                colors,
                stops,
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, maskW, maskH, paint);
        // The old bitmap is left to the collector. The render thread may
        // still be holding it for the frame that is in flight.
        mask = next;
        maskScaleX = (w * MASK_OVERSCAN) / maskW;
        maskScaleY = (h * MASK_OVERSCAN) / maskH;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mask == null) {
            buildMask();
        }
        if (mask == null) {
            return;
        }
        int w = getWidth();
        int h = getHeight();
        int rgb = 0xFF000000 | color;
        if (tint == null || tintColor != rgb) {
            tintColor = rgb;
            tint = new PorterDuffColorFilter(rgb, PorterDuff.Mode.SRC_IN);
        }
        float halfW = mask.getWidth() * maskScaleX / 2f;
        float halfH = mask.getHeight() * maskScaleY / 2f;
        float cx = w * centerX;
        float cy = h * centerY;
        washRect.set(cx - halfW, cy - halfH, cx + halfW, cy + halfH);
        washPaint.setColorFilter(tint);
        canvas.drawBitmap(mask, null, washRect, washPaint);

        if (grainPaint.getShader() == null) {
            grainPaint.setShader(new BitmapShader(noiseTile(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        }
        canvas.drawRect(0, 0, w, h, grainPaint);
    }

    /** A tile of near black noise, about one and a half percent strong. */
    private Bitmap noiseTile() {
        if (noise != null) {
            return noise;
        }
        int[] pixels = new int[GRAIN_SIZE * GRAIN_SIZE];
        Random random = new Random(20261019L);
        for (int i = 0; i < pixels.length; i++) {
            int value = random.nextInt(256);
            int alpha = 2 + (value % 4);
            pixels[i] = (alpha << 24) | (value << 16) | (value << 8) | value;
        }
        noise = Bitmap.createBitmap(pixels, GRAIN_SIZE, GRAIN_SIZE, Bitmap.Config.ARGB_8888);
        return noise;
    }

    /** The colour is red, green and blue only. The alpha comes from the mask. */
    private static int mix(int from, int to, float t) {
        int r = (int) (android.graphics.Color.red(from)
                + (android.graphics.Color.red(to) - android.graphics.Color.red(from)) * t);
        int g = (int) (android.graphics.Color.green(from)
                + (android.graphics.Color.green(to) - android.graphics.Color.green(from)) * t);
        int b = (int) (android.graphics.Color.blue(from)
                + (android.graphics.Color.blue(to) - android.graphics.Color.blue(from)) * t);
        return (r << 16) | (g << 8) | b;
    }
}
