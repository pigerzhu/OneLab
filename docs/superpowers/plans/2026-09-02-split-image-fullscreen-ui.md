# Split Image Fullscreen UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a reusable split action/switch card and a preview-only “分栏图片全屏” management page containing a Coolapk row, then install it to Android user 0 for visual review.

**Architecture:** `SplitActionSwitchCard` lives in `ui` and owns only layout and independent click regions. `SplitImageFullscreenScreen` owns Settings state and navigation; `MainActivity` composes it into the applications page. This phase intentionally contains no Hook behavior.

**Tech Stack:** Java, Android Views, MaterialSwitch, Settings.Global through `SettingsStore`, JUnit 4, Gradle.

## Global Constraints

- Preserve OneLab's existing card colors, spacing, typography, and rounded corners.
- The left action and right switch are independent touch targets separated by a vertical divider.
- The first management page contains only Coolapk.
- This phase must not install a Coolapk Hook or modify Activity Embedding rules.
- Update Simplified Chinese, Taiwan Traditional Chinese, English, and Korean resources together.
- Install only to Android user 0.

---

### Task 1: Reusable split action/switch card

**Files:**
- Create: `app/src/main/java/io/github/pigerzhu/onelab/ui/SplitActionSwitchCard.java`
- Test: `app/src/test/java/io/github/pigerzhu/onelab/ui/SplitActionSwitchCardTest.java`

**Interfaces:**
- Consumes: `Ui`, `Context`, `MaterialSwitch`, title text, optional subtitle, and `Runnable` action.
- Produces: constructor `SplitActionSwitchCard(Context context, Ui ui, String title, String subtitle, MaterialSwitch toggle, Runnable action)`.

- [ ] **Step 1: Write a failing source-contract test**

Test that the class source contains separate `actionRegion.setOnClickListener`, divider construction, and a switch region that does not assign the card-level click listener.

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests io.github.pigerzhu.onelab.ui.SplitActionSwitchCardTest`

Expected: FAIL because `SplitActionSwitchCard.java` does not exist.

- [ ] **Step 3: Implement the component**

Create a horizontal card whose weighted left `LinearLayout` owns the navigation click, whose one-pixel themed divider has vertical margins, and whose trailing region contains the supplied `MaterialSwitch`. Reuse `ui.card()`, `ui.text(...)`, and `ui.dp(...)`; do not read Settings.

- [ ] **Step 4: Run the focused test**

Run: `.\gradlew.bat testDebugUnitTest --tests io.github.pigerzhu.onelab.ui.SplitActionSwitchCardTest`

Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `ui: add split action switch card`

### Task 2: Preview screen and application navigation

**Files:**
- Create: `app/src/main/java/io/github/pigerzhu/onelab/feature/applications/SplitImageFullscreenScreen.java`
- Modify: `app/src/main/java/io/github/pigerzhu/onelab/MainActivity.java`
- Modify: `app/src/main/java/io/github/pigerzhu/onelab/contract/SettingsKeys.java`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`
- Modify: `app/src/main/res/values-ko/strings.xml`
- Test: `app/src/test/java/io/github/pigerzhu/onelab/ui/LocaleStringsTest.java`

**Interfaces:**
- Consumes: `SplitActionSwitchCard` from Task 1 and existing `MainActivity.showPage(...)` / `setNestedBackAction(...)` navigation.
- Produces: `entryCard()` for the applications page and `showPage()` for the management page.

- [ ] **Step 1: Add failing locale/resource assertions**

Require matching keys for the entry title, page title, and Coolapk row in all four language resource files.

- [ ] **Step 2: Run locale tests and verify they fail**

Run: `.\gradlew.bat testDebugUnitTest --tests io.github.pigerzhu.onelab.ui.LocaleStringsTest`

Expected: FAIL because the new keys are absent.

- [ ] **Step 3: Implement the preview screen**

Add master and Coolapk selection keys. Build the applications-page entry using `SplitActionSwitchCard`; keep the master selection and Coolapk selection independently persisted. Build a nested management page with the standard header and one Coolapk switch row. Wire back navigation to the applications page.

- [ ] **Step 4: Add all locale strings**

Use concise native translations for “分栏图片全屏” and “酷安”; keep the same keys and placeholder shapes across all locale files.

- [ ] **Step 5: Run focused tests**

Run: `.\gradlew.bat testDebugUnitTest --tests io.github.pigerzhu.onelab.ui.LocaleStringsTest --tests io.github.pigerzhu.onelab.navigation.PageNavigationPolicyTest`

Expected: PASS.

- [ ] **Step 6: Commit**

Commit message: `ui: add split image fullscreen preview`

### Task 3: Build, install, and visual handoff

**Files:**
- Verify only; no production files should change.

**Interfaces:**
- Consumes: completed Tasks 1–2.
- Produces: debug APK installed to Android user 0.

- [ ] **Step 1: Run verification**

Run: `.\gradlew.bat testDebugUnitTest assembleDebug lintDebug`

Expected: all tasks succeed.

- [ ] **Step 2: Inspect repository state**

Run: `git diff --check` and `git status --short`.

Expected: no unintended generated or analysis files are staged; existing `tmp/` and `.superpowers/` remain untracked.

- [ ] **Step 3: Install to user 0**

Run: `adb install --user 0 -r app\build\outputs\apk\debug\app-debug.apk`

Expected: `Success`.

- [ ] **Step 4: Confirm installation**

Run: `adb shell pm path io.github.pigerzhu.onelab`.

Expected: one installed base APK path for user 0.

- [ ] **Step 5: Hand off for visual review**

Ask the user to verify divider height, independent touch behavior, entry placement, management-page spacing, and the Coolapk row before implementing the Hook.
