package com.cleanhome.tv;

import android.view.View;

/**
 * The look of an app card when it has the focus and when it does not.
 *
 * Every card in the dock and in the grid uses this: it grows, brightens and
 * lifts off the screen while it is focused, and settles back down and dims
 * when the focus moves away.
 *
 * The launcher keeps its own focus model, so the cards are driven from the
 * dock index and the grid selection. The focus listener is installed as well,
 * so a card behaves the same way if the system ever hands it the focus.
 */
final class CardFocus {
    /** How much a focused card grows. */
    static final float SCALE = 1.08f;
    /** How dark a card looks while it is not the focused one. */
    static final float DIM = 0.75f;
    /** How long the change takes. */
    static final long DURATION = Motion.DURATION;
    /** How far a focused card lifts off the screen, in dp. */
    private static final float LIFT_DP = 8f;

    private static final android.view.animation.Interpolator EASING = Motion.EASING;

    private CardFocus() {}

    /** Installs the focus listener on a card. */
    static void watch(View card) {
        card.setOnFocusChangeListener((v, hasFocus) -> apply(v, hasFocus));
    }

    /** Puts a card in its focused or its resting look. */
    static void apply(View card, boolean focused) {
        float targetScale = focused ? SCALE : 1f;
        float targetAlpha = focused ? 1f : DIM;
        float targetZ = focused ? dp(card, LIFT_DP) : 0f;
        // The picture inside the card lifts a little with it.
        if (card instanceof CardArtView) {
            ((CardArtView) card).setArtFocus(focused);
        }
        if (Math.abs(card.getScaleX() - targetScale) < 0.002f
                && Math.abs(card.getAlpha() - targetAlpha) < 0.002f
                && Math.abs(card.getTranslationZ() - targetZ) < 0.5f) {
            return;
        }
        card.animate()
                .scaleX(targetScale)
                .scaleY(targetScale)
                .alpha(targetAlpha)
                .translationZ(targetZ)
                .setDuration(DURATION)
                .setInterpolator(EASING)
                .start();
    }

    static float dp(View view, float value) {
        return value * view.getResources().getDisplayMetrics().density;
    }
}
