package com.cleanhome.tv;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int COLS = 4;
    private static final int TOP = 0;
    private static final int DOCK = 1;
    private static final int GRID = 2;
    private static final int MENU = 3;
    private static final int REQ_SSID = 41;
    private static final String SSID_TAG = "SsidPill";

    /** How long the title and the arrow take to fade. */
    private static final long FADE_MS = 200L;
    private static final int FOCUS_TICKS = 16;
    private static final android.view.animation.Interpolator FOCUS_EASING =
            new android.view.animation.DecelerateInterpolator(1.8f);
    private static final android.view.animation.Interpolator FADE_EASING =
            new android.view.animation.AccelerateDecelerateInterpolator();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ConnectivityManager.NetworkCallback networkWatch;
    private List<HomeApp> allApps = Collections.emptyList();
    private final List<HomeApp> dockApps = new ArrayList<>();
    private final List<HomeApp> gridApps = new ArrayList<>();
    private final List<View> dockTiles = new ArrayList<>();
    private final List<TvInputInfo> inputs = new ArrayList<>();
    private final List<View> inputRows = new ArrayList<>();
    private final List<View> inputMarks = new ArrayList<>();
    private final List<ValueAnimator> inputMarkAnims = new ArrayList<>();
    private FocusSound focusSound;

    private View root;
    private TextView greeting;
    private TextView clock;
    private TextView ampm;
    private TextView dateView;
    private TextView[] pills;
    private AmbientBackgroundView ambient;
    private TextView shelfTitle;
    private int highlightRetries;
    private int seenStyleGeneration;
    private boolean reloadOnResume;
    private int appLoadToken;
    private BroadcastReceiver packageReceiver;
    /** Waits for the burst of package broadcasts that one install sends. */
    private final Runnable packageReload = this::reloadApps;
    private boolean clockBackDown;
    private boolean centerDown;
    private boolean centerWasLong;
    private final Runnable longPress = () -> {
        if (!centerDown) {
            return;
        }
        centerWasLong = true;
        showAppOptions();
    };
    private Typeface medium;
    private View homeHeader;
    private RecyclerView grid;
    private GridAdapter gridAdapter;
    private HorizontalScrollView dockBand;
    private LinearLayout dockRow;
    private LinearLayout inputMenu;
    private ImageView hintDown;
    private GlassHighlight highlight;
    private Typeface thin;
    private Typeface text;

    private int zone = DOCK;
    private int topIndex;
    private int dockIndex;
    private int dockMoveOrigin = -1;
    private static final int TCL_G10_NETFLIX_KEY = 4062;
    private static final int TCL_G10_YOUTUBE_KEY = 4065;
    private TextView dockMoveHint;
    private int gridIndex;
    private int menuIndex;
    private int menuPill;
    private int focusTicksLeft;
    private int ssidToken;
    private int heroToken;
    private Dialog optionsDialog;
    private int ringZone = -1;
    private boolean ringJump;
    private boolean gridScrolled;
    private String heroLabel = "";
    private Runnable pendingHeroSwap;
    private boolean appsReady;

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            renderClock();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 32 bit colour. A wide dark gradient needs the extra depth or the
        // panels show steps instead of a smooth fade.
        getWindow().setFormat(PixelFormat.RGBA_8888);
        focusSound = new FocusSound(this);
        setContentView(R.layout.activity_home);
        thin = outfit(300);
        text = outfit(420);
        medium = outfit(500);
        bindViews();
        loadWallpaper();
        restoreFocus();
        final int firstToken = ++appLoadToken;
        new Thread(() -> {
            List<HomeApp> all = AppLoader.load(this);
            List<TvInputInfo> found = loadInputs();
            runOnUiThread(() -> {
                // A package broadcast can arrive before this first load
                // finishes. Do not let the older list overwrite the newer one.
                if (firstToken != appLoadToken || isFinishing() || isDestroyed()) {
                    return;
                }
                applyApps(all);
                showInputs(found);
            });
        }, "home-load").start();
        loadSsidAsync();
        watchNetwork();
        watchPackages();
    }

    /**
     * Reads the network and puts the right word beside the wave.
     *
     * The connected name when one is known, or the plain word of what is
     * there now, so a network that is switched off does not leave its old
     * name behind.
     */
    private void loadSsidAsync() {
        final int token = ++ssidToken;
        new Thread(() -> {
            String ssid = readSsid();
            String label = ssid != null ? ssid : fallbackNetworkLabel();
            runOnUiThread(() -> {
                // A slow read must never overwrite a newer one, and a closed
                // screen must not be touched at all.
                if (token != ssidToken || isFinishing() || isDestroyed()) {
                    return;
                }
                setPill(pills[1], label);
                if (ssid == null) {
                    maybeAskSsid();
                }
            });
        }, "ssid").start();
    }

    /** Writes a button label only when it really changed. */
    private static void setPill(TextView pill, String label) {
        if (pill != null && !label.contentEquals(pill.getText())) {
            pill.setText(label);
        }
    }

    /**
     * Watches the network so the word beside the wave does not go stale.
     *
     * The name is read once when the screen is built and again every time the
     * launcher comes forward. While it sits open the callback follows the
     * network that arrives and the one that is switched off, so the button
     * never keeps the name of a connection that is gone.
     */
    private void watchNetwork() {
        if (networkWatch != null) {
            return;
        }
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) {
            return;
        }
        networkWatch = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                refreshNetworkButton();
            }

            @Override
            public void onLost(Network network) {
                // A network that was switched off leaves no name behind.
                refreshNetworkButton();
            }
        };
        cm.registerDefaultNetworkCallback(networkWatch);
    }

    /**
     * Follows apps being installed, updated and removed while the launcher is
     * running.
     *
     * Play Store sends more than one broadcast for a single install, so the
     * reload waits for the burst to settle. Without this the grid kept the
     * list it read when the screen was first built, and a newly installed app
     * only showed up after the launcher was restarted.
     */
    private void watchPackages() {
        if (packageReceiver != null) {
            return;
        }
        packageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())
                        && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                        && intent.getData() != null) {
                    List<String> dock = Dock.load(context);
                    if (dock.remove(intent.getData().getSchemeSpecificPart())) {
                        Dock.save(context, dock);
                    }
                }
                handler.removeCallbacks(packageReload);
                handler.postDelayed(packageReload, 600L);
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(packageReceiver, filter);
        }
    }

    /** Reads the network again on its own thread and puts its word up. */
    private void refreshNetworkButton() {
        loadSsidAsync();
    }

    /**
     * The word for a network whose name cannot be read.
     *
     * A wire connection says what it is, and everything else falls back to
     * plain "Wi-Fi", so a network that is switched off does not leave its old
     * name on the button.
     */
    private String fallbackNetworkLabel() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            Network network = cm == null ? null : cm.getActiveNetwork();
            if (network != null) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    return "Ethernet";
                }
            }
        } catch (RuntimeException ignored) {
            return "Wi-Fi";
        }
        return "Wi-Fi";
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (root != null) {
            root.removeCallbacks(focusTick);
        }
        if (optionsDialog != null && optionsDialog.isShowing()) {
            optionsDialog.dismiss();
        }
        if (networkWatch != null) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm != null) {
                cm.unregisterNetworkCallback(networkWatch);
            }
            networkWatch = null;
        }
        if (packageReceiver != null) {
            unregisterReceiver(packageReceiver);
            packageReceiver = null;
        }
        super.onDestroy();
    }

    /** Asked once only. If it is refused the pill keeps saying "Wi-Fi". */
    private void maybeAskSsid() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        SharedPreferences preferences = getPrefs();
        if (preferences.getBoolean("ssid-asked", false)) {
            return;
        }
        preferences.edit().putBoolean("ssid-asked", true).apply();
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_SSID);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_SSID && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadSsidAsync();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!reloadOnResume) {
            focusFirstDockApp();
        }
        root.requestFocus();
        // The user may have changed the buttons or the dock in the settings.
        applyHiddenButtons();
        int styles = AppStyles.generation(this);
        if (reloadOnResume || !Dock.loadAvailable(this).equals(dockPackages())
                || (seenStyleGeneration != 0 && styles != seenStyleGeneration)) {
            reloadOnResume = false;
            reloadApps();
        }
        seenStyleGeneration = styles;
        handler.removeCallbacks(clockTick);
        clockTick.run();
        // The name of the network may have changed while some app was open.
        loadSsidAsync();
        renderZone();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        focusFirstDockApp();
        renderZone();
    }

    /** Home opens on the first dock app, not the top buttons. */
    private void focusFirstDockApp() {
        stopDockMove(false);
        zone = DOCK;
        dockIndex = 0;
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(clockTick);
        handler.removeCallbacks(longPress);
        clockBackDown = false;
        centerDown = false;
        centerWasLong = false;
        // Any title swap that is still waiting must not fire on the next screen.
        heroToken++;
        if (pendingHeroSwap != null) {
            handler.removeCallbacks(pendingHeroSwap);
            pendingHeroSwap = null;
        }
        if (dockMoveOrigin >= 0) {
            stopDockMove(false);
            renderZone();
        }
        super.onPause();
    }

    private void bindViews() {
        root = findViewById(R.id.root);
        greeting = findViewById(R.id.greeting);
        clock = findViewById(R.id.clock);
        ampm = findViewById(R.id.ampm);
        dateView = findViewById(R.id.date);
        ambient = findViewById(R.id.glow);
        shelfTitle = findViewById(R.id.shelf_title);
        homeHeader = findViewById(R.id.home_header);
        grid = findViewById(R.id.grid);
        dockBand = findViewById(R.id.dock_band);
        dockRow = findViewById(R.id.dock_row);
        dockBand.addOnLayoutChangeListener((view, left, top, right, bottom,
                                            oldLeft, oldTop, oldRight, oldBottom) -> {
            view.setClipBounds(new Rect(0, 0, right - left, bottom - top));
            ViewGroup.MarginLayoutParams titleParams =
                    (ViewGroup.MarginLayoutParams) shelfTitle.getLayoutParams();
            int start = left - ((View) shelfTitle.getParent()).getLeft() + dockBand.getPaddingLeft();
            if (titleParams.leftMargin != start) {
                titleParams.leftMargin = start;
                shelfTitle.setLayoutParams(titleParams);
            }
        });
        inputMenu = findViewById(R.id.input_menu);
        hintDown = findViewById(R.id.hint_down);
        dockMoveHint = findViewById(R.id.dock_move_hint);
        dockMoveHint.setTypeface(medium);
        pills = new TextView[]{
                findViewById(R.id.pill_inputs),
                findViewById(R.id.pill_wifi),
                findViewById(R.id.pill_settings)
        };
        greeting.setTypeface(medium);
        clock.setTypeface(thin);
        ampm.setTypeface(medium);
        dateView.setTypeface(text);
        // The app name is the anchor of the screen, so it gets a medium weight.
        shelfTitle.setTypeface(medium);
        for (TextView pill : pills) {
            pill.setTypeface(medium);
        }
        grid.setLayoutManager(new GridLayoutManager(this, COLS));
        grid.addItemDecoration(new UpHintDecoration(this, COLS));
        grid.setItemAnimator(null);
        grid.setHasFixedSize(true);
        applyHiddenButtons();
        highlight = new GlassHighlight(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(10), dp(10));
        ((FrameLayout) root).addView(highlight, lp);
        root.requestFocus();
    }

    private void applyApps(List<HomeApp> all) {
        dockMoveOrigin = -1;
        allApps = all;
        HomeApp previousDockApp = dockIndex < dockApps.size() ? dockApps.get(dockIndex) : null;
        HomeApp previousGridApp = gridIndex < gridApps.size() ? gridApps.get(gridIndex) : null;
        dockApps.clear();
        gridApps.clear();
        for (String pkg : Dock.load(this)) {
            HomeApp match = null;
            for (HomeApp app : all) {
                if (pkg.equals(app.packageName)) {
                    match = app;
                    break;
                }
            }
            if (match != null) {
                dockApps.add(match);
            }
        }
        for (HomeApp app : all) {
            boolean inDock = false;
            for (HomeApp docked : dockApps) {
                if (docked.packageName.equals(app.packageName)) {
                    inDock = true;
                    break;
                }
            }
            if (!inDock) {
                gridApps.add(app);
            }
        }
        dockRow.removeAllViews();
        dockTiles.clear();
        int tileWidth = dockApps.size() > 4 ? 140 : 180;
        int tileHeight = Math.round(tileWidth * 9f / 16f);
        for (int i = 0; i < dockApps.size(); i++) {
            View tile = getLayoutInflater().inflate(R.layout.tile_dock, dockRow, false);
            HomeApp app = dockApps.get(i);
            ImageView art = (ImageView) tile;
            art.setImageBitmap(app.art);
            CardFocus.watch(art);
            CardFocus.apply(art, false);
            round(art, 16f / tileWidth);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    dp(tileWidth), dp(tileHeight));
            params.leftMargin = i == 0 ? 0 : dp(dockApps.size() > 4 ? 17 : 16);
            tile.setLayoutParams(params);
            dockRow.addView(tile);
            dockTiles.add(tile);
        }
        if (dockApps.isEmpty()) {
            TextView add = new TextView(this);
            add.setText("Add apps to dock");
            add.setTextSize(16f);
            add.setTypeface(medium);
            add.setTextColor(0xFFFFFFFF);
            add.setGravity(Gravity.CENTER);
            add.setBackgroundResource(R.drawable.input_card);
            dockRow.addView(add, new LinearLayout.LayoutParams(dp(180), dp(101)));
            dockTiles.add(add);
        }
        gridAdapter = new GridAdapter(gridApps, medium, this);
        grid.setAdapter(gridAdapter);
        gridIndex = restoredIndex(gridApps, previousGridApp, gridIndex);
        dockIndex = restoredIndex(dockApps, previousDockApp, dockIndex);
        if (zone == GRID && gridApps.isEmpty()) {
            zone = DOCK;
        }
        appsReady = true;
        renderZone();
    }

    private int restoredIndex(List<HomeApp> apps, HomeApp previous, int index) {
        if (previous != null) {
            for (int i = 0; i < apps.size(); i++) {
                if (apps.get(i).packageName.equals(previous.packageName)) {
                    return i;
                }
            }
        }
        return Math.max(0, Math.min(index, apps.size() - 1));
    }

    private void renderZone() {
        // A move to another part of the screen is not a slide. The ring
        // dissolves and comes back instead of flying across the panel.
        ringJump = (ringZone != zone) || gridScrolled;
        ringZone = zone;
        gridScrolled = false;
        boolean showGrid = zone == GRID && appsReady;
        boolean showShelf = (zone == DOCK || zone == TOP) && appsReady && !dockApps.isEmpty();
        homeHeader.setVisibility(showGrid && gridIndex >= COLS ? View.GONE : View.VISIBLE);
        grid.setVisibility(showGrid ? View.VISIBLE : View.GONE);
        // The arrow stays in place while it fades, so the dock never moves.
        boolean arrow = !gridApps.isEmpty() && (zone == DOCK || zone == TOP);
        hintDown.setVisibility(arrow && dockMoveOrigin < 0 ? View.VISIBLE : View.GONE);
        dockMoveHint.setVisibility(dockMoveOrigin >= 0 ? View.VISIBLE : View.GONE);
        shelfTitle.setVisibility(showShelf ? View.VISIBLE : View.INVISIBLE);
        // The wash stays on while the input menu is open. It is still the
        // same screen, the colour should not blink out from under the menu.
        // The grid is a screen of its own and it gets the wash as well.
        boolean showAmbient = (zone == DOCK || zone == TOP || zone == MENU || zone == GRID)
                && appsReady;
        ambient.setVisibility(showAmbient ? View.VISIBLE : View.INVISIBLE);
        inputMenu.setVisibility(zone == MENU ? View.VISIBLE : View.GONE);
        for (int i = 0; i < pills.length; i++) {
            // The button the menu came out of stays lit while it is open.
            pills[i].setSelected((zone == TOP && i == topIndex)
                    || (zone == MENU && i == menuPill));
        }
        // The name and the arrow belong to the dock. They fade away when the
        // top row takes the focus and fade back when the dock gets it again.
        // The dock owns the name: showHero fades it and swaps its text, so a
        // second fade here would make the two fight on every focus move.
        if (zone != DOCK) {
            fadeShelf(0f);
        }
        fadeArrow(zone == DOCK ? 1f : 0f);
        fadeDock(zone != GRID ? 1f : 0f);
        fadePills(zone != GRID ? 1f : 0f);
        giveGridTheDockRoom();
        updateCards();
        if (gridAdapter != null) {
            gridAdapter.setSelected(zone == GRID ? gridIndex : -1);
        }
        for (int i = 0; i < inputRows.size(); i++) {
            // The row lights up on the same curve the ring moves on, so the
            // two always agree.
            fadeMark(i, zone == MENU && i == menuIndex);
        }
        if (zone == GRID) {
            // The grid lights up with the app that has the focus in it.
            updateAmbient();

        } else if (zone == DOCK && showShelf && dockIndex < dockApps.size()) {
            showHero(dockApps.get(dockIndex));
        }
        root.post(() -> {
            scrollDockToFocus();
            placeHighlight();
            scrollGridToFocus();
        });
        startFocusTick();
    }

    private void scrollDockToFocus() {
        if (zone == DOCK && dockIndex < dockTiles.size()) {
            View tile = dockTiles.get(dockIndex);
            int left = tile.getLeft();
            int right = tile.getRight() - (dockBand.getWidth()
                    - dockBand.getPaddingLeft() - dockBand.getPaddingRight());
            int scroll = dockBand.getScrollX();
            int target = Math.max(right, Math.min(left, scroll));
            if (target != scroll && tile.getWidth() > 0) {
                dockBand.scrollTo(target, 0);
            }
        }
    }

    /**
     * Keeps the focused card in sight, but only if the grid really is taller
     * than the screen.
     *
     * This has to run after the layout. The stage only grows into the space
     * the dock was using, and until that has happened the grid still looks
     * too tall, so it would scroll and push the first row up under the clock.
     */
    private void scrollGridToFocus() {
        if (zone != GRID || gridApps.isEmpty()
                || !(grid.getLayoutManager() instanceof GridLayoutManager)) {
            return;
        }
        GridLayoutManager manager = (GridLayoutManager) grid.getLayoutManager();
        int rows = (gridApps.size() + COLS - 1) / COLS;
        int itemHeight = grid.getChildCount() > 0 ? grid.getChildAt(0).getHeight() : 0;
        int content = grid.getPaddingTop() + rows * itemHeight;
        if (content <= grid.getHeight()) {
            // Everything fits on the screen. The grid stays at the top so the
            // first row never slides up under the clock.
            if (manager.findFirstVisibleItemPosition() != 0) {
                manager.scrollToPositionWithOffset(0, 0);
            }
            return;
        }
        if (gridIndex < COLS) {
            manager.scrollToPositionWithOffset(0, 0);
        } else {
            manager.scrollToPositionWithOffset(gridIndex, dp(4));
        }
    }

    /**
     * The colour of the screen follows whatever has the focus, and the glow
     * sits over the column that has it.
     */
    private void updateAmbient() {
        HomeApp app = null;
        float centre = 0.30f;
        float centreY = 0.42f;
        if (zone == GRID) {
            if (gridIndex >= 0 && gridIndex < gridApps.size()) {
                app = gridApps.get(gridIndex);
            }
            // The grid is two dimensional, so the glow follows the column and
            // the row. Column 0 to 3 takes it from 0.25 to 0.75 of the width,
            // row 0 and down takes it from 0.42 to 0.62 of the height.
            int column = gridIndex % COLS;
            int row = gridIndex / COLS;
            centre = 0.25f + column * (0.75f - 0.25f) / (COLS - 1);
            centreY = 0.42f + row * 0.20f;
        } else {
            if (dockIndex >= 0 && dockIndex < dockApps.size()) {
                app = dockApps.get(dockIndex);
            }
            centre = glowCentreFor(dockIndex);
        }
        if (app != null) {
            ambient.animateTo(app.color, centre, centreY);
        }
    }

    /**
     * The grid takes the room the dock was using.
     *
     * The margin goes on the grid, not on the stage. A negative margin on the
     * stage would make the layout push the dock down, and the dock would then
     * jump from the middle of the screen instead of sliding out of its own
     * place. Here the grid simply grows down over the dock, which is faded out
     * anyway, and the dock does not move at all.
     */
    private void giveGridTheDockRoom() {
        if (grid == null || dockBand == null) {
            return;
        }
        int target = (zone == GRID) ? -dockBand.getHeight() : 0;
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) grid.getLayoutParams();
        if (lp != null && lp.bottomMargin != target) {
            lp.bottomMargin = target;
            grid.setLayoutParams(lp);
        }
    }

    /** The packages currently in the dock, in order. */
    private List<String> dockPackages() {
        List<String> list = new ArrayList<>();
        for (HomeApp app : dockApps) {
            list.add(app.packageName);
        }
        return list;
    }

    /** The launcher's own settings screen. */
    private void openLauncherSettings() {
        try {
            startActivity(new Intent(this, LauncherSettingsActivity.class));
        } catch (Exception ignored) {
        }
    }

    /**
     * The user can hide any of the three buttons in the top right corner.
     */
    private void applyHiddenButtons() {
        boolean[] hidden = {
                Dock.isHidden(this, Dock.BUTTON_INPUTS),
                Dock.isHidden(this, Dock.BUTTON_WIFI),
                Dock.isHidden(this, Dock.BUTTON_SETTINGS)
        };
        for (int i = 0; i < pills.length; i++) {
            pills[i].setVisibility(hidden[i] ? View.GONE : View.VISIBLE);
        }
        keepTopVisible();
    }

    /**
     * True when at least one button in the top right corner is on screen.
     *
     * With every button hidden there is no top row at all, so the focus must
     * stay down in the dock and UP must do nothing.
     */
    private boolean hasVisiblePill() {
        return firstVisiblePill() >= 0;
    }

    /**
     * The first button in the top row that is on screen, or -1 when the user
     * has hidden all three.
     *
     * A button the user has hidden cannot hold the focus. If it did, the
     * focus would sit on something nobody can see, and the first press of OK
     * would open the menu of the hidden button.
     */
    private int firstVisiblePill() {
        if (pills == null) {
            return -1;
        }
        for (int i = 0; i < pills.length; i++) {
            if (pills[i].getVisibility() == View.VISIBLE) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The next button in the top row that is on screen.
     *
     * Hidden buttons are stepped over. At the end of the row the focus stays
     * where it is.
     */
    private int stepTop(int dir) {
        if (pills == null) {
            return topIndex;
        }
        int index = topIndex;
        for (int step = 0; step < pills.length; step++) {
            index += dir;
            if (index < 0 || index >= pills.length) {
                return topIndex;
            }
            if (pills[index].getVisibility() == View.VISIBLE) {
                return index;
            }
        }
        return topIndex;
    }

    /** Keeps the focus on a button in the top row that is on screen. */
    private void keepTopVisible() {
        if (pills == null) {
            return;
        }
        if (topIndex < 0 || topIndex >= pills.length
                || pills[topIndex].getVisibility() != View.VISIBLE) {
            topIndex = firstVisiblePill();
        }
        if (topIndex < 0 && zone == TOP) {
            // Every button was hidden while the focus was up here. There is
            // nothing left to stand on, so the focus comes back to the dock.
            zone = DOCK;
        }
    }

    /** Grows the focused card and dims every other card. */
    private void updateCards() {
        for (int i = 0; i < dockTiles.size(); i++) {
            CardFocus.apply(dockTiles.get(i), zone == DOCK && i == dockIndex);
        }
    }

    private void fadeShelf(float target) {
        if (Math.abs(shelfTitle.getAlpha() - target) < 0.002f) {
            return;
        }
        shelfTitle.animate()
                .alpha(target)
                .setDuration(FADE_MS)
                .setInterpolator(FADE_EASING)
                .start();
    }

    private void fadeArrow(float target) {
        if (Math.abs(hintDown.getAlpha() - target) < 0.002f) {
            return;
        }
        hintDown.animate()
                .alpha(target)
                .setDuration(FADE_MS)
                .setInterpolator(FADE_EASING)
                .start();
    }

    private void fadeDock(float target) {
        if (dockBand == null) {
            return;
        }
        float targetY = (target == 0f) ? dp(160) : 0f;
        if (Math.abs(dockBand.getAlpha() - target) < 0.002f
                && Math.abs(dockBand.getTranslationY() - targetY) < 0.5f) {
            return;
        }
        dockBand.animate()
                .alpha(target)
                .translationY(targetY)
                .setDuration(Motion.DURATION)
                .setInterpolator(Motion.EASING)
                .start();
    }

    private void fadePills(float target) {
        if (pills == null) {
            return;
        }
        for (TextView pill : pills) {
            if (pill == null) {
                continue;
            }
            if (Math.abs(pill.getAlpha() - target) > 0.002f) {
                pill.animate()
                        .alpha(target)
                        .setDuration(Motion.DURATION)
                        .setInterpolator(Motion.EASING)
                        .start();
            }
        }
    }

    private void showHero(HomeApp app) {
        // The glow changes colour and drifts a little along with the focus.
        updateAmbient();
        if (app.label.equals(heroLabel)) {
            // The name is already right. It only has to come back if it was
            // faded out while the top row or the grid held the focus. A swap
            // cancelled mid-fade can leave the text shifted, so clear that too.
            shelfTitle.setTranslationY(0f);
            fadeShelf(1f);
            return;
        }
        heroLabel = app.label;
        final int token = ++heroToken;
        if (pendingHeroSwap != null) {
            handler.removeCallbacks(pendingHeroSwap);
        }
        shelfTitle.animate().cancel();
        // When the name is already invisible there is nothing to fade out, so
        // the new name can come straight in instead of blinking.
        boolean hidden = shelfTitle.getAlpha() < 0.05f;
        if (!hidden) {
            shelfTitle.animate()
                    .alpha(0f)
                    .translationY(-dp(6f))
                    .setDuration(80)
                    .setInterpolator(FADE_EASING)
                    .start();
        }
        pendingHeroSwap = new Runnable() {
            @Override
            public void run() {
                if (token != heroToken || isFinishing() || isDestroyed()) {
                    return;
                }
                shelfTitle.setText(heroLabel);
                shelfTitle.setTranslationY(dp(8f));
                shelfTitle.animate()
                        .alpha(zone == DOCK ? 1f : 0f)
                        .translationY(0f)
                        .setDuration(150)
                        .setInterpolator(FOCUS_EASING)
                        .start();
            }
        };
        handler.postDelayed(pendingHeroSwap, hidden ? 0 : 80);
    }

    /** The glow sits a little to the left of the first card and drifts right. */
    private float glowCentreFor(int index) {
        if (dockApps.size() <= 1) {
            return 0.30f;
        }
        float t = index / (float) (dockApps.size() - 1);
        return 0.30f + (0.65f - 0.30f) * t;
    }

    private void placeHighlight() {
        View target = null;
        float corner = dp(16);
        if (zone == TOP) {
            highlight.hide();
            return;
        } else if (zone == DOCK && dockIndex < dockTiles.size()) {
            target = dockTiles.get(dockIndex);
        } else if (zone == GRID && gridAdapter != null) {
            RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(gridIndex);
            if (holder == null) {
                highlight.setVisibility(View.INVISIBLE);
                if (highlightRetries < 8) {
                    highlightRetries++;
                    grid.post(this::placeHighlight);
                }
                return;
            }
            highlightRetries = 0;
            target = holder.itemView.findViewById(R.id.art);
        } else if (zone == MENU && menuIndex < inputRows.size()) {
            target = inputRows.get(menuIndex);
            corner = dp(10);
        }
        if (target == null || target.getWidth() < 2 || target.getHeight() < 2) {
            highlight.setVisibility(View.INVISIBLE);
            if (target != null) {
                target.post(this::placeHighlight);
            }
            return;
        }
        Rect rect = rectInRoot(target);
        highlight.setScale(target.getScaleX());
        if (ringJump) {
            ringJump = false;
            highlight.fadeTo(rect.left, rect.top, rect.width(), rect.height(), corner);
        } else {
            highlight.springTo(rect.left, rect.top, rect.width(), rect.height(), corner);
        }
    }

    /** Lights a menu row up, on the same curve the ring moves on. */
    private void fadeMark(int index, boolean on) {
        if (index < 0 || index >= inputMarks.size()) {
            return;
        }
        View mark = inputMarks.get(index);
        float target = on ? 1f : 0f;
        if (Math.abs(mark.getAlpha() - target) < 0.002f) {
            return;
        }
        ValueAnimator previous = inputMarkAnims.get(index);
        if (previous != null) {
            previous.cancel();
        }
        ValueAnimator anim = ValueAnimator.ofFloat(mark.getAlpha(), target);
        anim.setDuration(Motion.DURATION);
        anim.setInterpolator(Motion.EASING);
        final int row = index;
        anim.addUpdateListener(v -> mark.setAlpha((float) v.getAnimatedValue()));
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (inputMarkAnims.get(row) == animation) {
                    inputMarkAnims.set(row, null);
                }
            }
        });
        inputMarkAnims.set(index, anim);
        anim.start();
    }

    /**
     * A view's rect in the root's coordinates.
     *
     * The framework's offsetDescendantRectToMyCoords leaves out the
     * translation of a parent, and the input menu is moved with setX and
     * setY. That put the focus ring in the far corner of the screen instead
     * of on the menu row. The scale is left out on purpose, the ring draws
     * itself at the card's own scale.
     */
    private Rect rectInRoot(View target) {
        Rect rect = new Rect(0, 0, target.getWidth(), target.getHeight());
        float x = target.getLeft();
        float y = target.getTop();
        ViewParent parent = target.getParent();
        while (parent instanceof View && parent != root) {
            View view = (View) parent;
            x += view.getLeft() + view.getTranslationX() - view.getScrollX();
            y += view.getTop() + view.getTranslationY() - view.getScrollY();
            parent = view.getParent();
        }
        rect.offset(Math.round(x), Math.round(y));
        return rect;
    }

    /** The card that currently has the focus, if any. */
    private View focusedCardView() {
        if (zone == DOCK && dockIndex >= 0 && dockIndex < dockTiles.size()) {
            return dockTiles.get(dockIndex);
        }
        if (zone == GRID && gridAdapter != null) {
            RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(gridIndex);
            if (holder != null) {
                return holder.itemView.findViewById(R.id.art);
            }
        }
        return null;
    }

    /**
     * The ring is drawn at the card's own scale, so it has to follow the card
     * while the card grows. This runs for a few frames after every focus move.
     */
    private final Runnable focusTick = new Runnable() {
        @Override
        public void run() {
            View card = focusedCardView();
            if (card != null) {
                highlight.setScale(card.getScaleX());
            }
            placeHighlight();
            if (--focusTicksLeft > 0) {
                root.postOnAnimation(this);
            }
        }
    };

    private void startFocusTick() {
        focusTicksLeft = FOCUS_TICKS;
        root.removeCallbacks(focusTick);
        root.postOnAnimation(focusTick);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        if (key == KeyEvent.KEYCODE_BACK && handleClockBack(event)) {
            return true;
        }
        if (handleTclHomeShortcut(event)) {
            return true;
        }
        if (dockMoveOrigin >= 0 && isNav(key)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                handleDockMoveKey(key);
            }
            return true;
        }
        if (isActivateKey(key)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.isLongPress()) {
                handler.removeCallbacks(longPress);
                centerWasLong = true;
                showAppOptions();
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                centerDown = true;
                centerWasLong = false;
                handler.removeCallbacks(longPress);
                handler.postDelayed(longPress, android.view.ViewConfiguration.getLongPressTimeout());
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {
                handler.removeCallbacks(longPress);
                boolean wasLong = centerWasLong;
                boolean wasDown = centerDown;
                centerDown = false;
                centerWasLong = false;
                if (!wasLong && wasDown && !event.isCanceled()) {
                    activate();
                }
                return true;
            }
            return true;
        }
        if (!isNav(key)) {
            return super.dispatchKeyEvent(event);
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            onNav(key);
        }
        return true;
    }

    private boolean handleClockBack(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            clockBackDown = dockMoveOrigin < 0 && (zone == DOCK || zone == TOP)
                    && ClockPreferences.opensOnBack(this);
        }
        if (!clockBackDown) {
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_UP) {
            clockBackDown = false;
            if (!event.isCanceled() && dockMoveOrigin < 0 && (zone == DOCK || zone == TOP)) {
                startActivity(new Intent(this, ClockScreenActivity.class));
            }
        }
        return true;
    }

    private boolean handleTclHomeShortcut(KeyEvent event) {
        if (!"G10".equalsIgnoreCase(Build.DEVICE) || dockMoveOrigin >= 0
                || (zone != DOCK && zone != TOP)) {
            return false;
        }
        int key = event.getKeyCode();
        if (key != TCL_G10_NETFLIX_KEY && key != TCL_G10_YOUTUBE_KEY
                && key != KeyEvent.KEYCODE_NOTIFICATION) {
            return false;
        }
        if (event.getAction() != KeyEvent.ACTION_UP || event.isCanceled()) {
            return true;
        }
        if (key == KeyEvent.KEYCODE_NOTIFICATION) {
            openSettings(Settings.ACTION_SETTINGS);
            return true;
        }
        String pkg = key == TCL_G10_NETFLIX_KEY
                ? "com.netflix.ninja" : "io.gh.reisxd.tizentube.cobalt";
        for (HomeApp app : allApps) {
            if (pkg.equals(app.packageName)) {
                launch(app);
                return true;
            }
        }
        if (key == TCL_G10_YOUTUBE_KEY) {
            for (HomeApp app : allApps) {
                if ("com.google.android.youtube.tv".equals(app.packageName)) {
                    launch(app);
                    return true;
                }
            }
        }
        Toast.makeText(this, key == TCL_G10_NETFLIX_KEY ? "Netflix is unavailable"
                : "YouTube is unavailable", Toast.LENGTH_SHORT).show();
        return true;
    }

    private static boolean isActivateKey(int key) {
        return key == KeyEvent.KEYCODE_DPAD_CENTER
                || key == KeyEvent.KEYCODE_ENTER
                || key == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    private static boolean isNav(int key) {
        return key == KeyEvent.KEYCODE_DPAD_UP
                || key == KeyEvent.KEYCODE_DPAD_DOWN
                || key == KeyEvent.KEYCODE_DPAD_LEFT
                || key == KeyEvent.KEYCODE_DPAD_RIGHT
                || key == KeyEvent.KEYCODE_DPAD_CENTER
                || key == KeyEvent.KEYCODE_ENTER
                || key == KeyEvent.KEYCODE_NUMPAD_ENTER
                || key == KeyEvent.KEYCODE_BACK;
    }

    private void onNav(int key) {
        int before = focusSignature();
        if (key == KeyEvent.KEYCODE_BACK) {
            if (zone == MENU) {
                closeMenu();
            } else if (zone == GRID) {
                zone = DOCK;
            }
            renderZone();
            if (focusSignature() != before) {
                focusSound.play();
            }
            return;
        }
        if (key == KeyEvent.KEYCODE_DPAD_LEFT) {
            moveHorizontal(-1);
        } else if (key == KeyEvent.KEYCODE_DPAD_RIGHT) {
            moveHorizontal(1);
        } else if (key == KeyEvent.KEYCODE_DPAD_UP) {
            moveVertical(-1);
        } else if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
            moveVertical(1);
        }
        renderZone();
        if (focusSignature() != before) {
            focusSound.play();
        }
    }

    /** Where the focus is, as one number. Two different values mean it moved. */
    private int focusSignature() {
        if (zone == TOP) {
            return 100 + topIndex;
        }
        if (zone == DOCK) {
            return 200 + dockIndex;
        }
        if (zone == GRID) {
            return 300 + gridIndex;
        }
        if (zone == MENU) {
            return 400 + menuIndex;
        }
        return 0;
    }

    private void moveHorizontal(int dir) {
        if (zone == TOP) {
            topIndex = stepTop(dir);
        } else if (zone == DOCK) {
            if (dockIndex == 0 && dir < 0) {
                // Off the left end of the dock opens the launcher's settings.
                openLauncherSettings();
                return;
            }
            dockIndex = clamp(dockIndex + dir, 0, Math.max(0, dockApps.size() - 1));
        } else if (zone == MENU) {
            return;
        } else if (zone == GRID && !gridApps.isEmpty()) {
            int rowStart = (gridIndex / COLS) * COLS;
            int rowEnd = Math.min(rowStart + COLS, gridApps.size()) - 1;
            gridIndex = clamp(gridIndex + dir, rowStart, rowEnd);
        }
    }

    private void moveVertical(int dir) {
        if (zone == MENU) {
            if (dir < 0 && menuIndex == 0) {
                if (!hasVisiblePill()) {
                    return;
                }
                zone = TOP;
                topIndex = menuPill;
                keepTopVisible();
                return;
            }
            menuIndex = clamp(menuIndex + dir, 0, Math.max(0, inputRows.size() - 1));
            return;
        }
        if (dir < 0) {
            if (zone == GRID) {
                if (gridIndex >= COLS) {
                    gridIndex -= COLS;
                } else {
                    zone = DOCK;
                }
            } else if (zone == DOCK) {
                // With every button hidden there is nothing above the dock,
                // so UP must stay on the dock instead of landing on an
                // invisible button and opening its menu with OK.
                if (!hasVisiblePill()) {
                    return;
                }
                zone = TOP;
                // The button under the focus may be one the user has hidden.
                keepTopVisible();
            }
            return;
        }
        if (zone == TOP) {
            zone = DOCK;
        } else if (zone == DOCK && !gridApps.isEmpty()) {
            zone = GRID;
            if (gridIndex >= gridApps.size()) {
                gridIndex = 0;
            }
        } else if (zone == GRID) {
            int next = gridIndex + COLS;
            if (next < gridApps.size()) {
                gridIndex = next;
                // The whole grid moves. The ring must not travel across the
                // screen, it has to come back where the focus is.
                gridScrolled = true;
            } else {
                // The last row is short, so there is nothing directly under
                // this one. The focus goes to the last card in that row
                // instead of staying where it is.
                int last = gridApps.size() - 1;
                if (last > gridIndex) {
                    gridIndex = last;
                    gridScrolled = true;
                }
            }
        }
    }

    private HomeApp focusedApp() {
        if (zone == DOCK && dockIndex >= 0 && dockIndex < dockApps.size()) {
            return dockApps.get(dockIndex);
        }
        if (zone == GRID && gridIndex >= 0 && gridIndex < gridApps.size()) {
            return gridApps.get(gridIndex);
        }
        return null;
    }

    private void handleDockMoveKey(int key) {
        if (key == KeyEvent.KEYCODE_BACK || isActivateKey(key)) {
            stopDockMove(isActivateKey(key));
            renderZone();
        } else if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
            int next = dockIndex + (key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
            if (next >= 0 && next < dockApps.size()) {
                moveDockCard(next);
                focusSound.play();
                renderZone();
            }
        }
    }

    private void moveDockCard(int next) {
        if (next == dockIndex) {
            return;
        }
        int previous = dockIndex;
        dockApps.add(next, dockApps.remove(previous));
        View tile = dockTiles.remove(previous);
        dockTiles.add(next, tile);
        dockRow.removeViewAt(previous);
        dockRow.addView(tile, next);
        if (previous == 0 || next == 0) {
            for (int i = 0; i < 2; i++) {
                View item = dockTiles.get(i);
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) item.getLayoutParams();
                params.leftMargin = i == 0 ? 0 : dp(dockApps.size() > 4 ? 17 : 16);
                item.setLayoutParams(params);
            }
        }
        dockIndex = next;
    }

    private void stopDockMove(boolean save) {
        if (dockMoveOrigin < 0) {
            return;
        }
        if (save) {
            // Preserve slots for any saved entries that are temporarily unavailable.
            List<String> packages = Dock.load(this);
            List<String> order = dockPackages();
            order.retainAll(packages);
            int index = 0;
            for (int i = 0; i < packages.size(); i++) {
                if (order.contains(packages.get(i))) {
                    packages.set(i, order.get(index++));
                }
            }
            Dock.save(this, packages);
        } else {
            moveDockCard(dockMoveOrigin);
        }
        dockMoveOrigin = -1;
    }

    private void changeDockMembership(HomeApp app, boolean remove) {
        List<String> packages = Dock.load(this);
        if (remove) {
            packages.remove(app.packageName);
        } else if (!packages.contains(app.packageName)) {
            packages.add(app.packageName);
        }
        Dock.save(this, packages);
        if (!remove) {
            zone = DOCK;
            dockIndex = dockApps.size();
        }
        applyApps(allApps);
    }

    private void showAppOptions() {
        HomeApp app = focusedApp();
        if (app == null) {
            return;
        }
        // Only ever one menu. A long press can arrive twice; the second one
        // must not close and reopen the same card.
        if (optionsDialog != null && optionsDialog.isShowing()) {
            return;
        }
        Dialog dialog = new Dialog(this, android.R.style.Theme_DeviceDefault_Dialog);
        optionsDialog = dialog;
        dialog.setContentView(R.layout.options_menu);
        if (dialog.getWindow() != null) {
            android.view.Window window = dialog.getWindow();
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            // A light scrim. The default one kills the colour of the screen
            // behind the menu.
            android.view.WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = 0.45f;
            window.setAttributes(params);
        }
        TextView title = dialog.findViewById(R.id.option_title);
        title.setText(app.label);
        title.setTypeface(medium);
        boolean inDock = zone == DOCK;
        TextView move = dialog.findViewById(R.id.option_move);
        TextView dockAction = dialog.findViewById(R.id.option_dock);
        move.setTypeface(text);
        dockAction.setTypeface(text);
        move.setVisibility(inDock && dockApps.size() > 1 ? View.VISIBLE : View.GONE);
        dockAction.setText(inDock ? "Remove from dock" : "Add to dock");
        move.setOnClickListener(v -> {
            dialog.dismiss();
            dockMoveOrigin = dockIndex;
            renderZone();
        });
        dockAction.setOnClickListener(v -> {
            dialog.dismiss();
            changeDockMembership(app, inDock);
        });
        TextView info = dialog.findViewById(R.id.option_info);
        TextView uninstall = dialog.findViewById(R.id.option_uninstall);
        TextView edit = dialog.findViewById(R.id.option_edit);
        info.setTypeface(text);
        uninstall.setTypeface(text);
        edit.setTypeface(text);
        info.setOnClickListener(v -> {
            dialog.dismiss();
            openAppInfo(app.packageName);
        });
        uninstall.setOnClickListener(v -> {
            dialog.dismiss();
            uninstallApp(app.packageName);
        });
        edit.setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(this, EditAppActivity.class);
            intent.putExtra(EditAppActivity.EXTRA_PACKAGE, app.packageName);
            startActivity(intent);
        });
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setGravity(Gravity.CENTER);
                dialog.getWindow().setLayout(dp(360), ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            (move.getVisibility() == View.VISIBLE ? move : dockAction).requestFocus();
            // The card comes up gently, the same curve as everything else.
            View card = dialog.findViewById(R.id.option_card);
            card.setAlpha(0f);
            card.setScaleX(0.97f);
            card.setScaleY(0.97f);
            card.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(Motion.DURATION)
                    .setInterpolator(Motion.EASING)
                    .start();
        });
        dialog.show();
    }

    private void openAppInfo(String packageName) {
        reloadOnResume = true;
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", packageName, null));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException ex) {
            reloadOnResume = false;
        }
    }

    private void uninstallApp(String packageName) {
        reloadOnResume = true;
        Intent intent = new Intent(Intent.ACTION_DELETE);
        intent.setData(Uri.parse("package:" + packageName));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException ex) {
            Intent fallback = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
            fallback.setData(Uri.parse("package:" + packageName));
            fallback.putExtra(Intent.EXTRA_RETURN_RESULT, true);
            try {
                startActivity(fallback);
            } catch (ActivityNotFoundException missing) {
                reloadOnResume = false;
            }
        }
    }

    private void reloadApps() {
        final int token = ++appLoadToken;
        new Thread(() -> {
            List<HomeApp> all = AppLoader.load(this);
            runOnUiThread(() -> {
                // Two reloads can overlap when an install sends more than one
                // broadcast. Only the newest list may be applied, and never
                // to a screen that is already gone.
                if (token != appLoadToken || isFinishing() || isDestroyed()) {
                    return;
                }
                applyApps(all);
            });
        }, "home-reload").start();
    }

    private void activate() {
        if (zone == TOP) {
            // A hidden button has no border to show. If one ever holds the
            // focus, OK must not open its menu: take the focus back down.
            if (topIndex < 0 || topIndex >= pills.length
                    || pills[topIndex].getVisibility() != View.VISIBLE) {
                topIndex = firstVisiblePill();
                if (topIndex < 0) {
                    zone = DOCK;
                }
                renderZone();
                return;
            }
            if (topIndex == 0) {
                openInputsMenu();
            } else if (topIndex == 1) {
                openSettings(Settings.ACTION_WIFI_SETTINGS);
            } else {
                openSettings(Settings.ACTION_SETTINGS);
            }
            return;
        }
        if (zone == DOCK && appsReady && dockApps.isEmpty()) {
            openLauncherSettings();
            return;
        }
        if (zone == DOCK && dockIndex < dockApps.size()) {
            launch(dockApps.get(dockIndex));
            return;
        }
        if (zone == GRID && gridIndex < gridApps.size()) {
            launch(gridApps.get(gridIndex));
            return;
        }
        if (zone == MENU && menuIndex < inputs.size()) {
            switchInput(inputs.get(menuIndex));
            closeMenu();
        }
    }

    /** The menu of the inputs, under the Inputs button. */
    private void openInputsMenu() {
        if (inputRows.isEmpty()) {
            return;
        }
        menuPill = 0;
        openMenuPanel();
    }

    private void openMenuPanel() {
        zone = MENU;
        menuIndex = 0;
        // Put the menu where it belongs before it is shown. If it is shown
        // first it sits in the top left corner of the screen for a frame and
        // then jumps under its button.
        positionMenu();
        inputMenu.setVisibility(View.VISIBLE);
        renderZone();
    }

    /** Takes the ring back to the button the menu came out of. */
    private void closeMenu() {
        zone = TOP;
        topIndex = menuPill;
        keepTopVisible();
        renderZone();
    }

    private void positionMenu() {
        int width = dp(240);
        if (inputMenu.getLayoutParams().width != width) {
            inputMenu.getLayoutParams().width = width;
            inputMenu.requestLayout();
        }
        int[] pill = new int[2];
        int[] base = new int[2];
        pills[menuPill].getLocationInWindow(pill);
        root.getLocationInWindow(base);
        float x = pill[0] - base[0];
        float limit = root.getWidth() - width - dp(16);
        if (x > limit) {
            x = limit;
        }
        if (x < dp(16)) {
            x = dp(16);
        }
        inputMenu.setX(x);
        inputMenu.setY(pill[1] - base[1] + pills[menuPill].getHeight() + dp(8));
    }

    private void launch(HomeApp app) {
        try {
            startActivity(app.launch);
        } catch (RuntimeException ignored) {
            Intent fallback = getPackageManager().getLaunchIntentForPackage(app.packageName);
            if (fallback != null) {
                startActivity(fallback);
            }
        }
    }

    private void openSettings(String action) {
        try {
            startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException ex) {
            if (!Settings.ACTION_SETTINGS.equals(action)) {
                openSettings(Settings.ACTION_SETTINGS);
            }
        }
    }

    private void switchInput(TvInputInfo info) {
        try {
            if (info.getType() == TvInputInfo.TYPE_TUNER) {
                Intent live = getPackageManager().getLeanbackLaunchIntentForPackage("com.tcl.tv");
                if (live == null) {
                    live = getPackageManager().getLaunchIntentForPackage("com.tcl.tv");
                }
                if (live != null) {
                    startActivity(live);
                    return;
                }
            }
            Intent view = new Intent(Intent.ACTION_VIEW,
                    TvContract.buildChannelUriForPassthroughInput(info.getId()));
            view.setPackage("com.tcl.tv");
            view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(view);
        } catch (RuntimeException ex) {
            Intent view = new Intent(Intent.ACTION_VIEW,
                    TvContract.buildChannelUriForPassthroughInput(info.getId()));
            view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(view);
            } catch (RuntimeException ignored) {
                Intent live = getPackageManager().getLaunchIntentForPackage("com.tcl.tv");
                if (live != null) {
                    startActivity(live);
                }
            }
        }
    }

    private List<TvInputInfo> loadInputs() {
        List<TvInputInfo> found = new ArrayList<>();
        TvInputManager manager = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (manager == null) {
            return found;
        }
        boolean tunerAdded = false;
        for (TvInputInfo info : manager.getTvInputList()) {
            if (info == null || info.isHidden(this)) {
                continue;
            }
            int type = info.getType();
            if (type == TvInputInfo.TYPE_TUNER) {
                if (!tunerAdded) {
                    found.add(info);
                    tunerAdded = true;
                }
                continue;
            }
            if (type == TvInputInfo.TYPE_HDMI
                    || type == TvInputInfo.TYPE_COMPOSITE
                    || type == TvInputInfo.TYPE_COMPONENT
                    || type == TvInputInfo.TYPE_SVIDEO
                    || type == TvInputInfo.TYPE_SCART
                    || type == TvInputInfo.TYPE_VGA
                    || type == TvInputInfo.TYPE_DVI
                    || type == TvInputInfo.TYPE_DISPLAY_PORT) {
                found.add(info);
            }
        }
        return found;
    }

    private void showInputs(List<TvInputInfo> found) {
        inputs.clear();
        inputs.addAll(found);
        inputMenu.removeAllViews();
        inputRows.clear();
        inputMarks.clear();
        inputMarkAnims.clear();
        if (found.isEmpty()) {
            View empty = menuRow("No inputs");
            ((TextView) empty.getTag()).setAlpha(0.6f);
            inputMenu.addView(empty);
            inputRows.add(empty);
            refreshOpenMenu();
            return;
        }
        for (TvInputInfo info : found) {
            View row = menuRow(inputLabel(info));
            inputMenu.addView(row);
            inputRows.add(row);
        }
        refreshOpenMenu();
    }

    /** Keeps an open menu in step when its rows are rebuilt behind it. */
    private void refreshOpenMenu() {
        if (zone != MENU) {
            return;
        }
        menuIndex = clamp(menuIndex, 0, Math.max(0, inputRows.size() - 1));
        positionMenu();
        renderZone();
    }

    /**
     * One row of a menu.
     *
     * The light colour behind the text is a view of its own, so it can fade
     * in and out without taking the text with it.
     */
    private View menuRow(String label) {
        FrameLayout row = new FrameLayout(this);
        // A fixed height, not wrap_content. A wrapping row hands the light
        // band a height of zero (it is measured before the row knows how tall
        // it is), so the row was lit only by the ring and the band never
        // showed at all.
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));
        MarkView mark = new MarkView(this);
        mark.setCorner(dp(10f));
        mark.setAlpha(0f);
        row.addView(mark, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        inputMarks.add(mark);
        inputMarkAnims.add(null);

        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(dp(10), dp(7), dp(10), dp(7));

        TextView caption = new TextView(this);
        caption.setText(label);
        caption.setTextColor(0xFFF2F2F2);
        caption.setTextSize(15f);
        caption.setTypeface(text);
        caption.setSingleLine(true);
        caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
        line.addView(caption, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        row.addView(line, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL));
        row.setTag(caption);
        return row;
    }

    /**
     * The light colour behind a menu row.
     *
     * It is a view of its own so it can fade on its own. A plain view would
     * claim the whole height when it is measured, and the row would grow to
     * the size of the screen, so it reports no height until the row has been
     * measured and told exactly how tall it is.
     */
    private static final class MarkView extends View {
        private final android.graphics.Paint paint =
                new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private float corner;

        MarkView(android.content.Context context) {
            super(context);
        }

        void setCorner(float value) {
            corner = value;
            invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int height = android.view.View.MeasureSpec.getMode(heightMeasureSpec)
                    == android.view.View.MeasureSpec.EXACTLY
                    ? android.view.View.MeasureSpec.getSize(heightMeasureSpec)
                    : 0;
            setMeasuredDimension(
                    android.view.View.MeasureSpec.getSize(widthMeasureSpec), height);
        }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            paint.setColor(0x33FFFFFF);
            canvas.drawRoundRect(0, 0, getWidth(), getHeight(), corner, corner, paint);
        }
    }

    private String inputLabel(TvInputInfo info) {
        CharSequence custom = info.loadCustomLabel(this);
        if (custom != null && custom.length() > 0) {
            return custom.toString();
        }
        CharSequence label = info.loadLabel(this);
        if (label != null && label.length() > 0) {
            return label.toString();
        }
        if (info.getType() == TvInputInfo.TYPE_TUNER) {
            return "Live TV";
        }
        if (info.getType() == TvInputInfo.TYPE_HDMI) {
            return "HDMI";
        }
        return "AV";
    }

    /**
     * The name of the network, or nothing when it cannot be read.
     *
     * Android 12 only hands over the SSID to apps that hold location access,
     * and it never gives it through `NetworkCapabilities` at all, so both
     * doors are tried.
     */
    private String readSsid() {
        String raw = null;
        boolean log = Log.isLoggable(SSID_TAG, Log.DEBUG);
        try {
            if (log) {
                boolean granted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;
                Log.d(SSID_TAG, "location permission granted=" + granted);
            }
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            Network network = cm == null ? null : cm.getActiveNetwork();
            if (network != null) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                if (caps != null && caps.getTransportInfo() instanceof WifiInfo) {
                    raw = ((WifiInfo) caps.getTransportInfo()).getSSID();
                    if (log) {
                        Log.d(SSID_TAG, "from capabilities: " + raw);
                    }
                    String ssid = cleanSsid(raw);
                    if (ssid != null) {
                        return ssid;
                    }
                } else if (log) {
                    Log.d(SSID_TAG, "capabilities have no Wi-Fi transport");
                }
            } else if (log) {
                Log.d(SSID_TAG, "no active network");
            }
            WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wifi != null && wifi.getConnectionInfo() != null) {
                raw = wifi.getConnectionInfo().getSSID();
                if (log) {
                    Log.d(SSID_TAG, "from WifiManager: " + raw);
                }
                String ssid = cleanSsid(raw);
                if (ssid != null) {
                    return ssid;
                }
            }
        } catch (RuntimeException e) {
            Log.d(SSID_TAG, "reading the SSID failed", e);
        }
        return null;
    }

    private static String cleanSsid(String raw) {
        if (raw == null) {
            return null;
        }
        String ssid = raw.replace("\"", "");
        if (ssid.isEmpty() || "<unknown ssid>".equalsIgnoreCase(ssid)) {
            return null;
        }
        return ssid;
    }

    private void renderClock() {
        Date now = new Date();
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        String newGreeting = hour < 12 ? "Good morning"
                : hour < 18 ? "Good afternoon" : "Good evening";
        if (!newGreeting.contentEquals(greeting.getText())) {
            greeting.setText(newGreeting);
        }
        boolean hour24 = ClockPreferences.is24Hour(this);
        String newClock = new SimpleDateFormat(hour24 ? "H:mm" : "h:mm",
                Locale.getDefault()).format(now);
        // setText re-measures even when the words are the same. The clock is
        // the largest text on the screen, so this is the difference between a
        // quiet screen and a small hitch every second.
        if (!newClock.contentEquals(clock.getText())) {
            clock.setText(newClock);
        }
        if (hour24) {
            ampm.setVisibility(View.GONE);
        } else {
            ampm.setVisibility(View.VISIBLE);
            String newAmPm = new SimpleDateFormat("a", Locale.getDefault()).format(now);
            if (!newAmPm.contentEquals(ampm.getText())) {
                ampm.setText(newAmPm);
            }
        }
        String newDate = new SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(now);
        if (!newDate.contentEquals(dateView.getText())) {
            dateView.setText(newDate);
        }
    }

    private void loadWallpaper() {
        File dir = getExternalFilesDir(null);
        if (dir == null) {
            return;
        }
        File file = new File(dir, "wallpaper.jpg");
        if (!file.isFile()) {
            file = new File(dir, "wallpaper.png");
        }
        if (!file.isFile()) {
            return;
        }
        final File chosen = file;
        // Decoding a full picture belongs on a worker. On the main thread it
        // held the first frame back for as long as the picture took to read.
        new Thread(() -> {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(chosen.getAbsolutePath(), bounds);
            int sample = 1;
            while (bounds.outWidth / sample > 1280 && sample < 16) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bitmap = BitmapFactory.decodeFile(chosen.getAbsolutePath(), opts);
            if (bitmap == null) {
                return;
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                ImageView wallpaper = findViewById(R.id.wallpaper);
                wallpaper.setImageBitmap(bitmap);
                wallpaper.setVisibility(View.VISIBLE);
            });
        }, "wallpaper").start();
    }

    private void restoreFocus() {
        focusFirstDockApp();
    }

    private SharedPreferences getPrefs() {
        return getSharedPreferences("home", MODE_PRIVATE);
    }

    private Typeface outfit(int weight) {
        try {
            return new Typeface.Builder(getAssets(), "fonts/outfit.ttf")
                    .setFontVariationSettings("'wght' " + weight)
                    .build();
        } catch (RuntimeException ex) {
            return Typeface.create("sans-serif-light", Typeface.NORMAL);
        }
    }

    private void round(View view, float fraction) {
        CardArtView.round(view, fraction);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
