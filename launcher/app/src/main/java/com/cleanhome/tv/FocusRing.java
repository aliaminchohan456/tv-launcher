package com.cleanhome.tv;

import android.graphics.Rect;
import android.view.View;
import android.widget.FrameLayout;

/**
 * A focus ring that sits over the focused card of any screen.
 *
 * The launcher has one on its root. The edit screen and the icon picker have
 * one each, so a card looks the same everywhere: the same white ring, the
 * same band of dark inside it, the same lift.
 */
final class FocusRing {
    private final GlassHighlight highlight;
    private final FrameLayout root;

    FocusRing(FrameLayout root) {
        this.root = root;
        this.highlight = new GlassHighlight(root.getContext());
        root.addView(highlight, new FrameLayout.LayoutParams(dp(root), dp(root)));
    }

    /** Puts the ring over a card. The card is the view that holds the picture. */
    void moveTo(View card, float cornerDp) {
        if (card == null || card.getWidth() < 2 || card.getHeight() < 2) {
            highlight.hide();
            return;
        }
        Rect rect = new Rect(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, rect);
        highlight.setScale(card.getScaleX());
        highlight.springTo(rect.left, rect.top, rect.width(), rect.height(), cornerDp);
    }

    void hide() {
        highlight.hide();
    }

    private static int dp(View view) {
        return Math.round(16f * view.getResources().getDisplayMetrics().density);
    }
}
