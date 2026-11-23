package com.cleanhome.tv;

import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

/**
 * The one motion curve the whole home screen moves on.
 *
 * A card that grows, a focus ring that travels, a menu row that lights up and
 * a title that changes all use the same length and the same curve. If they do
 * not, they drift apart and the screen looks broken.
 */
final class Motion {
    /** How long a move takes. */
    static final long DURATION = 200L;
    /** How long a ring takes to dissolve and come back. */
    static final long FADE_MS = 130L;
    /** The curve. Quick to start, slow to settle. */
    static final Interpolator EASING = new DecelerateInterpolator(1.8f);

    private Motion() {}
}
