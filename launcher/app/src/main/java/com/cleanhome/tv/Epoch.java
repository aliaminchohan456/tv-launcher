package com.cleanhome.tv;

/**
 * A one-way generation counter for asynchronous work.
 *
 * Every piece of work that can finish out of order owns one of these. The
 * worker takes {@link #next()} before it starts and checks {@link #isCurrent}
 * before it touches the screen. A newer request, or an explicit
 * {@link #invalidate()}, makes the older result stale without any extra
 * booleans.
 *
 * This is the explicit form of the pattern that used to be written as
 * {@code int token = ++token} beside every background read. Keeping it in one
 * class makes the rule visible: a result is applied only if its epoch is still
 * current, and a destroyed screen invalidates the epoch instead of clearing a
 * handful of flags in a particular order.
 */
final class Epoch {
    private int current;

    /** Starts a new generation and returns its token. */
    int next() {
        return ++current;
    }

    /** True when no newer generation has started since {@code token}. */
    boolean isCurrent(int token) {
        return token == current;
    }

    /** Makes every token handed out so far stale. */
    void invalidate() {
        current++;
    }
}
