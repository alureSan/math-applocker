# AGENTS.md

Guidance for AI agents and contributors working on **Math Applocker**, plus the
pre-deployment test plan.

---

## 1. Project overview

A privacy-friendly Android parental-control app. Selected apps are gated behind a
short math challenge drawn as a `TYPE_APPLICATION_OVERLAY` window.

- **Stack:** Kotlin, Jetpack Compose (Material 3), Room, coroutines, KSP, R8.
- **SDK:** `minSdk 29`, `compileSdk 35`, `targetSdk 35`.
- **Application ID:** `com.mathapplocker.foss` (debug: `.debug`).
- **No network:** the release APK must not contain the `INTERNET` permission.
- **Languages:** English (`values/`) and Spanish (`values-es/`), 155 keys each.

### Architecture

```
MainActivity (Compose)
 ├─ PIN setup / auth (PBKDF2, lockout)
 ├─ Home (service switch, permissions, Basic/Advanced)
 ├─ Math rules (age, digits, operations, interval, task count)
 ├─ Custom challenge sets (Room: sets + questions, numeric/text)
 └─ App picker (labels, lazy icons, search)

OverlaySchedulerService (foreground, specialUse)
 ├─ ForegroundAppDetector (UsageStats, cached, screen-aware)
 ├─ QuestionGenerator (Basic generation / Advanced custom pool)
 ├─ MediaPauseController (audio focus + media-key fallback)
 └─ OverlayController → Compose quiz overlay
```

### Key source paths

| Path | Purpose |
|---|---|
| `app/src/main/java/com/mathapplocker/foss/math/` | Pure logic: `QuestionGenerator`, `ExpressionEvaluator`, `TextAnswerMatcher` |
| `app/src/main/java/com/mathapplocker/foss/data/` | `Prefs`, `PinHasher`, Room DB + migrations |
| `app/src/main/java/com/mathapplocker/foss/service/` | Foreground service, foreground-app detection |
| `app/src/main/java/com/mathapplocker/foss/overlay/` | Overlay window + quiz UI |
| `app/src/main/java/com/mathapplocker/foss/ui/` | Compose screens and theme |
| `app/src/test/java/` | JVM unit tests (JUnit 4) |
| `app/src/main/AndroidManifest.xml` | Permissions, service, boot receiver |

---

## 2. Build and test commands

Requires JDK 17+ and the Android SDK (platform 35, build-tools 35.0.0).
`local.properties` should point at the SDK (`sdk.dir=/home/Alure/android-sdk`).

```bash
./gradlew testDebugUnitTest        # JVM unit tests
./gradlew lintDebug                # Android Lint
./gradlew assembleDebug            # Debug APK
./gradlew assembleRelease          # Minified release APK (signed if keystore.properties exists)
./gradlew connectedDebugAndroidTest  # Instrumented tests (device/emulator required)
```

Useful artifact checks:

```bash
aapt dump permissions app/build/outputs/apk/release/app-release.apk   # assert no INTERNET
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

**Never commit** `keystore.properties`, `local.properties`, `*.jks`, `*.keystore`,
or APKs. They are already git-ignored.

---

## 3. Current test baseline

| Item | State |
|---|---|
| JVM unit tests | 30 passing (4 PIN, 8 expression evaluator, 13 generator, 5 text matcher) |
| Android Lint | 0 errors, 11 warnings (10 outdated-dependency notices, 1 `BatteryLife` policy warning) |
| CI (`.github/workflows/android.yml`) | `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleRelease`, release no-`INTERNET` check |
| `androidTest` | Not configured |
| Robolectric / Room migration tests | Not configured |
| Release smoke test | Manual only |

The remaining lint warnings are intentional: `GradleDependency` reports newer library
versions (upgrading needs a separate compatibility pass), and `BatteryLife` tracks the
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission that is core to parental control.


Connected test device: **HUAWEI JNY-LX2 (P40 lite), Android 10 / API 29**,
1080x2310 @ 480 dpi, debug and release builds installed. No emulator images
currently installed.

---

## 4. Pre-deployment test plan

Priority order: **P0** (must pass every commit), **P1** (must pass before
release), **P2** (manual / periodic).

### P0 — Extend JVM unit tests

| Area | Cases | Why |
|---|---|---|
| `QuestionGenerator` | Property/fuzz: answer equals expression, operands within digit/age range, no duplicates, `count=1..20`, `digits=0..4`, every operation subset, tables 1–9, Advanced fallback | Catches range/overflow regressions |
| `ExpressionEvaluator` | Fuzz random strings, nested parentheses, `Int.MIN/MAX`, `--5`, `2*-3`, very long input | Parent input must never crash the app |
| `TextAnswerMatcher` | Unicode (`ß`, dotless `ı`, emoji, zero-width), long strings, punctuation | Normalizer currently only strips NFD combining marks |
| `Settings` unlock policy | Interval clamping, `lastPromptAt=0`, exact expiry boundary, clock moved backward/forward | Uses wall-clock time |
| PIN lockout policy | 4 wrong still allowed, 5th → 30 s lock, success resets, persistence across restart, clock rollback | Lockout currently lives in UI code |
| Quiz answer state | Digit cap, negative toggle, backspace, text vs numeric submit, empty input, last question | Logic currently embedded in Compose |

**Required refactors for testability:**

1. Extract `ChallengeGate` from `OverlaySchedulerService.evaluateForegroundApp` /
   `showChallenge` — input: current package, blocked set, unlock window, screen
   state, challenge-in-flight; output: decision.
2. Extract `PinLockoutPolicy` from `PinScreens.kt` (attempts, lockout deadline).
3. Extract `QuizState` from `QuizOverlayScreen.kt` (input, submit, advance).

### P1 — Robolectric + Room integration tests

Add `org.robolectric:robolectric`, `androidx.test:core-ktx`,
`androidx.room:room-testing`, `kotlinx-coroutines-test`, and
`testOptions { unitTests.isIncludeAndroidResources = true }`.

- **`Prefs`:** every setter/getter round-trip, clamping, StringSet parsing of
  times tables/operations, corrupt values fall back safely, `StateFlow` emission.
- **Room migrations v1→v2→v3** with `MigrationTestHelper`. `exportSchema` is
  currently `false`; enable schema export and commit the JSON.
- **DAOs:** cascade delete of set questions, enabled-only filtering, ordering,
  rename.
- **`BootReceiver`:** enabled + apps + overlay permission → starts service; each
  missing condition → no-op.
- **`Notifications`:** channel creation, text selection per state, PendingIntent
  flags.

### P1 — Instrumented tests on the connected Huawei (API 29)

Add `androidTest` dependencies: `androidx.test.ext:junit`, `espresso-core`,
`compose-ui-test-junit4`, `ui-test-manifest`, `uiautomator`, `test:rules`.
Add `testTag`s where missing.

**Compose UI tests:**
- PIN setup: mismatch → error; 4 digits → saved; restart → auth required.
- PIN auth: wrong PIN increments attempts; 5 failures → lockout countdown;
  correct PIN resumes.
- Home: permission card reflects missing/granted state; protection switch starts
  service; Basic/Advanced switch.
- Math settings: age preset resets digits to Auto; interval/question count;
  operation toggles persist.
- Challenge sets: create/rename/delete; add numeric, text, invalid,
  division-by-zero, non-whole, mismatched-answer questions; per-question and
  per-set toggles.
- App picker: search, selection persists, excludes self and debug package.

**UiAutomator / service tests:**
- Grant overlay + usage access via appops, enable protection, launch a blocked
  app, assert overlay appears within ~2 s.
- Solve all questions → overlay disappears, unlock window starts, second launch
  within the interval shows no overlay.
- Press Home while overlay is shown → dismissed, not counted as solved; reopen →
  overlay again.
- Exit-to-home → launcher opens, media stays paused.
- Revoke overlay permission while running → service backs off, no crash loop.
- Screen off/on, rotation, multi-window, PiP (YouTube).
- Service restart (`START_STICKY`).
- Boot receiver: `adb reboot`, verify restart (EMUI autostart may block).
- Media pause/resume with Spotify/YouTube/VLC and headset play while overlay is
  open.

### P1 — Release acceptance / smoke test

Run against the **signed release APK** (R8 enabled):

```bash
./gradlew clean testDebugUnitTest lintDebug assembleRelease
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
aapt dump permissions app/build/outputs/apk/release/app-release.apk   # no INTERNET
```

Install the release APK and repeat the critical flow: PIN → permissions → enable
→ lock an app → solve → unlock window → reboot → update in place
(`adb install -r`) preserving prefs/DB.

Add a `testReleaseUnitTest` run and upload the R8 `mapping.txt` as a CI artifact.

### P2 — Non-functional and manual QA

| Type | Checks |
|---|---|
| Device matrix | Huawei/EMUI 10, stock Android 13, Android 14/15/16, Samsung One UI, tablet, landscape, split-screen |
| Localization | Spanish on API 29 (service/overlay context), pseudo-locale, RTL, long strings, notification language |
| Accessibility | TalkBack for PIN dots, keypad, backspace, progress, exit; font scale 200%; dark/light contrast; touch target size |
| Performance | Cold start < 2 s; overlay latency < 2 s; `dumpsys meminfo` after 1 h; battery with screen off |
| Reliability | 8-hour overnight run, battery saver, Doze, OEM task killer, low memory; overlay never floats over launcher |
| Security/privacy | PIN never stored plaintext; backup exclusion verified; exported components audited; lockout not bypassable by force-stop or clock change; no network calls |
| Upgrade | v1/v2 DB → v3, questions and settings survive; `MY_PACKAGE_REPLACED` restart |

---

## 5. Known risk register (test targets)

1. **Unanswerable custom answers** — `QuizOverlayScreen.kt` caps input at 5
   characters, but `QuestionsScreen.kt` accepts any `Int` answer. Answers like
   `100000` or `-12345` can never be typed. Add a test and either reject them at
   save time or size the input field to the answer.
2. **Wall-clock unlock/lockout** — `Settings.isWithinUnlockWindow()` and the PIN
   lockout use `System.currentTimeMillis()`. Changing the clock can extend the
   unlock or bypass the lockout. Test; consider `elapsedRealtime` + boot ID.
3. **Foreground detection staleness** — `ForegroundAppDetector` caches the last
   package and uses a 30 s query window after the first call; the service loop
   skips polling while the screen is off. Test screen-off/on and rapid switches.
4. **Service gating has no seams** — decision logic is private to the service;
   extract it (see P0 refactors) or it can only be tested manually.
5. **Untested DB migrations** — `exportSchema = false` blocks migration tests. A
   bad v1→v3 upgrade could lose parent-authored questions.
6. **Release-only failures** — R8 + Compose + Room; only the release APK proves
   the shipped artifact works.
7. **Boot/update restart on EMUI** — release-blocking for a parental-control app.

---

## 6. Deployment gates

- **Play Store target API:** as of **31 Aug 2026**, new apps and updates must
  target **API 36**. This project targets **35**, so a Play submission is
  currently blocked. Requires `compileSdk/targetSdk 36`, SDK platform 36, AGP and
  dependency compatibility, and retesting edge-to-edge and permission behavior.
  API 35 remains acceptable for F-Droid/GitHub/sideload distribution.
- **`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`** — Play requires this to be core
  functionality and user-initiated. Parental control qualifies, but be ready to
  justify it in the declaration. Lint already flags `BatteryLife`.
- **`specialUse` foreground service** — requires a Play Console declaration and
  review; keep the subtype explanation accurate.
- **Signing** — verify the release APK is signed; archive the keystore outside
  the repository.
- **CI minimum gate:** `testDebugUnitTest`, `lintDebug`, `assembleRelease`, and
  an APK permission check. CI now runs all four, plus `assembleDebug`,
  `testReleaseUnitTest`, and uploads the debug APK and R8 `mapping.txt`.

---

## 7. Execution order

1. **Day 1:** Add missing unit tests; extract `ChallengeGate`,
   `PinLockoutPolicy`, `QuizState`; run `testDebugUnitTest` + `lintDebug`.
2. **Day 2:** Enable Room schema export; add migration/DAO tests with Robolectric.
3. **Day 3–4:** Add Compose UI tests for PIN, Home, settings, and challenge sets;
   add `testTag`s.
4. **Day 5:** Instrumented overlay/service smoke tests on the Huawei; release APK
   smoke.
5. **Day 6:** Manual device matrix, Spanish/accessibility/battery checks, and the
   API 36 decision.

---

## 8. Conventions for agents

- Keep changes consistent with existing structure, naming, and Kotlin style.
- Prefer pure-Kotlin extraction over Android-dependent test plumbing.
- Deterministic tests: inject `kotlin.random.Random` and clocks; never assert on
  `System.currentTimeMillis()` directly.
- Do not change app behavior only to make a test pass; fix the behavior or the
  test.
- Run the relevant Gradle task before claiming a change is complete.
- Treat uncommitted source changes as user work; investigate before overwriting.
- When adding a test dependency, keep it in `app/build.gradle.kts` and update CI
  in the same change.
