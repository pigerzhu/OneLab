# Diagnostic Coverage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cover all user-facing feature state from 1.0 through current `dev` in the diagnostic bundle and prevent future setting-key omissions.

**Architecture:** Keep `Settings.Global` metadata in `DiagnosticCatalog`; keep the non-Settings Qishui state in `DiagnosticReport.appendExtraFeatureState()`. Reuse `QishuiMusicClient` constants so feature code and diagnostics cannot disagree about its package and preference contract.

**Tech Stack:** Java, Android SharedPreferences and PackageManager, JUnit 4, Gradle.

## Global Constraints

- Do not read Qishui Music private data or execute root commands during report generation.
- Preserve the dedicated `split-view.txt` handling for `KEY_SPLIT_VIEW_ALLOWED_PACKAGES`.
- Do not modify release versioning or publish artifacts in this change.

---

### Task 1: Lock diagnostic coverage with failing tests

**Files:**
- Modify: `app/src/test/java/io/github/pigerzhu/onelab/diagnostics/DiagnosticCatalogTest.java`
- Create: `app/src/test/java/io/github/pigerzhu/onelab/diagnostics/DiagnosticReportTest.java`

**Interfaces:**
- Consumes: `DiagnosticCatalog.FEATURES`, `DiagnosticCatalog.VALUES`, `SettingsKeys`.
- Produces: assertions for all setting keys, new image feature IDs, Qishui formatting/package state, and report format 5.

- [ ] Add a reflection-based assertion that every public string field beginning with `KEY_` is cataloged, except `KEY_SPLIT_VIEW_ALLOWED_PACKAGES` which is reported by `split-view.txt`.
- [ ] Add assertions for `experiments.split_image_fullscreen`, `apps.coolapk_image_fullscreen`, and `apps.xhs_image_fullscreen`.
- [ ] Add report assertions against package-private pure contracts for Qishui state, package inclusion, and report format.
- [ ] Run `./gradlew testDebugUnitTest` from `app` and verify failures identify the missing entries and contracts.

### Task 2: Add the missing report entries

**Files:**
- Modify: `app/src/main/java/io/github/pigerzhu/onelab/diagnostics/DiagnosticCatalog.java`
- Modify: `app/src/main/java/io/github/pigerzhu/onelab/diagnostics/DiagnosticReport.java`
- Modify: `app/src/main/java/io/github/pigerzhu/onelab/system/QishuiMusicClient.java`

**Interfaces:**
- Produces: `QishuiMusicClient.PACKAGE_NAME`, `QishuiMusicClient.PREFERENCES_NAME`, `QishuiMusicClient.PREFERENCE_ENABLED`; `DiagnosticReport.REPORT_FORMAT`; package-private Qishui state formatter.

- [ ] Add the three missing catalog features with Coolapk package `com.coolapk.market` and Xiaohongshu package `com.xingin.xhs`.
- [ ] Expose the Qishui package and preference names as public constants owned by `QishuiMusicClient`.
- [ ] Append Qishui's OneLab-owned preference state and installed status to `features.txt`.
- [ ] Add `com.luna.music` to `packages.txt` and change `report_format` to 5.
- [ ] Run `./gradlew testDebugUnitTest` and verify all tests pass.

### Task 3: Verify, install, and commit

**Files:**
- Verify only: all changed files.

**Interfaces:**
- Consumes: completed diagnostic coverage implementation.
- Produces: tested APK installed to Android user 0 and one scoped Git commit.

- [ ] Run `git diff --check` and inspect the scoped diff.
- [ ] From `app`, run `./gradlew testDebugUnitTest assembleDebug lintDebug assembleRelease` using the documented JDK and proxy command form.
- [ ] Confirm ADB connectivity, install the debug APK with `adb install --user 0 -r`, and verify package/version without clearing data.
- [ ] Confirm the install did not enable any experimental setting.
- [ ] Stage only the design, plan, implementation, and tests; commit with `feat: complete diagnostic feature coverage`.
