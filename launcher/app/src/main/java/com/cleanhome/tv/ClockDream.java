package com.cleanhome.tv;

import android.service.dreams.DreamService;

public class ClockDream extends DreamService {
    private FloatingClockView clock;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        ClockPreferences.Background mode = ClockPreferences.background(this);
        setInteractive(mode == ClockPreferences.Background.AMBIENT || mode == ClockPreferences.Background.MEADOW);
        setFullscreen(true);
        setScreenBright(false);
        clock = new FloatingClockView(this);
        setContentView(clock);
    }

    @Override public void onDreamingStarted() {
        super.onDreamingStarted();
        if (clock != null) clock.setDreamRunning(true);
    }

    @Override public void onDreamingStopped() {
        if (clock != null) clock.setDreamRunning(false);
        super.onDreamingStopped();
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (clock != null && clock.handleGreeting(event)) return true;
        if (isInteractive() && event.getKeyCode() == android.view.KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == android.view.KeyEvent.ACTION_UP) finish();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
}
