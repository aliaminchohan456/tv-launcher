package com.cleanhome.tv;

/**
 * The dock's reorder mode.
 *
 * The machine has exactly two states:
 *
 * <pre>
 *   IDLE --begin(index)--> REORDERING(origin)
 *   REORDERING --finish()--> IDLE (returns the origin so the caller can revert)
 * </pre>
 *
 * While it is reordering, every navigation key belongs to the dock: Left and
 * Right move the card, OK commits, Back cancels. Home cancels it, a package
 * broadcast cancels it, and leaving the screen cancels it. Encoding that as a
 * class means the mode can never be half on, and no caller has to know that
 * {@code -1} used to mean "off".
 */
final class DockReorder {
    private int origin = -1;

    boolean isActive() {
        return origin >= 0;
    }

    /** The slot the card started in. Only valid while {@link #isActive()}. */
    int origin() {
        return origin;
    }

    /** Enters reorder mode with the focused card at {@code index}. */
    void begin(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("dock origin must be >= 0");
        }
        if (origin >= 0) {
            // A second begin while a reorder is already running would lose the
            // only origin the caller can revert to. The first one wins.
            return;
        }
        origin = index;
    }

    /**
     * Leaves reorder mode and returns the origin, or -1 when it was not
     * active. The caller decides whether the new order is saved or reverted.
     */
    int finish() {
        int value = origin;
        origin = -1;
        return value;
    }

    /** Leaves reorder mode without using the origin. */
    void cancel() {
        origin = -1;
    }
}
