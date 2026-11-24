package com.cleanhome.tv;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

/**
 * A focus ring that sits over the focused card of any screen.
 *
 * The launcher has one on its root. The edit screen, the icon picker, the
 * photo picker and the settings screen use one each, so a card looks the same
 * everywhere: the same white ring, the same band of dark inside it, the same
 * lift, and live scale tracking while the card grows.
 */
final class FocusRing {
    private final GlassHighlight highlight;
    private final FrameLayout root;
    private View tracked;
    private float trackedCornerDp;

    FocusRing(FrameLayout root) {
        this.root = root;
        this.highlight = new GlassHighlight(root.getContext());
        root.addView(highlight, new FrameLayout.LayoutParams(dp(root), dp(root)));
        root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                syncTracked();
                return true;
            }
        });
    }

    /** Puts the ring over a card. The card is the view that holds the picture. */
    void moveTo(View card, float cornerDp) {
        if (card == null) {
            tracked = null;
            highlight.hide();
            return;
        }
        // Keep the card even before it has been measured. The pre-draw listener
        // retries every frame, so a card that receives focus during the first
        // layout still gets its ring instead of being forgotten at width 0.
        tracked = card;
        trackedCornerDp = cornerDp;
        syncTracked();
    }

    void hide() {
        tracked = null;
        highlight.hide();
    }

    private void syncTracked() {
        View card = tracked;
        if (card == null) {
            return;
        }
        if (!card.isAttachedToWindow() || !card.isShown()
                || card.getWidth() < 2 || card.getHeight() < 2) {
            highlight.hide();
            return;
        }
        Rect rect = new Rect(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, rect);
        highlight.setScale(card.getScaleX());
        highlight.springTo(rect.left, rect.top, rect.width(), rect.height(), trackedCornerDp);
    }

    private static int dp(View view) {
        return Math.round(16f * view.getResources().getDisplayMetrics().density);
    }
}
