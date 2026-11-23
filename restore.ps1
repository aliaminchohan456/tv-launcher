# Re-enable every package this project disabled, and put Google TV home back.
# Nothing here was uninstalled. Factory-disabled com.google.android.videos is left alone.
# Usage: powershell -ExecutionPolicy Bypass -File .\restore.ps1

$ErrorActionPreference = "Stop"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$serial = "192.168.1.21:5555"
$list = Join-Path $PSScriptRoot "backup\disabled-by-us.txt"

& $adb start-server
& $adb connect $serial
Start-Sleep -Seconds 1

$packages = Get-Content $list | ForEach-Object { $_.Trim() } | Where-Object { $_ -and -not $_.StartsWith("#") } | Select-Object -Unique
foreach ($pkg in $packages) {
    Write-Output "enable $pkg"
    & $adb -s $serial shell pm enable --user 0 $pkg
}

Write-Output "enable com.google.android.apps.tv.launcherx"
& $adb -s $serial shell pm enable --user 0 com.google.android.apps.tv.launcherx
Write-Output "enable com.google.android.tungsten.setupwraith"
& $adb -s $serial shell pm enable --user 0 com.google.android.tungsten.setupwraith
& $adb -s $serial shell cmd package set-home-activity --user 0 "com.google.android.apps.tv.launcherx/.home.HomeActivity"
& $adb -s $serial shell settings put secure screensaver_components "com.google.android.apps.tv.dreamx/.service.Backdrop"
& $adb -s $serial shell input keyevent 3
Write-Output "Home is Google TV again. Turn network debugging off when you are finished."
