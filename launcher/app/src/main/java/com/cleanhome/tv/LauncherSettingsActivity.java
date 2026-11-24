package com.cleanhome.tv;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The launcher's own settings.
 *
 * The dock can be changed here, the three buttons in the top right corner can
 * be hidden, and a few other things can be turned on and off.
 */
public class LauncherSettingsActivity extends Activity {
    private static final int DOCK_CARD_WIDTH = 120;
    private static final int DOCK_CARD_HEIGHT = 68;
    private static final int DOCK_CARD_GAP = 16;
    private static final int DOCK_COLUMNS = 4;
    private static final int DOCK_CARD_RADIUS = 16;
    private static final int PICK_APP = 31;

    /**
     * The four sections of the settings screen.
     *
     * <p>Each value owns the words and the content view that belong to it, so
     * the sidebar, the heading and the visible panel cannot drift apart. The
     * old code kept the names in one array and the descriptions in two
     * different arrays; the Buttons description had already drifted between
     * them.</p>
     */
    private enum Section {
        DOCK("Dock", "The apps on the home screen", "\u25A6", R.id.section_dock),
        BUTTONS("Buttons", "The three buttons in the top right corner", "\u25C9", R.id.section_buttons),
        CLOCK("Clock", "Time format and screensaver", "\u25F7", R.id.section_clock),
        MORE("More", "Reset and close", "\u2699", R.id.section_more);

        final String title;
        final String description;
        final String icon;
        final int contentId;

        Section(String title, String description, String icon, int contentId) {
            this.title = title;
            this.description = description;
            this.icon = icon;
            this.contentId = contentId;
        }
    }

    /**
     * What the app picker was opened for: add a new card, or replace the card
     * at {@code index}. The old code used {@code pendingIndex = -1} to mean
     * "add", which made a cancelled pick and a missing result indistinguishable
     * from a real request.
     */
    private static final class PickRequest {
        final boolean replace;
        final int index;

        private PickRequest(boolean replace, int index) {
            this.replace = replace;
            this.index = index;
        }

        static PickRequest add() {
            return new PickRequest(false, -1);
        }

        static PickRequest replace(int index) {
            return new PickRequest(true, index);
        }
    }

    private final List<String> dock = new ArrayList<>();
    private final List<View> dockCards = new ArrayList<>();
    private final List<View> dockArts = new ArrayList<>();
    private LinearLayout sidebar;
    private GridLayout dockRow;
    private LinearLayout buttonsBox;
    private LinearLayout moreBox;
    private TextView title;
    private TextView subtitle;
    private FocusRing ring;
    private PickRequest pendingPick;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        setContentView(R.layout.activity_launcher_settings);
        sidebar = findViewById(R.id.settings_tabs);
        dockRow = findViewById(R.id.dock_row);
        buttonsBox = findViewById(R.id.section_buttons);
        moreBox = findViewById(R.id.section_more);
        title = findViewById(R.id.settings_title);
        subtitle = findViewById(R.id.settings_subtitle);
        FrameLayout settingsRoot = findViewById(R.id.settings_root);
        ring = new FocusRing(settingsRoot);
        ((TextView) findViewById(R.id.settings_kicker)).setTypeface(outfit(500));
        title.setTypeface(outfit(600));
        subtitle.setTypeface(outfit(420));
        dock.clear();
        dock.addAll(Dock.loadAvailable(this));
        buildSidebar();
        buildDock();
        settingsRoot.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            if (newFocus != null) {
                int radius = newFocus.getParent() == sidebar ? 12 : DOCK_CARD_RADIUS;
                ring.moveTo(newFocus, dp(radius));
            } else {
                ring.hide();
            }
        });
        buildButtons();
        buildMore();
        buildClockSettings();
        showSection(Section.DOCK);
    }

    // ---------------------------------------------------------------- sidebar

    private void buildSidebar() {
        for (Section section : Section.values()) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setBackgroundResource(R.drawable.settings_sidebar_card);
            item.setFocusable(true);
            item.setDefaultFocusHighlightEnabled(false);
            item.setPadding(dp(16), dp(14), dp(16), dp(14));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(6);
            item.setLayoutParams(params);

            TextView icon = new TextView(this);
            icon.setText(section.icon);
            icon.setTextSize(18f);
            icon.setTextColor(0xCCFFFFFF);
            icon.setTypeface(outfit(400));
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                    dp(28), LinearLayout.LayoutParams.WRAP_CONTENT);
            item.addView(icon, iconParams);

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setPadding(dp(4), 0, 0, 0);

            TextView name = new TextView(this);
            name.setText(section.title);
            name.setTextSize(15f);
            name.setTypeface(outfit(500));
            name.setTextColor(0xFFFFFFFF);
            textCol.addView(name);

            TextView desc = new TextView(this);
            desc.setText(section.description);
            desc.setTextSize(11f);
            desc.setTypeface(outfit(400));
            desc.setTextColor(0xADFFFFFF);
            textCol.addView(desc);

            item.addView(textCol, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            final Section target = section;
            item.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    showSection(target);
                }
            });
            sidebar.addView(item);
        }
    }

    private void showSection(Section section) {
        findViewById(R.id.settings_dock_hint).setVisibility(
                section == Section.DOCK ? View.VISIBLE : View.INVISIBLE);
        for (Section candidate : Section.values()) {
            findViewById(candidate.contentId).setVisibility(
                    candidate == section ? View.VISIBLE : View.GONE);
        }
        title.setText(section.title);
        subtitle.setText(section.description);
    }

    // ---------------------------------------------------------------- dock

    private void buildDock() {
        dockRow.removeAllViews();
        dockCards.clear();
        dockArts.clear();
        for (int i = 0; i < dock.size(); i++) {
            addDockCard(i);
        }
        // A card to put a new app in.
        FrameLayout add = new FrameLayout(this);
        add.setBackgroundResource(R.drawable.input_card);
        add.setFocusable(true);
        add.setLayoutParams(dockCell(dock.size(), dp(DOCK_CARD_HEIGHT)));
        add.setOnClickListener(v -> pickApp(PickRequest.add()));

        TextView addLabel = new TextView(this);
        addLabel.setText("+ Add app");
        addLabel.setTextSize(14f);
        addLabel.setTypeface(outfit(500));
        addLabel.setTextColor(0xE6FFFFFF);
        addLabel.setGravity(Gravity.CENTER);
        add.addView(addLabel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        dockRow.addView(add);
        View firstCard = dockArts.isEmpty() ? add : dockArts.get(0);
        firstCard.setId(View.generateViewId());
        sidebar.getChildAt(Section.DOCK.ordinal()).setNextFocusRightId(firstCard.getId());
    }

    private GridLayout.LayoutParams dockCell(int index, int height) {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                GridLayout.spec(index / DOCK_COLUMNS, GridLayout.TOP), GridLayout.spec(index % DOCK_COLUMNS));
        params.width = dp(DOCK_CARD_WIDTH);
        params.height = height;
        params.rightMargin = index % DOCK_COLUMNS == DOCK_COLUMNS - 1 ? 0 : dp(DOCK_CARD_GAP);
        params.bottomMargin = dp(12);
        return params;
    }

    private void addDockCard(int index) {
        String pkg = dock.get(index);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setLayoutParams(dockCell(index, ViewGroup.LayoutParams.WRAP_CONTENT));

        CardArtView art = new CardArtView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        CardArtView.round(art, (float) DOCK_CARD_RADIUS / DOCK_CARD_WIDTH);
        art.setFocusable(true);
        Bitmap custom = AppStyles.loadCustomArt(this, pkg, 320);
        art.setImageBitmap(custom != null ? custom : artOf(pkg));
        art.setContentDescription(appLabel(pkg));
        card.addView(art, new LinearLayout.LayoutParams(dp(DOCK_CARD_WIDTH), dp(DOCK_CARD_HEIGHT)));

        art.setOnFocusChangeListener((view, focused) -> {
            view.animate().cancel();
            CardFocus.apply(view, focused);
        });
        art.setOnClickListener(v -> pickApp(PickRequest.replace(index)));
        art.setOnLongClickListener(v -> {
            showDockMenu(index);
            return true;
        });

        // Keep the label clear of both the banner artwork and the focus ring.
        TextView label = new TextView(this);
        label.setText(appLabel(pkg));
        label.setTextSize(10f);
        label.setTypeface(outfit(400));
        label.setTextColor(0xBBFFFFFF);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(dp(DOCK_CARD_WIDTH), dp(18));
        labelParams.topMargin = dp(8);
        card.addView(label, labelParams);

        dockRow.addView(card);
        dockCards.add(card);
        dockArts.add(art);
    }

    /**
     * Rebuilds the row after an edit and gives the focus back to the card the
     * user was on. Rebuilding removes the view that held the focus, so without
     * this the screen is left with no focused card and a ring in mid air.
     */
    private void rebuildDock(int focusIndex) {
        buildDock();
        int index = Math.max(0, Math.min(focusIndex, dockArts.size() - 1));
        View card = dockArts.isEmpty() ? dockRow.getChildAt(0) : dockArts.get(index);
        if (card != null) {
            card.requestFocus();
        }
    }

    private Bitmap artOf(String pkg) {
        PackageManager pm = getPackageManager();
        Drawable art = null;
        try {
            Intent query = new Intent(Intent.ACTION_MAIN);
            query.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            query.setPackage(pkg);
            List<ResolveInfo> found = pm.queryIntentActivities(query, 0);
            if (!found.isEmpty() && found.get(0).activityInfo != null) {
                art = found.get(0).activityInfo.loadBanner(pm);
            }
            if (art == null) {
                art = pm.getApplicationBanner(pkg);
            }
            if (art == null) {
                art = pm.getApplicationIcon(pkg);
            }
        } catch (Exception ignored) {
        }
        return AppLoader.toBitmap(art, 320);
    }

    private String appLabel(String pkg) {
        String custom = AppStyles.label(this, pkg);
        if (custom != null) {
            return custom;
        }
        try {
            return getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (Exception ignored) {
            return pkg;
        }
    }

    /** Opens the app picker for a new card or for the card at the request's index. */
    private void pickApp(PickRequest request) {
        pendingPick = request;
        Intent intent = new Intent(this, IconPickerActivity.class);
        intent.putExtra(IconPickerActivity.EXTRA_PICK_APP, true);
        intent.putExtra(IconPickerActivity.EXTRA_REPLACE_APP, request.replace);
        startActivityForResult(intent, PICK_APP);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_APP) {
            return;
        }
        // Consume the request even when the picker was cancelled, so a later
        // result can never apply to an older slot.
        PickRequest request = pendingPick;
        pendingPick = null;
        if (resultCode != RESULT_OK || data == null || request == null) {
            return;
        }
        String pkg = data.getStringExtra(IconPickerActivity.EXTRA_PACKAGE);
        if (pkg == null || pkg.isEmpty()) {
            return;
        }
        int existing = dock.indexOf(pkg);
        if (existing >= 0) {
            rebuildDock(existing);
            return;
        }
        if (!request.replace || request.index < 0 || request.index >= dock.size()) {
            dock.add(pkg);
            Dock.save(this, dock);
            rebuildDock(dock.size() - 1);
        } else {
            dock.set(request.index, pkg);
            Dock.save(this, dock);
            rebuildDock(request.index);
        }
    }

    /** Move the app, take it out, all on one card. */
    private void showDockMenu(int index) {
        final Dialog dialog = new Dialog(this, android.R.style.Theme_DeviceDefault_Dialog);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.options_card);
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        if (index > 0) {
            addMenuRow(box, "Move left", () -> {
                String moved = dock.remove(index);
                dock.add(index - 1, moved);
                Dock.save(this, dock);
                rebuildDock(index - 1);
            });
        }
        if (index < dock.size() - 1) {
            addMenuRow(box, "Move right", () -> {
                String moved = dock.remove(index);
                dock.add(index + 1, moved);
                Dock.save(this, dock);
                rebuildDock(index + 1);
            });
        }
        addMenuRow(box, "Replace app", () -> pickApp(PickRequest.replace(index)));
        addMenuRow(box, "Remove from dock", () -> {
            dock.remove(index);
            Dock.save(this, dock);
            rebuildDock(index);
        });
        dialog.setContentView(box);
        currentDialog = dialog;
        dialog.setOnShowListener(d -> {
            // The box itself cannot take the focus; the first row can.
            View first = box.getChildAt(0);
            if (first != null) {
                first.requestFocus();
            }
        });
        dialog.setOnDismissListener(d -> {
            if (currentDialog == dialog) {
                currentDialog = null;
            }
        });
        dialog.show();
    }

    private void addMenuRow(LinearLayout box, String label, Runnable action) {
        TextView row = new TextView(this);
        row.setText(label);
        row.setTextSize(16f);
        row.setTypeface(outfit(400));
        row.setTextColor(0xFFFFFFFF);
        row.setBackgroundResource(R.drawable.option_row);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(12), dp(18), dp(12));
        row.setFocusable(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(6);
        row.setLayoutParams(params);
        row.setOnClickListener(v -> {
            dialogDismiss();
            action.run();
        });
        box.addView(row);
    }

    private Dialog currentDialog;

    private void dialogDismiss() {
        if (currentDialog != null) {
            currentDialog.dismiss();
            currentDialog = null;
        }
    }

    // ------------------------------------------------------------- buttons

    private void buildButtons() {
        buttonsBox.removeAllViews();

        // A small heading above the list.
        TextView heading = new TextView(this);
        heading.setText("TOP RIGHT BUTTONS");
        heading.setTextSize(11f);
        heading.setTypeface(outfit(500));
        heading.setTextColor(0x99FFFFFF);
        heading.setLetterSpacing(0.18f);
        LinearLayout.LayoutParams headParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        headParams.bottomMargin = dp(12);
        buttonsBox.addView(heading, headParams);

        addToggle(buttonsBox, "Inputs", "The button that opens the inputs menu",
                R.drawable.ic_input, Dock.BUTTON_INPUTS);
        addToggle(buttonsBox, "Wi-Fi", "Shows the name of the network",
                R.drawable.ic_wifi, Dock.BUTTON_WIFI);
        addToggle(buttonsBox, "Settings", "Opens the Android settings",
                R.drawable.ic_settings, Dock.BUTTON_SETTINGS);
        linkSectionRows(Section.BUTTONS, buttonsBox);
    }

    private void linkSectionRows(Section section, LinearLayout rows) {
        View tab = sidebar.getChildAt(section.ordinal());
        if (tab.getId() == View.NO_ID) {
            tab.setId(View.generateViewId());
        }
        boolean first = true;
        for (int i = 0; i < rows.getChildCount(); i++) {
            View row = rows.getChildAt(i);
            if (!row.isFocusable()) {
                continue;
            }
            row.setNextFocusLeftId(tab.getId());
            if (first) {
                row.setId(View.generateViewId());
                tab.setNextFocusRightId(row.getId());
                first = false;
            }
        }
    }

    private void addToggle(LinearLayout box, String label, String description,
            int iconRes, String key) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.settings_detail_card);
        row.setFocusable(true);
        row.setDefaultFocusHighlightEnabled(false);
        row.setPadding(dp(20), dp(16), dp(20), dp(16));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(10);
        row.setLayoutParams(params);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(0xCCFFFFFF);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(22), dp(22));
        iconParams.rightMargin = dp(16);
        row.addView(icon, iconParams);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextSize(16f);
        name.setTypeface(outfit(500));
        name.setTextColor(0xFFFFFFFF);
        textCol.addView(name);

        TextView desc = new TextView(this);
        desc.setText(description);
        desc.setTextSize(12f);
        desc.setTypeface(outfit(400));
        desc.setTextColor(0xADFFFFFF);
        textCol.addView(desc);

        row.addView(textCol, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // A small pill that shows the state, like a switch.
        TextView state = new TextView(this);
        state.setTextSize(11f);
        state.setTypeface(outfit(500));
        state.setLetterSpacing(0.1f);
        state.setGravity(Gravity.CENTER);
        state.setPadding(dp(12), dp(6), dp(12), dp(6));
        LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        row.addView(state, stateParams);

        Runnable refresh = () -> {
            boolean hidden = Dock.isHidden(this, key);
            state.setText(hidden ? "HIDDEN" : "SHOWN");
            state.setBackgroundResource(hidden ? R.drawable.pill_bg : R.drawable.toggle_on);
            state.setTextColor(hidden ? 0xADFFFFFF : 0xFF0A0A0C);
        };
        refresh.run();

        row.setOnClickListener(v -> {
            boolean hidden = !Dock.isHidden(this, key);
            Dock.setHidden(this, key, hidden);
            refresh.run();
        });

        box.addView(row);
    }

    // ---------------------------------------------------------------- more

    private void buildMore() {
        moreBox.removeAllViews();

        TextView heading = new TextView(this);
        heading.setText("RESET");
        heading.setTextSize(11f);
        heading.setTypeface(outfit(500));
        heading.setTextColor(0x99FFFFFF);
        heading.setLetterSpacing(0.18f);
        LinearLayout.LayoutParams headParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        headParams.bottomMargin = dp(12);
        moreBox.addView(heading, headParams);

        addAction(moreBox, "Bring the dock back to how it was",
                "Puts the default apps back in the dock.",
                () -> {
                    Dock.save(this, new ArrayList<>(java.util.Arrays.asList(Dock.DEFAULT)));
                    dock.clear();
                    dock.addAll(Dock.loadAvailable(this));
                    buildDock();
                });
        addAction(moreBox, "Show the dock bar again",
                "Brings the three buttons back if they were hidden.",
                () -> {
                    Dock.setHidden(this, Dock.BUTTON_INPUTS, false);
                    Dock.setHidden(this, Dock.BUTTON_WIFI, false);
                    Dock.setHidden(this, Dock.BUTTON_SETTINGS, false);
                    buildButtons();
                });
        addAction(moreBox, "Close settings",
                "Goes back to the home screen.",
                () -> finish());
        linkSectionRows(Section.MORE, moreBox);
    }

    private void buildClockSettings() {
        LinearLayout box = findViewById(R.id.section_clock);
        addAction(box, "Time format", ClockPreferences.formatLabel(this), description -> {
            ClockPreferences.cycleFormat(this);
            description.setText(ClockPreferences.formatLabel(this));
        });
        addAction(box, "Back opens screensaver", clockShortcutLabel(), description -> {
            ClockPreferences.setOpensOnBack(this, !ClockPreferences.opensOnBack(this));
            description.setText(clockShortcutLabel());
        });
        addAction(box, "Screensaver background", backgroundLabel(), description -> {
            ClockPreferences.cycleBackground(this);
            description.setText(backgroundLabel());
        });
        addAction(box, "Preview screensaver", "OK greets the fox · Left/Right previews all 24 hours in Meadow · Back exits",
                () -> startActivity(new Intent(this, ClockScreenActivity.class)));
        linkSectionRows(Section.CLOCK, box);
    }

    private String backgroundLabel() {
        switch (ClockPreferences.background(this)) {
            case MEADOW: return "Fox Meadow · Illustrated landscape";
            case EARTH: return "Daily Earth · NASA satellite photography";
            case DUSK: return "Dusk · Ambient gradient";
            default: return "Ambient · Minimal Fox";
        }
    }

    private String clockShortcutLabel() {
        return ClockPreferences.opensOnBack(this) ? "On · From Home" : "Off";
    }

    private void addAction(LinearLayout box, String label, String description, Runnable action) {
        addAction(box, label, description, ignored -> action.run());
    }

    private void addAction(LinearLayout box, String label, String description,
            java.util.function.Consumer<TextView> action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.settings_detail_card);
        row.setFocusable(true);
        row.setDefaultFocusHighlightEnabled(false);
        row.setPadding(dp(20), dp(16), dp(20), dp(16));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(10);
        row.setLayoutParams(params);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextSize(16f);
        name.setTypeface(outfit(500));
        name.setTextColor(0xFFFFFFFF);
        textCol.addView(name);

        TextView desc = new TextView(this);
        desc.setText(description);
        desc.setTextSize(12f);
        desc.setTypeface(outfit(400));
        desc.setTextColor(0xADFFFFFF);
        textCol.addView(desc);

        row.addView(textCol, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView arrow = new TextView(this);
        arrow.setText("\u203A");
        arrow.setTextSize(22f);
        arrow.setTypeface(outfit(300));
        arrow.setTextColor(0x99FFFFFF);
        row.addView(arrow);

        row.setOnClickListener(v -> action.accept(desc));

        box.addView(row);
    }

    // ---------------------------------------------------------------- keys

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        int key = event.getKeyCode();
        if (key == android.view.KeyEvent.KEYCODE_BACK) {
            // Act on the release only. Acting on the press as well let one
            // physical Back dismiss a dialog and finish this screen on the way
            // up, before the owner could press again.
            if (event.getAction() != android.view.KeyEvent.ACTION_UP || event.isCanceled()) {
                return true;
            }
            // A dialog that is still up takes the first Back. A dialog that
            // has already closed itself must not leave this screen stuck.
            if (currentDialog != null && currentDialog.isShowing()) {
                dialogDismiss();
                return true;
            }
            finish();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDestroy() {
        dialogDismiss();
        pendingPick = null;
        super.onDestroy();
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

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
