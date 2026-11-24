# TV launcher handoff

This folder is a live job on a real TCL Android TV. Another agent should continue from here. Do not start over. Do not uninstall system packages. Do not disable the packages listed under Keep.

The user talks in Roman Urdu. Reply in clear Roman Urdu or simple English. They watch the TV while you work. After every launcher change, install the debug APK and look at a screenshot from the TV before saying it is done.

## Fox Meadow handoff for 23 November 2026

The installed debug build includes the revised sunrise pose, earlier 09:00 flock, more visible daytime grass, and a shorter bedtime handoff. The APK checksum matched the locally built artifact. Three local Robolectric tests passed. The TV was returned to Home with automatic time enabled.

Three completed one-minute runs on the same TCL measured 50.0 presented FPS each at 22:00. They used the original full scene, a solid-color control, and the same control with the 14 ms gate removed. The display reported 60 Hz throughout. The cause of the 20 ms presentation interval remains open. The final installed build's additional pacing run was interrupted at handoff. The next review should distinguish callback cadence from buffer submission and presentation, and inspect the revised bedtime footage for residual ghosting.

- [Qwen handoff with implementation details and open questions](Qwen-Fox-Meadow-handoff.txt)
- [Completed pacing measurements](videos/fox-meadow/sofa-review/pacing.json)
- [Revised TCL recording](videos/fox-meadow/sofa-review/Fox-Meadow-TCL-review.mp4) · [TCL screenshots](videos/fox-meadow/sofa-review/TCL-screenshots.jpg)

Whole-launcher source pass on the same date, also not yet built or installed: `IconPickerActivity` no longer asks one Intent for both launcher categories (which left the picker nearly empty) and now builds `app:pkg:banner/icon` specs that match artwork that exists; `ClockPreferences.background()` falls back instead of crashing on a stale preference; the first app load in `MainActivity` shares the reload token so a package broadcast cannot be overwritten by an older list; one Back press in settings no longer dismisses a dialog and finishes the screen; async picker callbacks no longer touch a destroyed activity; the dead Wi-Fi panel, unused `AtmosphereView`, unused methods and unused imports were removed. Details below.

Source-only follow-up, not yet built or installed on the TV: `FloatingClockView.updateAmbient()` no longer refreshes the mascot's period from the wall clock while Meadow is mounted. That call overwrote the period chosen by `setMeadowHour()` and broke the generic 07/11/18 choreography whenever the preview hour differed from the real hour. `ClockScreenActivity` now leaves the preview only on Back/Escape instead of on any key, consumes the other D-pad keys, pauses the Meadow frame loop in `onStop()`, and shows a fading control legend. Dawn now gets haze and dew beads, and the larger stars get a quiet halo. The duplicate `PhotoPickerActivity` manifest entry is gone.

## Whole-launcher audit pass (24 November 2026)

The 17-file audit was applied and verified on the TCL. Two extra fixes were needed:

- `FocusRing` now keeps tracking a card that receives focus before it has been measured, and `GlassHighlight` retries `requestLayout()` when the first layout-params change is dropped. Without these, the icon picker, photo picker and settings screen showed no ring on first focus.
- `Dock.DEFAULT` now names Nuvio as `com.nuvio.tv`, the installed package. `com.nuvio.app` is the old id, so "Bring the dock back" used to drop Nuvio. The TV dock was restored to Netflix, TizenTube (shown as YouTube), Nuvio, NetMirror.

Verified: both modules assemble; the installed APK hash matches; Home resolves; dock, focus ring and Wi-Fi SSID pill render; grid Up/Down/Left/Right and ring work; long-press Edit shows the selected badge on "Icons from other app" and "Icon from gallery"; Save updates Home immediately; Settings Dock/Buttons/Clock/More work and one Back closes a dialog without leaving the screen; PhotoPicker ring works; no `com.cleanhome` crash in logcat. The Live Cable APK was installed; its HDMI surface is secure, so screencap is blank, but the activity launches.

TV cleanup: removed the old APK/XAPK installers from Download (about 1 GB) and cleared the MediaStore image/video rows. Launcher data and wallpaper were not touched.

Remaining: the javac deprecation note and the two pre-existing manifest lint errors (`MissingLeanbackLauncher`, `CoarseFineLocation`).

## Goal

Debloat the TV and replace Google TV Home with a small native launcher that looks like the right-hand set in `C:\Users\aliam\Downloads\stock-vs-new-side.jpg`.

- Pitch-black background.
- When a dock app is focused, a full-screen colour wash sampled from that app (Netflix = deep red fading to black on the right), plus the app name in large thin type above the dock.
- Dock at the bottom: Netflix, TizenTube, Nuvio, NetMirror. Same tile size, 16 dp rounded corners, one white focus ring that sits on the icon.
- Top right pills: Inputs, Wi-Fi, Settings.
- Top left: greeting, thin clock, date.
- Down from the dock opens the other apps. Up from the top row of that grid closes it.
- Long-press an app: App Info, Uninstall, Edit.

## Device

| Item | Value |
| --- | --- |
| Set | TCL 43-inch P7K, on-screen name Smart Tv Pro |
| Product | `G10_4K_GB` |
| Device | `G10` |
| Android | 12, SDK 31 |
| Build | `STT2.230203.001 release-keys` |
| UI size | Physical 3840x2160, override 1920x1080, density 320. Design in dp for 1080p. |
| IP | `192.168.1.21` (DHCP; the router changes it now and then) |
| ADB serial | `192.168.1.21:5555` |
| MAC | `B0-06-11-FD-8A-63` area Wi-Fi module |
| Host adb | `C:\Users\aliam\AppData\Local\Android\Sdk\platform-tools\adb.exe` |
| Host | Windows PowerShell 5.1. No `&&`. Chain with `;`. |

USB ports: nothing is plugged in. Network debugging can cut USB power on this set. Remind the user to turn debugging off when the whole job is finished.

Every new shell must `adb start-server` then `adb connect 192.168.1.21:5555` and wait about 1 second. The local adb server dies when the shell exits. The first command otherwise races as "device not found". If the connect times out, the TV has taken a new DHCP address: `adb mdns services` prints the current one as `adb-... _adb._tcp 192.168.1.x:5555`. After a TV reboot the set also asks for the USB debugging authorisation again; nothing can be done from the host until that dialog is accepted on the TV.

Screenshots: PowerShell redirection corrupts PNG. Use:

```powershell
$adb = "C:\Users\aliam\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$s = "192.168.1.21:5555"
$shot = "C:\Users\aliam\OneDrive\Desktop\tv\screenshots\name.png"
cmd /c "`"$adb`" -s $s exec-out screencap -p > `"$shot`""
```

A real home PNG is roughly 0.2â€“1.2 MB. 0 bytes means the capture failed. Do not run `dumpsys tv_input`. On this set it drops adb. `pm` commands: one package per `adb shell` call.

Reference repo the user pointed at: https://github.com/Gathin23/cleanyourtv  
Use it for the G10 debloat list and the setupwraith / HOME order. Do not ship their prebuilt APK. This project is `com.cleanhome.tv`, not `com.gathin.tvhome`.

## Debloat status

Backup before any disable is in `backup\`. Restore script is `restore.ps1`. It re-enables every package in `backup\disabled-by-us.txt`, turns Google TV launcher and setup recovery back on, sets Home back to `com.google.android.apps.tv.launcherx/.home.HomeActivity`, and restores the old screensaver. It does not touch factory-disabled `com.google.android.videos`.

Packages were disabled with `pm disable-user --user 0`. None were uninstalled.

Disabled by us (dedupe the file before trusting a raw read; it has duplicates):

- Telemetry: `com.tcl.logkit`, `tv.samba.ssm.ui`, `com.google.android.feedback`, `android.autoinstalls.config.google.gtvpai`
- TCL content / ads: `com.tcl.waterfall.overseas`, `com.tcl.messagebox`, `com.tcl.tv.tclhome_passive`, `com.tcl.esticker`, `com.tcl.exhibit`, `com.tcl.partnercustomizer`, `com.tcl.channelplus`, `com.tcl.interactive`, `com.tcl.useragreement`
- Preloads: `com.tcl.ocean.instructions`, `com.tcl.repairguide`, `com.tcl.gamebar`, `com.tcl.ui_mediaCenter` (USB media; re-enable to play USB files), `com.tcl.UpdatePeripheral` (soundbar firmware, not the TV update), `com.google.android.play.games`, `com.tcl.guard`
- User apps the user did not keep: YouTube Music, Prime Video, Castle, Instagram, TCL Browser, LocalSend, File Manager+, T-solo
- Cast / voice the user turned off: `com.tcl.smartalexa`, `com.tcl.airplay2`, `com.realtek.airplay2` (both AirPlay packages are still off; Miracast and MagiConnect were turned back on on 27 October 2026, see the wireless display section)
- Services: TalkBack, HearAid, `com.tcl.usercenter`
- Screensaver package `com.google.android.apps.tv.dreamx` after the screensaver setting was moved off it
- `com.tcl.ttvs` (firmware update AND TCL analytics; cannot be split). User accepted disabling the whole package. Internet still worked after.
- Stock home `com.google.android.apps.tv.launcherx`
- Setup recovery `com.google.android.tungsten.setupwraith`

`com.tcl.channelplus` is TCL Live FAST and also a TV input. Disabling it removes TCL Live channels, not HDMI. The red Live TV tile stayed.

Quick Paste `app.quickpaste.keyboard` was left enabled on purpose at debloat time. It was a normal user app and was uninstalled on 25 October 2026 at the user's request; it is not in the debloat backup list, so restoring it means installing it from Play Store again. Its keyboard service is gone from the enabled input methods; Gboard is the default.

### Never disable

`com.tcl.tv` (Live TV and HDMI playback), `com.tcl.tvinput`, `com.tcl.dashboard`, `com.tcl.suspension` (Quick panel / TCL HOME tiles), `com.tcl.autopair`, `com.tcl.systemserver`, `com.tcl.providers.config`, `com.tcl.factory.view`, `com.tcl.initsetup`, both keyboards (`com.tcl.inputmethod.international`, `com.google.android.inputmethod.latin`), Play Store `com.android.vending`, `com.google.android.gms`, `com.google.android.gsf`, `com.android.tv.settings`, `com.android.bluetooth`, `com.realtek.power`, `com.realtek.dongledetect`, SystemUI, `com.google.android.tv.remote.service` (phone remote; user said never disable remote services), Google Assistant `com.google.android.katniss`, Chromecast `com.google.android.apps.mediashell`, Netflix `com.netflix.ninja` and `com.netflix.tokenmanager`, YouTube `com.google.android.youtube.tv`, TizenTube `io.gh.reisxd.tizentube.cobalt`, Nuvio `com.nuvio.tv`, NetMirror `app.netmirror.newtv`.

TCL HOME and Quick panel tiles stayed after usercenter was disabled. They belong to dashboard / suspension. Leave those packages alone.

HOME replacement order that worked:

1. Install launcher with `adb push` then `pm install -r` (not `adb install`).
2. `cmd package set-home-activity --user 0 com.cleanhome.tv/.MainActivity`
3. `pm disable-user --user 0 com.google.android.apps.tv.launcherx`
4. `pm disable-user --user 0 com.google.android.tungsten.setupwraith` (separate shell; reconnect if adb drops)
5. `set-home-activity` again
6. `cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME` must be `com.cleanhome.tv/.MainActivity`

`cmd package set-home-activity` is ignored while a higher-priority HOME activity is still enabled. launcherx is priority 2, setupwraith RecoveryActivity is priority 1. If Home is wrong, re-enable launcherx immediately. Disabling setupwraith is required or Home can land in setup recovery. It is reversible via `restore.ps1`.

Screensaver now: `com.cleanhome.tv/.ClockDream`. Old value was `com.google.android.apps.tv.dreamx/.service.Backdrop` (`backup\screensaver-before.txt`).

## Wireless display from the PC (27 October 2026)

The PC could not see the TV in Windows' Connect list (`Win` + `K`) because the debloat had disabled `com.tcl.miracast` and `com.tcl.magiconnectfree`. Windows uses Miracast for wireless display, and on this set Miracast is not a system setting: TCL ships it inside MagiConnect. With both packages off there was no sink to discover, so the TV was simply absent from the list.

Fix, both with `pm enable --user 0`: `com.tcl.miracast` and `com.tcl.magiconnectfree`. Both AirPlay packages stay disabled. This does not bring back ads or telemetry; it restores the casting stack only.

How to start the sink on the TV:

1. Open **MagiConnect** from the launcher grid (the launcher added the tile by itself when the package was enabled).
2. Choose **Wireless Display**. Two tutorial pages follow; press OK twice.
3. The set switches to `com.tcl.miracast/.SinkActivity` and waits for a sender. The screen is `FLAG_SECURE`, so a screenshot of it is blank by design, not a failure.

How to connect from Windows:

1. `Win` + `K`, wait a few seconds.
2. **Ali TCL** appears in the list. That is the TV: `dumpsys wifi` reports `wifi_p2p_device_name=Ali TCL`, and p2p0 comes up at `192.168.49.1` while the sink runs.
3. Pick it and accept the prompt on the TV.

Proof on the PC side: a WinRT Wi-Fi Direct enumeration returned exactly one device, `Ali TCL` (`\\?\SWD#WiFiDirect#B2:6B:11:FD:8A:63`), enabled and unpaired. The Intel Wireless-AC 9560 driver reports `Wireless Display Supported: Yes (Graphics Driver: Yes, Wi-Fi Driver: Yes)`. The scan script is `C:\Users\aliam\AppData\Local\Temp\opencode\miracast-scan.ps1`.

Two things to remember:

- Starting the sink reconfigures Wi-Fi Direct, so network ADB drops for a few seconds. Reconnect after 5–10 s; the TV keeps its address.
- `com.tcl.miracast/.MainActivity` is TCL's internal **MiraTestMenu** (START/STOP service), not a user screen. The user path is always MagiConnect → Wireless Display.

Do not disable `com.tcl.miracast` or `com.tcl.magiconnectfree` while PC casting is wanted. They are no longer part of the disabled set.

## Launcher project

Path: `C:\Users\aliam\OneDrive\Desktop\tv\launcher`  
Application id: `com.cleanhome.tv`  
Java, minSdk 31, targetSdk 34, compileSdk 34, AGP 8.7.3, Gradle wrapper 8.9.  
JDK: Temurin 17 on PATH. SDK: `%LOCALAPPDATA%\Android\Sdk` in `launcher\local.properties` (gitignored).  
Dependency: `androidx.recyclerview:recyclerview:1.3.2` only.  
Font: Outfit variable font at `launcher\app\src\main\assets\fonts\outfit.ttf` (SIL OFL, Rodrigo Fuenzalida).

Build and install: `powershell -ExecutionPolicy Bypass -File .\launcher\build-and-install.ps1`  
Or assemble debug, push `app\build\outputs\apk\debug\app-debug.apk` to `/data/local/tmp/cleanhome.apk`, then `pm install -r`.

Dock order is the array in `launcher\app\src\main\java\com\cleanhome\tv\Dock.java`:

```text
com.netflix.ninja
io.gh.reisxd.tizentube.cobalt
com.nuvio.tv
app.netmirror.newtv
```

Wallpaper: after the launcher has opened once, push `wallpaper.jpg` or `wallpaper.png` to `/sdcard/Android/data/com.cleanhome.tv/files/`. Delete the file to go back to black. README at the repo root explains restore, dock edit, and wallpaper.

### Important source files

- `MainActivity.java` â€” home screen, D-pad zones, wash, glass ring, arrows, long-press menu. `alignTitle()` lines the app name up with the first card, `alignGrid()` lines the grid's first column up with the dock, and it logs the geometry under the tag `GridAlign`
- `Motion.java` â€” the one length and the one curve the whole screen moves on. A card that grows, a ring that travels, a menu row that lights up and a title that changes all use it, so they never drift apart
- `CardArtView.java` â€” the picture inside a card, the same class for the dock and the grid. It draws a centre crop by hand so the picture can be a little larger and a little higher than its frame when the card has the focus, 1.02x and 1 dp. `CardArtView.round(view, fraction)` is the rounded outline both places use
- `CardFocus.java` â€” the focus look of a card: 1.08x scale, full brightness when focused, 0.78 when not, and 8 dp of lift. Both the dock and the grid use it, so the two look the same
- `FocusSound.java` â€” the click a TV makes when the focus moves. Android plays this itself when a view takes the focus, but this launcher keeps its own focus model, so the framework never hears about a focus change and stays silent. It uses the system's own key click, follows the system's sound effects setting, and ignores repeats inside 45 ms so a fast run along the dock is not a machine gun
- `AmbientBackgroundView.java` â€” the full-screen radial wash. A custom View that draws a 32 stop `RadialGradient` on a `Canvas` with `Paint.setDither(true)`, then puts a 1.5% grain tile over the top. It also moves the centre of the glow when the focus moves along the dock
- `CardArtView.java` â€” the picture inside a card. It draws a centre crop by hand so the picture can be a little larger and a little higher than its frame when the card has the focus, 1.02x and 1 dp. The frame and the ring around it do not move
- `FocusSound.java` â€” the click a TV makes when the focus moves. Android plays this itself when a view takes the focus, but this launcher keeps its own focus model, so the framework never hears about a focus change and stays silent. It uses the system's own key click, follows the system's sound effects setting, and ignores repeats inside 45 ms so a fast run along the dock is not a machine gun
- `AppLoader.java` â€” lists leanback then launcher apps, skips self and settings, applies custom name/icon
- `AppStyles.java` â€” SharedPreferences `app-styles`. Name and icon overrides for this home screen only
- `EditAppActivity.java` â€” edit name, pick this app's art, another app's icon, or a gallery image. Also "Reset to original".
- `PhotoPickerActivity.java` â€” the built-in picture picker, four columns, newest first. It reads MediaStore directly. The system picker cannot be used on this set: the only activity that advertises `GET_CONTENT` for images is `com.android.tv.frameworkpackagestubs`, and that package is disabled, so the chooser answers "You don't have an app that can do this".
- `GlassHighlight.java` â€” the focus ring. One crisp 2.5 dp white stroke, parallel to the card, with an even dark band right at the card edge and a soft halo outside the ring. All of it is outside the card, so the ring still reads on a white card such as Netflix
- `AmbientBackgroundView.java` â€” the full-screen radial wash. A custom View that draws a 32 stop `RadialGradient` on a `Canvas` with `Paint.setDither(true)`, then puts a 1.5% grain tile over the top. It also moves the centre of the glow when the focus moves along the dock
- `UpHintDecoration.java` â€” up chevron drawn above the first grid row so it scrolls away
- `ClockDream.java` â€” plain clock screensaver
- Layouts: `activity_home.xml`, `activity_edit.xml`, `activity_photo_picker.xml`, `options_menu.xml`, `tile_dock.xml`, `tile_grid.xml`

Zones: TOP pills, DOCK, GRID (other apps), MENU (inputs dropdown). Up/Down move one row. Back from grid returns to dock. Back from inputs returns to the Inputs pill. Ends of a row do not fall through. Last grid index is remembered during the session. Every time the launcher opens or Home is pressed, focus is forced to dock index 0 (Netflix). That is `focusFirstDockApp()` in `onResume` and `onNewIntent`.

Dock tiles are 180 dp by 101 dp, corner radius 16 dp, centerCrop. Grid tiles are the same size and radius. The grid RecyclerView is 750 dp wide and centered so the tiles match the dock. Focus ring corner on both is 16 dp.

The ring is drawn on the root, slightly outside the tile, so it does not leave a black gap. Do not scale the tile itself; scaling made a black gap and clipped labels. **This changed on 19 October 2026: the tiles now do scale to 1.08, and the ring follows the scale.** The rule now is that the ring must be drawn at the card's live scale. `GlassHighlight.setScale()` takes the card's current `getScaleX()` and `MainActivity` pushes it every frame while a focus animation runs. If the ring is drawn at the unscaled rect the card grows out of it and you get a gap again.

Down arrow: `hint_down` is a child of the root FrameLayout, gravity bottom center, marginBottom 6 dp. It is not in the vertical layout. If you put it back in the column, the dock jumps up when the arrow becomes visible. The user was explicit: the empty gap under the dock stays, and the arrow sits in that gap. Show it only when zone is DOCK and there are other apps. Use GONE when hidden; the overlay does not change dock position.

Up arrow: not a floating view. `UpHintDecoration` adds space above the first grid row and draws the chevron there. When focus moves to the second row, `scrollToPositionWithOffset` scrolls that row to the top, so the arrow leaves with the first row. Do not pin it to the stage.

Inputs pill opens a list from `TvInputManager.getTvInputList()` (HDMI, AV, tuner). It switches passthrough with `TvContract` and package `com.tcl.tv`. Tuner launches `com.tcl.tv`. Wi-Fi pill opens Wi-Fi settings. Settings pill opens TV settings.

Wi-Fi pill now shows the SSID. Android 12 hides the SSID from apps without location access, so the manifest asks for `ACCESS_FINE_LOCATION` and the launcher requests it once at startup (`REQ_SSID` in `MainActivity`). If it is granted the pill text becomes the SSID, e.g. "Ali ðŸ  5G". If it is refused the pill keeps saying "Wi-Fi" and it is never asked again (`ssid-asked` in the `home` preferences). On this TV the permission is already granted, so no dialog appears. Grant it with `pm grant com.cleanhome.tv android.permission.ACCESS_FINE_LOCATION` if it is ever revoked.

Long-press OK (about the system long-press timeout, or a DOWN with `FLAG_LONG_PRESS`) on a dock or grid app opens App Info, Uninstall, Edit. Short OK still launches the app. App Info is `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`. Uninstall is `Intent.ACTION_DELETE` for `package:`. That is the normal system confirm dialog. System apps cannot be uninstalled; the TV says so. Do not `pm uninstall` system packages.

Edit screen (`EditAppActivity`, not exported, so `adb shell am start` cannot open it; open it from the menu):

- The name field takes the focus the moment the screen opens, and the cursor sits at the end of the name, not in front of it. The keyboard stays away. Pressing Ok on the field opens it, that is the only way it comes up.
- The keyboard staying away needed the window, not just the field. `setShowSoftInputOnFocus(false)` stops the field from asking for it, but the name field is the first thing that takes the focus, so the window opened the keyboard by itself. The window needs `SOFT_INPUT_STATE_ALWAYS_HIDDEN` as well.
- Header has three buttons: "Reset to original", "Add photo", "Save". "Reset to original" clears the custom name and the custom icon for that app, deletes the saved gallery file, bumps `gen`, and puts the real name and the original art back on screen. It takes effect at once, no Save needed. Verified on the TV with TizenTube: the name went from "YouTube" back to "TizenTube" and the `app-styles` preferences were left with nothing but `gen`.
- Preview must be the art the home screen actually uses: custom art if saved, otherwise the leanback activity banner, otherwise the icon. Do not preview `getApplicationIcon` first. That showed the round TizenTube glyph while the dock shows the wide "TizenTube android tv" banner. Fixed in `standardArt()`.
- Name field stays dark (`edit_field.xml`). Do not use `option_row` on it. `option_row` turns solid white when focused and the white text disappears.
- Keyboard does not open just because the field is focused (`setShowSoftInputOnFocus(false)`). OK on the field opens it. Down from the field focuses the first icon in "Icons from this app".
- The focus for the name field has to be asked for in `onWindowFocusChanged`, not in `onCreate`. The window does not have the focus yet in `onCreate`, so the request is dropped and the focus lands on the first button instead.
- Icon focus: white ring plus a slight scale. The current choice also keeps a ring.
- "Icons from this app" = original home art, plus the app icon, plus the banner.
- "Use another app's icon" = horizontal row of other apps' icons. Spec form `app:<package>:icon` or `app:<package>:banner`.
- Add photo uses the built-in `PhotoPickerActivity`. It needs `READ_EXTERNAL_STORAGE` on Android 12 and `READ_MEDIA_IMAGES` from Android 13. The permission is asked for the first time only when the user actually presses "Add photo", never at startup. The picked picture is copied to a PNG in the app files dir `icons/`. Spec `file:<name>`.
- The picker only sees images that MediaStore has finished scanning. A file that was pushed with adb stays `is_pending=1` and stays invisible until it is scanned: `adb shell content call --uri content://media/ --method scan_file --arg /sdcard/Pictures/pic1.png`. `scan_volume` alone leaves the rows pending.
- `loadThumbnail` refuses some files on this set, so the picker falls back to reading the picture itself.
- Save writes `app-styles` and bumps `gen`. `MainActivity.onResume` reloads apps when `gen` changes. The override is only on this home screen, not inside the real app.

## The premium look

Redone on 19 October 2026, then refined the same day.

- **Focus ring.** One crisp 2.5 dp white stroke that runs parallel to the card, so the gap is the same the whole way round including the corners. Between the card and the ring there is an even band of dark, drawn as a stroke, not a soft band, because a soft band goes lumpy in the corners. The dark band is the point: without it a white ring on the white Netflix card is invisible. The ring is drawn at the card's live scale so it never falls behind a growing card. A soft halo in the app's own colour sits outside the ring, never inside it.
- **Cards.** Focused card: 1.08x, full brightness, lifted 8 dp, which is 16 px at this set's density, for a real drop shadow. Unfocused: 0.78 alpha. 200 ms, decelerating. `CardFocus` does it for the dock and the grid.
- **Title and arrow.** They belong to the dock, so they fade out over 200 ms when the focus moves to the top row, and fade back when it returns. The arrow stays in place while it fades, so the dock never jumps.
- **Title type.** Outfit Medium (weight 500) instead of the light weight, letter spacing -0.015.
- **Title transition.** Moving along the dock fades the name out over 80 ms, swaps the text, then fades it in over 150 ms while it slides up from 8 dp. A token guards the swap, so quick presses cannot leave the name blank.
- **Title alignment.** The name is level with the left edge of the first card in the dock. `alignTitle()` works it out at run time from the real card position, so it stays right if the dock changes. The XML value is only the starting point.
- **Wash.** `AmbientBackgroundView`, a custom View that draws a multi stop `RadialGradient` with `Paint.setDither(true)` and a 1.5% grain tile over the top. A plain `GradientDrawable` was not enough: on an eight bit panel the rings came back in the teal and purple washes. The radius is about one panel width and the window is 32 bit (`PixelFormat.RGBA_8888`).
- **Glow position.** The centre of the wash drifts from 0.30 of the width on the first card to 0.65 on the last, and the colour cross fades at the same time, over 420 ms.
- **Dock container.** Translucent dark fill, a faint white line, and a corner radius of 34 dp, which is the 16 dp card radius plus the 18 dp dock padding, so the two curves are concentric. The gap between cards is 16 dp, which is what the focused card needs to grow into without its ring touching the next card. The padding also has to hold the focus ring of the focused card. At 14 dp the ring landed on the dock's own border and the two lines merged into one thick messy edge.
- **The wash never dies.** The colour of an app is sampled from its art, and a dark app would give a black screen. `AppLoader.lift` raises the colour until it can light the panel and pushes a washed out colour away from grey, so Nuvio and NetMirror light the room like Netflix and TizenTube do.
- **The long press menu.** It is a glass card with rounded corners, a faint white line and the Outfit font, the same language as the dock. The focused row gets a soft fill and a thin white ring, not a solid white block, and the scrim is light so the colour of the screen behind it stays alive. The card comes up gently on the same curve as everything else.
- **Names.** `app.netmirror.newtv` ships the label "NetMirrorTV". `AppLoader.tidyName` shows it as "NetMirror TV".
- **Touch.** There is no ripple on purpose. The cards are not touch targets on a TV, and the motion, the shadow and the click are the tactile cues.
- **Sound.** The TV has `sound_effects_enabled` set to 0, so the focus click is silent until the user turns system sounds on. `adb shell settings put system sound_effects_enabled 1` does it from the PC. The setting is read again every two seconds, so no restart is needed.
- **The input menu.** The dropdown is moved with `setX` and `setY`, and the framework's `offsetDescendantRectToMyCoords` leaves out the translation of a parent. The focus ring has to use `rectInRoot()`, which counts the translation, or it lands in the top left corner of the screen instead of on the menu row. The colour wash stays on while the menu is open, and the Inputs pill stays lit, so opening the menu does not feel like the screen blinked out.
- **One motion.** Everything moves on `Motion.DURATION` and `Motion.EASING`, 200 ms. The card that grows, the ring that travels, the menu row that lights up and the title that changes all use it. Before that the menu row lit up at once while the ring took 240 ms, and the two disagreed.
- **The card is `CardArtView`.** The dock and the grid must use the same class and the same `CardArtView.round(art, 16f / 180f)` and the same `CardFocus`, or the two will not look alike. A plain `ImageView` in the grid is what made it look different.

## What Google TV does, learned by decompiling it

The stock Google TV launcher was decompiled with REA (JADX) on 20 October 2026. `com.google.android.apps.tv.launcherx`, 22,060 classes, 2,958 resources.

**How to reverse engineer an APK with REA: `reverse engineer apk guide.md`** in the project root. It has the setup, the MCP tools, the exact workflow, the parsing pitfalls and the ADB traps. Read it before using REA.

- **The scale is Leanback's `androidx.leanback.widget.ScaleFrameLayout`.** It scales the children as a transform, `view.setScaleX(this.c)` in `addView`, and `onLayout` moves the child's pivot to the parent's right edge and the vertical centre, so a card grows sideways and not into its left neighbour. The layout compensates with padding.
- **The ring is the card's own foreground.** `com.google.android.libraries.tv.widgets.card.CardFocusableFrameLayout` overrides `setForeground` and guards `requestLayout`, so the foreground is not dropped by a re-layout.

**What this means for us.** Our `GlassHighlight` is a separate overlay on the root that we place and scale every frame. Google TV puts the ring in the card, so it scales with the card and costs nothing per frame. The catch is that a foreground lives inside the card's bounds, so the card would need padding for the ring and the art would get about 8 dp smaller. Our dark seam, which is what keeps the ring readable on a white card, fits in that padding, so the trade is real but not clean. Changing this is a decision, not a bug.

**Also learned.** The TCL quick panel is `com.tcl.suspension`. It is shown by the broadcast `com.tcl.quickpanel.show.window`, closed by `com.tcl.quickmenu.dismiss`, and opens the input screen with `com.android.tv.action.VIEW_INPUTS`. The panel can open over this launcher on a down press, and it still lists "Google TV Home", the stock launcher we disabled, as one of its inputs.

## TRIED AND REJECTED: Google's pivot and the foreground ring (20 October 2026)

Both changes were built, installed and tested on the TV in a throwaway copy at `C:\Users\aliam\OneDrive\Desktop\tv-premium` (its own package id `com.cleanhome.tv.premium`, so it lived beside the real launcher and never replaced it). The user compared the two and chose the current launcher. The copy and the app were deleted, the real launcher was never touched.

What the copy did:

- **The pivot.** The card grew from its left edge and the vertical middle instead of from the centre, so it never pushed into the card before it. The ring had to grow around the same pivot, so `GlassHighlight` gained a pivot.
- **The foreground ring.** The card became a `CardView` (a `FrameLayout`): a dark plate, the picture 4 dp inside it, and the white ring as the card's own `foreground`. The picture therefore got 8 dp smaller (180x101 became 172x93) and every card grew a black frame, focused or not. The glow had to move inside the ring, so the coloured halo around the card was lost, and the ring read thinner than the current one.

What was measured: the left-edge pivot pushes the whole card 14.4 dp to the right (the full 8% of 180 dp, not half), so with the 16 dp dock gap the gap to the next card drops to about 2 dp while the gap to the previous one grows to 16 dp. The centred pivot keeps both gaps at about 9 dp. The grid's labels also have to slide sideways with the card or they come off centre.

Lessons for the next time:

- A variant that must not disturb the running launcher needs its own `applicationId` in `app\build.gradle`. The Java package and the namespace can stay the same, so no code has to change. A different package means its own preferences, so the dock and the custom names have to be copied over.
- **Copy the preferences as raw bytes.** `adb shell run-as ... cat file > local` through PowerShell writes UTF-16 and Android silently ignores the whole preferences file, so the launcher falls back to the default dock. Use `cmd /c "adb ... exec-out run-as ... cat file > local"`, or push and copy with `run-as sh -c 'cat /data/local/tmp/f > shared_prefs/f'`.
- A second launcher that declares `android.intent.category.HOME` takes the home job away from the real one. Take the HOME category out of the variant's manifest and start it with `am start -n <pkg>/com.cleanhome.tv.MainActivity` instead.
- The screenshots of both versions are in `C:\Users\aliam\AppData\Local\Temp\opencode\shots` (`original-*.png`, `variant-*.png`, `compare-dock.png`). They are gone with the temp folder, but they show the trade clearly.

## The launcher's own settings

Added on 20 October 2026. Press **Left on the first card of the dock** and the settings screen opens.

- **A small bar of settings along the top**: Dock, Buttons, More. The section follows the focus, so moving along the bar changes what is below it.
- **Dock.** The dock sits below as a small row of cards. Ok on a card replaces the app in that slot, the card at the end adds a new one. A long press on a card opens a small menu: Move left, Move right, Change the app, Take it out.
- **Buttons.** Inputs, Wi-Fi and Settings, the three buttons in the top right corner, can each be hidden. A hidden button is skipped by the focus, it cannot be selected and its menu cannot be opened from the launcher any more. The top row's focus is kept on a button that is on screen by `keepTopVisible()`, and moving left or right steps over hidden buttons with `stepTop()`. This was a bug on 20 October 2026: the focus went to the hidden Inputs button, no border showed anywhere, and OK opened the input menu.
- **More.** Put the dock back the way it was, bring the three buttons back, and close.

The dock is no longer a list in the code. `Dock` reads and writes it in the "dock" preferences, and the list in `Dock.DEFAULT` is only the starting point. The launcher reloads the dock when it comes back from the settings. To put the dock back by hand, delete `shared_prefs/dock.xml` and the default list is used again.

`IconPickerActivity` grew a second mode. With `EXTRA_PICK_APP` it only asks for an app and returns its package, which is what the dock uses. Without it, it asks for an app and then for one of that app's pictures, and returns the picture spec.
- **A jump is not a slide.** Moving between parts of the screen, from a card in the dock to a row in the input menu or the other way round, would send the ring across the whole panel. The ring goes straight to the new place and comes back there, `GlassHighlight.fadeTo()`. It must not linger on the old card for a frame, that reads as the border jumping about. Only a move inside the same part of the screen slides.
- **Nothing shows at the wrong place for a frame.** The input menu is placed under the Inputs button before it is shown, `positionInputMenu()`. If it is shown first it sits in the top left corner of the screen for a frame and then jumps. The ring is blanked before it moves, so it cannot be seen at the old place while it travels.
- **The menu row light.** It is a view of its own behind the text, `MarkView`, so it can fade without taking the text with it. It reports no height until the row has been measured, or a plain view would claim the whole screen and the menu would grow off the bottom.

## RESOLVED: the Wi-Fi button shows the network name (Fixed on 23 October 2026)

The button in the top right that used to read "Wi-Fi" now carries the name of the connected network, so it reads "Ali 🏠 5G" here. Three things had to line up, and finding them took a walk:

- **The name is only given out with location.** On Android 12 an app cannot see the SSID without `ACCESS_FINE_LOCATION` and the Location switch on. Both were fine here and the permission was asked once and granted, so that was not the trouble.
- **`NetworkCapabilities` never gives the name to an app.** `getNetworkCapabilities().getTransportInfo()` came back `<unknown ssid>` every time, permission in hand. The path that works on this TV is `WifiManager.getConnectionInfo().getSSID()`, which said "Ali 🏠 5G" right away. `readSsid()` tries both and falls through to the second.
- **The picture of the trouble: the name was only read once.** `loadSsidAsync()` ran in `onCreate` only. The permission landed after that screen was built, the launcher process lived on, the TV only ever handed it `onResume` again, and the button kept its first word for days. The proof came from `appops get com.cleanhome.tv FINE_LOCATION`, which showed the app had used location and was let in, next to a screenshot from the same minute that still said "Wi-Fi".

The fix keeps the name fresh four ways: `loadSsidAsync()` also runs in `onResume`, so every press of Home re-reads it; a `ConnectivityManager` callback (`watchNetwork()`) follows the network while the launcher sits open, firing on `onAvailable` when one arrives and on `onLost` when it is switched off — that second call matters more than the first, because with the Wi-Fi off no new network ever arrives and only `onLost` can take the old name down; `onDestroy()` unregisters the watcher. When the name cannot be read, `fallbackNetworkLabel()` picks the word for what is actually there: "Ethernet" over a wire, plain "Wi-Fi" when the network is gone or its name is hidden. The user proved the switched-off case on the TV: with the connection cut the button went back to "Wi-Fi", and the name came back the moment the network did.

Reading the SSID writes one line per read with the tag `SsidPill` when `log.tag.SsidPill` says `DEBUG`: which of the two paths answered and what it said. That tag is how the stale button was traced. The TV is on `192.168.1.21` now; the router gives it a new address now and then. If a shell cannot connect, read the current one from `adb mdns services`.

## The Wi-Fi menu (tried and set aside, 23 October 2026)

OK on the Wi-Fi button was briefly changed to open a menu of the networks around, in the same glass panel the Inputs button uses, with the Android screen as the last row inside it. It showed the five strongest networks, the connected one first, a small drawn padlock on locked ones and a "CONNECTED" word on the right. A network could be joined from it: open networks directly, secured ones through a password dialog whose Join button stays hidden until eight characters are typed, and the password is remembered so the same network joins with one tap afterwards.

It was set aside because Android 12 does not let a third-party app read the saved networks (`getConfiguredNetworks()` returned 0 while the television knew two) and does not let one force a connection to a saved network (`addNetwork`, `enableNetwork` and `connect` all refuse third-party callers since Android Q). `addNetworkSuggestions` is the only road left, and it only suggests; the system decides when to connect. That could not give the direct connect the user asked for, so the button went back to opening the Android Wi-Fi settings, which is where a saved network can be joined with one tap.

The unused code was removed on 23 November 2026: the `WifiEntry` class, `wifiRows`, the Wi-Fi menu methods, the padlock/badge drawing and the `MENU_WIFI` constant. The Wi-Fi pill opens `Settings.ACTION_WIFI_SETTINGS` directly. Nothing here can be restored except from git history or this description.

## The settings screen (23 October 2026)

The launcher's own settings were rebuilt. The old screen was a row of tabs above a content area, with plain text rows and a horizontal scroll for the dock. The new one is a two-column screen: a sidebar on the left with the three sections, each carrying an icon and a short description, and a content area on the right. The title and the subtitle change with the section, a thin rule sits under the title, and a soft wash fills the background.

- **Dock.** The cards are smaller and sit in glass cards, each with the app's banner and its name underneath. A dashed-looking "Add" card with a plus sign stands at the end. A long press still opens the small menu: move left, move right, change the app, take it out.
- **Buttons.** Each of the three buttons is now a glass row with its icon, its name, a short description and a pill on the right that reads "SHOWN" or "HIDDEN". The pill fills when the button is shown and stays hollow when it is hidden, so the state reads at a glance. Tapping the row flips it.
- **More.** The three actions became glass rows with a description each and a chevron, so it is clear what each one does before it is pressed.

The focus ring moves between the sidebar and the content the same way it moves everywhere else, on the same curve and the same timing. The settings open the same way they always did, with Left on the first card of the dock.

## Smoothness pass, part one (23 October 2026)

The home screen was made lighter on the rendering thread, without changing how it looks.

- **AmbientBackgroundView.** The radial gradient went from 32 stops to 12, with dithering still on, so the banding stays invisible but the shader is built about three times faster. The colour and stop arrays are reused instead of allocated on every frame of the 420 ms fade. The grain tile is unchanged.
- **GlassHighlight.** The halo went from 8 bands to 4. The ring still reads the same, it just costs half the draw calls per frame while it travels. The halo turned out to be dead code and was removed completely in part two.
- **MainActivity.** The two `root.post` calls at the end of `renderZone` became one, so the ring and the grid scroll land on the same frame instead of two. `placeHighlight` and `scrollGridToFocus` no longer race the choreographer.
- **CardArtView.** The interpolator is now shared instead of allocated on every focus change.

The focus ring still tracks the growing card on every frame through `focusTick`, which is what keeps the gap even while a card grows. What that loop must not do is touch layout; part two found and removed exactly that.

## Smoothness pass, part two (23 October 2026)

Part one made the shaders cheaper. Part two found the real reason the launcher still felt heavy: the biggest single cost was not the ring or the gradient, it was a full-screen paint with **dithering on**, and on this panel that forces the slow path on every pass. Measured with `dumpsys gfxinfo` over a scripted D-pad sweep, the dock went from **57 ms p50 and 40% janky frames** to **16 ms p50 and 2.8%**. The slow issue-draw-commands count went from 80 to 12.

What was found and fixed, in the order it was found:

- **Dither was the tax.** The ambient wash and its gradient were dithered. Dither on a full-screen paint costs about 30 ms per pass on the Realtek GPU and pushes the frame onto the software path. It was not covering banding that anyone could see, because the gradient is soft and the grain tile sits on top. Dither was removed entirely. This one change took the dock from 57 ms to about 26 ms.
- **The radial gradient was rebuilt every frame.** `AmbientBackgroundView` was drawing a radial gradient into the whole screen for each frame of the 420 ms colour fade. It now bakes the gradient **once** into a white mask at one fifth of the screen size, stretches it to twice the screen (so its edge can never show), and tints it with a `PorterDuffColorFilter`. The fade only changes the filter colour. The draw is now a plain bitmap blit: about 26 ms to 20 ms, and the faint rings the shader left in the dark are gone.
- **The ring's halo was dead code.** `haloSpread` was 5 dp while `ringOuter` was 4.5 dp, so every halo band fell outside the ring and was clipped away. It had never drawn a single pixel. It was removed rather than enlarged, along with both `clipPath` calls. The seam between the card and the ring is now a stroked annulus (`seamRect.inset(-gap/2)`, stroke `gap`), which is pixel-equivalent to the old clipped fill. This took the dock to about 19 ms. The `Path`, the halo array, `drawBand()`, `accent` and `setAccent()` all went with it.
- **`alignTitle()` was forcing a full-screen relayout every frame.** The title was positioned from the **scaled** card left, so its target moved for the whole 200 ms grow, and `setLayoutParams` ran on every `focusTick`. That invalidates the whole hierarchy and measures the screen again: roughly 80 ms per frame, hidden inside the animation. It now uses the card's **unscaled** left (`loc + width * (scale - 1) / 2`), which is constant for a given card. `focusTick` no longer calls `alignTitle()` or `alignGrid()` at all, and `FOCUS_TICKS` went from 30 to 16. The ring still tracks the live scale; the text does not need to.
- **The ring could come back half transparent.** `GlassHighlight.hide()` faded the ring but left its alpha wherever the fade stopped, so returning from the top row could show a ring at 0.4 alpha for a moment, and a fade still in flight could land on the old target. `hide()` now resets alpha to 1, snaps `shown` to the target and bumps `fadeToken`, so the next `show()` starts a clean fade at full opacity.
- **The menu's highlight band never drew.** `menuRow` was `wrap_content`, and the `MarkView` that draws the focused band was measured at zero height, so the row looked selected only by its text colour. Rows are now a fixed `dp(42)` with `CENTER_VERTICAL`, and the filled band shows under the focused row.
- **The title faded twice.** `showHero` and `fadeShelf` were both animating it, one after the other. The dock now owns the title; if the title is already hidden it swaps immediately, and `showHero` only runs in the dock zone.
- **Small per-frame work that added up.** The clock called `setText` every second even when the string had not changed; it now compares first, and the greeting uses `Calendar.HOUR_OF_DAY`. The SSID pill and the network callback only set their label when it differs. The wallpaper decode moved off the UI thread, with a token so a destroyed activity cannot apply it. `GridAdapter.setSelected` returns early when the index has not changed (it was calling `notifyItemChanged` twice for every key). `showInputs` refreshes an already-open menu, so a late input load does not leave it empty.
- **Races around leaving the screen.** `loadSsidAsync` uses a token; `onPause` clears `longPress`, `centerDown` and any pending hero swap; `onDestroy` clears the handler, the `focusTick` and the dialog. A long press that arrives twice no longer closes and reopens the app-options dialog.
- **The settings screen lost its focus after every change.** Adding, changing, moving or removing a dock app rebuilt the dock but left the focus and the ring behind. `rebuildDock(focusIndex)` now restores both. The app picker opens with the focus on the first row, `currentDialog` is nulled on dismiss, and BACK closes the dialog first and only finishes on the second press, so the screen can no longer get stuck. `onDestroy` dismisses the dialog.
- **Nothing was left behind for the measurement.** `Perf.java` and every `Perf.MODE` hook used for the A/B runs were deleted. The final APK has none of them.

Final numbers, on the installed final build, after a `gfxinfo reset` and a scripted sweep:

| Screen | Frames | p50 | p90 | Janky |
| --- | --- | --- | --- | --- |
| Dock, 20 D-pad moves | 534 | 16 ms | 24 ms | 2.81% |
| Grid, 16 moves + scroll | 400 | 13 ms | 19 ms | 2.75% |
| Settings | — | 7 ms | 13 ms | 3.36% |

The crash buffer was empty afterwards, and the dock and grid were photographed on the TV: ring centred on the grown card, title level with it, labels clear of the ring, pills in place, the up chevron floating above the grid.

Two rules came out of this pass and are in **Do not** below: never dither a full-screen paint on this panel, and never put a layout call inside `focusTick`.

## Fixed: the empty top row and the app list (25 October 2026)

Two reports from the user, both real.

**UP went to a top row that was not there.** With all three buttons hidden in the launcher's settings, the focus was on the dock, and UP followed by OK opened the Inputs menu in the top left corner. The cause was a fallback: `firstVisiblePill()` returned `0` when no button was visible, `moveVertical()` set `zone = TOP` without asking whether the row had anything in it, and `activate()` then treated index 0 as the Inputs button. The fix has four parts, so the focus cannot be left on an invisible button through any path:

- `firstVisiblePill()` returns **-1** when every button is hidden. `hasVisiblePill()` is the question the callers actually need.
- UP from the dock only enters the top row when `hasVisiblePill()` is true. With every button hidden it does nothing; the focus stays on the dock card.
- `keepTopVisible()` drops the zone back to `DOCK` if the row has become empty (for example, the last button was hidden while the focus was up there).
- `activate()` refuses to act on a pill that is not visible, and if there is no visible pill at all it returns the focus to the dock instead of opening a menu.

The same guard was added to the UP path out of an open input menu, so a hidden pill cannot be reached from there either.

**A newly installed app did not appear in the grid.** The app list was read in `onCreate` and after a dock change or a style change, and nowhere else. Installing an app from Play Store left the launcher on the list it had built at start-up, so the new app only showed up after a restart. A package receiver now listens for `PACKAGE_ADDED`, `PACKAGE_REMOVED`, `PACKAGE_CHANGED` and `PACKAGE_REPLACED` while the launcher is alive. One install sends a burst of broadcasts, so the reload waits 600 ms for the burst to settle, and a token makes sure an older enumeration cannot overwrite a newer one. The receiver is unregistered in `onDestroy`. The launcher still re-reads the list on resume when the dock or the app styles changed; this is the path that catches a missed broadcast after the process was killed.

Verified on the TV:

- With Inputs, Wi-Fi and Settings all hidden, the top right corner is empty, UP does nothing, the focus stays on Netflix, and OK launches Netflix. The input menu does not open.
- `com.sidhbalitech.ninexplayer`, installed at 09:11 that morning, was missing from the old grid and appears as **XP Player** on the new build.
- Quick Paste (`app.quickpaste.keyboard`) was uninstalled while the launcher was running; it disappeared from the grid and XP Player stayed. Quick Paste is a user app, not part of the debloat backup; restoring it means installing it from Play Store. Gboard is the default keyboard and was not affected.
- The crash buffer stayed empty.

The last test left all three buttons hidden. Putting the previous Inputs-only state back needs one more connection; after the overnight reboot the TV reported `unauthorized` to adb, so the Allow debugging dialog has to be accepted on the TV first. The launcher's own **Buttons** screen can also put the three buttons back without a cable.

## The grid (the other apps screen)


Also redone on 20 October 2026, to look like the home screen, then refined into a bespoke luxury experience:

- **Smooth Dock Slide-Out & Fade:** When moving down from the dock to the grid, the dock container (`dock_band`) translates 160 dp down off-screen and fades to 0 over 200 ms with `Motion.EASING`. Row 2 no longer collides with or touches the dock; the grid now has luxurious breathing room. When pressing Back or Up to return to the dock, the dock slides and fades back in smoothly.
- **Top Bar De-clutter:** The top-right pills (Inputs, Wi-Fi, Settings) smoothly fade out to 0 alpha when entering the grid, eliminating visual clutter so focus is 100% on the apps. They fade back in when returning to the dock/home.
- **Outfit Medium Typography:** App labels in the grid now use the bespoke Outfit font (`medium`, weight 500) with `letterSpacing="0.02"` and refined `#F5F5F7` color, replacing the generic system font.
- **The wash is on.** `updateAmbient()` lights the screen with the colour of the app that has the focus in the grid, and the centre of the glow moves with the column: 0.20, 0.40, 0.60, 0.80 of the width.
- **Every card shows its name.** The focused one at full brightness (1.0), the others at 0.45.
- **Two rows fit whole.** Both rows and both sets of names fit with generous padding above and below.
- **The up chevron floats.** The chevron floats clear of the card corners and guides navigation back to the dock.
- **The columns line up with the dock.** Grid cards and dock cards sit at the exact same horizontal anchor (192 px). Focus ring hugs every card with 4 px gap symmetrically on all 4 sides.
- **The ring never touches the label.** The gap between the card and its name is 16 dp, the name steps 5 dp further down while the card is focused, and the rows are 24 dp apart. Measured on the TV, the ring's bottom is 33 px above the top of the text.
- **Top breathing room.** The space above the first row is 50 dp. It holds the up arrow with 16 dp of clearance and it pushes the grid down away from the clock. The arrow sits at 55% alpha so it is a hint, not a headline.
- **The glow follows the grid in two directions.** `AmbientBackgroundView` takes a centre in the width and in the height. On the grid the column takes the centre from 0.25 to 0.75 of the width and the row from 0.42 to 0.62 of the height, and the colour cross fades with it over 420 ms.
- **Unfocused cards sit at 0.75**, the same as the dock, and their names at 0.50. A focused card goes to 1.0 and its name steps down out of the way of the ring.
- **The grid takes the room the dock was using, without moving the dock.** The grid gets a negative bottom margin, equal to the height of the dock, so it grows down over the faded dock. The margin must not go on the stage. On the stage it made the layout push the dock down, and the dock jumped from the middle of the screen instead of sliding out of its own place. The grid's cards are at the top of the stage, so the grid itself does not move.
- **The space above the first row is the RecyclerView's padding, not an item offset.** `scrollToPositionWithOffset` eats an item offset, and the first row jumped up under the clock. As padding it survives, and the grid sits below the clock.
- **The grid only scrolls if the rows do not all fit.** There are six apps in two rows and they fit, so the grid never scrolls. `scrollGridToFocus()` measures the rows and leaves the grid at the top when everything fits.
- **A short last row must not trap the focus.** The grid holds six apps in two rows of four, so the second row has only two cards. Pressing Down from the third or fourth column had nothing to land on and the focus simply sat still, which read as a dead key. When there is no card directly below, the focus now goes to the last card of that row, which is what a TV grid is expected to do. Going Up still follows the column, so Up from the last card lands on the card above it in the same column.
- **There is no fade under the grid any more.** There used to be one, a black gradient that softened a row running into the dock. The background is the colour wash, not black, so the fade showed up as a dark rectangle lying on top of the red. And the grid only holds two rows, so there is nothing below to soften. It was removed on 20 October 2026, `grid_fade.xml` and the view that used it are gone. Do not put it back, fade to the background colour or not at all.

### The shake, and why it is fixed

`alignGrid()` nudges the grid with `setTranslationX` so its first column matches the dock. The first version measured the card's position, which already contained the nudge, and set the nudge to the difference. That is a feedback loop: the grid measured itself, moved, measured itself again and shook for ever, and the focus looked stuck.

The delta now takes the current translation out again:

```java
int delta = dockPos[0] - artPos[0] + Math.round(grid.getTranslationX());
```

## RESOLVED: the grid card fills the ring evenly (Fixed on 20 October 2026)

The grid card now fills the focus ring with the exact same 4 px gap on all four sides as the dock cards, both on first opening from the dock and when navigating between cards.

**Root causes identified & fixed:**
1. **Clipping:** In `tile_grid.xml`, `LinearLayout` had `paddingStart="8dp"` and `paddingEnd="8dp"` without `clipToPadding="false"`. Android's hardware renderer hard-clipped child drawings to the padding bounds `[16, 376]`, locking the card art to 360 px unscaled.
   - *Fix:* Set `gravity="center_horizontal"` to center the 180 dp card inside the 196 dp cell (giving 8 dp margin naturally on both sides). Removed paddingStart/paddingEnd, added `clipToPadding="false"` and `clipChildren="false"`, and added `paddingTop="6dp"` for vertical headroom.
2. **First-time open misalign:** When moving from dock to grid for the first time:
   - `alignGrid()` was measuring `dockPos[0]` and `artPos[0]` with `getLocationInWindow()`, which includes the active card's focus scale (1.08x). Because Netflix scaled to 1.08 in the dock (shifting left by 14 px) while grid card 0 was unscaled (scale 1.0), `alignGrid()` computed `delta = -14` while on the dock. When transitioning to the grid, `placeHighlight()` aimed at this -14 px position while the grid translation shifted back, causing the card to jump away from the ring.
   - In `GlassHighlight.java`, `springTo()` had `if (fading) return;` which dropped all position updates during the fade-in, leaving the ring frozen at the stale initial coordinate until a D-pad navigation occurred.
   - *Fix:* 
     - In `MainActivity.alignGrid()`, removed the focus scale offset (`+ width * (scale - 1) / 2`) so grid alignment compares unscaled layout anchors (both at 192 px), keeping `delta = 0` constantly.
     - In `GlassHighlight.java`, updated `springTo()` so that if the target shifts while `fading == true`, `shownX/Y/W/H` update immediately and track the live position.
     - In `MainActivity.renderZone()`, posted `alignGrid()` and `placeHighlight()` to `grid` on `showGrid` so layout settles cleanly.

**Live TV Verification:**
- Measured on first open from dock (`grid-first-open.png`):
  - Left ring: 169 to 173 px (inner dark band: 174 px).
  - Card art: 178 to 566 px (388 px wide, exact 1.08x scale).
  - Right ring: inner dark band 570 px, ring 571 to 575 px.
  - Left gap: **4 px**
  - Right gap: **4 px**
  - Top gap: **4 px** (269 - 265 px)
  - Bottom gap: **4 px** (491 - 487 px)
- Cycle test from dock to grid repeated multiple times: 100% consistent alignment on first entry and subsequent moves.

**Checked again after the fix**, independently, on the three positions that matter, and on the dock for comparison. The art is the scaled 388 px and the ring is 407 px everywhere, with a 5 px gap on the left and a 4 px gap on the right:

- Grid, column 0: art 192 to 580, ring 182 to 589
- Grid, column 1: art 570 to 958, ring 560 to 967
- Grid, second row after scrolling: art 178 to 566, ring 168 to 575
- Dock, focused Netflix: art 178 to 566, ring 168 to 575

**Why it works.** The art is centred in its 196 dp cell instead of being pinned to the left edge of it. The 8 dp of slack on each side is what the 7.2 dp of overflow needs, so the whole scaled card stays inside the item view and nothing can cut it. The item root also got `clipToPadding="false"`, because `clipToPadding` defaults to true and that is what tightens the clip.

**The debug log can go now.** `MainActivity.alignGrid()` still logs the geometry under the tag `GridAlign`, once per frame while the focus moves. It is guarded by `Log.isLoggable`, so it stays quiet unless the tag is switched on. It is the fastest way to check the geometry if this ever comes back, so it was left in.

## Verified on the TV

Home is `com.cleanhome.tv/.MainActivity`. Pressing Home stays on this launcher. Netflix focus shows the red wash, the large name, the dock, and the down arrow in the gap under the dock. Grid tiles match dock size (measured 360 by 202 px at density 320). Long-press Netflix showed App Info / Uninstall / Edit. Edit for TizenTube showed the banner preview, a dark name field reading the custom label, this app's art, and the other-app icon row.

Also verified on 19 October 2026:

- The premium pass: the white ring is readable on the white Netflix card, the focused card grows and dims the rest, the title and the arrow fade away on the top row and come back on the dock, the name is level with the first card, the dock is a frosted container with concentric corners, the wash is soft with no visible rings, the glow drifts along with the focus and the name slides in as it changes.
- The focus click. Four focus moves gave four clicks in the audio log, and a press at the end of a row, where the focus does not move, gave none.
- The input menu. The ring sits on the chosen row and follows it down the list, the colour wash stays on while it is open, the Inputs pill stays lit, and Back closes it and puts the focus back on that pill.
- The ring no longer flies. Frames taken one after the other right after the menu opened show the ring gone in the first frame and back on the first row in the next, never in between. The same happens on the way back to a card.
- Nothing flashes in the wrong place. The menu is under the Inputs button in the very first frame, and the ring is never seen on the card it came from.
- Going down from the top row to the dock, the ring, the title and the arrow all come back together in one frame. There is no blink.
- The focused card's ring now has room inside the dock. The dock's border and the ring are two separate curves again, and they are parallel.
- Nuvio and NetMirror light the screen now, they used to leave it black.
- The long press menu is a glass card in Outfit, the focused row is a soft fill with a thin ring, and the colour of the screen shows through the scrim.
- The grid. The wash is on, the names are on, two rows fit whole, the columns line up with the dock, the up chevron floats clear of the card, the ring fades when the grid scrolls, and the shaking is gone.
- The grid takes the screen over. The three pills at the top right are gone and the dock has slid down out of the way, so only the apps are on screen. Pressing Up brings the pills and the dock back, sliding and fading in. Checked on the TV in both directions, and the grid still works after coming back.
- The grid polish. The ring no longer touches the labels, the rows are 24 dp apart, the grid sits below the clock with the arrow floating clear of the cards, the glow follows the column and the row, and the unfocused cards and their names sit at 0.75 and 0.50.
- The dock slides out of its own place when the grid opens, it does not jump down from the middle of the screen. Checked with the animations slowed to a fifth of their speed and a burst of frames taken.
- The focused card in the grid now fills its ring exactly like the dock's. Measured again after the fix, on all three positions that matter, and every one of them is the same 388 px art in a 407 px ring with a 5 px and a 4 px gap: column 0, column 1, and the second row after the grid has been scrolled. See the RESOLVED section above.
- The Wi-Fi pill shows the SSID "Ali ðŸ  5G" once location access is granted.
- "Reset to original" on the TizenTube edit screen put the name back to "TizenTube" and left the `app-styles` prefs with nothing but `gen`. The user's "YouTube" label and the banner icon were then written back by hand, and the home screen shows "YouTube" with the teal TizenTube wash again.
- `backup\disabled-by-us.txt` holds 40 unique packages; 38 of them are still disabled on the TV. `com.tcl.miracast` and `com.tcl.magiconnectfree` were re-enabled on 27 October 2026 for wireless display, and `com.google.android.videos` is still the factory-disabled one that the list never touched.
- The whole "Add photo" chain works: picker opens, four pictures show, picking one puts it in the preview, Save puts it in the dock, and the wash turns to the colour of the picked picture. "Reset to original" then deleted the saved PNG and cleared the prefs. The test pictures were removed from the TV afterwards.
- The final build, after the second smoothness pass: a scripted dock sweep measured 16 ms p50 and 2.81% jank, a grid sweep 13 ms and 2.75%, and the settings screen 7 ms and 3.36%. The crash buffer was empty. The dock and the grid were photographed: the ring is centred on the grown card, the title is level with it, the labels clear the ring, the up chevron floats above the grid, the hidden Inputs pill is skipped, and the Wi-Fi pill reads the network name.
- Wireless display, 27 October 2026: after re-enabling `com.tcl.miracast` and `com.tcl.magiconnectfree`, the TV was started into `com.tcl.miracast/.SinkActivity` from MagiConnect → Wireless Display, p2p0 came up at `192.168.49.1`, `wifi_p2p_device_name` read **Ali TCL**, and a Windows Wi-Fi Direct scan found exactly that device, enabled and unpaired, ready for `Win` + `K`.

TizenTube carries the custom label "YouTube" and the icon `app:io.gh.reisxd.tizentube.cobalt:banner`. The user asked to keep that. It is data in the TV's `app-styles` prefs, not a code default. To put the real name back: remove `io.gh.reisxd.tizentube.cobalt.label` from those prefs, press "Reset to original" in the app's Edit screen, or set the name again in Edit and Save.

## Do not

- Uninstall system packages.
- Disable anything in the Keep list to hide a tile.
- Run `dumpsys tv_input`.
- Chain several `pm` commands in one device shell. They stop after the first.
- Put the down arrow back inside the vertical LinearLayout.
- Make the up arrow a view pinned to the stage. It must scroll with the first row.
- Draw the focus ring at the card's unscaled rect. The ring has to be drawn at the card's live scale, or a growing card grows out of its ring.
- Find the ring's position with `offsetDescendantRectToMyCoords`. It leaves out the translation of a parent, and the input menu is moved with `setX` and `setY`. Use `rectInRoot()`.
- Hide the ring in the top row and forget to bring it back. `GlassHighlight.springTo` returns early when the target has not moved, so the visibility has to be restored on that path too.
- Give the ring a length or a curve of its own. Everything on this screen moves on `Motion`, or two things that should happen together drift apart.
- Show the input menu before it has been placed under the Inputs button. It shows in the top left corner for a frame.
- Let the ring fade out where it was before it moves. It has to go straight to the new place, or the old border is seen for a few frames and then it jumps.
- Use a white focused background on the edit name field.
- Measure the grid's position with `getLocationInWindow` and then set the translation from it. The position already contains the translation, and using it again is a feedback loop, the grid shakes and the focus looks stuck. Take the translation out, see `alignGrid()`.
- Change the ring to fix the grid. The ring measures the same 407 px in both places and sits evenly in the dock. The art is what is wrong.
- Put the space above the grid's first row in an item offset. `scrollToPositionWithOffset` eats it and the first row lands under the clock. It is the RecyclerView's padding.
- Give the stage a negative margin so the grid can grow. The layout then pushes the dock down and the dock jumps from the middle of the screen. The margin goes on the grid, the grid overflows the stage, and the dock does not move.
- Scroll the grid to the focused row without measuring whether the rows fit. The grid is shorter than the screen, so it must stay at the top.
- Read the dock from `Dock.PACKAGES`. It is in the preferences now, the user can change it. `Dock.load(context)` is the one to use.
- Let the focus land on a button the user has hidden. The top row's focus must skip hidden buttons, see `keepTopVisible()` and `stepTop()`. A hidden button has no border to show and its menu still opens on OK, so the focus sits on nothing and the wrong menu appears.
- Fall back to index 0 when no button is visible. `firstVisiblePill()` returns -1 for a reason: with every button hidden, UP must stay on the dock and OK must launch the dock app, never open the Inputs menu.
- Enter the top row from the dock without checking `hasVisiblePill()`. The row being empty is a normal state the user can create.
- Assume the app list is fresh because `onCreate` ran. Installs and uninstalls arrive as package broadcasts; `watchPackages()` reloads the grid after a 600 ms debounce, and only the newest load may apply.
- Join a network with `addNetwork`, `enableNetwork` or `connect`. Android 12 refuses all three to a normal app. `addNetworkSuggestions` is the only road, see the Wi-Fi menu section.
- Call `adb install` if it hangs. Push and `pm install -r`.
- Leave screensaver pointing at a disabled dream.
- Call `FoxMascotView.refreshTimePeriodFromClock()` while Meadow is mounted. Meadow's period comes from `setMeadowHour()`; the wall clock overwrites it in preview and drops the 07/11/18 choreography.
- Let any key except Back/Escape leave the screensaver preview. Left/Right step hours, Up/Down and OK are consumed; the control legend fades but the preview stays until Back.
- Turn dither on for the ambient wash or any other full-screen paint. On this panel it costs about 30 ms per pass and drops the frame onto the slow path. The grain covers what dither would have covered.
- Rebuild the ambient's radial gradient per frame. It is baked once into the mask bitmap and tinted; the fade changes only the tint.
- Put a `clipPath` back into `GlassHighlight` for a halo. The halo never drew (5 dp spread against a 4.5 dp ring), and `clipPath` is expensive. The seam is a stroked annulus now.
- Measure the title from the card's scaled left. `setLayoutParams` then runs on every frame of the grow and relayouts the whole screen. Use the unscaled left, see `alignTitle()`.
- Call `alignTitle()` or `alignGrid()` from `focusTick`. It is a position loop, not a layout loop.
- Use `wrap_content` for a menu row. The `MarkView` measures zero height and the focused band disappears.
- Call `setText` on the clock every second without comparing first.
- Rebuild the dock in the settings screen without restoring the focus and the ring; use `rebuildDock(focusIndex)`.
- Let BACK finish the settings activity while a dialog is open. It must close the dialog first.
- Reintroduce the `Perf` hooks. They were for measurement and are gone from the final build.
- Disable `com.tcl.miracast` or `com.tcl.magiconnectfree` while wireless display from the PC is wanted. Windows finds the TV as **Ali TCL** only while the Miracast sink runs, and MagiConnect is the only user-facing way to start it.

## Not finished / easy to break

- Edit "other apps" row scrolls sideways; the last icon is cut off until you move right. That is intentional. Do not go back to a wrapping grid that clips the second row.
- Uninstall was not confirmed on a real user app. The menu opens the system intent only.
- Network debugging is still on. The user has to turn it off on the TV; nothing in the launcher needs it any more.
- The top row currently hides Inputs and Wi-Fi; Settings is shown. That is the state left on the set after the 25 October tests and the 27 October casting work, confirmed by reading `shared_prefs/dock.xml`. The all-hidden case is fixed and tested, but it is not the live setting.
- Quick Paste is uninstalled. If the user wants it back, it has to come from Play Store; it is not in `backup\disabled-by-us.txt`.
- The abandoned Wi-Fi panel was deleted from `MainActivity.java` with its imports. The pill opens `Settings.ACTION_WIFI_SETTINGS` directly; do not rebuild the menu without solving the Android 12 saved-network restriction first.
- README, `restore.ps1`, and `launcher\build-and-install.ps1` exist. Keep them accurate if you change package names, dock order, or paths.

## Testing without a person at the remote

`adb shell input keyevent --longpress KEYCODE_DPAD_CENTER` works on this set and opens the App Info / Uninstall / Edit menu. That is the only reliable way to reach the Edit screen, because `EditAppActivity` is not exported and `am start` on it is refused with a SecurityException.

The `home` preferences hold `ssid-asked`. It is only set when the launcher has to ask for location access, so granting the permission by hand (`pm grant`) never causes a dialog.

To edit the launcher's own preferences by hand, stop it first, then write the file:

```powershell
adb -s 192.168.1.21:5555 shell am force-stop com.cleanhome.tv
adb -s 192.168.1.21:5555 shell "cat /data/local/tmp/app-styles.xml | run-as com.cleanhome.tv sh -c 'cat > /data/data/com.cleanhome.tv/shared_prefs/app-styles.xml'"
```

`/data/local/tmp` is not readable by the app uid, so the file has to be piped in from the shell side. Remove the `.bak` next to it afterwards, or delete the stale one first.

## User decisions already made

- Dock, left to right: Netflix, TizenTube, Nuvio, NetMirror. YouTube stays in the grid, not the dock.
- Keep Assistant and Chromecast. Disable Alexa, both AirPlay packages, TalkBack, HearAid, ambient screensaver, TCL account, and `com.tcl.ttvs`. Miracast and Magic Connect were re-enabled on 27 October 2026 for wireless display from the PC, so they are no longer on the disable list.
- Keep Netflix and YouTube as streaming apps. The three extra dock apps stay installed even though they were not on the first keep list.
- Build our own launcher. Do not install a third-party launcher.
- On open and on Home, focus the first dock app.
- TizenTube keeps the custom name "YouTube" and the banner icon. Do not clear it.
- The Wi-Fi pill should show the network name, not the word "Wi-Fi".
