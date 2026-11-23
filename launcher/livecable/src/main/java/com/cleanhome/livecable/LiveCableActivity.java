package com.cleanhome.livecable;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputManager;
import android.media.tv.TvView;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

public final class LiveCableActivity extends Activity {
    // The TCL G10 exposes physical HDMI 1 as hardware input 15.
    private static final String INPUT_ID = "com.tcl.tvinput/.TvPassThroughService/HW15";
    private static final String TAG = "LiveCable";
    private static final long CONNECT_TIMEOUT_MS = 8000;
    private static final long LIVE_LABEL_MS = 2600;

    private TvView video;
    private TextView message;
    private boolean live;
    private final Runnable showNoSignal = () -> showMessage(R.string.no_signal);
    private final Runnable hideLabel = () -> message.setVisibility(View.INVISIBLE);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout screen = new FrameLayout(this);
        screen.setBackgroundColor(Color.BLACK);
        video = new TvView(this);
        video.setFocusable(true);
        screen.addView(video, new FrameLayout.LayoutParams(-1, -1));

        message = new TextView(this);
        message.setTextColor(0xFFF4F7FF);
        message.setTextSize(16);
        message.setPadding(dp(16), dp(10), dp(16), dp(10));
        GradientDrawable badge = new GradientDrawable();
        badge.setColor(0xF21B2336);
        badge.setCornerRadius(dp(12));
        message.setBackground(badge);
        FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(
                -2, -2, Gravity.TOP | Gravity.START);
        position.setMargins(dp(32), dp(24), dp(32), 0);
        screen.addView(message, position);
        setContentView(screen);
        video.requestFocus();

        video.setCallback(new TvView.TvInputCallback() {
            @Override
            public void onVideoAvailable(String inputId) {
                live = true;
                Log.i(TAG, "HDMI 1 video available in Live Cable");
                showLiveLabel();
            }

            @Override
            public void onVideoUnavailable(String inputId, int reason) {
                live = false;
                showMessage(R.string.opening_input);
                message.postDelayed(showNoSignal, CONNECT_TIMEOUT_MS);
            }

            @Override
            public void onConnectionFailed(String inputId) {
                showInputUnavailable();
            }

            @Override
            public void onDisconnected(String inputId) {
                showInputUnavailable();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        openHdmi();
    }

    @Override
    protected void onStop() {
        video.reset();
        message.removeCallbacks(showNoSignal);
        message.removeCallbacks(hideLabel);
        super.onStop();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                if (live) {
                    showLiveLabel();
                } else {
                    openHdmi();
                }
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void openHdmi() {
        live = false;
        TvInputManager manager = getSystemService(TvInputManager.class);
        if (manager == null || manager.getTvInputInfo(INPUT_ID) == null) {
            showInputUnavailable();
            return;
        }
        showMessage(R.string.opening_input);
        message.postDelayed(showNoSignal, CONNECT_TIMEOUT_MS);
        video.reset();
        video.tune(INPUT_ID, TvContract.buildChannelUriForPassthroughInput(INPUT_ID));
    }

    private void showInputUnavailable() {
        live = false;
        showMessage(R.string.input_unavailable);
    }

    private void showLiveLabel() {
        showMessage(R.string.live_input);
        message.postDelayed(hideLabel, LIVE_LABEL_MS);
    }

    private void showMessage(int resourceId) {
        message.removeCallbacks(showNoSignal);
        message.removeCallbacks(hideLabel);
        message.setText(resourceId);
        message.setVisibility(View.VISIBLE);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
