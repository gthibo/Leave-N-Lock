# Replace `androidx.security:security-crypto` in `SecureStore` — plan for approval

Status: **approved by the maintainer 2026-11-26 (approach A; security-crypto kept through v0.3.0 for the migration only).**
Date: 2026-11-26. Base: `main` @ cc49c3f. When approved, this file goes into the PR as
`docs/superpowers/specs/2026-11-26-secure-store-design.md`.

## 1. Why
`androidx.security:security-crypto` (1.1.0) is deprecated upstream, so `SecureStore` builds with
deprecation warnings and will get no fixes. `SecureStore` holds the three secrets the app has:
the BlueLink PIN and the US email + password (needed every ~30 min to sign in again, so
background auto-lock depends on them). Success = the warnings are gone, the secrets stay
encrypted by an Android Keystore key, and **people who update keep their PIN and sign-in with
no re-login.**

Non-goals: changing what is stored, when it's cleared, or the sign-in flow. Anti-goals: any
plaintext copy on disk (even briefly), a key that needs the phone unlocked (auto-lock runs with
the phone locked in a pocket), or a migration that can loop or lock someone out.

## 2. Where security-crypto is used today (all of it)
| Place | Use |
|---|---|
| `gradle/libs.versions.toml:13,51` | `security = "1.1.0"`, `androidx-security-crypto` |
| `app/build.gradle.kts:145` | `implementation(libs.androidx.security.crypto)` |
| `data/secure/SecureStore.kt` | the only code use: `MasterKey` (AES256_GCM, StrongBox first, then TEE) + `EncryptedSharedPreferences` file `autolock_secure` (keys AES256_SIV, values AES256_GCM); corrupt → delete file and start empty |
| Docs | `CLAUDE.md` §2 (Storage), §5b; `README.md:62` |

`SecureStore` API (kept as-is, so callers and tests don't change): `savePin`/`loadPin`,
`saveUsCredentials`/`loadUsUsername`/`loadUsPassword`/`clearUsCredentials`, `clear()`.
Callers: `BlueLinkProvider` (`pinProvider`, `SecureUsCredentialStore`), `HomeViewModel.hasPin()`
(called from the UI thread) + manual lock/unlock PIN check, `LoginViewModel.onSignIn` (saves
PIN). Tests mock the class (`BlueLinkProviderTest`, `SecureUsCredentialStoreTest`).
Keys in the old file: `service_pin`, `us_username`, `us_password`. Old Keystore alias:
`_androidx_security_master_key_`.

Noticed in passing (not changed by this plan):
- `res/xml/data_extraction_rules.xml` holds `<full-backup-content>`, not
  `<data-extraction-rules>`. On Android 12+ `allowBackup="false"` stops cloud backup but **not**
  device-to-device transfer, so the encrypted file (not its key) can be copied to a new phone.
  Harmless today (it can't be decrypted there and gets wiped), but it should be fixed. Follow-up.
- `SecureStore.clear()` has no callers; sign-out keeps the PIN. Unchanged.

## 3. Approaches
**A. Keystore AES-GCM key + plain SharedPreferences of encrypted values (recommended).**
One Keystore key (`leavenlock_secure_v1`, AES-256, GCM, no padding; StrongBox when the phone has
it, else TEE). Each value is `AES-GCM(value, aad = key name)`, stored as Base64(iv ‖ ciphertext)
in a new private prefs file `secure_v2`. No new dependency; ~120 lines we own; stays
synchronous, so today's API and callers don't change. Trade-off: we write the (small) crypto
code ourselves, so it gets real-crypto tests (§6).

**B. Tink (`tink-android`) AEAD with `AndroidKeysetManager`.** What ESP used underneath: a Tink
keyset wrapped by a Keystore key. Well-reviewed, but adds a ~1 MB dependency and a keyset to
manage for three strings; the Keystore is still the root of trust, so no real security gain.

**C. DataStore + Keystore AES-GCM.** Same crypto as A, stored in DataStore. Matches "DataStore for
settings", but DataStore is async: `loadPin()`, `hasPin()`, `pinProvider` and `UsCredentialStore`
would become `suspend` (or use `runBlocking` on the main thread). More churn, no security gain.

Ruled out: re-implementing ESP's file format with Tink to read old data without the old library
(fragile, undocumented format).

**Pick: A.** Smallest change, no new library, same API.

## 4. Design (approach A)
New files in `data/secure/`:
- `SecretCipher` — interface: `encrypt(plain: String, aad: String): String`,
  `decrypt(blob: String, aad: String): String`.
- `AesGcmSecretCipher(keyProvider: () -> SecretKey)` — `AES/GCM/NoPadding`, 128-bit tag, IV
  from the cipher (Keystore picks it; never reuse), output `v1:` + Base64(iv ‖ ct). Pure JVM
  code, so tests use a software key.
- `KeystoreKey` — gets or creates the alias. `setIsStrongBoxBacked(true)` on API 28+, catch
  `StrongBoxUnavailableException` → create without it. **Never** `setUserAuthenticationRequired`
  or `setUnlockedDeviceRequired` (auto-lock reads the PIN with the phone locked).
- `LegacySecureStoreReader` — the only file that touches security-crypto, under
  `@file:Suppress("DEPRECATION")`; opens `autolock_secure` exactly as today and returns the three
  values, or throws. `deleteLegacy()` removes the file and the old Keystore alias.
- `SecureStore` — same public API, now on `secure_v2` + `SecretCipher`. On a decrypt failure for
  a value (key gone, file copied from another phone): remove that value, return null → the user
  is asked to sign in again (today's "corrupt → re-sign-in, never plaintext" rule).
  Writes use `commit()` on the caller's thread (as small as today's `apply()`; it is what makes
  the migration atomic, see below).

Warm-up: `AutoLockApp.onCreate` starts `secureStore.warmUp()` on `Dispatchers.IO`, so key
creation and the migration happen off the main thread; first use is guarded by a lock, so a
caller that arrives early waits instead of migrating twice.

## 5. One-time migration (no re-login)
Runs once, in `SecureStore`'s first open (`warmUp()` or the first call, whichever comes first):

1. If `secure_v2` has `migrated = 1`: skip to step 5.
2. If the old file `autolock_secure` doesn't exist (fresh install): write `migrated = 1`, done.
3. Read the old values with `LegacySecureStoreReader`.
   - Open/decrypt throws (old store corrupt, old key gone): there is nothing usable — same result
     as today's wipe. Write `migrated = 1`, delete the old file + alias. User signs in again.
4. Encrypt each value that exists with the new key and write **all of them plus `migrated = 1`
   in one `commit()`**. SharedPreferences writes the whole file atomically (temp file + rename),
   so the new store has either everything or nothing.
5. If `migrated = 1` and the old file or old alias still exists: delete them.

What happens if it fails partway:
| Fails at | State left | Next launch |
|---|---|---|
| New key can't be created (Keystore error) | old file untouched, no `migrated` | Retries. This session: reads return null, so the app asks for sign-in; a sign-in now would also fail to save, so the error is logged (WARN, no secrets) and the old file is kept for the retry. |
| Process killed during step 3 or 4 | old file untouched, `secure_v2` unchanged (atomic) | Retries from step 1, same result. |
| `commit()` returns false (disk full, I/O) | same as above | Retries. This session reads null. |
| Killed after step 4, before step 5 | new store complete, old file still there | Step 5 deletes the old file. Never re-copies (so a newer PIN is never overwritten by the old one). |
| Old file deletion fails | old ciphertext left on disk | Step 5 tries again every launch. Harmless: same protection as today. |
| User downgrades to v0.2.0 after migrating | old file gone | v0.2.0 starts empty → sign in again. Acceptable; noted in the changelog. |

Nothing is ever written in plaintext; the plaintext exists only in memory between the old read
and the new encrypt.

**Decision for you — when security-crypto leaves the build.** The reader needs the old library.
- (Recommended) Keep it for **v0.3.0 only**, isolated in `LegacySecureStoreReader` with the warning
  suppressed (so the build is warning-free), then delete that file and the dependency in the
  release after. Anyone who skips v0.3.0 and installs a later version signs in once more.
- Or drop it now: no migration, everyone who updates signs in again once.

## 6. Tests to add
JVM unit tests (`app/src/test/.../data/secure/`), no Robolectric needed: `SecureStore` gets its
prefs, cipher and legacy reader through an internal constructor; tests pass an in-memory
`SharedPreferences` fake, `AesGcmSecretCipher` with a software AES key, and a fake legacy reader.

`AesGcmSecretCipherTest`
- round-trip ASCII, empty string, and non-ASCII values
- two encryptions of the same value differ (fresh IV)
- wrong AAD fails (a PIN blob pasted under `us_password` won't decrypt)
- tampered ciphertext / truncated blob / unknown prefix fails with an exception, never returns
  garbage

`SecureStoreTest`
- save/load PIN; save/load/clear US credentials; `clear()` empties values but keeps `migrated`
- stored bytes never contain the plaintext value
- decrypt failure → `load…` returns null and removes that value

`SecureStoreMigrationTest`
- fresh install (no old file) → `migrated` set, store empty, legacy reader never opened
- old store with PIN + email + password → all three readable from the new store, old file and
  alias deleted, `migrated` set
- old store with only a PIN → PIN migrated, credentials null
- old store unreadable (reader throws) → `migrated` set, old file deleted, store empty
- new write fails (fake `commit()` returns false / cipher throws) → old file kept, no `migrated`;
  a second open retries and succeeds
- `migrated` set but old file still present → old file deleted, values not re-copied (a PIN saved
  after migration survives)
- two threads open the store at once → legacy read happens once

Kept green unchanged: `SecureUsCredentialStoreTest`, `BlueLinkProviderTest` (they mock
`SecureStore`; its API doesn't change), and the `UsBlueLinkClient` / controller suites.

Instrumented test (`androidTest`, run by hand on the API 37 emulator; CI doesn't run these):
- real Keystore key: create, round-trip, reopen after process restart
- real ESP → v2: write values with security-crypto 1.1.0, open the new `SecureStore`, values match
  and `autolock_secure.xml` is gone

Manual, before merge:
- Upgrade on the emulator: install a `main` debug build, sign in + set the PIN, install the branch
  debug build over it (same debug key) → still signed in, manual lock asks for the same PIN, no
  re-login prompt.
- The maintainer's car test on the Pixel, updating the installed build in place (not a fresh
  install): walk-away auto-lock in Armed mode, widget Unlock… with the PIN, sign-out then sign-in.

## 7. Delivery
- Branch `claude/secure-store-keystore` cut from `main`. CHANGELOG `## [Unreleased]` line
  ("Encrypted storage no longer uses the deprecated security-crypto library; your sign-in carries
  over"). CLAUDE.md §2, §3, §5b and README:62 updated in the same change (CLAUDE.md rule).
- PR needs green CI (`./gradlew testDebugUnitTest lint assembleDebug`, plus a check that the
  build log has no deprecation warnings outside `LegacySecureStoreReader`) and the car test.
  Update the branch by merging `main`, never force-push.
- Builder's calls (left to the implementer): exact class/alias names, Base64 flavour, whether
  `warmUp()` logs a one-line INFO "Secure storage upgraded" (no values).
- Follow-ups (separate changes): fix `data_extraction_rules.xml` to exclude everything from cloud
  backup and device transfer; remove `LegacySecureStoreReader` + security-crypto in the release
  after v0.3.0.

## Asks for the maintainer
1. Approve approach A (Keystore AES-GCM + plain SharedPreferences), or pick B/C.
2. Keep security-crypto for v0.3.0 only for the migration (recommended), or drop it now and
   accept one re-sign-in.
