# Build the debug launcher and install it on the TV.
# Usage: powershell -ExecutionPolicy Bypass -File .\build-and-install.ps1
param([string]$Serial)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$apk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"

if (-not (Test-Path (Join-Path $root "local.properties"))) {
    $sdk = ($env:LOCALAPPDATA + "\Android\Sdk").Replace('\', '\\')
    Set-Content -Path (Join-Path $root "local.properties") -Value ("sdk.dir=" + $sdk) -Encoding ascii
}

& (Join-Path $root "gradlew.bat") -p $root :app:assembleDebug --no-daemon
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

function Invoke-Adb {
    $output = & $adb @args
    if ($LASTEXITCODE -ne 0) { throw "ADB failed ($LASTEXITCODE): $args" }
    $output
}

Invoke-Adb start-server
if (-not $Serial) {
    # Identify this TCL by the persistent ADB service name across Wi-Fi changes.
    $services = Invoke-Adb mdns services
    $tvService = $services | Select-String '^adb-D00650B304B0BB069\s+_adb\._tcp\.?\s+(\S+)\s*$' | Select-Object -First 1
    if (-not $tvService) { throw "TCL TV not discovered. Supply -Serial IP:port." }
    $Serial = $tvService.Matches[0].Groups[1].Value
}
Invoke-Adb connect $Serial
$expectedHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $apk).Hash
$staged = "/data/local/tmp/cleanhome.apk"
Invoke-Adb -s $Serial push $apk $staged
$result = Invoke-Adb -s $Serial shell pm install -r $staged
if ($result -notcontains 'Success') { throw "Install failed: $result" }
Invoke-Adb -s $Serial shell sync
$paths = Invoke-Adb -s $Serial shell pm path --user 0 com.cleanhome.tv
$base = $paths | Select-String '^package:(/data/app/[^\s]+/base\.apk)$' | Select-Object -First 1
if (-not $base) { throw "Package Manager cannot locate the installed launcher APK." }
$digest = Invoke-Adb -s $Serial shell sha256sum $base.Matches[0].Groups[1].Value
if (($digest -split '\s+')[0] -ne $expectedHash) { throw "Installed launcher APK failed verification." }

$homeActivity = "com.cleanhome.tv/.MainActivity"
Invoke-Adb -s $Serial shell cmd package set-home-activity --user 0 $homeActivity
Invoke-Adb -s $Serial shell sync
$resolved = Invoke-Adb -s $Serial shell cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME
if ($resolved -notcontains $homeActivity) { throw "Android resolved Home to: $resolved" }
Invoke-Adb -s $Serial shell am start -W --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME
Invoke-Adb -s $Serial shell rm -f $staged
Write-Output "Installed APK verified. Android Home resolves to $homeActivity."
