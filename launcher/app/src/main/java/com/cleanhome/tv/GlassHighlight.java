package com.cleanhome.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.widget.FrameLayout;

/**
 * The focus ring for the dock and the grid.
 *
 * It lives on the root instead of on the card, so a card never clips its own
 * border. Two things are drawn, both of them outside the card edge:
 *
 *   1. an even band of dark right at the card, so the white ring still reads
 *      on a white card such as Netflix,
 *   2. one crisp white stroke that runs parallel to the card, so the gap is
 *      the same the whole way round including the corners.
 *
 * Both are plain strokes. Nothing here clips a canvas: a clip path costs this
 * panel more frames than the rest of the ring put together.
 *
 * The ring is drawn at the card's own scale, so it tracks a card that is
 * growing or shrinking. Call {@link #setScale(float)} every frame while a
 * focus animation is running.
 */
final class GlassHighlight extends View {
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint seam = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF cardRect = new RectF();
    private final RectF ringRect = new RectF();
    private final RectF seamRect = new RectF();

    private final float pad;
    private final float ringWidth;
    private final float gap;

    private float radius;
    private float scale = 1f;

    // The card rect the ring is aiming at, and the rect it is showing right now.
    private float targetX;
    private float targetY;
    private float targetW = 1f;
    private float targetH = 1f;
    private float shownX;
    private float shownY;
    private float shownW = 1f;
    private float shownH = 1f;
    private boolean placed;
    private boolean fading;
    private int fadeToken;
    private android.animation.ValueAnimator travel;
    private android.animation.ValueAnimator fade;

    GlassHighlight(Context context) {
        super(context);
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        float density = context.getResources().getDisplayMetrics().density;
        ringWidth = 2.5f * density;
        gap = 2f * density;
        // Room for the ring around a card that has grown to its focus size.
        pad = 24f * density;

        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(ringWidth);
        ring.setColor(0xFFFFFFFF);

        seam.setStyle(Paint.Style.STROKE);
    }

    /** The card's current focus scale. The ring is drawn at the same scale. */
    void setScale(float value) {
        float next = Math.max(0.01f, value);
        if (Math.abs(next - scale) > 0.0005f) {
            scale = next;
            invalidate();
        }
    }

    /**
     * Aims the ring at a card. The rect is the card's own rect, without any
     * focus scale, in root coordinates.
     */
    void springTo(float x, float y, float w, float h, float corner) {
        if (w < 2f || h < 2f) {
            return;
        }
        radius = corner;
        boolean same = placed
                && Math.abs(x - targetX) < 0.5f
                && Math.abs(y - targetY) < 0.5f
                && Math.abs(w - targetW) < 0.5f
                && Math.abs(h - targetH) < 0.5f;
        targetX = x;
        targetY = y;
        targetW = w;
        targetH = h;
        if (fading) {
            // A dissolve is taking the ring to this target.
            // If the target position shifts (e.g. layout settles or parent translates),
            // update coordinates immediately so it dissolves in at the exact live position.
            if (Math.abs(x - shownX) > 0.5f || Math.abs(y - shownY) > 0.5f
                    || Math.abs(w - shownW) > 0.5f || Math.abs(h - shownH) > 0.5f) {
                shownX = x;
                shownY = y;
                shownW = w;
                shownH = h;
                apply();
            }
            return;
        }
        if (same) {
            // Already aiming here. Keep the animation that is running, but the
            // ring has to come back on screen: it is hidden while the top row
            // holds the focus, and the target does not change when the focus
            // comes back down to the same card.
            if (getVisibility() != VISIBLE) {
                apply();
            }
            return;
        }
        if (!placed) {
            placed = true;
            shownX = x;
            shownY = y;
            shownW = w;
            shownH = h;
            apply();
            return;
        }
        if (travel != null) {
            travel.cancel();
        }
        travel = android.animation.ValueAnimator.ofFloat(0f, 1f);
        travel.setDuration(Motion.DURATION);
        travel.setInterpolator(Motion.EASING);
        final float sx = shownX;
        final float sy = shownY;
        final float sw = shownW;
        final float sh = shownH;
        travel.addUpdateListener(v -> {
            float t = (float) v.getAnimatedValue();
            shownX = sx + (x - sx) * t;
            shownY = sy + (y - sy) * t;
            shownW = Math.max(1f, sw + (w - sw) * t);
            shownH = Math.max(1f, sh + (h - sh) * t);
            apply();
        });
        travel.start();
    }

    /**
     * Takes the ring off screen. Used while the top row holds the focus, where
     * no card is focused.
     */
    void hide() {
        // Any dissolve still in flight must not come back on screen.
        fadeToken++;
        if (travel != null) {
            travel.cancel();
        }
        if (fade != null) {
            fade.cancel();
        }
        fading = false;
        // A dissolve that was cancelled half way leaves a half transparent
        // ring behind. The next show must always start at full strength.
        setAlpha(1f);
        if (placed) {
            // Do not come back at a place the ring was only passing through.
            shownX = targetX;
            shownY = targetY;
            shownW = targetW;
            shownH = targetH;
        }
        setVisibility(INVISIBLE);
    }

    /**
     * Moves the ring to a card that is far away.
     *
     * The ring must not linger where it was. Fading out on the old card and
     * then travelling to the new one reads as the border jumping about, so it
     * goes straight to the new place and comes back there.
     */
    void fadeTo(float x, float y, float w, float h, float corner) {
        if (w < 2f || h < 2f) {
            return;
        }
        radius = corner;
        targetX = x;
        targetY = y;
        targetW = w;
        targetH = h;
        placed = true;
        fading = true;
        if (travel != null) {
            travel.cancel();
        }
        if (fade != null) {
            fade.cancel();
        }
        final int token = ++fadeToken;
        // Blank it first, so the ring cannot be seen at the old place for a
        // frame while it moves.
        setAlpha(0f);
        shownX = x;
        shownY = y;
        shownW = w;
        shownH = h;
        apply();
        fade = android.animation.ValueAnimator.ofFloat(0f, 1f);
        fade.setDuration(Motion.FADE_MS);
        fade.addUpdateListener(v -> setAlpha((float) v.getAnimatedValue()));
        fade.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (token == fadeToken) {
                    fading = false;
                    if (Math.abs(targetX - shownX) > 0.5f || Math.abs(targetY - shownY) > 0.5f) {
                        shownX = targetX;
                        shownY = targetY;
                        shownW = targetW;
                        shownH = targetH;
                        apply();
                    }
                }
            }
        });
        fade.start();
    }

    private void apply() {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        if (lp == null) {
            return;
        }
        int width = Math.max(1, Math.round(shownW + pad * 2f));
        int height = Math.max(1, Math.round(shownH + pad * 2f));
        if (lp.width != width || lp.height != height) {
            lp.width = width;
            lp.height = height;
            setLayoutParams(lp);
        }
        setX(shownX - pad);
        setY(shownY - pad);
        setVisibility(VISIBLE);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (shownW <= 2f || shownH <= 2f) {
            return;
        }
        float cardW = shownW * scale;
        float cardH = shownH * scale;
        float left = pad + (shownW - cardW) / 2f;
        float top = pad + (shownH - cardH) / 2f;
        RectF card = cardRect;
        card.set(left, top, left + cardW, top + cardH);
        float r = radius * scale;

        // The dark band at the card edge, drawn as a stroke that sits entirely
        // outside the card. Its inner edge is the card edge itself, so nothing
        // paints over the picture and no clip is needed. The old version
        // clipped a centred stroke, and a clip path here costs this panel more
        // frames than every other part of the ring put together.
        seamRect.set(card);
        seamRect.inset(-gap / 2f, -gap / 2f);
        seam.setStrokeWidth(gap);
        seam.setColor(0xF0000000);
        canvas.drawRoundRect(seamRect, r + gap / 2f, r + gap / 2f, seam);

        // The ring itself. Its radius is the card radius plus the offset, so
        // the two curves stay parallel and the gap never changes.
        float offset = gap + ringWidth / 2f;
        ringRect.set(card);
        ringRect.inset(-offset, -offset);
        canvas.drawRoundRect(ringRect, r + offset, r + offset, ring);
    }
}
