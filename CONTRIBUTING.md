# Contributing to Leave-N-Lock

Thanks for helping! This app controls a real car, so the bar is "safe first, then clever".

## Ground rules
- **Keep the mission small.** Leave-N-Lock does one job: lock a US BlueLink Hyundai when you
  walk away. No trip logging, cloud sync, analytics or multi-user features.
- **Safety defaults don't change.** `RunMode.DRY_RUN` stays the default, and the app only ever
  sends *lock* on its own. Unlock is manual only.
- **Never call the real BlueLink API from tests.** Use the `FakeBlueLinkClient` or Ktor's
  `MockEngine` (see `UsBlueLinkClientTest`). Live testing against a car is something you do
  yourself, by hand, in Dry run first.
- **No secrets, ever.** Don't commit `local.properties`, keystores, `.env` files, or real
  credentials, tokens, VINs or emails, including in test fixtures and issue logs. CI blocks
  the obvious ones.
- **Never log** passwords, PINs, tokens, VINs, or the `Authorization`/`accessToken` headers.

## Before you open a PR
1. Trace what your change touches: every caller, every test that asserts it, and every screen,
   notification or widget that shows it.
2. Add or update unit tests. Pure logic (`LockPolicy`, `LockStateMachine`, `AutoLockController`)
   should be covered.
3. Run:
   ```bash
   ./gradlew testDebugUnitTest lint assembleDebug
   ```
4. Update [CLAUDE.md](CLAUDE.md) in the same PR if you change architecture, the detection flow,
   or the BlueLink client, and add a line to [CHANGELOG.md](CHANGELOG.md) for anything a user
   would notice.

## Reporting your car
If you own a US Hyundai other than an Ioniq 5, a
[compatibility report](../../issues/new?template=compatibility_report.yml) is one of the most
useful contributions you can make.
