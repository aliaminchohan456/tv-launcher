# Clean Home

A small Android TV launcher for this TCL Smart TV Pro (G10, Android 12), plus the list of packages that were disabled on it. Packages were disabled for the current user. None of them were uninstalled.

The TV uses a DHCP address on ADB port 5555. The launcher installer discovers this TCL on the current Wi-Fi network. To supply its address manually, pass `-Serial IP:5555` to `launcher\build-and-install.ps1`. The host tools use `adb` from `%LOCALAPPDATA%\Android\Sdk\platform-tools`.

The address is DHCP and changes now and then. If `adb connect` times out, `adb mdns services` prints the current one as `adb-... _adb._tcp 192.168.1.x:5555`. After a TV reboot the set also asks for USB debugging authorisation again; accept that dialog on the TV before any command will run.

## Fox Meadow screensaver

Choose **Fox Meadow** in **Launcher Settings → Clock → Screensaver Background**. In manual preview, press Left or Right to step through the 24 hours, OK to greet the fox, and Back to return Home. The automatic screensaver follows the TV clock. The fox settles to sleep when the live clock crosses from 21:59 to 22:00.

The latest TCL recording shows the sunrise stretch, the morning flock, wind passing through the daytime grass, and the bedtime transition. [Watch the 80-second recording](videos/fox-meadow/sofa-review/Fox-Meadow-TCL-review.mp4) · [Open the review prompt and test results](Qwen-Fox-Meadow-handoff.txt)

## Put Google TV back

```powershell
powershell -ExecutionPolicy Bypass -File .\restore.ps1
```

That re-enables every package in `backup\disabled-by-us.txt`, turns the stock Google TV launcher and setup recovery back on, sets Home back to `com.google.android.apps.tv.launcherx`, and restores the ambient screensaver. `com.google.android.videos` was already disabled at the factory and is not touched.

## Turn one app back on

```powershell
adb connect 192.168.1.21:5555
adb -s 192.168.1.21:5555 shell pm enable --user 0 com.example.app
```

Use the package name from `backup\disabled-by-us.txt`. USB media playback needs `com.tcl.ui_mediaCenter`. A TCL soundbar update needs `com.tcl.UpdatePeripheral`. Firmware updates and TCL analytics are the same package, `com.tcl.ttvs`.

## Wireless display from the PC

The TV is a Miracast sink and appears in Windows as **Ali TCL**. `com.tcl.miracast` and `com.tcl.magiconnectfree` must stay enabled for this; both were turned back on for it. On the TV open **MagiConnect → Wireless Display** and press OK through the two tutorial pages. On the PC press `Win` + `K` and pick **Ali TCL**. Network adb drops for a few seconds while Wi-Fi Direct starts; reconnect afterwards. The sink screen is `FLAG_SECURE`, so screenshots of it are blank.

## Change the dock

The dock, left to right, is the list in `launcher\app\src\main\java\com\cleanhome\tv\Dock.java`. Edit `PACKAGES`, then:

```powershell
powershell -ExecutionPolicy Bypass -File .\launcher\build-and-install.ps1
```

An app that is not installed is skipped. Everything else stays in the grid, in alphabetical order.

## Change the wallpaper

The background stays black until a picture is present. After the launcher has been opened once, copy a JPEG or PNG:

```powershell
adb -s 192.168.1.21:5555 push wallpaper.jpg /sdcard/Android/data/com.cleanhome.tv/files/wallpaper.jpg
```

`wallpaper.png` works the same way. Restart the launcher. The file is only read from the app's own folder. Delete it to go back to black.

## Rename an app or change its icon

Long press an app on the home screen and pick **Edit**. There you can give it a different name, use a different picture, or use another app's icon. **Reset to original** puts the real name and the real icon back and deletes any picture you had saved. The name and icon only change on this home screen, not inside the app itself.

**Add photo** opens a picture picker built into the launcher. It lists the pictures that are on the TV. The first time you use it, the TV asks for permission to read your photos; say yes or there is nothing to pick. Pictures that were copied onto the TV with `adb push` are only listed after the TV has scanned them:

```powershell
adb -s 192.168.1.21:5555 shell content call --uri content://media/ --method scan_file --arg /sdcard/Pictures/pic1.png
```

## The Wi-Fi name

The Wi-Fi button shows the name of the network you are on, for example "Ali 5G". Android 12 only gives that to apps with location access, so the launcher asks for it once. If you say no, the button keeps saying "Wi-Fi" and it will not ask again. To allow it later:

```powershell
adb -s 192.168.1.21:5555 shell pm grant com.cleanhome.tv android.permission.ACCESS_FINE_LOCATION
```

## The look

The home screen is a black canvas with a wide, soft colour wash that follows the focused app and drifts along the dock as the focus moves, a frosted dock, and one crisp white focus ring. The ring sits just outside the card and has an even band of dark around it, so it stays readable even on a white card like Netflix. A focused card grows a little, brightens and lifts, and the picture inside it comes forward a shade. The other cards dim. The app name and the arrow under the dock belong to the dock, so they fade away when the focus moves to the buttons along the top, and the name slides in when it changes.

Moving the focus makes a click, the same one the rest of the set makes. It follows the TV's own sound effects setting, so if system sounds are off the launcher stays quiet too.

The dock container is a translucent dark shape with a faint white line. Its corner radius is the card radius plus the dock padding, 16 + 18 = 34 dp, so the inner cards and the outer container share one curve. The gap between the cards is 16 dp, and the padding is 18 dp, which is what the focused card needs to grow into together with its focus ring.

The other apps screen, the one under the dock, uses the same cards, the same ring and the same colour wash, and its columns line up with the cards in the dock.

The colour behind the screen comes from the focused app. A dark app would give a black screen, so the colour is lifted until it can light the panel. Every app in the dock lights the room.

The long press menu is a glass card in the same typeface, with a soft fill and a thin ring on the focused row.

## Screensaver

The plain clock is `com.cleanhome.tv/.ClockDream`. To use it:

```powershell
adb -s 192.168.1.21:5555 shell settings put secure screensaver_components com.cleanhome.tv/.ClockDream
```

## Typeface

The clock and labels use Outfit by Rodrigo Fuenzalida, licensed under the SIL Open Font License 1.1. The file is `launcher\app\src\main\assets\fonts\outfit.ttf`.

## When you are finished

Turn off USB debugging and network debugging in the TV settings. On this set, leaving network debugging on can cut power to the USB ports.
