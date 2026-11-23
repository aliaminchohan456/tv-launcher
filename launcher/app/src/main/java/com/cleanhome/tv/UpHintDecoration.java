package com.cleanhome.tv;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** Up chevron drawn above the first app row, so it scrolls away with that row. */
final class UpHintDecoration extends RecyclerView.ItemDecoration {
    private final int span;
    private final int arrowW;
    private final int arrowH;
    private final int lift;
    private final Drawable arrow;

    UpHintDecoration(android.content.Context context, int span) {
        this.span = span;
        float density = context.getResources().getDisplayMetrics().density;
        arrowW = Math.round(28f * density);
        arrowH = Math.round(14f * density);
        // The focused card grows upwards, so the arrow has to float higher
        // than the row or it sits on the card's corner.
        lift = Math.round(16f * density);
        arrow = context.getDrawable(R.drawable.ic_chevron_up);
        if (arrow != null) {
            arrow.setAlpha(140);
        }
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        // The space above the first row is the RecyclerView's own padding, not
        // an item offset. An item offset would be eaten by
        // scrollToPositionWithOffset and the first row would jump up under the
        // clock.
    }

    @Override
    public void onDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        if (arrow == null) {
            return;
        }
        int top = Integer.MAX_VALUE;
        boolean found = false;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int position = parent.getChildAdapterPosition(child);
            if (position >= 0 && position < span) {
                top = Math.min(top, child.getTop());
                found = true;
            }
        }
        if (!found) {
            return;
        }
        int center = parent.getWidth() / 2;
        int bottom = top - lift;
        arrow.setBounds(center - arrowW / 2, bottom - arrowH, center + arrowW / 2, bottom);
        arrow.draw(canvas);
    }
}
