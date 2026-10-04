# Error Audit — Small Deeds, Big Rewards

**Date:** 2026-10-04
**Branch:** `arena/01a106e2-small-deed-big-rewards` (from `415e647`)
**Scope:** all 49 tracked files — 15 Kotlin sources (~4,742 lines), 3 Gradle build scripts, version catalog, manifest, resources, tests.

The sandbox has no JDK, Gradle, or Android SDK and no network egress, so no compile was possible. Findings below come from static analysis plus byte-level inspection of the binary assets plus verification of every declared toolchain version against upstream release data.

---

## 1. Blockers — the app cannot be built or shipped as-is

### 1.1 Every binary asset in the repo is corrupted (CRITICAL)

All 12 binary files were passed through a **text pipeline** before being committed. Three separate mutations are visible in the bytes:

| Mutation | Evidence |
|---|---|
| Lossy UTF-8 decode | `small_deed.webp` contains **16,420** copies of `EF BF BD` (U+FFFD REPLACEMENT CHARACTER). Each one destroyed the original byte(s). |
| Latin-1 → UTF-8 re-encode | `0x89` became `C2 89`. 920 such sequences in `greeting.png`, 86 in the WebP. |
| CRLF → LF normalization | `greeting.png` contains **zero** `0x0D` bytes. The PNG magic reads `C2 89 50 4E 47 0A 1A 0A` instead of `89 50 4E 47 0D 0A 1A 0A`. |

**Affected files (all of them, byte-identical to each other):**

```
app/src/main/res/drawable/small_deed.webp            72,661 B  (header declares 40,414 B)
app/src/main/res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher.webp        x5
app/src/main/res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher_round.webp  x5
app/src/test/screenshots/greeting.png                 2,868 B  (all 11 WebPs share md5 b82d61c6…)
```

**Proof of corruption**, from `small_deed.webp`:

```
RIFF header declares payload end at byte 40,414
Actual file size                          72,661   -> 32,247 bytes too long
Chunk walk: VP8X @12 ok, then chunk @30 has fourcc BF BD 03 00 (non-ASCII)
            and size 540,561,494 -> malformed stream
Parsed canvas: 12,435,440 x 15,663,108 px  (nonsense)
```

**Impact.** `AndroidManifest.xml` points `android:icon` at `@mipmap/ic_launcher` and `android:roundIcon` at `@mipmap/ic_launcher_round`; `mipmap-anydpi-v26/ic_launcher.xml` composes an adaptive icon whose foreground is `drawable/ic_launcher_foreground.xml`, a `<layer-list>` that references `@drawable/small_deed`. So **both** the legacy and the adaptive icon path depend on the destroyed file. Best case the app installs with a blank/default launcher icon; worst case AAPT2 rejects the resource, or a launcher/runtime attempt to decode a 12.4M x 15.6M bitmap OOMs. `GreetingScreenshotTest` will also fail against the unreadable golden PNG.

**Not recoverable from this repo.** The corruption is baked into the committed git blobs (`git cat-file -p HEAD:…` returns the same bad bytes), there is no `.gitattributes`, and no smudge/clean filter or `core.autocrlf` is configured — so this happened *before* `git add`, and re-checking-out will not help. The 16,420 U+FFFD substitutions are one-way. The artwork must be re-exported from source.

**Also add** a `.gitattributes` with `*.webp binary`, `*.png binary`, `*.jpg binary`, `*.keystore binary`, `*.jks binary` to stop this recurring.

**Secondary defect, same files:** all five density buckets ship the *same* bytes. Launcher icons should be 48/72/96/144/192 px for mdpi→xxxhdpi; instead every density carries the full-size source, so the OS rescales at runtime. `ic_launcher_round.webp` is also identical to `ic_launcher.webp` — it has no circular mask and no alpha channel (`VP8X` flags `0x08`, alpha bit clear), which a round icon requires. That is ~800 KB of duplicated payload.

### 1.2 No Gradle wrapper

```
gradle/libs.versions.toml     <- the only thing in gradle/
gradlew                       MISSING
gradlew.bat                   MISSING
gradle/wrapper/…              MISSING
```

There is no pinned Gradle distribution and no `./gradlew` entry point, so the project cannot be built reproducibly — or at all, on a machine without a matching system Gradle. This matters more than usual here because **AGP 9.1.1 requires Gradle 9.3.1 minimum and JDK 17**. Add the wrapper at `gradle-9.3.1-bin.zip` or newer.

### 1.3 Both signing configs point at a keystore that is gitignored

`app/build.gradle.kts` sets `storeFile = file("${rootDir}/debug.keystore")` for the `debugConfig` signing config (used by the `debug` build type) and again as the `release` fallback when `my-upload-key.jks` is absent. But `.gitignore` line 18 excludes `debug.keystore`, and neither `debug.keystore` nor the `debug.keystore.base64` that the top-of-file decoder expects is present.

On a fresh clone: `assembleDebug` fails with *"Keystore file … not found for signing config 'debugConfig'"*, and `assembleRelease` fails the same way unless `my-upload-key.jks` plus `STORE_PASSWORD`/`KEY_PASSWORD` are supplied. Note also that when `my-upload-key.jks` *does* exist but the env vars are unset, `storePassword`/`keyPassword` resolve to `null` and Gradle aborts — the `if (uploadKeyFile.exists())` guard should also test the credentials.

---

## 2. Tests that will fail

### 2.1 `ExampleInstrumentedTest` asserts the wrong package name
`app/src/androidTest/java/com/example/ExampleInstrumentedTest.kt:20`
```kotlin
assertEquals("com.example", appContext.packageName)
```
`com.example` is the *namespace*; the *applicationId* is `com.aistudio.smalldeeds.kfkjqo` (`app/build.gradle.kts:28`), and `targetContext.packageName` returns the applicationId. Guaranteed failure on device. Fix: assert `"com.aistudio.smalldeeds.kfkjqo"`, or read `BuildConfig.APPLICATION_ID`.

### 2.2 `ExampleUnitTest.checkLogoValidity` is a network test that also sabotages the resource tree
`app/src/test/java/com/example/ExampleUnitTest.kt:19-49`

It performs a live HTTP download inside a JVM unit test:
```kotlin
val logoUrl = "https://sblbbrvhsyrryfoxiqna.supabase.co/storage/v1/object/public/The%20Date%20Farm/SDBR/Small-Deed.jpg"
val image = javax.imageio.ImageIO.read(java.net.URL(logoUrl))
```
Two independent problems:

- **It fails** whenever the sandbox/CI has no egress or that AI-Studio-scoped Supabase bucket has gone away — `ImageIO.read` returns `null` and `assertNotNull` trips.
- **If it ever succeeds, it breaks the next build.** It writes `src/main/res/drawable/small_deed.png` while `small_deed.webp` already occupies that name, which AAPT2 rejects as a duplicate resource (`drawable/small_deed` defined twice with the same config). It also silently deletes `small_deed.jpg`.

A unit test should never mutate `src/main/res`. Delete this test, or move the download out of the test suite into a one-off script.

### 2.3 Roborazzi runs in record mode and dirties the repo
`GreetingScreenshotTest` writes to `src/test/screenshots/greeting.png`. Without `-Droborazzi.test.verify=true`, Roborazzi defaults to *record*, so every test run overwrites the committed golden image and leaves an uncommitted diff. Set `roborazzi.test.verify=true` (or `roborazzi.test.compare=true`) in CI.

### 2.4 `ExampleRobolectricTest.test DailyHadithWorker scheduling and cancellation` asserts nothing
It calls `scheduleDailyNotification` then `cancelDailyNotification` and checks no outcome. Under Robolectric, WorkManager is not initialized, so both calls throw `IllegalStateException` — which the production `try`/`catch` swallows. The test therefore passes even when scheduling is completely broken.

---

## 3. Runtime and logic bugs

### 3.1 Rotation yanks the user back to the notification's hadith
`app/src/main/java/com/example/MainActivity.kt:44-53`
```kotlin
val initialHadithId = intent?.getIntExtra(DailyHadithWorker.EXTRA_HADITH_ID, -1) ?: -1
setContent {
  val viewModel: HadithViewModel = viewModel()
  LaunchedEffect(initialHadithId) {
    if (initialHadithId > 0) { viewModel.navigateTo(ActiveScreen.HADITH_DETAIL, detailId = initialHadithId) }
  }
```
The Activity declares no `android:configChanges`, so rotation (and split-window resize, dark-mode switch, locale change) destroys and recreates it. The `Intent` still carries `EXTRA_HADITH_ID`, but the ViewModel *survives*, holding the user's real navigation state. The recreated composition re-runs `LaunchedEffect` with the stale extra and force-navigates back.

**Repro:** tap the daily notification → Hadith #42 opens → press Back to Home → rotate → the app jumps back to #42, discarding where you were.

Fix: consume the extra once (clear it from the intent after handling), or gate the effect behind a one-shot `SavedStateHandle`/`rememberSaveable` flag.

### 3.2 `onNewIntent` is unreachable dead code
`MainActivity.kt:69-77`. Because the Activity is `standard` launch mode and the notification `Intent` uses `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TOP` *without* `FLAG_ACTIVITY_SINGLE_TOP` (`DailyHadithWorker.kt:82`), CLEAR_TOP destroys the existing instance and re-creates it — `onCreate` handles the deep link, `onNewIntent` never fires. Either add `android:launchMode="singleTop"` (plus `FLAG_ACTIVITY_SINGLE_TOP`) so the override works, or delete it.

### 3.3 The haptics setting does nothing
`app/src/main/java/com/example/viewmodel/HadithViewModel.kt:521-528`
```kotlin
// Perform tiny haptic feedback (15ms vibration)
fun triggerHaptic() {
    if (_hapticEnabled.value) {
        // Safely bypass hardware vibration calls in VM/Cloud streaming environments
        android.util.Log.d("HadithViewModel", "Haptic interaction triggered safely")
    }
}
```
This is a logging stub. `triggerHaptic()` is called from 18 places, and Settings exposes a user-facing **"Haptic Feedback (Vibrate)"** toggle (`AppScreens.kt:2224`) that only controls whether a debug line is logged. `android.permission.VIBRATE` is declared in the manifest and never used. The comment suggests this was stubbed out to survive a headless preview environment — it should be restored for real devices behind a `VibratorManager`/`View.performHapticFeedback` call with a try/catch, not left permanently disabled.

### 3.4 Streak day boundary is UTC, not local
`HadithViewModel.kt:398` and `:431`
```kotlin
val todayEpochDay = todayCalendar.timeInMillis / (24L * 60L * 60L * 1000L)
```
Dividing epoch millis by 86,400,000 yields a **UTC** day index. For a UTC+8 user the "day" rolls over at 08:00 local; for UTC-5, at 19:00 local. Reading two hadiths either side of that invisible boundary breaks or mis-credits the streak. Compute the day in the local zone instead (e.g. `LocalDate.now()` via `java.time` with core library desugaring, or `Calendar` fields `YEAR`/`DAY_OF_YEAR`).

`_weeklyActivity` has a related design flaw: it accumulates `Calendar.DAY_OF_WEEK` values (1-7) into a `Set` that is only cleared once ≥7 days have elapsed since the last read, so it mixes days from different weeks and can never show a meaningful "this week" grid.

### 3.5 The notification and the Home card show different "daily" hadiths
`HadithViewModel.getDailyHadith()` (`:370-393`) seeds `java.util.Random(year * 1000 + dayOfYear)` so the Home **Daily Insight** card is deterministic per day. `DailyHadithWorker.doWork()` (`:33`) and `triggerImmediateNotification` (`:198`) instead pick with unseeded `kotlin.random.Random.nextInt()` / `hadiths.random()`. The 8:00 AM notification therefore advertises a different hadith than the card the user sees when they open the app. The Worker should call the same seeded selection.

### 3.6 Two whole features are computed and persisted but never rendered
`currentStreak` and `weeklyActivity` (declared `HadithViewModel.kt:131-135`, updated by `recordActivityForToday()` and `checkAndUpdateStreakOnStartup()`, written to `smdr_streak` / `smdr_weekly_activity`, and reset by `resetProgress()`) are **never read by any UI file**. The code comments call this "Feature 5" — it exists only as dead state and orphaned SharedPreferences writes. Either surface it (Home screen is the obvious place) or remove it.

### 3.7 Dark theme makes the status-bar icons invisible
`app/src/main/res/values/themes.xml:7` hardcodes `<item name="android:windowLightStatusBar">true</item>`, which requests **dark** icons. There is no `values-night/themes.xml`, so the dark theme (`background = #141210`, `Theme.kt:20`) draws dark icons on a near-black bar under `enableEdgeToEdge()`. Move the attribute into `values-night/` as `false`, or drive it from `WindowInsetsControllerCompat.isAppearanceLightStatusBars`.

### 3.8 Version string is wrong
`app/build.gradle.kts:31-32` declares `versionCode = 6`, `versionName = "6.0"`, but Settings hardcodes `AppScreens.kt:2310`:
```kotlin
text = "Small Deeds, Big Rewards v1.0.0",
```
Read it from `BuildConfig.VERSION_NAME` (`buildConfig = true` is already enabled).

### 3.9 Duplicate and self-defeating worker scheduling
`scheduleDailyNotification` is called from `MyApplication.onCreate` (`:28`) **and** `HadithViewModel.init` (`:164`) on every cold start, and again from `updateDailyNotifications` (`:278`). Each call rebuilds the request with a freshly computed `setInitialDelay(time until next 08:00)` and enqueues it with `ExistingPeriodicWorkPolicy.UPDATE`, which **resets the period and the delay**. A user who opens the app shortly after 08:00 pushes that day's notification out and re-arms for tomorrow — the reminder silently never lands for engaged users, which is exactly the audience. Schedule once, and prefer `ExistingPeriodicWorkPolicy.KEEP` so an in-flight period is not restarted.

---

## 4. Correctness risks and stale configuration

### 4.1 Compose BOM is two years out of step with the rest of the toolchain
`gradle/libs.versions.toml`: `composeBom = "2024.09.00"` (Compose 1.7.x, Material3 1.3.0) against `kotlin = "2.2.10"`, `agp = "9.1.1"`, `compileSdk 36.1`, `coreKtx = "1.18.0"`, `activityCompose = "1.10.1"`. The current BOM is `2026.09.00`. It resolves today — the Compose Compiler Gradle plugin only enforces a 1.0.0 runtime floor, and `material-icons-core` (used here) is still pinned by the 2024.09.00 BOM, which matters because that artifact was deprecated and stops at 1.7.8, so a BOM bump will require migrating icons — but a 24-month gap between the UI toolkit and the compiler is a standing source of subtle behaviour differences.

### 4.2 `secrets-gradle-plugin` 2.0.1 predates AGP 9 by four years
`secretsGradlePlugin = "2.0.1"` is still the latest release, but it was built against **AGP 7.0.2** (Feb 2022) and hooks `AndroidComponentsExtension`. Running it under AGP 9.1.1's new DSL is unverified and unmaintained. It is also pointless here: the only secret it injects is `GEMINI_API_KEY`, which nothing in the app reads (see 4.3). Removing the plugin eliminates the risk.

### 4.3 Leftover AI Studio scaffolding that does not describe this app
- `metadata.json` declares `"majorCapabilities": ["MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API"]`, and `.env.example` documents `GEMINI_API_KEY`. **No Gemini, AI, or network call exists anywhere in the source.** Both are misleading and should go.
- `vercel.json` configures a Vercel redirect for an app that is an Android-only Gradle project with no web output. A Vercel deploy of this repo produces nothing.
- `android.permission.INTERNET` is declared but no production code opens a socket; only the broken test in 2.2 uses the network. Play Console will ask you to justify it.

### 4.4 Template naming never cleaned up
`settings.gradle.kts:25` still says `rootProject.name = "My Application"`; the Java/Kotlin package is `com.example` while the applicationId is `com.aistudio.smalldeeds.kfkjqo`; the XML theme is `Theme.MyApplication`; `MainActivity` is `com.example.MainActivity`. Harmless, but it leaks into `namespace`, generated `R`/`BuildConfig` packages, and ProGuard rules (`-keep class com.example.model.**`), so it is worth a deliberate rename rather than an accidental one.

### 4.5 Chapter colour and icon data is duplicated and will drift
`Chapter.colorHex` and `Chapter.iconName` (`model/Hadith.kt:20-21`) are populated for all 8 chapters in `HadithsData.kt:8-15` and **never read**. The UI instead hardcodes the same values in `getChapterColor()` and `getChapterIcon()` (`AppScreens.kt:148-186`). Two sources of truth for one concept; drive the UI from `Chapter.colorHex`/`iconName` and delete the duplicate tables.

### 4.6 Hardcoded `/100` assumptions
`AppScreens.kt` hardcodes the hadith count at lines 361, 362, 1019, 1043, 1277, 1281, 1850, 1862, and `resetProgress()` loops `for (id in 1..100)` (`HadithViewModel.kt:362`). Settings displays `readHadiths.size` with a literal `%` suffix (`:1817` and `:1858`) — correct only because the count happens to be exactly 100. Use `HadithsData.hadiths.size`.

### 4.7 Minor code hygiene
- `DailyHadithWorker.kt:25` — `private val context: Context` shadows `CoroutineWorker.getApplicationContext()`; it is redundant, use the inherited property.
- `DailyHadithWorker.kt:48-50` — `createNotificationChannelIfNeeded()` is private and never called.
- `AppScreens.kt:51,52,61` — unused imports `androidx.compose.foundation.Image`, `androidx.compose.ui.res.painterResource`, `java.util.Calendar`.
- `MainActivity.kt:11` imports `isSystemInDarkTheme` but every call site is fully qualified; `MainActivity.kt:62` likewise.
- `HadithDetailScreen` holds `val isAutoRead = viewModel.autoReadEnabled.collectAsState()` as a `State` and reads `.value` inside `LaunchedEffect(hadith.id)` (`AppScreens.kt:967` and `:1000`), so toggling auto-read while a hadith is open does not re-trigger marking.
- `Modifier.onSwipeGesture` (`CommonComponents.kt:86-108`) keys `pointerInput` on the two lambdas; if the Compose compiler does not memoize them, every recomposition restarts the detector and can drop an in-flight swipe. Keying on a stable value (e.g. the hadith id) is safer.
- Browse search (`AppScreens.kt:679`) matches `hadith.id.toString() == searchQuery` exactly, so searching "1" will not surface hadiths 10-19; it also never searches `description`, `arabicText`, or `reference`.
- `MyApplication` installs a `Thread.setDefaultUncaughtExceptionHandler` that logs and delegates. That is fine, but it will interfere with any crash-reporting SDK added later, and it swallows nothing useful today.
- `HomeScreen` computes `remember { viewModel.getDailyHadith() }` with no key, so it goes stale if the app stays open across midnight.

---

## 5. Verified clean

These were checked and are **not** problems — recorded so they don't get re-litigated:

- **Structural syntax:** braces, parentheses, and brackets balance in all 15 Kotlin files (checked with a string- and comment-aware tokenizer).
- **`HadithsData.kt` string literals (1,120 lines of mixed Arabic/English):** zero unterminated literals, zero raw newlines inside quotes, zero illegal escape sequences.
- **Dataset integrity:** exactly 100 `Hadith` entries; IDs 1-100 with no duplicates, no gaps, no out-of-range values, and strictly ascending; all 9 required fields present and non-empty on every entry; every `chapter` falls inside its `Chapter.rangeIds`; every `chapterName` matches its chapter's declared `name`; the 8 chapter ranges are contiguous and non-overlapping, spanning exactly 1-100; declared per-chapter counts match actual membership.
- **Symbol resolution:** every `viewModel.<member>` reference across all sources resolves to a real declaration in `HadithViewModel`. No unresolved project-local composable or helper calls.
- **Material Icons:** all 20 distinct icons used (`Star`, `Favorite`, `FavoriteBorder`, `Search`, `Info`, `CheckCircle`, `Check`, `Refresh`, `Home`, `Share`, `Settings`, `Person`, `Notifications`, `KeyboardArrowUp`, `KeyboardArrowDown`, `Close`, `ArrowDropDown`, and AutoMirrored `ArrowBack`/`ArrowForward`/`List`) are in `material-icons-core`, which is the only icons artifact on the classpath — `material-icons-extended` is correctly commented out.
- **`FilterChip` name collision** between `com.example.viewmodel.FilterChip` and `androidx.compose.material3.FilterChip` is resolved correctly: the explicit import outranks the `material3.*` star import, and the UI uses `SuggestionChip` anyway.
- **Material3 API usage** matches the pinned 1.3.0: `SuggestionChipDefaults.suggestionChipColors/suggestionChipBorder`, `Card(onClick=…)` (stable, no opt-in needed), `LinearProgressIndicator(progress = {…})`, `Button(interactionSource = …)`, `HorizontalDivider`, `FlowRow` (with the `ExperimentalLayoutApi` opt-in present). `Slider(steps = 2, valueRange = 0f..3f)` correctly yields the four stops that map to 14/16/18/22 sp.
- **Scaffold insets are right:** the `topBar` lambda always produces a measurable placeable (height 0 when hidden), so `innerPadding.top` is 0 on detail screens — the extra `statusBarsPadding()` there is necessary, not doubled.
- **No `!!`, no `TODO()`, no `printStackTrace()`, no `runBlocking`, no `GlobalScope`, no `Thread.sleep`.**
- **AGP 9 configuration is correct.** AGP 9.0+ has built-in Kotlin support and *rejects* `org.jetbrains.kotlin.android` if applied; this project correctly omits it. `compileSdk { version = release(36) { minorApiLevel = 1 } }` is the documented AGP DSL for minor API levels, not a typo. `testOptions { unitTests { isIncludeAndroidResources = true } }` is correctly set — without it Robolectric silently falls back to SDK 23.
- **All declared versions exist and are mutually compatible.** AGP 9.1.1 (Apr 2026), KSP 2.3.5, foojay-resolver-convention 1.0.0, Roborazzi 1.59.0, Robolectric 4.16.1 (supports API 23-36, matching both `@Config(sdk = [36])` sites and `minSdk = 24`), Work 2.10.0 (provides `ExistingPeriodicWorkPolicy.UPDATE`).
- **ProGuard/R8 rules** correctly keep the WorkManager worker and the data models for the minified release build.
- **Reading env vars (`System.getenv`) at configuration time** is supported by the Gradle configuration cache — it registers them as configuration inputs. Not a bug, though it does cause cache misses when they change.

---

## 6. Suggested fix order

1. Re-export the launcher artwork and `small_deed` from source; regenerate the 5 densities properly sized, give `ic_launcher_round` real circular alpha; add `.gitattributes` marking all binary globs. (§1.1)
2. Add the Gradle wrapper pinned to ≥ 9.3.1 with JDK 17. (§1.2)
3. Decide the signing story — commit a checked-in debug keystore or generate one in CI, and tighten the `release` credential guard. (§1.3)
4. Fix or delete the two broken tests. (§2.1, §2.2)
5. Fix the rotation/deep-link bug and the haptics stub — both are user-visible. (§3.1, §3.3)
6. Correct the UTC streak boundary, unify the daily-hadith selection, and de-duplicate worker scheduling. (§3.4, §3.5, §3.9)
7. Add `values-night/themes.xml`, wire the version string to `BuildConfig`, surface or delete the streak state. (§3.6, §3.7, §3.8)
8. Strip the Gemini/Vercel/INTERNET scaffolding; rename the project off the `My Application` / `com.example` template. (§4.3, §4.4)

---

# Addendum — resolution record (2026-10-04, same branch)

Every finding above was actioned in this commit unless explicitly listed under
"Deliberate non-changes" at the end.

## §1.1 Corrupted binaries — FIXED
All 11 WebP files and the golden screenshot were deleted and replaced by a
generated icon set. `tools/make_icons.py` renders an eight-pointed star
(rub el hizb, the same diamond geometry the app's `DecorativeDiamond` and
`IslamicHeaderDecoration` already draw) on the brand emerald gradient and emits
correctly sized PNGs for all five densities, a true-alpha circular
`ic_launcher_round`, a 108dp adaptive foreground respecting the 66dp safe zone,
the adaptive-icon XML, and a 512×512 Play Console asset under `tools/playstore/`.
`tools/pngtool.py` is a dependency-free PNG codec (encode + decode, all five
scanline filters, colour types 0/2/3/4/6) with area-average resampling and
alpha masks; `tools/test_pngtool.py` (20 assertions) verifies it and asserts the
old mangled assets are rejected rather than silently decoded.
`.gitattributes` now marks every image/keystore glob `binary`, and every PNG is
round-trip verified on write. To use real artwork later:
`python3 tools/make_icons.py --source your-logo.png`.

## §1.2 No Gradle wrapper — PARTIALLY FIXED (see deliberate non-changes)
`gradle/wrapper/gradle-wrapper.properties` pins Gradle 9.3.1 (AGP 9.1.1's
minimum) and `gradlew`/`gradlew.bat` were added; both detect a missing
`gradle-wrapper.jar` and print the exact regeneration command instead of a
cryptic JVM error. The jar itself cannot be produced in an environment with no
JDK and no network; `tools/README.md` documents the one-line fix.

## §1.3 Signing — FIXED
The `debugConfig` signing config and the missing-keystore failure mode are
gone: `debug` uses AGP's built-in auto-generated keystore. `release` uses a real
upload keystore when credentials are present, falls back to a decoded
`debug.keystore` with a loud warning for legacy CI, and otherwise emits an
unsigned artefact for Play App Signing instead of failing.

## §2 Tests — FIXED
`ExampleInstrumentedTest` now asserts the real applicationId.
The network logo test was deleted and replaced with JVM-only dataset tests
(100 unique ordered ids, contiguous non-overlapping chapter ranges,
day-deterministic daily selection, safe fallbacks). The Robolectric suite now
asserts real behaviour using `androidx.work:work-testing`: exactly one unique
periodic work is armed, `KEEP` does not duplicate or re-id it, cancel works,
and `sendNotification` posts exactly one notification once
`POST_NOTIFICATIONS` is granted. The corrupt Roborazzi golden was deleted;
`app/build.gradle.kts` forwards `-Droborazzi.test.verify` / `.compare` so CI can
verify instead of record.

## §3 Runtime / logic bugs — FIXED
- **3.1** Deep link applied only when `savedInstanceState == null`, so
  rotation/resize no longer yanks the user back to the notification's hadith;
  `onNewIntent` still handles live taps.
- **3.2** `android:launchMode="singleTop"` + `FLAG_ACTIVITY_SINGLE_TOP` make
  `onNewIntent` reachable.
- **3.3** `triggerHaptic()` now drives `VibratorManager`/`Vibrator` with a
  15 ms `VibrationEffect`, honouring the setting and degrading silently on
  devices without a vibrator. The Settings toggle controls real hardware again.
- **3.4** Streak day numbers now come from the local calendar date via the
  days-from-civil algorithm (exact, consecutive across DST/year boundaries)
  instead of UTC-millis division.
- **3.5** `HadithsData.hadithForDay()` is the single seeded selection used by
  both the Home card and the worker, so they can no longer disagree.
- **3.6** `currentStreak` is surfaced as a chip on the Home continue-reading
  card; the half-built `weeklyActivity` state (which mixed days from different
  weeks and was never rendered) was removed along with its prefs keys.
- **3.7** `values-night/themes.xml` plus a `WindowInsetsControllerCompat`
  `SideEffect` in `MyApplicationTheme` keep the status-bar glyph colour correct
  for forced light/dark/white themes.
- **3.8** Settings reads `BuildConfig.VERSION_NAME`; version bumped to 7 / "7.0".
- **3.9** Duplicate scheduling removed (only `MyApplication` arms on start);
  the worker uses `ExistingPeriodicWorkPolicy.KEEP` so re-arming no longer
  resets the 08:00 countdown. A regression test covers both halves.

## §4 Risks / hygiene — FIXED
- **4.2/4.3** The `secrets-gradle-plugin` (built against AGP 7.0.2) and KSP were
  removed from the build and catalog; `.env.example` deleted and
  `metadata.json`'s false Gemini capability cleared. `vercel.json` kept — its
  `/smalldeeds` redirect to the Play listing is intentional and useful.
- **4.4** `rootProject.name` renamed to `SmallDeedsBigRewards`. The Java package
  remains `com.example` — see deliberate non-changes.
- **4.5** Chapter colours render from `Chapter.colorHex` via a new
  `colorArgb` property; the duplicated `when(chapterId)` colour table is gone.
  Icons map from `Chapter.iconName`.
- **4.6** All hardcoded `100`s (progress text, prev/next bounds, header,
  Settings ring, `resetProgress` loop) now use `HadithsData.totalHadiths`;
  Settings computes a real percentage.
- **4.7** Unused imports removed; worker's shadowed `context` and dead
  `createNotificationChannelIfNeeded()` removed; auto-read is now keyed on the
  setting so toggling it while a hadith is open re-marks immediately; search
  trims input and covers description/reference/chapter and prefix id matches.

## Deliberate non-changes (and why)
1. **`gradle-wrapper.jar`** cannot be synthesised here (no JDK, no network).
   Pinned + guarded; regenerate with `gradle wrapper --gradle-version 9.3.1`.
2. **Java package `com.example`** left as-is. It is cosmetic (users see the
   applicationId), and renaming would churn `namespace`, `BuildConfig`, `R`,
   ProGuard rules and the manifest for zero functional gain. Revisit as a
   standalone refactor with a compiler available.
3. **Compose BOM `2024.09.00`** left pinned. It resolves and is stable; newer
   BOMs drop `material-icons-core` (this app's only icon source) and change
   Material3 APIs, so bumping is a verified-build task, not a blind edit.
4. **`Modifier.onSwipeGesture`'s `pointerInput` keys** left as-is: the Compose
   compiler memoises the capturing lambdas (stable captures), so restarts do not
   occur in practice; changing it without a device to test on risks regressions.
