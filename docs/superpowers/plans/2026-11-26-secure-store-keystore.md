# SecureStore on a Keystore AES-GCM key — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move `SecureStore` off deprecated `EncryptedSharedPreferences`/`MasterKey` onto our own Keystore AES-256-GCM key, migrating existing PIN + US sign-in once, with no re-login.

**Architecture:** A pure-JVM `AesGcmSecretCipher` encrypts each value (AAD = key name) into a plain private prefs file `secure_v2`. `SecureStore` keeps its public API; on first open it runs a one-shot, atomic migration from the old `autolock_secure` file through `LegacySecretSource`, whose only Android implementation is the one file allowed to touch security-crypto.

**Tech Stack:** Kotlin, Android Keystore (`AndroidKeyStore`, `KeyGenParameterSpec`), `javax.crypto` AES/GCM/NoPadding, `java.util.Base64`, Hilt, JUnit4 + MockK (JVM), AndroidX test (instrumented).

**Spec:** `docs/superpowers/specs/2026-11-26-secure-store-design.md`

## Global Constraints
- minSdk 26; StrongBox only on API 28+ with fallback to TEE on `StrongBoxUnavailableException`.
- Key alias `leavenlock_secure_v1`, AES-256, GCM, NoPadding, 128-bit tag. Never `setUserAuthenticationRequired` / `setUnlockedDeviceRequired`.
- New prefs file `secure_v2`; keys `service_pin`, `us_username`, `us_password`, marker `migrated` (Int 1).
- Blob format: `"v1:" + Base64(iv ‖ ciphertext)` using `java.util.Base64` (works in JVM tests).
- Old file `autolock_secure`, old alias `_androidx_security_master_key_`.
- Only `data/secure/LegacySecureStoreReader.kt` may import `androidx.security.crypto`, under `@file:Suppress("DEPRECATION")`.
- Never write plaintext to disk; never log values, only exception class names.
- `SecureStore` public API unchanged: `savePin`, `loadPin`, `saveUsCredentials`, `loadUsUsername`, `loadUsPassword`, `clearUsCredentials`, `clear`; new `warmUp()`.
- Writes use `commit()`.

## Review Focus
1. Keystore key exists but is invalidated/missing (new phone via device transfer, OS key loss) → values read as null and are removed; app asks for sign-in, no crash. (Task 2 test `decryptFailureRemovesValueAndReturnsNull`.)
2. A PIN saved after migration must survive later launches while the old file lingers. (Task 3 test `markerSetWithLingeringOldFileDeletesItWithoutRecopying`.)
3. First access from the UI thread races `warmUp()` on IO → migrate once. (Task 3 test `concurrentFirstOpenMigratesOnce`.)
4. New key can't be created → old file must not be deleted. (Task 3 test `failedWriteKeepsOldFileAndRetries`, cipher throwing.)
5. Tampered/truncated blob in the file → exception inside the cipher, null to the caller, never garbage. (Task 1 tamper tests + Task 2 decrypt-failure test.)

---

### Task 1: `AesGcmSecretCipher`

**Files:**
- Create: `app/src/main/java/io/github/gthibo/leavenlock/data/secure/SecretCipher.kt`
- Test: `app/src/test/java/io/github/gthibo/leavenlock/data/secure/AesGcmSecretCipherTest.kt`

**Interfaces:**
- Produces: `interface SecretCipher { fun encrypt(plain: String, aad: String): String; fun decrypt(blob: String, aad: String): String }` and `class AesGcmSecretCipher(private val key: () -> SecretKey) : SecretCipher`. `decrypt` throws `GeneralSecurityException` (or `IllegalArgumentException` for a malformed blob) on any failure.

- [ ] **Step 1: Write failing tests** with a software key (`KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()`):
  `roundTripsAsciiEmptyAndNonAscii` ("1234", "", "pässwörd ✓"); `sameValueEncryptsDifferently` (two blobs differ); `blobStartsWithV1Prefix`; `wrongAadFails` (encrypt under "service_pin", decrypt under "us_password" → throws); `tamperedCiphertextFails` (flip one byte after Base64-decoding → throws); `truncatedBlobFails` (`"v1:AAAA"` → throws); `unknownPrefixFails` (`"v9:..."` → throws).
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "*AesGcmSecretCipherTest"` → FAIL (unresolved reference).
- [ ] **Step 3: Implement.** `Cipher.getInstance("AES/GCM/NoPadding")`; encrypt with `init(ENCRYPT_MODE, key())` (no IV supplied — required for Keystore keys), `updateAAD(aad.toByteArray(UTF_8))`, output `iv ‖ ct`; decrypt with `GCMParameterSpec(128, iv)` where iv = first 12 bytes; reject blobs shorter than 12 + 16 bytes.
- [ ] **Step 4: Run** the same command → PASS.
- [ ] **Step 5: Commit** `feat(secure): add AES-GCM secret cipher`.

### Task 2: `SecureStore` on `secure_v2`

**Files:**
- Modify: `app/src/main/java/io/github/gthibo/leavenlock/data/secure/SecureStore.kt` (whole file)
- Create: `app/src/main/java/io/github/gthibo/leavenlock/data/secure/LegacySecretSource.kt`
- Create (test): `app/src/test/java/io/github/gthibo/leavenlock/data/secure/InMemorySharedPreferences.kt`, `SecureStoreTest.kt`

**Interfaces:**
- Consumes: `SecretCipher` (Task 1).
- Produces:
  - `interface LegacySecretSource { fun exists(): Boolean; fun read(): LegacySecrets; fun delete() }`, `data class LegacySecrets(val pin: String?, val usUsername: String?, val usPassword: String?)` with `toString()` = `"LegacySecrets(redacted)"`, and `object NoLegacySecrets : LegacySecretSource` (exists = false).
  - `class SecureStore internal constructor(prefs: () -> SharedPreferences, cipher: SecretCipher, legacy: LegacySecretSource, warn: (String) -> Unit)`. The Hilt `@Inject constructor(@ApplicationContext context)` is added in Task 4; until then the class has no Hilt constructor, so Tasks 2–4 land together before the app is run (unit tests and `compileDebugUnitTestKotlin` are the gate for Tasks 2–3).
  - `fun warmUp()` opens the store (and in Task 3 migrates).
- Test helper: `class InMemorySharedPreferences(var failCommits: Boolean = false) : SharedPreferences` — map-backed; `Editor.commit()` returns false and applies nothing when `failCommits`; `apply()` same as commit; `getAll()` returns a copy.

- [ ] **Step 1: Write failing tests** (`SecureStore(prefs = { fake }, cipher = AesGcmSecretCipher { softwareKey }, legacy = NoLegacySecrets, warn = {})`):
  `savesAndLoadsPin`; `savesLoadsAndClearsUsCredentials` (clear leaves PIN); `clearEmptiesValuesButKeepsMarker` (`fake.getInt("migrated", 0) == 1` after `clear()`); `storedValuesNeverContainPlaintext` (every value in `fake.all` lacks "1234"/"hunter2"/"me@x.com" and starts with "v1:"); `decryptFailureRemovesValueAndReturnsNull` (put `"v1:garbage"` under `service_pin` → `loadPin() == null` and `!fake.contains("service_pin")`); `missingValuesReturnNull`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "*SecureStoreTest"` → FAIL.
- [ ] **Step 3: Implement.** Lazy, `synchronized` open that sets `migrated = 1` when absent and no legacy file exists. `load(key)` = decrypt with AAD = key; on exception `warn("Secure value unreadable: ${e.javaClass.simpleName}")`, remove key, return null. `clear()` removes the three keys only.
- [ ] **Step 4: Run** → PASS; also run `--tests "*SecureUsCredentialStoreTest" --tests "*BlueLinkProviderTest"` → PASS unchanged.
- [ ] **Step 5: Commit** `feat(secure): store secrets as AES-GCM blobs in plain prefs`.

### Task 3: One-time migration

**Files:**
- Modify: `app/src/main/java/io/github/gthibo/leavenlock/data/secure/SecureStore.kt` (open path)
- Test: `app/src/test/java/io/github/gthibo/leavenlock/data/secure/SecureStoreMigrationTest.kt` (uses a `FakeLegacy(var secrets: LegacySecrets?, var throwOnRead: Boolean)` counting `reads` and `deletes`; `exists()` = not yet deleted and secrets/throw configured)

**Interfaces:** Consumes `LegacySecretSource`, `LegacySecrets` (Task 2). Produces nothing new.

- [ ] **Step 1: Write failing tests:**
  `freshInstallSetsMarkerWithoutReadingLegacy` (reads == 0, marker 1); `migratesPinAndCredentials` ("1234", "me@x.com", "hunter2" readable; deletes == 1; marker 1); `migratesPinOnly`; `unreadableOldStoreStartsEmptyAndDeletesIt` (throwOnRead → marker 1, deletes == 1, all null); `failedWriteKeepsOldFileAndRetries` (first store with `failCommits = true` → loads null, deletes == 0, no marker; then `failCommits = false`, new `SecureStore` on same fake → values migrated, deletes == 1); `cipherFailureKeepsOldFile` (cipher whose `encrypt` throws → deletes == 0, no marker); `markerSetWithLingeringOldFileDeletesItWithoutRecopying` (marker 1 + PIN "9999" already in v2, legacy holds "1234" → `loadPin() == "9999"`, reads == 0, deletes == 1); `concurrentFirstOpenMigratesOnce` (8 threads call `loadPin()` behind a `CountDownLatch` → reads == 1).
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "*SecureStoreMigrationTest"` → FAIL.
- [ ] **Step 3: Implement** spec §5 steps 1–5 in the synchronized open: marker set → cleanup only (`if (legacy.exists()) runCatching { legacy.delete() }`); no legacy → write marker; read (throw → marker + delete); else encrypt all present values and put them + marker in **one** editor, `commit()`; delete legacy only when commit returned true. Any failure before commit: `warn(...)`, leave state, mark the store "opened" for this process so reads return null without retrying every call (retry happens next process).
- [ ] **Step 4: Run** → PASS (and Task 2 tests still PASS).
- [ ] **Step 5: Commit** `feat(secure): migrate old encrypted prefs once, atomically`.

### Task 4: Android wiring (Keystore key, legacy reader, Hilt, warm-up)

**Files:**
- Create: `app/src/main/java/io/github/gthibo/leavenlock/data/secure/KeystoreKey.kt`
- Create: `app/src/main/java/io/github/gthibo/leavenlock/data/secure/LegacySecureStoreReader.kt`
- Modify: `SecureStore.kt` (`@Inject` secondary constructor)
- Modify: `app/src/main/java/io/github/gthibo/leavenlock/AutoLockApp.kt` (entry point + warm-up)
- Test: `app/src/androidTest/java/io/github/gthibo/leavenlock/data/secure/SecureStoreDeviceTest.kt`; add `androidTestImplementation(libs.androidx.test.runner)` only if the runner isn't already present.

**Interfaces:**
- Produces: `object KeystoreKey { fun getOrCreate(alias: String = "leavenlock_secure_v1"): SecretKey }`; `class LegacySecureStoreReader(context: Context) : LegacySecretSource`; `AppEntryPoint.secureStore(): SecureStore`.

- [ ] **Step 1: Write the instrumented test** (`AndroidJUnit4`): `keystoreRoundTripSurvivesReopen` (save PIN, new `SecureStore(context)` → same PIN); `migratesRealEncryptedSharedPreferences` (wipe both files, write the three keys with ESP exactly as the old code did, then `SecureStore(context).warmUp()` → values match, `File(dataDir, "shared_prefs/autolock_secure.xml")` gone, old alias absent from `KeyStore.getInstance("AndroidKeyStore")`).
- [ ] **Step 2: Implement.** `KeystoreKey`: load `AndroidKeyStore`, return existing entry or generate with `KeyGenParameterSpec.Builder(alias, ENCRYPT or DECRYPT).setBlockModes(GCM).setEncryptionPaddings(NONE).setKeySize(256)`, StrongBox on API 28+ with catch-and-retry without it. `LegacySecureStoreReader`: `exists()` = the old xml file exists; `read()` = the old `createEncryptedPrefs()` body (old master key builder with StrongBox fallback) then `getString` ×3; `delete()` = `deleteSharedPreferences("autolock_secure")` + delete alias `_androidx_security_master_key_` (ignore missing). `SecureStore @Inject constructor(@ApplicationContext context)` = `this({ context.getSharedPreferences("secure_v2", MODE_PRIVATE) }, AesGcmSecretCipher { KeystoreKey.getOrCreate() }, LegacySecureStoreReader(context), { Log.w("SecureStore", it) })`. `AutoLockApp.onCreate`: launch on `Dispatchers.IO` → `secureStore().warmUp()` wrapped in `runCatching`.
- [ ] **Step 3: Run** `./gradlew assembleDebug testDebugUnitTest` → BUILD SUCCESSFUL; `./gradlew connectedDebugAndroidTest --tests`-equivalent on the API 37 emulator (`-Pandroid.testInstrumentationRunnerArguments.class=io.github.gthibo.leavenlock.data.secure.SecureStoreDeviceTest`) → 2 PASS.
- [ ] **Step 4: Check warnings:** `./gradlew :app:compileDebugKotlin --rerun-tasks 2>&1 | grep -i "deprecat"` → no lines.
- [ ] **Step 5: Commit** `feat(secure): Keystore key, legacy reader and warm-up`.

### Task 5: Docs, changelog, full verification

**Files:** `CLAUDE.md` (§2 Storage line, §3 `data/secure/` entry, §5b SecureStore bullet, §8 where-we-left-off, §9 new 2026-11-26 entry), `README.md:62`, `CHANGELOG.md` (`## [Unreleased]`, create if absent: "Encrypted storage no longer uses the deprecated security-crypto library; your PIN and sign-in carry over. Going back to 0.2.0 after updating means signing in again.").

- [ ] **Step 1: Edit docs** with the facts from the Global Constraints (alias, file, AES-GCM, StrongBox/TEE, migration, removal of security-crypto planned for the release after v0.3.0; follow-up: `data_extraction_rules.xml`).
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest lint assembleDebug` → BUILD SUCCESSFUL, 0 lint errors, test count = previous 196 + new.
- [ ] **Step 3: Manual upgrade check on the emulator:** install a `main` debug build, sign in with a real BlueLink account + PIN (or, without one, run the instrumented migration test instead), then install the branch debug build over it (same debug key) → still signed in, Home's manual lock asks for and accepts the same PIN, `shared_prefs/autolock_secure.xml` gone (`adb shell run-as io.github.gthibo.leavenlock ls shared_prefs`).
- [ ] **Step 4: Commit** `docs: record the SecureStore Keystore move`.
