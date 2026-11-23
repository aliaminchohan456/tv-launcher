package com.cleanhome.tv;

import android.content.Context;
import android.media.AudioManager;
import android.os.SystemClock;
import android.provider.Settings;

/**
 * The click a TV makes when the focus moves.
 *
 * Android plays this by itself whenever a view takes the focus, but this
 * launcher keeps its own focus model: the root view holds the real focus and
 * every arrow key is consumed in dispatchKeyEvent, so the framework never
 * hears about a focus change and stays silent. Without this the launcher
 * feels dead compared with every other TV interface.
 *
 * The sound is the system's own key click, so it matches the rest of the set.
 * It follows the system's sound effects setting, a TV with sounds turned off
 * stays quiet. A short throttle keeps a fast run along the dock from turning
 * into a machine gun.
 */
final class FocusSound {
    private static final long THROTTLE_MS = 45L;
    private static final long SETTINGS_TTL_MS = 2000L;
    private static final float VOLUME = 0.35f;

    private final Context context;
    private final AudioManager audio;
    private long lastClick;
    private long lastSettingsRead;
    private boolean effectsEnabled = true;

    FocusSound(Context context) {
        this.context = context.getApplicationContext();
        this.audio = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
    }

    /** One click. Call it only when the focus really moved. */
    void play() {
        if (audio == null) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastClick < THROTTLE_MS) {
            return;
        }
        lastClick = now;
        if (!effectsEnabled()) {
            return;
        }
        try {
            audio.playSoundEffect(AudioManager.FX_KEY_CLICK, VOLUME);
        } catch (RuntimeException ignored) {
            // A set with no audio output must not fall over because of a click.
        }
    }

    private boolean effectsEnabled() {
        long now = SystemClock.uptimeMillis();
        if (now - lastSettingsRead > SETTINGS_TTL_MS) {
            lastSettingsRead = now;
            try {
                effectsEnabled = Settings.System.getInt(context.getContentResolver(),
                        Settings.System.SOUND_EFFECTS_ENABLED, 1) == 1;
            } catch (RuntimeException ignored) {
                effectsEnabled = true;
            }
        }
        return effectsEnabled;
    }
}
