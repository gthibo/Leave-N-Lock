# Changelog

## [Unreleased]

- **Removed the "Minimum refresh interval" slider.** It never did anything: LEAVE/LOCK
  doesn't poll the car on a timer, and it wakes the car only once, at the end of an auto-lock
  check. Older backups still restore.
- **Fewer "Lock sent, not verified" results.** If Hyundai's saved status still hasn't caught
  up after the three checks, LEAVE/LOCK asks the car once for a live status before giving up.
  This wakes the car briefly, so it happens only at that last step, never on Home refreshes.
  It still never sends a second lock.
- **New look and name: LEAVE/LOCK.** The app now uses the Porcelain Grid design: calm
  porcelain (light) and graphite (dark) themes, flat cards with one cut corner, square
  "pixel lamp" meters, new fonts, a new app icon and a new notification icon. The old
  gradients and glowing, animated pixel textures are gone.
- **Theme choice:** Settings → Appearance offers System default, Light or Dark. The
  "Dynamic color" (wallpaper colours) switch was removed so the app keeps its own palette.
- **Clearer settings:** Dry run and Armed now say what they do on a second line ("Never sends
  a real lock", "Locks the car for real").
- **The Home card now updates after an auto-lock.** The card reads the same saved status as
  the widget and notification, so auto-locks and background refreshes show up right away. As
  soon as Hyundai reports a lock or unlock command done, the card shows it, and an older status
  from Hyundai (which often lags by minutes) can't flip it back for 30 minutes.
- **Auto-lock checks the result longer:** after the lock command it checks Hyundai's status up
  to 3 times, 20 seconds apart, before saying "Lock sent, not verified". That outcome is no
  longer shown as an error. It still never sends a second lock.
- **One time on the card:** "Retrieved …" and "Car reported …" are replaced by a single
  "Updated …" line (the newer of the car's last report and your last command).
- **Simpler Home screen:** in Armed mode the "Simulation off (Armed)" button and its hint are
  gone. Cancel now appears in the System status card only while a countdown or lock is running.
- **12-hour times:** every clock time follows your phone's setting: 12-hour with AM/PM, or
  24-hour if you've turned that on.
- **Quieter activity log:** the temporary "US status report time" lines are gone.
- **Range on the Home card, in miles.** The card now shows the car's remaining driving range
  from Hyundai (it only appeared in demo mode before, and in km). The notification and widget
  summary also show range in miles.

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
