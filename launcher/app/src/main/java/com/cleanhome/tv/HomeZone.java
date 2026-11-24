package com.cleanhome.tv;

/**
 * The four focus zones of the home screen.
 *
 * <pre>
 *        TOP  (Inputs, Wi-Fi, Settings)
 *         |
 *       DOCK  (the four app cards)
 *         |
 *       GRID  (everything else)
 *
 *       MENU  (inputs, anchored under the Inputs pill)
 * </pre>
 *
 * The allowed moves are deliberately small and are enforced in
 * {@code MainActivity}:
 *
 * <ul>
 *   <li>TOP: Left/Right steps between visible pills; Down goes to DOCK.</li>
 *   <li>DOCK: Left at the first card opens launcher settings; Up goes to TOP
 *       only when a pill is visible; Down goes to GRID only when the grid has
 *       an app; Left/Right clamp at the ends.</li>
 *   <li>GRID: Up from the first row goes to DOCK; Down on a short last row
 *       moves to the last card instead of doing nothing; Left/Right stay
 *       inside the current row; Back goes to DOCK.</li>
 *   <li>MENU: Up from the first row goes back to the pill it came from; Back
 *       does the same; Down/Up clamp inside the list.</li>
 * </ul>
 *
 * The zone is the only mode flag for the home screen. Everything else is an
 * index within it. A screen is rendered from the zone, never by toggling
 * visibility at the call site.
 */
enum HomeZone {
    TOP,
    DOCK,
    GRID,
    MENU
}
