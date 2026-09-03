# Reusable Image Fullscreen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a reusable fullscreen image host and an XHS adapter that preserves XHS's native long-press menu and image paging.

**Architecture:** A package-neutral viewer owns an independent full-screen window, lifecycle, state restoration, and dismissal. Per-app adapters locate and temporarily host native image views in that window and delegate app-specific long-press behavior. OneLab selects adapters only when the master and app settings are enabled.

**Tech Stack:** Java, Android View hierarchy, Xposed hooks, Android unit tests, existing OneLab `SettingsStore` and UI patterns.

## Global Constraints

- Do not copy bitmap data or recreate app menus; the viewer adds only a valid-token full-screen window.
- Do not modify apps without a matching adapter.
- XHS must fail closed if native long-press delegation or layout restoration cannot be proven.
- Install and test only on Android user 0.

### Task 1: Define viewer and adapter contracts

**Files:**
- Create: `app/src/main/java/io/github/pigerzhu/onelab/hook/image/FullscreenImageViewer.java`
- Create: `app/src/main/java/io/github/pigerzhu/onelab/hook/image/ImageFullscreenAdapter.java`
- Test: `app/src/test/java/io/github/pigerzhu/onelab/hook/image/FullscreenImageViewerTest.java`

**Interfaces:**
- `ImageFullscreenAdapter.canAttach()` returns whether the current page is safe to take over.
- `ImageFullscreenAdapter.attach(FullscreenImageViewer.Host host)` returns a session or rejection.
- Session exposes `onLongPress()`, `onPageChanged(int)`, and idempotent `restore()`.
- Viewer exposes `open(session)`, `close()`, and `isOpen()`; repeated close is harmless.

- [ ] Write tests for open/close idempotence, rejected sessions, and restore-on-close.
- [ ] Implement only state transitions, valid-token window creation, and host callbacks; do not reference XHS.
- [ ] Run `:app:testDebugUnitTest --tests '*FullscreenImageViewerTest'`.
- [ ] Commit `feat: define reusable fullscreen image viewer contracts`.

### Task 2: Implement XHS adapter discovery and native delegation

**Files:**
- Create: `app/src/main/java/io/github/pigerzhu/onelab/hook/applications/XhsImageFullscreenAdapter.java`
- Create: `app/src/main/java/io/github/pigerzhu/onelab/hook/applications/XhsImageFullscreenHook.java`
- Test: `app/src/test/java/io/github/pigerzhu/onelab/hook/applications/XhsImageFullscreenAdapterTest.java`

**Interfaces:**
- Adapter targets `com.xingin.xhs` and the observed `NoteDetailActivity` structure.
- Discovery requires a measured `imageListView`/`mediaContainer` path and rejects missing or ambiguous nodes.
- Session saves parent, index, layout params, visibility, scroll and scale state before reparenting.
- Long press is forwarded to the original XHS image view; no replacement menu is allowed.

- [ ] Add tests for unique discovery, missing-node rejection, and idempotent restoration using fake View trees.
- [ ] Hook XHS page lifecycle and image click only after the image view is measured.
- [ ] Reparent the original image view into the viewer window host and restore it on close, page destruction, invalid token, or failure.
- [ ] Run adapter unit tests and inspect Xposed logs for attach/reject reasons.
- [ ] Commit `feat: add xhs native image fullscreen adapter`.

### Task 3: Wire settings and runtime gating

**Files:**
- Modify: `app/src/main/java/io/github/pigerzhu/onelab/hook/Entry.java`
- Modify: `app/src/main/java/io/github/pigerzhu/onelab/contract/SettingsKeys.java`
- Modify: `app/src/main/java/io/github/pigerzhu/onelab/feature/applications/SplitImageFullscreenScreen.java`
- Test: existing settings and policy tests

**Interfaces:**
- Adapter is installed only when `onelab_split_image_fullscreen=1` and the XHS child setting is `1`.
- Existing application-reader intersection remains the source of eligible app rows.

- [ ] Add the XHS child setting and row using existing silent-save switch behavior.
- [ ] Gate Hook installation and viewer opening on both settings.
- [ ] Run full unit tests, `lintDebug`, and `assembleDebug`.
- [ ] Commit `feat: gate xhs image fullscreen from onelab settings`.

### Task 4: Device validation and release decision

**Files:**
- Modify: `docs/XHS_FOLD_RESEARCH.md`
- Modify: `docs/COOLAPK_FOLD_RESEARCH.md` only if shared behavior changes

- [ ] Build with the documented JDK/SDK configuration and install to user 0.
- [ ] Verify XHS single-image and multi-image notes, native long press, left/right paging, rapid close, back navigation, and detail recreation.
- [ ] Reject the adapter if native long press or restoration fails; do not ship a substitute menu.
- [ ] Record measured bounds and failure logs in the XHS research document.
- [ ] Commit only after all six acceptance conditions in the design spec pass.
