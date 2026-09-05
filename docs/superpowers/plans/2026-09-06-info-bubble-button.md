# Reusable Info Bubble Button Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a reusable non-modal information bubble button and place it beside the Qishui Music title.

**Architecture:** `InfoBubbleButton` owns its icon, anchored `PopupWindow`, timeout, and detach cleanup. `QishuiMusicScreen` supplies localized message and accessibility text and keeps all setting behavior unchanged.

**Tech Stack:** Java, Android views, Material Components, JUnit 4.

## Global Constraints

- Reuse `R.drawable.ic_info`.
- The popup must not add a scrim or block unrelated page controls.
- Dismiss on a second icon click, after 4 seconds, or when detached.
- Install only to Android user 0 and do not clear app data.

---

### Task 1: Add failing UI contract tests

**Files:**
- Create: `app/src/test/java/io/github/pigerzhu/onelab/ui/InfoBubbleButtonTest.java`
- Modify: `app/src/test/java/io/github/pigerzhu/onelab/ui/LocaleStringsTest.java`

**Interfaces:**
- Produces: source-contract assertions for non-focusable popup behavior, timeout, toggle dismissal, detach cleanup, Qishui integration, and localized resource keys.

- [ ] Write tests requiring `InfoBubbleButton`, `DISPLAY_DURATION_MS = 4_000L`, `setFocusable(false)`, showing-state toggle, delayed dismissal, detach cleanup, and Qishui integration.
- [ ] Require `info_bubble_content_description` and `qishui_music_version_notice` in every locale.
- [ ] Run the focused tests and confirm they fail because the component and resource keys do not exist.

### Task 2: Implement the component and integration

**Files:**
- Create: `app/src/main/java/io/github/pigerzhu/onelab/ui/InfoBubbleButton.java`
- Modify: `app/src/main/java/io/github/pigerzhu/onelab/feature/applications/QishuiMusicScreen.java`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`
- Modify: `app/src/main/res/values-ko/strings.xml`

**Interfaces:**
- Produces: `InfoBubbleButton(Context, Ui, CharSequence, CharSequence)`.

- [ ] Implement a 32 dp image button using `ic_info`, an anchored bounded-width Material card popup, non-focusable window behavior, four-second dismissal, click toggle, and detach cleanup.
- [ ] Insert the component immediately after the Qishui title, with flexible space before the existing switch.
- [ ] Add Simplified Chinese, Taiwan Traditional Chinese, English, and Korean strings.
- [ ] Run the focused tests and confirm they pass.

### Task 3: Verify, install, and commit

**Files:**
- Verify: all changed production, test, resource, and plan files.

**Interfaces:**
- Produces: verified APK installed to user 0 and one scoped implementation commit.

- [ ] Run `git diff --check` and inspect the final diff.
- [ ] Run `testDebugUnitTest assembleDebug lintDebug assembleRelease` with the documented JDK.
- [ ] Install `app-debug.apk` with `adb install --user 0 -r` and verify the installed package version.
- [ ] Stage only scoped files and commit with `feat: add reusable info bubble`.
