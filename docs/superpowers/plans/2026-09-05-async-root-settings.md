# Async Root Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent OneLab's UI thread from waiting on root commands and ensure root subprocesses cannot block forever on output or execution.

**Architecture:** Consolidate root process handling in `Shell` behind one bounded execution primitive, then expose asynchronous `SettingsStore` write methods backed by a single worker executor and main-thread callbacks. Migrate only UI-originated settings writes to those methods while preserving existing feedback and state behavior.

**Tech Stack:** Java 11, Android Handler/Looper, `Process`, JUnit 4, Gradle Android plugin.

## Global Constraints

- Only audit finding 1 is in scope: root output consumption, timeout, and moving UI settings writes off the main thread.
- Do not change failure rollback behavior, aspect-ratio feedback, or source-text UI tests.
- Preserve synchronous `Shell` and `SettingsStore` APIs for callers already running off the UI thread.
- Install only to Android user 0 and never clear application data.

---

### Task 1: Bounded root process execution

**Files:**
- Modify: `app/src/main/java/io/github/pigerzhu/onelab/system/Shell.java`
- Create: `app/src/test/java/io/github/pigerzhu/onelab/system/ShellProcessRunnerTest.java`

**Interfaces:**
- Produces: package-private `Shell.ProcessRunner` that returns a result containing exit status and captured output within a fixed timeout.
- Preserves: `runSu`, `runSuForOutput`, `runSuInMasterMount`, and `runSuInMasterMountForOutput` signatures.

- [ ] **Step 1: Write failing process-runner tests**

Test a normal command, a command producing output larger than a pipe buffer, and a command exceeding a short injected timeout. Assert exit/output semantics and bounded completion.

- [ ] **Step 2: Run the focused test and verify RED**

Run: `.\gradlew.bat testDebugUnitTest --tests io.github.pigerzhu.onelab.system.ShellProcessRunnerTest`

Expected: compilation failure because the package-private process runner API does not exist.

- [ ] **Step 3: Implement one bounded execution path**

Start the process, drain its merged output concurrently, wait with `Process.waitFor(timeout, TimeUnit.MILLISECONDS)`, forcibly destroy it on timeout, join the drain task, and translate the result to the existing public return types.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the command from Step 2 and require zero failures.

- [ ] **Step 5: Commit the process fix**

Commit only `Shell.java` and `ShellProcessRunnerTest.java` with message `fix: bound root command execution`.

### Task 2: Asynchronous settings-write boundary

**Files:**
- Modify: `app/src/main/java/io/github/pigerzhu/onelab/system/SettingsStore.java`
- Create: `app/src/main/java/io/github/pigerzhu/onelab/system/SettingsWriteDispatcher.java`
- Create: `app/src/test/java/io/github/pigerzhu/onelab/system/SettingsWriteDispatcherTest.java`

**Interfaces:**
- Produces: `SettingsStore.setGlobalAsync`, `putGlobalQuietlyAsync`, `putGlobalsQuietlyAsync`, `setSecureAsync`, and `putSystemQuietlyAsync`, each accepting a single `Consumer<Boolean>` completion callback.
- Produces: a single ordered worker queue and a main-thread callback dispatcher.

- [ ] **Step 1: Write failing dispatcher tests**

Assert that work runs on a different thread from the caller, queued work preserves submission order, and each completion is delivered exactly once through the injected callback executor.

- [ ] **Step 2: Run the focused test and verify RED**

Run: `.\gradlew.bat testDebugUnitTest --tests io.github.pigerzhu.onelab.system.SettingsWriteDispatcherTest`

Expected: compilation failure because `SettingsWriteDispatcher` does not exist.

- [ ] **Step 3: Implement dispatcher and async store methods**

Use one daemon single-thread executor for settings operations and `Handler(Looper.getMainLooper())` for production completions. Each async store method delegates to its existing synchronous method inside the worker and posts exactly one result.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the command from Step 2 and require zero failures.

- [ ] **Step 5: Commit the asynchronous boundary**

Commit the dispatcher, tests, and `SettingsStore` changes with message `feat: dispatch settings writes off main thread`.

### Task 3: Migrate UI-originated settings writes

**Files:**
- Modify: UI screen classes under `app/src/main/java/io/github/pigerzhu/onelab/feature/`
- Modify: `app/src/main/java/io/github/pigerzhu/onelab/navigation/AppListPage.java`
- Test: existing unit tests plus compilation of all Android variants.

**Interfaces:**
- Consumes: the asynchronous `SettingsStore` methods from Task 2.
- Preserves: current Toast policy, current control state behavior, and all setting key/value formats.

- [ ] **Step 1: Add a failing architecture test**

Create a source-boundary test that enumerates UI packages and fails when a direct synchronous settings-write method is called from a screen or `AppListPage`; exempt reads and clients that already execute inside their own background executor.

- [ ] **Step 2: Run the architecture test and verify RED**

Run the focused test and confirm it reports the current synchronous UI call sites.

- [ ] **Step 3: Migrate each reported call site**

Replace synchronous writes with the matching async method. Move only code that depends on the boolean result into the completion callback, guard callbacks from updating a finishing or destroyed Activity, and leave unrelated UI semantics unchanged.

- [ ] **Step 4: Run the architecture test and unit suite**

Run: `.\gradlew.bat testDebugUnitTest`

Expected: all tests pass and the boundary test reports no synchronous UI settings writes.

- [ ] **Step 5: Commit UI migration**

Commit only the migrated screens, navigation page, and boundary test with message `fix: keep root settings writes off UI thread`.

### Task 4: Full verification and device installation

**Files:**
- No production changes expected.

**Interfaces:**
- Verifies all outputs of Tasks 1–3.

- [ ] **Step 1: Run full verification**

Run: `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease`

Expected: `BUILD SUCCESSFUL` with no test, lint-error, compilation, or packaging failures.

- [ ] **Step 2: Inspect the final diff and workspace state**

Run `git diff HEAD~3 --check`, inspect `git status --short`, and confirm `.superpowers/` and `tmp/` remain untouched.

- [ ] **Step 3: Install the debug APK to user 0**

Use the documented ADB server socket and `adb install --user 0 -r` command from the local troubleshooting guide. Do not clear app data.

- [ ] **Step 4: Verify package presence and launch**

Confirm the package is installed for user 0 and launch the main activity once. Do not toggle user settings automatically.
