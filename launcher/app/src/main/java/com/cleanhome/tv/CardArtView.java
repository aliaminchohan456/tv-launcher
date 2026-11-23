package com.cleanhome.tv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;

/**
 * The picture inside an app card.
 *
 * It draws like a centre crop, but the picture can be a little larger and a
 * little higher than its frame when the card has the focus. That tiny move is
 * what makes a card feel like the artwork is sitting inside it and coming
 * forward, rather than the card simply getting bigger. The frame itself, and
 * the focus ring around it, do not move.
 */
public class CardArtView extends ImageView {
    private static final float FOCUS_SCALE = 1.02f;
    private static final float FOCUS_LIFT_DP = 1f;
    private static final long DURATION = 200L;
    private static final android.view.animation.Interpolator ART_EASING =
            new android.view.animation.DecelerateInterpolator(1.8f);

    private float artScale = 1f;
    private float artLift;
    private boolean fitInside;
    private ValueAnimator animator;

    public CardArtView(Context context) {
        this(context, null);
    }

    public CardArtView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setScaleType(ScaleType.CENTER_CROP);
    }

    /**
     * The rounded outline of a card. The dock and the grid both use this, so
     * the two look exactly the same.
     */
    static void round(View view, float fraction) {
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), v.getWidth() * fraction);
            }
        });
        view.setClipToOutline(true);
    }

    /** The picture lifts a little when the card takes the focus. */
    void setArtFocus(boolean focused) {
        float targetScale = focused ? FOCUS_SCALE : 1f;
        float targetLift = focused ? dp(FOCUS_LIFT_DP) : 0f;
        if (Math.abs(artScale - targetScale) < 0.001f
                && Math.abs(artLift - targetLift) < 0.05f) {
            return;
        }
        if (animator != null) {
            animator.cancel();
        }
        final float fromScale = artScale;
        final float fromLift = artLift;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(DURATION);
        animator.setInterpolator(ART_EASING);
        animator.addUpdateListener(value -> {
            float t = (float) value.getAnimatedValue();
            artScale = fromScale + (targetScale - fromScale) * t;
            artLift = fromLift + (targetLift - fromLift) * t;
            invalidate();
        });
        animator.start();
    }

    /**
     * Shows the whole picture instead of cropping it to fill the frame. A
     * round app icon looks wrong when it is cropped to a wide tile.
     */
    public void setFitInside(boolean value) {
        fitInside = value;
        invalidate();
    }

    /**
     * The picture is drawn by hand, so the micro move can be added on top of
     * the centre crop without fighting the scale type.
     */
    @Override
    protected void onDraw(Canvas canvas) {
        Drawable drawable = getDrawable();
        if (drawable == null) {
            super.onDraw(canvas);
            return;
        }
        int width = getWidth();
        int height = getHeight();
        int drawableWidth = drawable.getIntrinsicWidth();
        int drawableHeight = drawable.getIntrinsicHeight();
        if (width <= 0 || height <= 0 || drawableWidth <= 0 || drawableHeight <= 0) {
            super.onDraw(canvas);
            return;
        }
        float scale = fitInside
                ? Math.min(width / (float) drawableWidth, height / (float) drawableHeight)
                : Math.max(width / (float) drawableWidth, height / (float) drawableHeight);
        scale *= artScale;
        int scaledWidth = Math.round(drawableWidth * scale);
        int scaledHeight = Math.round(drawableHeight * scale);
        float dx = (width - scaledWidth) / 2f;
        float dy = (height - scaledHeight) / 2f - artLift;
        int save = canvas.save();
        canvas.translate(dx, dy);
        drawable.setBounds(0, 0, scaledWidth, scaledHeight);
        drawable.draw(canvas);
        canvas.restoreToCount(save);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
