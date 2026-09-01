# 汽水音乐应用开关 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在应用页面提供可验证、可恢复的汽水音乐大屏模式开关。

**Architecture:** `feature.applications` 负责页面和状态，`system.QishuiMusicClient` 负责后台特权 I/O，`contract.SettingsKeys` 保存共享键，Keva Dex 作为 asset 执行。

**Tech Stack:** Android Java, Gradle, JUnit。

## Global Constraints

- Root、APK 提取和 Keva 操作只能在用户明确切换后后台执行。
- 写入前保存原始记录，写入后回读验证，关闭时恢复。
- 所有用户文本进入三套 locale 资源。

### Task 1: Client contract and tests

**Files:**
- Modify: `app/src/main/java/io/github/pigerzhu/onelab/system/QishuiMusicClient.java`
- Create: `app/src/test/java/io/github/pigerzhu/onelab/system/QishuiMusicClientTest.java`

- [ ] Write failing tests for supported-record detection, enable JSON, and restore decision.
- [ ] Run `./gradlew testDebugUnitTest --tests '*QishuiMusicClientTest'` and confirm expected failures.
- [ ] Implement minimal pure helpers and guarded read/write flow.
- [ ] Re-run targeted tests and then the full unit suite.

### Task 2: Application page integration

**Files:**
- Modify: existing application feature screen/registry and `contract/SettingsKeys.java`
- Modify: `app/src/main/res/values/strings.xml`, `values-zh-rTW/strings.xml`, `values-en/strings.xml`

- [ ] Add the toggle card under the applications section.
- [ ] Dispatch client operations on a background executor and reflect confirmed state.
- [ ] Add localized title, summary, and failure text.
- [ ] Run unit tests and resource compilation.

### Task 3: Verification and commit

- [ ] Run `git diff --check`.
- [ ] Run `testDebugUnitTest`, `assembleDebug`, `lintDebug`, and `assembleRelease`.
- [ ] Review diff and commit only scoped production/docs changes.
