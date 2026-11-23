package com.cleanhome.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/** A quiet lower horizon with sparse, time-specific movement. */
final class FoxSkyView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Shader halo = new RadialGradient(0, 0, 1, 0xffffffff, 0x00ffffff, Shader.TileMode.CLAMP);
    private final long started = SystemClock.uptimeMillis();
    private Shader mist;
    private int period = -1;

    FoxSkyView(Context context) { super(context); }

    void setHour(int hour) {
        int next = hour < 5 || hour >= 20 ? 3 : hour < 11 ? 0 : hour < 17 ? 1 : 2;
        if (next != period) {
            period = next;
            prepareMist();
        }
    }

    boolean isMidday() { return period == 1; }

    private void prepareMist() {
        if (getHeight() == 0) return;
        int tint = period == 3 ? 0x1c48577e : period == 1 ? 0x204e9197 : 0x20cb907b;
        mist = new LinearGradient(0, getHeight()*.76f, 0, getHeight(),
                tint & 0xffffff, tint, Shader.TileMode.CLAMP);
        invalidate();
    }

    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) { prepareMist(); }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float t = (SystemClock.uptimeMillis()-started)/1000f;
        paint.setColor(0xffffffff);
        paint.setShader(mist);
        c.drawRect(0, h*.76f, w, h, paint);
        paint.setShader(null);
        int count = period == 0 ? 6 : period == 1 ? 5 : period == 2 ? 4 : 8;
        for (int i=0; i<count; i++) {
            float phase = i*2.4f;
            float sway = (float)Math.sin(phase + t*(.09f + i*.008f));
            float x = w*((.09f+i*.379f)% .93f + .02f*sway);
            float y = h*((period == 3 ? .1f : .58f) + (i*.21f)% (period == 3 ? .76f : .36f));
            if (period != 3) y += h*.026f*(float)Math.cos(phase+t*.12f);
            float radius = h*(period == 1 ? .026f : period == 2 ? .006f : .0016f);
            if (period == 1 || period == 2) {
                c.save();
                c.translate(x,y); c.scale(radius,radius);
                paint.setColor(0xffffffff);
                paint.setShader(halo);
                paint.setAlpha((int)((period == 1 ? 19 : 50) * (1+.28f*sway)));
                c.drawCircle(0,0,1,paint);
                paint.setShader(null); c.restore();
            }
            if (period != 1) {
                paint.setColor(period == 2 ? 0xffffbf67 : 0xffffe6c6);
                paint.setAlpha((int)((period == 0 ? 50 : 77) * (1+.50f*sway)));
                c.drawCircle(x,y,period == 2 ? radius*.31f : radius,paint);
            }
        }
        float trail = t%50f - 36f;
        if (period == 3 && trail >= 0 && trail < 1.8f) {
            float p = trail/1.8f;
            float x = w*(.65f+.13f*p), y=h*(.10f+.07f*p);
            paint.setColor(0xffe2e4ff); paint.setAlpha((int)(53*Math.sin(Math.PI*p)));
            paint.setStrokeWidth(h*.001f);
            c.drawLine(x-w*.015f,y-h*.008f,x,y,paint);
        }
        if (isShown()) postInvalidateDelayed(80);
    }
}
