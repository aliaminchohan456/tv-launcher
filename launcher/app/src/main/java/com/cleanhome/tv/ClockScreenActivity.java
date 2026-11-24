package com.cleanhome.tv;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;

public final class ClockScreenActivity extends Activity {
    private FloatingClockView clock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        clock = new FloatingClockView(this, true);
        setContentView(clock);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (clock != null) clock.setDreamRunning(true);
    }

    @Override
    protected void onStop() {
        if (clock != null) clock.setDreamRunning(false);
        super.onStop();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (clock == null) return super.dispatchKeyEvent(event);
        if (clock.handleGreeting(event)) return true;
        int key = event.getKeyCode();
        if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
            boolean pressed = event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0;
            int delta = key == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1;
            // Consume both halves even outside Meadow: a stray key must never
            // dismiss a preview that is meant to be left only with Back.
            clock.stepPreviewHour(pressed ? delta : 0);
            return true;
        }
        if (key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_ESCAPE) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) finish();
            return true;
        }
        if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN
                || key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER
                || key == KeyEvent.KEYCODE_NUMPAD_ENTER) return true;
        return super.dispatchKeyEvent(event);
    }
}
