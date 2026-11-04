# Leave-N-Lock

Automatically locks your **Hyundai Ioniq 5** when you walk away and forget to lock it, through
Hyundai **BlueLink (US)**. One job, on your phone only, running in the background.

- **Tested on:** the author's Ioniq 5 with a US BlueLink account.
- **May also work** with other BlueLink-enabled US Hyundai vehicles (Ioniq 6, Ioniq 9, Kona
  Electric, Tucson, Santa Fe…). The US BlueLink service is the same for all of them, but only
  the Ioniq 5 has been tried. If you test another model, please
  [report it](../../issues/new/choose), whether it works or not.
- **Not supported:** Kia and Genesis (different servers), and BlueLink outside the US.

> ⚠️ **Unofficial.** Not affiliated with or endorsed by Hyundai. The BlueLink API is
> reverse-engineered and can change or rate-limit at any time. You need an active BlueLink
> subscription that includes remote lock. This app controls a real vehicle, so use it
> responsibly and at your own risk.

---

## How it works
1. **Trigger:** your phone disconnects from the car's Bluetooth, and a check starts.
2. **Confirm** *(optional)*: activity recognition (driving → walking) and/or a geofence confirm
   you really walked away.
3. **Grace:** a short countdown you can cancel from the notification.
4. **Verify:** the app reads the car's status from BlueLink. It only continues if the car is
   unlocked, the engine is off, and (by default) no door or window is open.
5. **Lock:** sends the BlueLink lock command and waits for the car to confirm it. In **Dry run**
   it only logs what it *would* have done.

If something goes wrong (BlueLink unreachable, signed out, lock rejected), you get a
notification saying the car was **not** locked, so a failure is never silent.

## Safety first
| Mode | What it does |
|------|--------------|
| **Demo** | A simulated car. No account or vehicle needed. |
| **Dry run** *(default)* | Runs the whole flow but **never sends a real lock**. It logs "would have locked". |
| **Armed** | Sends real lock commands. |

Leave-N-Lock only ever sends **lock** automatically. Unlock is a manual button that asks for
your BlueLink PIN.

## Install
Grab the APK from [Releases](../../releases), or build it yourself (below). Android 8.0+.

## First launch
1. Grant the permissions it asks for (Bluetooth, location, activity, notifications).
2. **Settings → Account → Sign in** with your BlueLink (US) email, password and 4-digit PIN.
   Or turn on **Demo mode** to look around first.
3. **Reload vehicles** and pick your car.
4. **Settings → Car Bluetooth:** pick the paired device that is your car.
5. Leave **Dry run** on and tap **Simulate leaving** on the Home screen to watch the full flow.
   Then do a real walk-away test or two, still in Dry run.
6. When you trust it, switch to **Armed**.
7. Allow background location and turn off battery optimization for Leave-N-Lock
   (Settings → Keep Leave-N-Lock running), or Android may stop it in the background.

## Privacy & security
- Your BlueLink email, password and PIN are stored **encrypted on the phone**
  (EncryptedSharedPreferences, AES-256-GCM, Android Keystore, StrongBox when available). They
  are never logged, never backed up (`allowBackup=false`), and only ever sent to Hyundai.
  The password is kept because Hyundai's US sessions expire after about 30 minutes and
  renewing them needs it.
- No analytics, no ads, no third-party servers. The only network calls go to
  `api.telematics.hyundaiusa.com`.
- Cleartext traffic is blocked; only system certificate authorities are trusted.
- The sign-in screen blocks screenshots (`FLAG_SECURE`).
- The `client_id` / `clientSecret` values in `UsBlueLinkClient` are **public app constants**
  shared by every BlueLink app (also in the reference projects below), not anyone's account
  credentials.

## Build from source
Requirements: **JDK 17**, Android SDK platform 35 + build-tools 35.0.0 (Android Studio installs
both). See [docs/TESTING.md](docs/TESTING.md) for a step-by-step setup.

```bash
./gradlew testDebugUnitTest   # unit tests, no device needed
./gradlew lint                # static analysis
./gradlew assembleDebug       # → app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug        # install on a connected phone / emulator
```

Emulators have no real Bluetooth, so use **Simulate leaving** there and a real phone for
walk-away tests.

## Project layout
```
app/src/main/java/io/github/gthibo/leavenlock/
├─ data/
│  ├─ bluelink/   client interface, provider, metered decorator
│  │  ├─ us/      the real US BlueLink client (sign-in, status, lock/unlock + confirmation poll)
│  │  └─ fake/    simulated car for demo mode and tests
│  ├─ settings/   settings (DataStore)
│  ├─ secure/     encrypted store (PIN + sign-in)
│  └─ …           device, location, status cache, metrics, backup, sound
├─ domain/        AutoLockController, detection state machine, LockPolicy, ActivityLog
├─ service/       foreground service + notifications
├─ receiver/      Bluetooth, boot, geofence, activity receivers
└─ ui/            Compose screens
```
Architecture and conventions are in [CLAUDE.md](CLAUDE.md). Contributing: [CONTRIBUTING.md](CONTRIBUTING.md).

## Releases
Push a `v*` tag and [.github/workflows/release.yml](.github/workflows/release.yml) builds the
APK and publishes a GitHub Release, using the matching section of [CHANGELOG.md](CHANGELOG.md)
as the notes. For a signed APK, add the repository secrets `RELEASE_KEYSTORE_BASE64`,
`RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` (see
`local.properties.example` for local signing).

## Credits
- **[i5-AutoLock](https://github.com/Vel-San/i5-AutoLock) by Vel-San.** Leave-N-Lock is a
  US-only, English-only fork of it. The app, its design, and the detection engine are Vel-San's
  work; this fork removes the EU client and adds a US BlueLink client.
- **[hyundai_kia_connect_api](https://github.com/Hyundai-Kia-Connect/hyundai_kia_connect_api)**
  and **[bluelinky](https://github.com/Hacksore/bluelinky)**, whose reverse-engineering of the
  US BlueLink API the US client is based on.

## Disclaimer
Not affiliated with, endorsed by, or supported by Hyundai Motor America. "Hyundai", "Ioniq" and
"BlueLink" are trademarks of their owners and are used here only to describe compatibility.
Use at your own risk.

## License
[MIT](LICENSE). Original copyright © 2026 Vel-San; modifications © 2026 gthibo.
