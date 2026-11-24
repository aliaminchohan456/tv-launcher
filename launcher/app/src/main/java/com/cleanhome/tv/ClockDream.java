package com.cleanhome.tv;

import android.service.dreams.DreamService;
import android.view.KeyEvent;

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

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        if (clock != null) clock.setDreamRunning(true);
    }

    @Override
    public void onDreamingStopped() {
        if (clock != null) clock.setDreamRunning(false);
        super.onDreamingStopped();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (clock != null && clock.handleGreeting(event)) return true;
        int key = event.getKeyCode();
        if (isInteractive() && (key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_ESCAPE)) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) finish();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
}
