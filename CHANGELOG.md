# Changelog

## [Unreleased]

- **New look and name: LEAVE/LOCK.** The app now uses the Porcelain Grid design: calm
  porcelain (light) and graphite (dark) themes, flat cards with one cut corner, square
  "pixel lamp" meters, new fonts, a new app icon and a new notification icon. The old
  gradients and glowing, animated pixel textures are gone.
- **Theme choice:** Settings → Appearance offers System default, Light or Dark. The
  "Dynamic color" (wallpaper colours) switch was removed so the app keeps its own palette.
- **Clearer settings:** Dry run and Armed now say what they do on a second line ("Never sends
  a real lock", "Locks the car for real").
- No changes to how detection or locking works.

## [0.1.0] - 2026-11-04

First release of **Leave-N-Lock**, a US-only, English-only fork of
[i5-AutoLock](https://github.com/Vel-San/i5-AutoLock) by Vel-San. It automatically locks your
Hyundai Ioniq 5 through Hyundai BlueLink (US) when you walk away.

- **US BlueLink client:** sign in with email, password and PIN; read the car's status; lock and
  unlock, confirming with the car that the command actually went through. Your sign-in is kept
  encrypted on the phone, so it survives restarts and settings changes.
- **No silent failures:** if Leave-N-Lock can't reach BlueLink, gets signed out, or hits an
  error, it posts a notification saying the car was not locked. The pre-lock status check now
  retries up to 3 times.
- **Honest status times:** the Home card shows when the app retrieved the status and when the
  car itself last reported it.
- **Removed:** the EU client and region picker, EU-only settings (live wake, device
  re-registration, API diagnosis), and the German, Spanish, French and Dutch translations.
- **New identity:** app name Leave-N-Lock, app ID `io.github.gthibo.leavenlock`, so it can be
  installed next to the original app.
- **Tested on** an Ioniq 5 only. Other BlueLink-enabled US Hyundais may work.

[0.1.0]: https://github.com/gthibo/Leave-N-Lock/releases/tag/v0.1.0
