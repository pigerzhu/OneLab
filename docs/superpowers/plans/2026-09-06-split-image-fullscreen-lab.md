# Split Image Fullscreen Lab Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the split-image fullscreen UI into Experiments and rename it to “分屏下全屏浏览图片” without changing settings or runtime behavior.

**Architecture:** Keep `SplitImageFullscreenScreen` and all settings keys in place. Change only its parent navigation, back destination, and localized display strings.

**Tech Stack:** Android Java, Android string resources, Gradle unit tests and lint.

## Global Constraints

- Preserve existing settings keys, switch state, supported applications, and hooks.
- Install the verified debug APK only to Android user 0.

---

### Task 1: Move the entry and navigation destination

**Files:**
- Modify: `app/src/main/java/io/github/pigerzhu/onelab/MainActivity.java`
- Modify: `app/src/main/java/io/github/pigerzhu/onelab/feature/applications/SplitImageFullscreenScreen.java`

**Interfaces:**
- Consumes: `SplitImageFullscreenScreen.entryCard()` and `MainActivity.showExperimentsPage(boolean)`.
- Produces: one Experiments entry whose nested page returns to Experiments.

- [ ] Remove `splitImageFullscreenScreen.entryCard()` from `showSamsungAppsPage(boolean)`.
- [ ] Add `splitImageFullscreenScreen.entryCard()` to `showExperimentsPage(boolean)`.
- [ ] Change the nested back action to `host.showExperimentsPage(true)`.

### Task 2: Rename localized UI copy

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`
- Modify: `app/src/main/res/values-ko/strings.xml`

**Interfaces:**
- Consumes: `split_image_fullscreen_title` and `split_image_fullscreen_page_title`.
- Produces: renamed entry and page title in all supported locales.

- [ ] Set Simplified Chinese strings to `分屏下全屏浏览图片`.
- [ ] Set Traditional Chinese strings to `分割畫面下全螢幕瀏覽圖片`.
- [ ] Set English strings to `Browse images fullscreen in split view`.
- [ ] Set Korean strings to `분할 화면에서 이미지 전체 화면 보기`.

### Task 3: Verify and install

**Files:**
- Verify all modified files above.

**Interfaces:**
- Consumes: the completed navigation and resource changes.
- Produces: a tested APK installed to Android user 0.

- [ ] Run `.\\gradlew.bat testDebugUnitTest lintDebug assembleDebug` and require `BUILD SUCCESSFUL`.
- [ ] Install with `adb install --user 0 -r app\\build\\outputs\\apk\\debug\\app-debug.apk` and require `Success`.
- [ ] Inspect `git diff --check` and commit only the scoped feature changes.
