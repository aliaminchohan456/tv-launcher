package com.cleanhome.tv;

/**
 * The two lists of the icon picker.
 *
 * <pre>
 *   APPS --choose app--> ICONS(app, returnPosition)
 *   ICONS --Back-------> APPS
 * </pre>
 *
 * The screen used to encode this with a nullable {@code chosenPackage}. That
 * worked, but it made "which list is showing?" and "which app are we looking
 * at?" the same question, and it hid the return position in a field beside it.
 * The mode is now explicit, so Back and OK cannot disagree about what is on
 * screen.
 */
final class IconPickerState {
    enum Mode { APPS, ICONS }

    private Mode mode = Mode.APPS;
    private String packageName;
    private int returnPosition;

    boolean isShowingApps() {
        return mode == Mode.APPS;
    }

    boolean isShowingIcons() {
        return mode == Mode.ICONS;
    }

    /** The app whose artwork is on screen, or null in the app list. */
    String packageName() {
        return packageName;
    }

    /** The app-list position to restore when Back leaves the artwork list. */
    int returnPosition() {
        return returnPosition;
    }

    void showApps() {
        mode = Mode.APPS;
        packageName = null;
    }

    void showIcons(String pkg, int appPosition) {
        mode = Mode.ICONS;
        packageName = pkg;
        returnPosition = Math.max(0, appPosition);
    }

    /**
     * Handles Back. Returns true when it was consumed by leaving the artwork
     * list; false means the app list is already showing and the screen should
     * finish.
     */
    boolean backToApps() {
        if (mode != Mode.ICONS) {
            return false;
        }
        showApps();
        return true;
    }
}
