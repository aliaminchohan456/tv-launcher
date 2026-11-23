# REA se APK Reverse Engineer karne ki Guide

**Yeh file doosre AI agent ke liye hai** â€” jisko koi bhi Android APK reverse engineer karna ho, REA (Reverse Engineer Anything) ke saath.

REA ek **MCP server** hai jo agent ko bina source code ke koi app/binary decompile karne deta hai. Ye
[github.com/morluto/rea](https://github.com/morluto/rea) par hai, npm package `rea-agents` hai, aur
website [rea.tools](https://rea.tools/) par hai.

---

## 0. Ek line me REA kya karta hai

- **APK decompile** â†’ JADX se Java code (manifest, classes, methods, references)
- **Native binary** â†’ Ghidra / Hopper / IDA se pseudocode
- **JS/Electron app** â†’ modules, imports, IPC
- **ADB device** â†’ packages, processes, logs, dumps, screen captures
- **Websites** â†’ page structure, scripts, network

Sab kuch **local** chalta hai. Har result me **Evidence** aata hai (coverage + limitations ke sath).

---

## 1. Setup (ek baar)

### 1.1 Requirements check

```powershell
node --version      # 22.19+ ya 24.11+ ya 26+
npm --version
java -version       # FULL JDK chahiye (jdk.compiler module), JRE nahi
# JDK ka asli path (Windows):
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.16.8-hotspot"
```

### 1.2 JADX engine download + SHA-256 verify (pinned release)

REA khud JADX **nahi** install karta â€” ye khud download karke verify karna hota hai.

```powershell
$jar = Join-Path $HOME '.local\share\rea\android\jadx-headless-mcp-0.7.1-all.jar'
New-Item -ItemType Directory -Force (Split-Path $jar) | Out-Null
Invoke-WebRequest -Uri 'https://github.com/1013503897/jadx-headless-mcp/releases/download/v0.7.1/jadx-headless-mcp-0.7.1-all.jar' -OutFile $jar
if ((Get-FileHash -Algorithm SHA256 $jar).Hash.ToLowerInvariant() -ne '6e5eacf500b64292bfb73c49797c1958f6ee44646e43e868039ae7feb573ff75') { throw 'SHA-256 mismatch - use it nahi' }
$env:REA_JADX_MCP_JAR = $jar
```

> âš ï¸ **SHA-256 verify karna mat chhodo.** REA ka apna rule hai ke bina verify kiye JAR use na ho.

### 1.3 REA install

```powershell
npm install --global rea-agents     # ya: npx rea-agents@latest ...
rea --version
rea doctor                          # node âœ“ host âœ“ jadx âœ“
```

`doctor` me `hopper` / `ghidra` / `ida` **fail** dikhenge â€” **wo theek hai**, sirf APK analysis ke liye
JADX chahiye. Native binary ka kaam karwana ho to Ghidra/Hopper install karna padega.

### 1.4 Agent me register karo (MCP ke liye)

```powershell
$env:REA_JADX_MCP_JAR = Join-Path $HOME '.local\share\rea\android\jadx-headless-mcp-0.7.1-all.jar'
rea setup --client opencode --yes
```

Ye `~/.config/opencode/opencode.jsonc` me likhta hai:

```json
"rea": {
  "type": "local",
  "command": ["C:\\nvm4w\\nodejs\\node.exe",
               "C:\\Users\\aliam\\AppData\\Roaming\\npm\\node_modules\\rea-agents\\scripts\\rea.mjs", "mcp"],
  "enabled": true,
  "environment": {
    "JAVA_HOME": "C:\\Program Files\\Eclipse Adoptium\\jdk-17.0.16.8-hotspot",
    "REA_JADX_MCP_JAR": "C:\\Users\\aliam\\.local\\share\\rea\\android\\jadx-headless-mcp-0.7.1-all.jar"
  }
}
```

**â†’ Agent restart zaroori hai** (tools tabhi aate hain). Check: MCP resources list me "rea" dikhna chahiye.

Supported clients: `claude_code`, `codex`, `cursor`, `gemini_cli`, `windsurf`, `opencode`, `copilot_cli`,
`grok_build`, `vscode`, `hermes`, `droid`, `dsh`, `kimi`, `qoder`, `trae`, `workbuddy`, `zcode` (ye aakhir
6.5.0+ chahiye).

---

## 2. Kaam shuru karne se pehle: APK nikalo

TV/phone se system aur user apps ke APKs:

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$s = "192.168.1.21:5555"        # apna IP
& $adb start-server; Start-Sleep 1
& $adb connect $s; Start-Sleep 3

# package ka path
$path = (& $adb -s $s shell pm path com.tcl.suspension) |
        Where-Object { $_ -match "package:" } |
        ForEach-Object { ($_ -replace 'package:','').Trim() } | Select-Object -First 1

# pull karo
New-Item -ItemType Directory -Force "$env:TEMP\apks" | Out-Null
& $adb -s $s pull $path "$env:TEMP\apks\com.tcl.suspension.apk"
```

**Split APKs**: kuch apps ke kai splits hote hain (`base.apk`, `split_config.xhdpi.apk`, `split_config.en.apk`).
Resources (strings, locales) `split_config.*` me hote hain, code `base.apk` me. Dono pull kar lo.

```powershell
# saare splits ek saath
(& $adb -s $s shell pm path <pkg>) | Where-Object { $_ -match "package:" } |
  ForEach-Object { ($_ -replace 'package:','').Trim() } |
  ForEach-Object { & $adb -s $s pull $_ "$env:TEMP\apks\$([IO.Path]::GetFileName($_))" }
```

---

## 3. Do raaste hain: MCP ya CLI

### 3.1 MCP (agent ke andar â€” yahi best hai)

`execute` ke andar `tools["rea"].<tool>` se call hota hai. Tool dhoondne ke liye:

```js
const r = search({ query: "inspect_android_method", limit: 20 });
// r.items[].path  ->  "tools.rea.inspect_android_method"
// r.items[].signature  ->  full parameter schema
```

**APK analysis ke useful MCP tools:**

| Tool | Kaam |
|---|---|
| `inspect_android_package` | Manifest + classes + resources ka overview (`detail: "complete"\|"summary"`) |
| `search_android_classes` | Class name se search (empty query = saare class names) |
| `inspect_android_class` | Class ka summary (fields, methods, overloads) |
| `inspect_android_method` | **Method ka Java/smali body** â€” asli decompiled code yahin se aata hai |
| `trace_android_references` | Kaun call karta hai, kaun use karta hai (callers/callees) |
| `project_android_application_graph` | Components + native bridges ka projection |
| `decode_android_resources` | Apktool se resources/strings decode |
| `inspect_adb_device` | Live device se packages/logs/screens |

**Typical call:**

```js
const apk = "C:\\Users\\aliam\\AppData\\Local\\Temp\\opencode\\apks\\launcherx-base.apk";
const r = await tools["rea"].inspect_android_method({
  path: apk,
  class_name: "androidx.leanback.widget.ScaleFrameLayout",
  method_name: "onMeasure"
});
return r.normalized_result.source.text;   // <- asli code yahan
```

`r.normalized_result` ke useful fields:
- `source.text` â†’ decompiled Java code (ya smali)
- `body_status` â†’ `available` / `not_available`
- `markers` â†’ `java_body_unavailable`, `native_method`, `abstract_method`, `static_initializer_disassembly` â€¦
- `limitations[]` â†’ kya establish nahi hua
- `evidence_id` â†’ usi session me aur queries me reference kar sakte ho

### 3.2 CLI (agent ke bahar â€” one-off kaam ke liye)

```powershell
$env:REA_JADX_MCP_JAR = Join-Path $HOME '.local\share\rea\android\jadx-headless-mcp-0.7.1-all.jar'
rea inspect-android-package  <apk> --json
rea search-android-classes  <apk> <query>
rea inspect-android-class    <apk> <class>
rea inspect-android-method   <apk> <class> <method> [--overload-index N]
rea trace-android-references <apk> <class> [--method-name M]
rea decode-android-resources <apk>
rea doctor
```

> **MCP prefer karo.** CLI ka output ek **double-encoded JSON** string hai (JSON ke andar JSON), jise
> parse karna mushkil hai. MCP me seedha structured object milta hai.

---

## 4. Asli workflow (step by step) â€” yehi follow karna

### Step 1 â€” Package ka overview
```js
const r = await tools["rea"].inspect_android_package({ path: apk, detail: "complete" });
r.normalized_result.package_name     // com.netflix.ninja
r.normalized_result.class_count      // 22060
r.normalized_result.manifest.text    // poora manifest XML
```

### Step 2 â€” Class dhoondo
```js
const r = await tools["rea"].search_android_classes({ path: apk, query: "CardFocus" });
r.normalized_result.matches   // ["com.google.android.libraries.tv.widgets.card.CardFocusableFrameLayout", ...]
r.normalized_result.total_classes
```
> `query` **case-sensitive substring** hai. Empty string = saare class names (22060 â€” bohat bada output).

### Step 3 â€” Class ka structure dekho
```js
const r = await tools["rea"].inspect_android_class({ path: apk, class_name: "..." });
r.normalized_result.methods    // [{name, reported_signature, overload_index, is_constructor, dex_descriptor}]
r.normalized_result.fields     // [{name, reported_type}]
```

### Step 4 â€” Method ka body nikalo (sab se important)
```js
const r = await tools["rea"].inspect_android_method({
  path: apk, class_name: "...", method_name: "setForeground"
});
r.normalized_result.source.text      // Java code
r.normalized_result.body_status      // "available"
```

> **Overload ambiguity**: same naam ke 2+ methods ho to `overload_index` zaroori hai. Guess mat karo â€”
> pehle `inspect_android_class` se overloads dekho, phir index do.

### Step 5 â€” Kaun use karta hai (references)
```js
const r = await tools["rea"].trace_android_references({
  path: apk, class_name: "...", method_name: "..."   // method_name optional
});
r.normalized_result.total            // 0 ho sakta hai!
r.normalized_result.references[]     // {kind, name, containing_class, direct_invokes:[...]}
```

> âš ï¸ **`total: 0` ka matlab "use nahi hota" nahi hai.** Aisi library classes hoti hain jo APK me bundled
> hain lekin launcher unhe use nahi karta. Ye ek valid finding hai â€” report karo, confuse mat ho.

### Step 6 â€” Manifest se launchable activities nikaalo
Manifest XML parse karo (PowerShell):

```powershell
$raw = Get-Content "$out\pkg-$a.txt" -Raw
$i = $raw.IndexOf('<?xml'); $j = $raw.LastIndexOf('</manifest>')
$x = $raw.Substring($i, $j-$i+10) -replace '\\r\\n',"`n" -replace '\\n',"`n" -replace '\\u003c','<' -replace '\\u003e','>' -replace '\\"','"' -replace '\\\\','\'
foreach ($b in [regex]::Matches($x, '<activity\b[\s\S]*?</activity>')) {
  $t = $b.Value
  $n  = [regex]::Match($t, 'android:name="([^"]+)"').Groups[1].Value
  $e  = [regex]::Match($t, 'android:exported="([^"]+)"').Groups[1].Value
  $cats = ([regex]::Matches($t,'android\.intent\.category\.([A-Z_]+)') | % { $_.Groups[1].Value } | Sort-Object -Unique) -join ','
  $acts = ([regex]::Matches($t,'android\.intent\.action\.([A-Z_.]+)') | % { $_.Groups[1].Value } | Sort-Object -Unique) -join ','
  if ($cats -or $acts) { "{0,-44} {1,-8} cat[{2}] act[{3}]" -f $n,$e,$cats,$acts }
}
```

---

## 5. âš ï¸ Parsing pitfalls (mujhe in me time laga)

| Problem | Fix |
|---|---|
| Manifest JSON-string ke andar hai, `\"` escaped | `\\r\\n` â†’ newline, `\\"` â†’ `"`, `\\u003c` â†’ `<` |
| `[xml]` parse fail hota hai | XML entities/control chars hote hain â€” **regex use karo**, XML parser nahi |
| Regex `\"body\":\"` se start karna padta hai | CLI output double-encoded hai; MCP me seedha `source.text` lo |
| `search-android-classes` ka CLI output 3 MB+ | MCP use karo, ya `Out-File` karke parse karo |
| Empty query CLI se drop ho jata hai | MCP me `query: ""` chalta hai |

---

## 6. âš ï¸ ADB ke traps (is TV par)

| Trap | Asli wajah | Fix |
|---|---|---|
| `input keyevent KEYCODE_DOWN` **kaam hi nahi karta** | `KEYCODE_DOWN` valid naam **nahi** hai â†’ system `keyCode 0` (UNKNOWN) bhejta hai | `input keyevent **KEYCODE_DPAD_DOWN**` likho |
| `adb devices` khali dikhta hai | local adb server har shell ke baad mar jata hai | har command block me `adb start-server` â†’ `adb connect` â†’ 2-3s sleep |
| `screencap` bohat slow (1 screenshot â‰ˆ 1 min!) | TV ka screencap heavy hai | batch me lo, ya device-side `screencap` loop chalao |
| TV ka IP change ho jata hai | DHCP | har baar `pm path` se verify karo, aur `summary.md` me IP update karo |

**Current TV:** `192.168.1.21:5555` (pehle `192.168.1.13` tha)

---

## 7. Findings cheat-sheet (already nikaale hue)

### 7.1 Google TV launcher (`com.google.android.apps.tv.launcherx`) â€” focus architecture

22,060 classes, 2,958 resources. APK: `/data/app/.../launcherx-.../base.apk` (+ `split_config.xhdpi.apk`, `split_config.en.apk`)

**Scale = Leanback ka `androidx.leanback.widget.ScaleFrameLayout`:**
```java
// addView: scale ek TRANSFORM hai, re-measure nahi
view.setScaleX(this.c);
view.setScaleY(this.c);

// onMeasure: container apna size scale ke hisaab se nikalta hai
float f = this.a;
if (f == 1.0f && this.b == 1.0f) { super.onMeasure(i, i2); return; }
super.onMeasure(a(i, f), a(i2, this.b));
setMeasuredDimension((int)((getMeasuredWidth()*this.a)+0.5f), (int)((getMeasuredHeight()*this.b)+0.5f));

// onLayout: pivot â†’ parent ka RIGHT edge + vertical centre
childAt.setPivotX(width - i7);
childAt.setPivotY(pivotY - i11);
// ... aur padding se space compensate hota hai

// setForeground â†’ THROWS
public final void setForeground(Drawable drawable) { throw new UnsupportedOperationException(); }
```

**Ring = card ka apna foreground:**
```java
// com.google.android.libraries.tv.widgets.card.CardFocusableFrameLayout
public final void setForeground(Drawable drawable) {
    try { this.a = true; super.setForeground(drawable); }
    finally { this.a = false; }
}
public final void requestLayout() {
    if (this.a) return;      // foreground set karte waqt dobara layout mat karo
    super.requestLayout();
}
```

**Lesson (hamare launcher ke liye):** ring ko card ke **foreground** me rakhna behtar hai â†’ scale ke sath
automatic chalti hai, per-frame kaam **zero**. Trade-off: foreground card ke bounds ke **andar** hota hai â†’
ring ke liye padding chahiye â†’ art ~8dp chhota (180Ã—101 â†’ 172Ã—93). Hamara dark seam us padding me fit
ho jata hai, to white card par ring readable bhi rehta hai.

### 7.2 Streaming apps â€” launchable activities

| App | Sirf ek launchable activity |
|---|---|
| Netflix (`com.netflix.ninja`) | `com.netflix.ninja.MainActivity` |
| YouTube TV (`com.google.android.youtube.tv`) | `com.google.android.apps.youtube.tv.activity.ShellActivity` |
| TizenTube (`io.gh.reisxd.tizentube.cobalt`) | (same pattern) |

**Shortcut meta-data kisi me bhi nahi** â†’ "Netflix â†’ sirf Movies" jaisa shortcut apps publish nahi karte.
Lekin **deep links** hain, jo dock me daal sakte hain:
`nflx://www.netflix.com` Â· `vnd.youtube://` Â· `vnd.youtube.launch://` Â· `cast://Netflix`

### 7.3 TCL quick panel (`com.tcl.suspension`)

Classes: `QuickPanelDialogManager` Â· `QuickPanelInitial` Â· `ui.QuickPanelDialog` Â· `handler.QuickPanelExitHandler`

**Actions (manifest se):**
```
com.tcl.quickpanel.show.window   â† panel SHOW
com.tcl.quickmenu.dismiss        â† band
com.tcl.suspension.action.main
com.android.tv.action.VIEW_INPUTS  â† input screen kholta hai
com.tcl.suspension.VIEW_INPUTS
```
```java
// QuickPanelDialogManager
private void initStartWay(Intent intent) {
    this.openInputWhenStart = intent.getBooleanExtra("openInput", false);
    String systemCountry = PlatformImplUtil.getInstance().getSystemCountry(...);
    if ("US".contains(systemCountry) || "CA".contains(systemCountry) || "MX".contains(systemCountry)) {
        this.openInputWhenStart = true;   // US/CA/MX par seedha input khulta hai
    }
}
private void regOtherReceiver() {
    IntentFilter f = new IntentFilter();
    f.addAction("android.intent.action.SCREEN_OFF");
    f.addAction("android.intent.action.SCREEN_ON");
    f.addAction(Const.ACTION_HOME);
    f.addAction("android.intent.action.CLOSE_SYSTEM_DIALOGS");
    this.sContext.registerReceiver(this.finishQuickPanelBroadcastReceiver, f);
}
```

**âš ï¸ Behaviour note:** ek DOWN press par ye system quick panel hamare launcher ke **upar** aa sakta hai, aur
usme **"Google TV Home"** (humne disable kiya hua stock launcher) ek **input ki tarah** listed rehta hai.

---

## 8. Ek complete worked example (copy-paste)

```js
// 1. APK ka overview
const apk = "C:\\Users\\aliam\\AppData\\Local\\Temp\\opencode\\apks\\launcherx-base.apk";
const pkg = await tools["rea"].inspect_android_package({ path: apk, detail: "summary" });

// 2. Class dhoondo
const found = await tools["rea"].search_android_classes({ path: apk, query: "CardFocus" });
// -> ["com.google.android.libraries.tv.widgets.card.CardFocusableFrameLayout", ...]

// 3. Class ka structure
const cls = await tools["rea"].inspect_android_class({
  path: apk, class_name: "com.google.android.libraries.tv.widgets.card.CardFocusableFrameLayout"
});
// methods: <init>x4, requestLayout, setForeground

// 4. Method ka body
const body = await tools["rea"].inspect_android_method({
  path: apk,
  class_name: "com.google.android.libraries.tv.widgets.card.CardFocusableFrameLayout",
  method_name: "setForeground"
});
return body.normalized_result.source.text;

// 5. Kaun use karta hai
const refs = await tools["rea"].trace_android_references({
  path: apk,
  class_name: "com.google.android.libraries.tv.widgets.card.CardFocusableFrameLayout",
  method_name: "setForeground"
});
return refs.normalized_result.references;
```

---

## 9. Troubleshooting

| Problem | Solution |
|---|---|
| MCP tools nahi dikh rahe | Agent restart karo. Config check: `~/.config/opencode/opencode.jsonc` me `"rea"` hona chahiye |
| `doctor` me `jadx: missing_dependency` | `REA_JADX_MCP_JAR` env var set nahi â€” `doctor` ke shell me set karo |
| `Cannot execute JAVA_HOME java ... ENOENT` | `JAVA_HOME` galat path â€” `Get-Command java` se exact path lo |
| `resource_constraint` / heap fail | `REA_JADX_HEAP_MIB=8192` set karo, ya `JAVA_TOOL_OPTIONS=-Xmx8g` |
| Bada APK time out | `REA_JADX_OPERATION_TIMEOUT_MS=600000` |
| `body_status: not_available` | Native/abstract method hai, ya JADX ne Java body nahi banayi â€” `markers[]` dekho, fallback smali aata hai |
| `fell_back: true` | Java body nahi mili, smali diya gaya â€” woh bhi useful hai |
| `total: 0` references | Class bundled hai lekin use nahi hoti â€” valid finding |

---

## 10. Yaad rakhein (ethics + rules)

- REA doosre apps ka code **decompile** karta hai. Usse **seekhna** (techniques, timings, behaviour,
  measurements) legal hai aur yehi REA ka apna disclaimer kehta hai. **Proprietary code copy karna nahi.**
- REA **target ko kabhi execute nahi karta** â€” sirf static analysis. Isi liye "ye runtime par aisa karta hai"
  kabhi prove nahi hota, sirf "ye code aisa likha hai" hota hai.
- Hamesha **Evidence ke sath** report karo: kya dekha, kya establish nahi hua, kya unknown raha.
- JADX ka **SHA-256 hamesha verify** karo.
- Bade output (22k classes) ko file me daal ke filter karo, screen pe mat ghisairo.

---

## 11. Is project me kya hua (TV launcher)

- **Project:** `C:\Users\aliam\OneDrive\Desktop\tv` â€” Java + XML Views ka custom TCL Android TV launcher
- **TV:** `192.168.1.21:5555` (pehle `192.168.1.13` tha), "Smart TV Pro", Android 12 (SDK 31)
- **Build:** `powershell -ExecutionPolicy Bypass -File .\launcher\build-and-install.ps1`
- **Handoff doc:** `summary.md` (poora project ka asl record yahin hai â€” **pehle woh padho**)
- **Restore:** `powershell -ExecutionPolicy Bypass -File .\restore.ps1` (Google TV wapas, sab debloat undo)
- **REA is project me:** Google TV launcher decompile karke focus architecture seekha gaya (Â§7.1) aur TCL quick
  panel ke actions nikle (Â§7.3)
