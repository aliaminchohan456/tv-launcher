package com.cleanhome.tv;

/**
 * The state of one visit to the edit screen.
 *
 * Two things can happen at once: the screen is reading the saved name/icon
 * from disk on a worker, and the owner may already be choosing a picture from
 * another app or the gallery. The selection is therefore a small state
 * machine, not a single {@code String} field:
 *
 * <pre>
 *   LOADING --finishLoad(token, saved)--> READY
 *   LOADING --select(spec)--------------> LOADING (touched, saved load ignored)
 *   LOADING --reset()-------------------> LOADING (touched, original wins)
 *   READY   --select(spec)--------------> READY
 *   READY   --reset()-------------------> READY
 * </pre>
 *
 * The load may finish after the owner has chosen something. In that case it
 * must not overwrite the choice, and it must not replace the preview. The one
 * exception is a reset: an empty selection means "the app's original art", so
 * the standard artwork that arrived with the load is exactly what should be
 * shown.
 *
 * {@code selectedSpec} is {@code null} until the first load finishes and the
 * owner has not touched anything. That is important for Save: a null spec
 * means "we do not know what the icon should be yet", so Save leaves the
 * existing preference alone instead of clearing it.
 */
final class EditSession {
    enum Phase { LOADING, READY }

    private final Epoch loads = new Epoch();
    private Phase phase = Phase.LOADING;
    private String selectedSpec;
    private boolean touched;

    /** Starts a load and returns its token. */
    int beginLoad() {
        phase = Phase.LOADING;
        return loads.next();
    }

    /**
     * Applies the result of a load if it is still the newest one.
     *
     * @param savedSpec the icon spec already saved for this app, or null
     * @return true when the result is current and the screen may show it
     */
    boolean finishLoad(int token, String savedSpec) {
        if (!loads.isCurrent(token)) {
            return false;
        }
        phase = Phase.READY;
        if (!touched) {
            selectedSpec = savedSpec == null ? "" : savedSpec;
        }
        return true;
    }

    /**
     * True when the picture that arrived from disk should replace the preview.
     *
     * An untouched screen takes the saved picture. A screen that was reset is
     * showing the original artwork and should take the standard picture that
     * just arrived. Any other selection was already previewed by the picker
     * that produced it and must be left alone.
     */
    boolean appliesLoadedPreview() {
        return !touched || (selectedSpec != null && selectedSpec.isEmpty());
    }

    boolean isReady() {
        return phase == Phase.READY;
    }

    /** The spec Save will write, or null when it is still unknown. */
    String selectedSpec() {
        return selectedSpec;
    }

    boolean isTouched() {
        return touched;
    }

    /** The owner picked a picture, or picked the original art. */
    void select(String spec) {
        selectedSpec = spec == null ? "" : spec;
        touched = true;
    }

    /**
     * Marks the selection as being changed before the new value exists.
     *
     * The gallery copy happens on a worker and can take a moment. Without this
     * the saved-art load could finish during that window and replace the
     * preview with the old picture just before the new one arrives.
     */
    void beginPendingChoice() {
        touched = true;
    }

    /** The owner asked for the app's original name and art. */
    void reset() {
        selectedSpec = "";
        touched = true;
    }

    /** Makes any load still in flight stale. */
    void invalidate() {
        loads.invalidate();
    }
}
