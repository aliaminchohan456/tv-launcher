package com.cleanhome.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/** Wide colour wash behind the focused app. It fades out before the view edge. */
public final class AtmosphereView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix scale = new Matrix();
    private int color;

    public AtmosphereView(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
    }

    void setColor(int color) {
        if (this.color == color) {
            return;
        }
        this.color = color;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w < 2 || h < 2 || color == 0) {
            return;
        }
        float cx = w / 2f;
        float cy = h / 2f;
        float radius = h * 0.46f;
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        RadialGradient gradient = new RadialGradient(
                cx,
                cy,
                radius,
                new int[]{
                        Color.argb(42, red, green, blue),
                        Color.argb(12, red, green, blue),
                        Color.argb(0, red, green, blue)
                },
                new float[]{0f, 0.38f, 1f},
                Shader.TileMode.CLAMP);
        scale.setScale(1.7f, 1f, cx, cy);
        gradient.setLocalMatrix(scale);
        paint.setShader(gradient);
        canvas.drawRect(0, 0, w, h, paint);
    }
}
