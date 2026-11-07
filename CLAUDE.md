# CLAUDE.md — Leave-N-Lock (US BlueLink auto-locker)

> **This file is the source of truth for how this codebase is built and must be kept
> up to date.** Whenever you change architecture, conventions, dependencies, the
> detection flow, or the BlueLink client, update the relevant section in the SAME
> change. Treat drift between code and this file as a bug.

Leave-N-Lock is a US-only, English-only fork of [i5-AutoLock](https://github.com/Vel-San/i5-AutoLock)
by Vel-San (MIT). Credit for the original app, design and detection engine is Vel-San's.

## 1. Mission (do not scope-creep)
One job: **automatically detect that the user has left their (unlocked) Hyundai and lock it via
the Hyundai BlueLink (US) API.** Tested on an Ioniq 5; other BlueLink-enabled US Hyundais may
work. Everything else exists only to make that reliable, private, and effortless. No trip
logging, multi-user, cloud, or analytics. Kia, Genesis and non-US regions are out of scope.

## 2. Tech stack
- 100% Kotlin, JDK 17, `minSdk 26`, `compileSdk 37`, `targetSdk 35`. Toolchain: Gradle 9.8.2,
  AGP 9.5.1, Kotlin 2.4.21, KSP 2.3.13, Hilt 2.60.1, Ktor 3.6.1, OkHttp 5.5.1.
  `targetSdk` stays 35 on purpose: raising it opts into Android 16/17 runtime behaviour changes
  (background, foreground-service and Bluetooth rules) that need their own branch and car test;
  `compileSdk 37` is only what the androidx libraries require to build.
- UI: Jetpack Compose + Material 3, themed with the **Porcelain Grid** design system
  (`ui/theme/porcelain/`, see §6b). Dynamic colour is **off**; the user picks System default /
  Light / Dark. Fonts are bundled in `res/font/`. No XML layouts (except the widget).
- DI: Hilt. Async: Coroutines + Flow. HTTP: Ktor (OkHttp engine) + kotlinx.serialization.
- Storage: DataStore (settings), EncryptedSharedPreferences/Keystore (PIN + US sign-in).
- Background: Foreground Service (`connectedDevice`) + BroadcastReceivers. WorkManager via Hilt.
- Build: Gradle version catalog (`gradle/libs.versions.toml`), KSP (never kapt). AGP 9's
  built-in Kotlin compiles the app: `app/build.gradle.kts` does **not** apply
  `org.jetbrains.kotlin.android` (the root keeps it `apply false` only to pin KGP 2.4.21, which
  must match the compose/serialization plugins). Compiler settings go in the top-level
  `kotlin { compilerOptions { … } }` block (`kotlinOptions` is gone). Generated sources/assets
  go through the Variant API (`androidComponents.onVariants { sources.… }`), not `sourceSets`
  with a Provider: see `SyncChangelogAsset`, which bundles `CHANGELOG.md` as an asset.
  `hiltViewModel` comes from `androidx.hilt.lifecycle.viewmodel.compose`.
- App id / namespace / Kotlin package: `io.github.gthibo.leavenlock`. Strings: English only
  (`res/values/strings.xml`).

## 3. Package map (`app/src/main/java/io/github/gthibo/leavenlock`)
- `data/settings/` — `AppSettings`, `SettingsRepository` (DataStore). All non-secret prefs.
- `data/secure/` — `SecureStore`: BlueLink PIN + US email/password, encrypted. **Never log them.**
- `data/device/` — `BluetoothDevices` (bonded devices for the car picker).
- `data/location/` — `LocationHelper` (last location + reverse-geocoded parked label).
- `data/detection/` — `ActivityRecognitionManager`, `GeofenceManager`.
- `data/status/` — `StatusCache` (last full status; the single source for the Home card, widget
  and notification). Save command results with `saveStatus(…, fromCommand = true)`: for
  `CachedStatus.COMMAND_HOLD_MS` (30 min) a plain read the car reported before that command, or
  with no report time, keeps the command's lock/door state (`CachedStatus.after`). The cache holds
  one vehicle; `clear()` on vehicle switch.
- `data/metrics/` — `ApiMetrics` (persisted telemetry: op name, duration, outcome only).
- `data/backup/` — `BackupManager` (non-secret settings export/restore).
- `data/sound/` — `EvChime`, `LockSound`.
- `data/bluelink/` — `BlueLinkClient` (interface), `BlueLinkProvider` (demo → fake, else US),
  `MeteredBlueLinkClient` (times every call into `ApiMetrics`), `CertPins`, `model/`.
  - `us/` — `UsBlueLinkClient` + `UsCredentialStore` (see §4b).
  - `fake/` — `FakeBlueLinkClient` for demo mode and tests.
- `domain/` — `detection/` (`DetectionState`, `DetectionEvent`, pure `LockStateMachine`),
  `usecase/LockPolicy` (pure `decide()`), `AutoLockController` (timers + side effects),
  `ActivityLog`, `StatusSummary`.
- `service/` — `AutoLockService` (foreground, watch mode), `AutoLockNotification`, `NotificationChannels`.
- `receiver/` — Bluetooth (primary trigger), boot, geofence, activity-transition receivers.
- `tile/`, `widget/` (`AutoLockWidget`, pure `widgetActionFor` → `WidgetAction`),
  `work/StatusRefreshWorker`, `di/AppModule`.
- `ui/` — Compose: `home/`, `settings/`, `login/`, `stats/`, `help/`, `about/`, `onboarding/`,
  `permissions/`, `components/` (`Porcelain.kt`: the shared design pieces — `PgCard`, `PgButton`,
  `PgSwitch`, `PgRadio`, `PgSlider`, `PgLabel`, `SegmentMeter`, `StatTile`, `SettingRow`,
  `PgField`, `PgTopBar`, `PorcelainMark`; `Hero.kt`: `HeroBanner`), `theme/` (`porcelain/` theme
  files copied from `design/`, `ThemeViewModel`), `AppNavigation`.

## 4. The two hard parts
### 4a. "I left the car" detection
1. **Primary trigger:** car Bluetooth `ACL_DISCONNECTED` (user picks the paired device).
2. **Confirmation (optional):** Activity Recognition (→ walking) and/or geofence distance polling
   during the confirm window. `requireWalkAwayConfirmation` makes them mandatory.
3. State flow: `IDLE → (ARMED) → CONFIRMING → GRACE → VERIFYING → (AWAITING_CONFIRM) → LOCKING →
   LOCKED / SKIPPED / NOT_LOCKED_OPEN / SENT_UNVERIFIED / ERROR`; reconnect or cancel → `ABORTED` (a car Bluetooth reconnect mid-run logs "Car Bluetooth reconnected —
   auto-lock cancelled." via `cancel(reason)`). When adding a
   `DetectionState`, also add it to `isTerminal()` in `AutoLockService`.
4. `AutoLockController` owns the grace timer, the pre-lock status read (up to 3 tries, 15 s
   each), `LockPolicy.decide`, the lock call with a retry window (exponential backoff), the
   post-lock verify, and the one-off departure / failure notifications (`failAndNotify`), so a
   give-up is never silent. When Hyundai reports the lock command done, the controller saves the
   LOCKED status to `StatusCache` as a command right away (the Home card updates like a manual
   lock). Verify then reads cached status up to `VERIFY_ATTEMPTS` (3) times, `VERIFY_PAUSE_MS`
   (20 s) apart; if none confirms, one live read (`status(forceRefresh = true)`) asks the car
   itself. `LOCKED` only on a newer, fresh LOCKED read, otherwise `SENT_UNVERIFIED` (not
   `ERROR`). Verification never sends another command.

### 4b. US BlueLink client (`data/bluelink/us/UsBlueLinkClient`)
Host `https://api.telematics.hyundaiusa.com`. Verified against `hyundai_kia_connect_api` and
`bluelinky`; re-verify there if Hyundai changes things.
- **Sign-in:** password grant → access token (~30 min). Email + password are saved encrypted via
  `UsCredentialStore` → `SecureStore` because refreshing needs the password; cleared on sign-out
  or a failed sign-in. Access token lives in memory only. `UsCredentials.toString()` is redacted.
- **Vehicles:** `GET /ac/v2/enrollment/details/{username}` (generation, VIN, registration id).
- **Status:** `GET /ac/v2/rcs/rvs/vehicleStatus`. `forceRefresh = false` → header `refresh: false`
  (Hyundai's cached snapshot); `forceRefresh = true` → `refresh: true` (wakes the car for a live
  reading; 90 s timeout instead of the app-wide 30 s, when `HttpTimeout` is installed). Live reads
  drain the 12V battery and count toward Hyundai's limits, so only the auto-lock verify step uses
  them; Home, the widget worker and the pre-lock read stay cached. `dateTime` → `timestamp` +
  `reportedAtEpochMs`; `LockPolicy` skips if the report is missing, in the future or > 5 min old.
- **Range:** `evStatus.drvDistance[0].rangeByFuel.totalAvailableRange`, else `dte`, each
  `{value, unit}`; unit 1 = km (converted), 2/3 = miles (`DISTANCE_UNITS` in
  hyundai_kia_connect_api). Stored as `VehicleStatus.rangeMiles`; the app is miles-only (Home tile
  "%d mi", notification/widget summary "mi"). `StatusCache` key `range_mi`; an old `range_km`
  value is converted km → mi when read and removed on the next write.
- **Lock/unlock:** `POST /ac/v2/rcs/rdo/off|on` with vehicle headers + `blueLinkServicePin`, body
  `{userName, vin}`; then poll `GET /ac/v2/rmt/getRunningStatus` (10 × 3 s) by transaction id.
  `SUCCESS` → Success, `ERROR`/`errorCode` → Failure, 429 → RateLimited, 401 → Failure + fresh
  sign-in next call. The client never re-sends; the controller's retry window does.
- `client_id` / `clientSecret` headers are **public app constants**, not user credentials.

## 5. Safety model (never bypass)
- `RunMode.DRY_RUN` (default) runs the whole flow but NEVER sends a real lock. `ARMED` does.
- The app only ever sends **lock** automatically; unlock is a manual, PIN-confirmed action.
  The widget's "Unlock…" (shown only when `StatusCache` says LOCKED) never unlocks itself: it
  launches `MainActivity.ACTION_CONFIRM_UNLOCK`, which only opens Home's unlock dialog; the
  unlock goes through `HomeViewModel.manualUnlock` (Armed mode, PIN, sign-in). Keep it that way.
- `demoMode` swaps in `FakeBlueLinkClient`.
- Always read status and run `LockPolicy.decide` before locking: skip if already locked, engine
  running, unknown/stale status, or (default on) a door/window open.
- Tests never touch the real BlueLink API; use `FakeBlueLinkClient` or Ktor `MockEngine`.

## 5b. Security model
- `SecureStore`: EncryptedSharedPreferences (AES-256-GCM), Keystore master key, StrongBox when
  available; corrupted store → wipe + re-sign-in, never plaintext. Not backed up.
- Network: `network_security_config.xml` forbids cleartext, trusts system CAs only. An OkHttp
  `CertPins` pinner is wired but empty (no-op) until real SPKI pins are supplied.
- Login screen uses `FLAG_SECURE`. Ktor request logging stays off; never log credentials,
  PIN, tokens, VINs or auth headers.
- `ApiMetrics` records only operation name / duration / outcome.
- No secrets in the repo (CI guard blocks keystores, `local.properties`, `.env`, etc.).

## 6. Conventions
- Pure functions in `domain`; Android/IO at the edges (`data`, `service`, `ui`).
- ViewModels expose `StateFlow`; collect with `collectAsStateWithLifecycle`. Blocking
  Keystore/disk work runs on `Dispatchers.IO`.
- Request permissions contextually (`ui/permissions/PermissionGate`).
- Clock times on screen go through `ui/util/TimeFormat` (follows the phone's 12/24-hour setting;
  12-hour with AM/PM by default). Never hard-code `"HH:mm"`. Exceptions: the clipboard log export
  and backup filenames keep fixed 24-hour formats.
- The Home vehicle card shows one time, "Updated …" = `CachedStatus.statusAsOfEpochMs()`.
- Before changing behavior, trace every reader, test, screen, notification and widget it touches.
- Keep `LockPolicy`, `LockStateMachine`, `AutoLockController`, `UsBlueLinkClient` and the fake
  client tests green.

## 6b. Design system (Porcelain Grid) — adopted
The app's look is the **Porcelain Grid** design system; the user-facing name is **LEAVE/LOCK**
(app label, screens, notifications, README). The repo name `Leave-N-Lock` and the app ID stay.
Read `design/README.md` before any UI, theme, icon, copy or notification work.
- Source of truth: the maintainer's design system page on claude.ai; `design/` is a copy. If
  they disagree, the page wins. Change the page first, then refresh `design/`, then the app.
- Wiring: `design/android/theme/*.kt` → `ui/theme/porcelain/`, fonts → `res/font/`,
  `porcelain_colors.xml` → `res/values(-night)/`, `ic_launcher_*`, `ic_stat_leavelock` and the
  used `ic_pg_*` icons → `res/drawable/`. `MainActivity` applies `PorcelainTheme(darkTheme)` from
  the saved `ThemeMode` and sets the system-bar icon colour to match. `AppSettings.dynamicColor`
  is kept (backups) but no longer used or shown.
- Screens build only from `ui/components/Porcelain.kt`; colours come from `Porcelain.colors`
  (never raw hex), text from `PorcelainText`, spacing from `PorcelainSpace`. Inside a hero card
  `LocalOnHero` flips controls to the on-teal-deep colours.
- Two app-side deviations from the `design/` copy (keep them when refreshing): the `ic_pg_*`
  icons use `?android:attr/colorControlNormal` (the app has no AppCompat), and the Material
  `Shapes` slots use `CutCornerShape(topEnd = …)` because Material 3 1.3 needs corner-based
  shapes there (`ChamferShape` is still used for cards).
- `PorcelainMark` (welcome screen, `BrandLockup`) redraws `mark-compact-on-*` on a Canvas with
  the `markPx*` / `markSeam` tokens; keep its geometry in step with `design/brand/` when the art
  changes.
- The widget is a flat teal-deep surface with the hero chamfer (24dp, top-right) and square
  corners. RemoteViews can't use `ChamferShape`, so `widget_bg.xml` builds the cut from layers
  (two rects + the `widget_bg_corner` triangle, edge then 1dp-inset fill); a single vector would
  warp on resize. The layout root is `@android:id/background` with `clipToOutline` so Launcher3
  doesn't round the corners; keep the transparent full-size first layer, since a LayerDrawable's
  outline (and so the clip) comes from its first layer.
  Its button: "Lock now" on `widget_button_bg` (unlocked/unknown), "Unlock…" on
  `widget_button_danger_bg` (locked), "Auto-lock running…" while a run is under way.
  `AutoLockService` redraws the widget when the detection state changes.
- UI rules in short: tokens only; cards chamfered top-right (16dp, 24dp hero), 4dp max radius
  elsewhere; Exo 2 / Instrument Sans / IBM Plex Mono; no gradients, glows, blurred textures,
  pulsing or heavy shadows; pixel clusters crisp and static; 48dp touch targets; short,
  factual, sentence-case copy; status always has words.

## 7. Build & test
- `./gradlew testDebugUnitTest` · `./gradlew lint` · `./gradlew assembleDebug` · `./gradlew installDebug`
- Needs SDK platform `android-37.0` and build-tools 36.0.0. A fresh worktree has no
  `local.properties`, so set `ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk` there.
- On this Windows PC, Android Studio's bundled JDK (25) breaks Gradle: set
  `JAVA_HOME=C:\Users\mrthi\.jdks\jbr-21.0.11` first. If `app\build` gives AccessDenied, run
  `gradlew --stop`, delete `app\build`, then build again with `--no-daemon`.

## 7b. CI (`.github/workflows/`)
- `ci.yml` — `guard` (required docs incl. this file with a Changelog, ≥ 40 lines; no committed
  secrets; secret-pattern scan + best-effort gitleaks) and `android` (wrapper validation, JDK 17,
  platform 37.0 + build-tools 36.0.0, unit tests, lint, assembleDebug).
- `release.yml` — on `v*` tags: builds the release APK, notes from `CHANGELOG.md`, publishes a
  GitHub Release (signed if the `RELEASE_*` secrets exist).

## 8. Open TODOs / known gaps
**Where we left off (2026-11-06):** `main` holds everything and is pushed, including the widget
Unlock… button and the refreshed app icon. The GitHub Actions Dependabot bumps are merged.
**Car test 2026-11-06 (Pixel 11 Pro, Ioniq 5, Armed): passed.** Three auto-locks, each confirmed
on the first cached verify read about 70 s after walking away; the range in miles and the widget
Unlock flow look right. One pre-lock status read timed out (15 s) and the retry worked. The
toolchain upgrade (Gradle 9.8.2 / AGP 9.5.1 / Kotlin 2.4.21 / compileSdk 37, PR #11) passed a
second car test (walk-away lock, reconnect-cancel log line, widget Unlock confirmation, What's
new) and is merged; Dependabot #6–#10 are closed as superseded. The What's new screen now joins
wrapped CHANGELOG lines (`ui/about/ChangelogText.kt`). No open PRs; `main` is the only branch.
0.2.0 is prepared on `release/0.2.0` (version bump + changelog roll); it is tagged once the
release-signing secrets are set up. Then, in order: targetSdk 36/37 on
its own branch with a car test; a replacement plan for `androidx.security:security-crypto`
(deprecated upstream; `SecureStore` builds with warnings). Ideas not started yet, in priority
order: US diagnostics export with redaction; Stats "Session expires" row; car-reported location.
New work goes on its own branch cut from `main`.
- Only tested on an Ioniq 5. Gather compatibility reports for other US Hyundais.
- The live verify read (`refresh: true`) is built from the reference libraries and mock-tested
  only. In the 2026-11-06 car test the cached reads always confirmed first, so it hasn't run
  against a real Ioniq 5 yet (reply shape, timing, `dateTime` freshness).
- **Shelved (2026-11-06): US refresh-token grant.** Not worth it: still one call per ~30 min
  (no felt speed-up); the password must stay saved as the fallback, so on-phone exposure is
  unchanged (it only crosses HTTPS less often); and the references disagree on the call
  (bluelinky: `POST /v2/ac/oauth/token/refresh` with `refresh_token` only;
  hyundai_kia_connect_api: `POST /v2/ac/oauth/token` with `grant_type: refresh_token` +
  username/password, falling back to a full sign-in), so it adds an untested path in front of
  every lock. Revisit only if full sign-ins start causing trouble (lockouts, sign-in emails,
  rate limits). Today `authenticate()` ignores the `refresh_token` Hyundai returns.
- Cert-pin hashes and Play `GeofencingClient` at arrival are not done.
- `ActivityLog` diagnostic messages are hard-coded English (no `Context` in the domain layer).

## 9. Changelog
- 0.1.0 (2026-11-04) — Forked from i5-AutoLock as Leave-N-Lock: removed the EU client, region
  picker, EU-only settings (live wake, device re-registration, API diagnosis, CCS2/legacy
  control flags, EU token fields) and the de/es/fr/nl translations; new app id + Kotlin package
  `io.github.gthibo.leavenlock`; network config limited to the US host; new README, CONTRIBUTING,
  issue templates; credits to Vel-San, hyundai_kia_connect_api and bluelinky. Carried over from
  i5-AutoLock development: real US lock/unlock with confirmation polling, encrypted US sign-in
  that survives restarts, car-reported time on the Home card, pre-lock status retries and
  "not locked" failure notifications.
- 2026-11-05 — Added the Porcelain Grid design system under `design/` as reference (not wired in);
  package paths updated to `io.github.gthibo.leavenlock`. Fonts are SIL OFL (licences in
  `design/licenses/`).
- 2026-11-05 — Adopted the Porcelain Grid design on the `design-system` branch: `PorcelainTheme`
  (dynamic colour off, System/Light/Dark kept), bundled fonts, new launcher and notification
  icons, shared components in `ui/components/Porcelain.kt`; every screen, the widget and the
  notification accent restyled; old gradients, glows and pixel textures (`Theme.kt`, `Color.kt`,
  `Brand.kt`, `PixelDecor.kt`) removed. User-facing name is now LEAVE/LOCK. No locking-logic
  changes; two controller notice texts renamed and their two test assertions updated.
- 2026-11-05 — Home card wired to `StatusCache` (it had only shown its own fetches, so auto-lock
  results never reached it). Command results saved with `fromCommand` and held over older reads
  (`CachedStatus.after`, `COMMAND_HOLD_MS`); `lastLockedAt` is now stamped only when the state
  becomes LOCKED or on a lock command. Auto-lock saves LOCKED on command success, verify retries
  3× / 20 s, unverified ends `SENT_UNVERIFIED`. Card shows one "Updated …" time. Armed-mode
  simulate button + hint removed; Cancel lives in the status card while in flight. New
  `ui/util/TimeFormat` (device 12/24 h) for Home and Stats. Temporary "US status report time"
  diagnostic removed from `UsBlueLinkClient`.
- 2026-11-05 — Real range on the Home card, in miles: `UsBlueLinkClient` parses
  `totalAvailableRange` (or `dte`) with its unit code; `VehicleStatus.rangeKm` → `rangeMiles`
  (merge, `CachedStatus`, `StatusSummary`, Home tile, fake client 198 mi). Cache key `range_km` →
  `range_mi` with a one-time km → mi read. `StatusCache.read`/`write` moved to its companion so
  tests round-trip them. New `kmToMiles` in `model/`.
- 2026-11-05 — Live status for the lock check: `UsBlueLinkClient.status(forceRefresh = true)` now
  sends `refresh: true` (live read from the car, 90 s timeout); `false` still reads the cache.
  `AutoLockController.verifyLocked` adds one live read after the 3 cached tries fail, so a lock
  that worked no longer ends "Lock sent, not verified" just because Hyundai's snapshot lagged.
  Home's pull/refresh button now always asks for the cached read (it passed `force` before, which
  the US client ignored), so it still never wakes the car. 5 new tests (178 total).
- 2026-11-06 — Removed the dead "Minimum refresh interval" setting (left from i5-AutoLock, never
  enforced; its copy promised live-poll throttling the app doesn't do). Gone from `AppSettings`,
  `SettingsBackup` (old backups still restore: `ignoreUnknownKeys`), the ViewModel, the Settings
  Timing section and `strings.xml`. `SettingsRepository` deletes the stored `min_refresh_seconds`
  key on the next save.
- 2026-11-06 — App icon refresh from the maintainer's Illustrator file: art moved 14 units left,
  4×4 8.25-unit pixel cluster replaced by a notched 3×4 grid of 11.022-unit cells, light seam
  (#E6E8E5, 2.4) along the body diagonal, right shackle leg runs down behind the grid. Updated
  `ic_launcher_foreground/monochrome.xml` (translateX 68.56 → 77.8), `design/brand/app-icon*.svg`,
  `mark-*.svg` (viewBox x 128 → 114) and `favicon.svg`.
- 2026-11-06 — Widget chamfer: `widget_bg.xml` is now a layer-list with a 24dp top-right cut
  (new `widget_bg_corner.xml`) and square corners; the layout root id is `@android:id/background`
  with `clipToOutline` (opts out of Launcher3's rounding), so `AutoLockWidget` sets the open-app
  click on `android.R.id.background` (was `R.id.widget_root`). Checked on the API 37 emulator in
  light and dark.
- 2026-11-06 — Caught the claude.ai design page up with the app (icon/mark art, corner bleed,
  widget spec, copy), then the app with the page: `line-strong` #80888C → #7A8286 (light) and
  #687075 → #70787C (dark) to keep 3:1 on the new surfaces; new mark tokens `mark-px-lit/-mid/
  -dim/-off` and `mark-seam` (Kotlin, `porcelain_colors.xml`, `design/tokens`); `PorcelainMark`
  redrawn with the new art (notched 3×4 grid, seam, viewBox x 114). `design/tokens` and
  `design/web/bundle.*` refreshed from the page.
- 2026-11-06 — Widget button follows the saved lock state (`widget/WidgetAction.kt`,
  `widgetActionFor`): LOCKED → "Unlock…" (danger) which opens Home's existing unlock
  confirmation via `MainActivity.ACTION_CONFIRM_UNLOCK` (never a one-tap unlock); unlocked or
  unknown → one-tap "Lock now"; a running auto-lock → "Auto-lock running…" (opens the app).
  `AutoLockService` refreshes the widget on detection-state changes. 5 new tests.
- 2026-11-06 — Toolchain upgrade in one change (replaces Dependabot #6–#10): Gradle 8.11.1 →
  9.8.2, AGP 8.7.3 → 9.5.1 (built-in Kotlin, kotlin-android plugin dropped from `app`), Kotlin
  2.1.0 → 2.4.21, KSP → 2.3.13, Hilt 2.60.1, compileSdk 35 → 37 (targetSdk stays 35), androidx
  (Compose BOM 2026.09.00, navigation 2.10.2, hilt-navigation 1.4.0, work 2.12.0, datastore
  1.3.0, security-crypto 1.1.0), Ktor 3.6.1, OkHttp 5.5.1 (`CertificatePinner` API unchanged),
  kotlinx serialization 1.12.0 / coroutines 1.11.0. `kotlinOptions` → `compilerOptions`; the
  changelog asset copy is now a Variant-API task; `hiltViewModel` import moved; one new lint
  error fixed (Settings ringtone title read via `stringResource`). CI/release install platform
  37.0 + build-tools 36.0.0. No locking-logic changes; 185 tests green.
- 2026-11-06 — What's new joins wrapped CHANGELOG lines (`ui/about/ChangelogText.kt`,
  `unwrapChangelog`, 4 tests).
- 0.2.0 (2026-11-06) — First tagged release (v0.1.0 was never tagged; its changelog link 404s).
  versionCode 1 → 2. `## [Unreleased]` rolled into `## [0.2.0]`; start a new `## [Unreleased]`
  heading with the next user-facing change. Release title in `release.yml` renamed from the
  i5-AutoLock leftover "AutoLock" to "Leave-N-Lock". Release APKs are signed from the
  `RELEASE_*` repo secrets (keystore kept off the repo by the maintainer).
