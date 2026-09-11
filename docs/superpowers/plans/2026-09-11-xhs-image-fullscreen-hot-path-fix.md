# XHS Image Fullscreen Hot-Path Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve Xiaohongshu split-image fullscreen behavior while removing all View-tree work from scrolling and `ViewGroup.addView(...)`.

**Architecture:** Replace global view-addition observation with a small touch-sequence policy and one delayed, coalesced decor-tree inspection after a confirmed tap. Keep the existing app-to-system-server broadcast and bounds controller unchanged.

**Tech Stack:** Java, Android/Xposed APIs, JUnit 4, Gradle

## Global Constraints

- Never hook `ViewGroup.addView(...)` for this feature.
- Never scan a View tree from MOVE events or ordinary list scrolling.
- Preserve the existing settings keys and broadcast contract.
- Install and test APKs only for Android user 0.
- Do not clear Xiaohongshu application data.

---

### Task 1: Touch-trigger policy and hot-path regression guard

**Files:**
- Create: `app/src/main/java/io/github/pigerzhu/onelab/hook/applications/XhsViewerTapPolicy.java`
- Create: `app/src/test/java/io/github/pigerzhu/onelab/hook/applications/XhsViewerTapPolicyTest.java`

**Interfaces:**
- Produces: `XhsViewerTapPolicy.onDown(float, float, long)`, `onMove(float, float, int)`, `onCancel()`, and `onUp(float, float, long, int)`.
- `onUp(...)` returns `true` only for a single-pointer tap within touch slop and below the long-press timeout.

- [ ] Write JUnit tests for a valid tap and rejected drag, multi-pointer, cancel, and long press.
- [ ] Run the focused tests and confirm the expected failures.
- [ ] Implement the minimal touch policy and run the focused tests to green.

### Task 2: Event-driven viewer detection

**Files:**
- Modify: `app/src/main/java/io/github/pigerzhu/onelab/hook/applications/XhsImageFullscreenHook.java`
- Modify: `app/src/main/java/io/github/pigerzhu/onelab/hook/applications/XhsImageFullscreenPolicy.java`
- Modify: `app/src/test/java/io/github/pigerzhu/onelab/hook/applications/XhsImageFullscreenPolicyTest.java`

**Interfaces:**
- Consumes: `XhsViewerTapPolicy` from Task 1.
- Produces: one coalesced delayed inspection after a valid tap and one coalesced exit inspection.

- [ ] Add failing policy tests for the chosen 500ms enter delay and single-pass candidate facts.
- [ ] Remove the global `addView` hook and invoke the tap policy from `dispatchTouchEvent`.
- [ ] Coalesce pending callbacks by removing the prior runnable before scheduling another.
- [ ] Replace nested candidate scans with a single traversal that computes the required structural facts.
- [ ] Cancel callbacks and restore reported state on pause, destroy, setting disable, or Activity replacement.
- [ ] Run focused tests and all unit tests.

### Task 3: Documentation and device verification

**Files:**
- Modify: `docs/XHS_FOLD_RESEARCH.md`
- Modify: `docs/TROUBLESHOOTING.md`

**Interfaces:**
- Records the confirmed hot-path failure and the event-driven replacement.

- [ ] Document the confirmed cause, the rejected global-hook pattern, and the new diagnostic rule.
- [ ] Run `git diff --check`, `testDebugUnitTest`, `assembleDebug`, `lintDebug`, and `assembleRelease`.
- [ ] Install the Debug APK with `adb install --user 0 -r`, then force-stop only `com.xingin.xhs`.
- [ ] Confirm the replacement hook installs and run available non-interactive runtime checks.
- [ ] Inspect tracked and ignored/untracked state, stage only scoped paths, and commit the coherent fix.
