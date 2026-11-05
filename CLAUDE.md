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
- 100% Kotlin, JDK 17, `minSdk 26`, `target/compileSdk 35`.
- UI: Jetpack Compose + Material 3 (dynamic color). No XML layouts.
- DI: Hilt. Async: Coroutines + Flow. HTTP: Ktor (OkHttp engine) + kotlinx.serialization.
- Storage: DataStore (settings), EncryptedSharedPreferences/Keystore (PIN + US sign-in).
- Background: Foreground Service (`connectedDevice`) + BroadcastReceivers. WorkManager via Hilt.
- Build: Gradle version catalog (`gradle/libs.versions.toml`), KSP (never kapt).
- App id / namespace / Kotlin package: `io.github.gthibo.leavenlock`. Strings: English only
  (`res/values/strings.xml`).

## 3. Package map (`app/src/main/java/io/github/gthibo/leavenlock`)
- `data/settings/` — `AppSettings`, `SettingsRepository` (DataStore). All non-secret prefs.
- `data/secure/` — `SecureStore`: BlueLink PIN + US email/password, encrypted. **Never log them.**
- `data/device/` — `BluetoothDevices` (bonded devices for the car picker).
- `data/location/` — `LocationHelper` (last location + reverse-geocoded parked label).
- `data/detection/` — `ActivityRecognitionManager`, `GeofenceManager`.
- `data/status/` — `StatusCache` (last full status for Home, widget and notification).
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
- `tile/`, `widget/`, `work/StatusRefreshWorker`, `di/AppModule`.
- `ui/` — Compose: `home/`, `settings/`, `login/`, `stats/`, `help/`, `about/`, `onboarding/`,
  `permissions/`, `components/`, `theme/`, `AppNavigation`.

## 4. The two hard parts
### 4a. "I left the car" detection
1. **Primary trigger:** car Bluetooth `ACL_DISCONNECTED` (user picks the paired device).
2. **Confirmation (optional):** Activity Recognition (→ walking) and/or geofence distance polling
   during the confirm window. `requireWalkAwayConfirmation` makes them mandatory.
3. State flow: `IDLE → (ARMED) → CONFIRMING → GRACE → VERIFYING → (AWAITING_CONFIRM) → LOCKING →
   LOCKED / SKIPPED / NOT_LOCKED_OPEN / SENT_UNVERIFIED / ERROR`; reconnect or cancel → `ABORTED`. When adding a
   `DetectionState`, also add it to `isTerminal()` in `AutoLockService`.
4. `AutoLockController` owns the grace timer, the pre-lock status read (up to 3 tries, 15 s
   each), `LockPolicy.decide`, the lock call with a retry window (exponential backoff), the
   post-lock verify, and the one-off departure / failure notifications (`failAndNotify`), so a
   give-up is never silent.

### 4b. US BlueLink client (`data/bluelink/us/UsBlueLinkClient`)
Host `https://api.telematics.hyundaiusa.com`. Verified against `hyundai_kia_connect_api` and
`bluelinky`; re-verify there if Hyundai changes things.
- **Sign-in:** password grant → access token (~30 min). Email + password are saved encrypted via
  `UsCredentialStore` → `SecureStore` because refreshing needs the password; cleared on sign-out
  or a failed sign-in. Access token lives in memory only. `UsCredentials.toString()` is redacted.
- **Vehicles:** `GET /ac/v2/enrollment/details/{username}` (generation, VIN, registration id).
- **Status:** cached vehicle status (`refresh: false`). `dateTime` → `timestamp` +
  `reportedAtEpochMs`; `LockPolicy` skips if the report is missing, in the future or > 5 min old.
- **Lock/unlock:** `POST /ac/v2/rcs/rdo/off|on` with vehicle headers + `blueLinkServicePin`, body
  `{userName, vin}`; then poll `GET /ac/v2/rmt/getRunningStatus` (10 × 3 s) by transaction id.
  `SUCCESS` → Success, `ERROR`/`errorCode` → Failure, 429 → RateLimited, 401 → Failure + fresh
  sign-in next call. The client never re-sends; the controller's retry window does.
- `client_id` / `clientSecret` headers are **public app constants**, not user credentials.

## 5. Safety model (never bypass)
- `RunMode.DRY_RUN` (default) runs the whole flow but NEVER sends a real lock. `ARMED` does.
- The app only ever sends **lock** automatically; unlock is a manual, PIN-confirmed action.
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
- Before changing behavior, trace every reader, test, screen, notification and widget it touches.
- Keep `LockPolicy`, `LockStateMachine`, `AutoLockController`, `UsBlueLinkClient` and the fake
  client tests green.

## 6b. Design system (Porcelain Grid) — reference only, not yet adopted
A new look lives in `design/` (tokens, Compose theme, fonts, icons, brand marks). Read
`design/README.md` before any UI, theme, icon, copy or notification work.
- Source of truth: the maintainer's design system page on claude.ai; `design/` is a copy. If
  they disagree, the page wins.
- **Not wired into the build.** The app still runs on the old theme in `ui/theme/`. Adopting it
  is a deliberate change: copy `design/android/theme/*.kt` to `ui/theme/porcelain/`, the fonts to
  `res/font/`, the `ic_launcher_*` drawables over the current ones, use `PorcelainTheme` with
  dynamic colour off and the user's System/Light/Dark choice, then update §2 in this file.
- Decided 2026-11-05: when adopted, the user-facing name becomes **LEAVE/LOCK** (app label,
  screens, notifications, README). Repo name and app ID stay. Adoption happens **after** the
  first Dry-run testing on the car, so redesign issues and logic issues stay separate.
- UI rules in short: tokens only (no raw hex); cards chamfered top-right (16dp, 24dp hero), 4dp
  max radius elsewhere; Exo 2 / Instrument Sans / IBM Plex Mono; no gradients, glows or heavy
  shadows; 48dp touch targets; short, factual, sentence-case copy.

## 7. Build & test
- `./gradlew testDebugUnitTest` · `./gradlew lint` · `./gradlew assembleDebug` · `./gradlew installDebug`

## 7b. CI (`.github/workflows/`)
- `ci.yml` — `guard` (required docs incl. this file with a Changelog, ≥ 40 lines; no committed
  secrets; secret-pattern scan + best-effort gitleaks) and `android` (wrapper validation, JDK 17,
  SDK 35, unit tests, lint, assembleDebug).
- `release.yml` — on `v*` tags: builds the release APK, notes from `CHANGELOG.md`, publishes a
  GitHub Release (signed if the `RELEASE_*` secrets exist).

## 8. Open TODOs / known gaps
- Only tested on an Ioniq 5. Gather compatibility reports for other US Hyundais.
- US `status(forceRefresh)` reads Hyundai's cached snapshot; if it updates slowly after a lock,
  the run can end "Lock sent, not confirmed" even though the car locked. A live refresh
  (`refresh: true`) is supported by the reference library but not used yet.
- Optional speed-up: US refresh-token grant instead of a full sign-in every ~30 min.
- Cert-pin hashes, a bundled display font, and Play `GeofencingClient` at arrival are not done.
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
