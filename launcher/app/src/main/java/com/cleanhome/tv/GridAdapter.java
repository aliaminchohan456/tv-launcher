package com.cleanhome.tv;

import android.graphics.Outline;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

final class GridAdapter extends RecyclerView.Adapter<GridAdapter.Holder> {
    private final List<HomeApp> apps;
    private final android.graphics.Typeface font;
    private final android.content.Context context;
    private int selected = -1;

    GridAdapter(List<HomeApp> apps, android.graphics.Typeface font,
               android.content.Context context) {
        this.apps = apps;
        this.font = font;
        this.context = context;
    }

    void setSelected(int index) {
        if (selected == index) {
            // The same card was already lit. Telling the list about it again
            // would rebind the card on every key press for nothing.
            return;
        }
        int old = selected;
        selected = index;
        if (old >= 0) {
            notifyItemChanged(old);
        }
        if (index >= 0) {
            notifyItemChanged(index);
        }
    }

    int getSelected() {
        return selected;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.tile_grid, parent, false);
        View art = view.findViewById(R.id.art);
        // Exactly the same outline the dock uses.
        CardArtView.round(art, 16f / 180f);
        CardFocus.watch(art);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        HomeApp app = apps.get(position);
        holder.art.setImageBitmap(app.art);
        holder.name.setText(app.label);
        if (font != null) {
            holder.name.setTypeface(font);
        }
        boolean on = position == selected;
        holder.itemView.setSelected(on);
        // The card grows and brightens. Every card keeps its name, only the
        // focused one is bright. One lonely name under one tile read like a
        // mistake.
        CardFocus.apply(holder.art, on);
        float targetName = on ? 1f : 0.50f;
        // The label steps down out of the way of the card that grows, so the
        // focus ring never lands on the text.
        float targetStep = on ? dp(5f) : 0f;
        if (Math.abs(holder.name.getAlpha() - targetName) > 0.002f
                || Math.abs(holder.name.getTranslationY() - targetStep) > 0.5f) {
            holder.name.animate()
                    .alpha(targetName)
                    .translationY(targetStep)
                    .setDuration(Motion.DURATION)
                    .setInterpolator(Motion.EASING)
                    .start();
        }
    }

    private float dp(float value) {
        return value * context.getResources().getDisplayMetrics().density;
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView art;
        final TextView name;

        Holder(View view) {
            super(view);
            art = view.findViewById(R.id.art);
            name = view.findViewById(R.id.name);
        }
    }
}
