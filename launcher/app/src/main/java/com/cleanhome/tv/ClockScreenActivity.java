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
        clock = new FloatingClockView(this);
        setContentView(clock);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (clock != null && clock.handleGreeting(event)) return true;
        int key = event.getKeyCode();
        if (clock != null && (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            boolean pressed = event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0;
            int delta = key == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1;
            if (clock.stepPreviewHour(pressed ? delta : 0)) return true;
        }
        if (key == KeyEvent.KEYCODE_VOLUME_UP || key == KeyEvent.KEYCODE_VOLUME_DOWN
                || key == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return super.dispatchKeyEvent(event);
        }
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            finish();
        }
        return true;
    }
}
