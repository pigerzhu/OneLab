# 可信应用功能开关实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 OneLab 应用程序页面的开关只在当前目标应用版本的 Hook 或系统分屏规则确认生效后显示打开，并对未安装应用实时禁用。

**Architecture:** 在 `contract` 中定义稳定功能 ID 和状态值；新增导出的状态 Provider 作为 Hook 进程、`system_server` 与 OneLab UI 之间的最小回传边界；应用页面通过一个通用控制器读取安装状态、版本绑定状态和设置值。应用 Hook 与系统 Hook 分别在安装、启用确认和失败路径上报状态。所有状态都绑定目标包版本与 OneLab 版本。

**Tech Stack:** Java 17 Android SDK 36, Android `ContentProvider`, `ContentResolver.call()`, JUnit 4, Material `MaterialSwitch`,现有 `SettingsStore` 和 Xposed Hook API。

## Global Constraints

- 只修改应用程序页面，不修改性能、温控、USB PD 旁路充电或应用分栏比例页面。
- 目标应用未安装时开关必须禁用。
- 设置写入成功不等于功能成功；没有当前版本 `ACTIVE` 回传时不得显示打开。
- 不自动启动、强停目标应用，不请求 root，不清除应用数据。
- 不修改现有应用 Hook 的主进程范围问题。
- 失败开放；状态 Provider、Hook 回传和 UI 查询失败时保持关闭或原有系统行为。
- 所有状态回传必须限制功能 ID、调用 UID、目标包名和版本信息。
- 每个行为提交都运行 `git diff --check` 和对应单元测试；最终运行完整测试、Debug 构建和 Release 构建。

---

### Task 1: 定义状态协议和功能目录

**Files:**
- Create: `app/src/main/java/io/github/pigerzhu/onelab/contract/HookFeatureState.java`
- Create: `app/src/main/java/io/github/pigerzhu/onelab/contract/HookFeatureCatalog.java`
- Modify: `app/src/main/java/io/github/pigerzhu/onelab/contract/SettingsKeys.java`
- Test: `app/src/test/java/io/github/pigerzhu/onelab/contract/HookFeatureStateTest.java`

**Interfaces:**
- `HookFeatureState`: `UNAVAILABLE`, `AVAILABLE_OFF`, `ACTIVE` UI 状态和 `LOADED`, `ACTIVE`, `FAILED` 回传状态；提供 `isActive(...)` 的纯函数判定。
- `HookFeatureCatalog.Entry`: `featureId`, `targetPackage`, `settingKey`, `dependencyFeatureId`, `sourceType`。
- `HookFeatureCatalog.find(String featureId)` 返回已登记条目，未知 ID 返回空结果。
- `HookFeatureCatalog.applicationFeatureIds()` 返回应用程序页面使用的稳定功能 ID 集合。

- [ ] **Step 1: Write the failing test**

```java
@Test
public void activeRequiresMatchingVersionsAndActiveReport() {
    assertTrue(HookFeatureState.isActive(
            HookFeatureState.Report.ACTIVE,
            "com.example.app", "12", "7",
            "com.example.app", "12", "7"));
    assertFalse(HookFeatureState.isActive(
            HookFeatureState.Report.LOADED,
            "com.example.app", "12", "7",
            "com.example.app", "12", "7"));
    assertFalse(HookFeatureState.isActive(
            HookFeatureState.Report.ACTIVE,
            "com.example.app", "11", "7",
            "com.example.app", "12", "7"));
}
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests io.github.pigerzhu.onelab.contract.HookFeatureStateTest`

Expected: FAIL because `HookFeatureState` and its matching function do not exist.

- [ ] **Step 3: Write the minimal protocol and catalog**

Define only the enums, immutable report fields, version matching function, provider authority, and the eleven feature entries listed in the design document. Define B站 tablet as dependent on B站 gate. Define the two XHS features as independent entries. Do not add UI or Android I/O to these classes.

- [ ] **Step 4: Run the focused test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests io.github.pigerzhu.onelab.contract.HookFeatureStateTest`

Expected: PASS.

- [ ] **Step 5: Commit**

```text
git add app/src/main/java/io/github/pigerzhu/onelab/contract app/src/test/java/io/github/pigerzhu/onelab/contract/HookFeatureStateTest.java
git commit -m "feat: define trusted hook feature state contract"
```

### Task 2: Add the secured status Provider and client

**Files:**
- Create: `app/src/main/java/io/github/pigerzhu/onelab/system/HookStatusProvider.java`
- Create: `app/src/main/java/io/github/pigerzhu/onelab/system/HookStatusClient.java`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/io/github/pigerzhu/onelab/system/HookStatusRecordTest.java`

**Interfaces:**
- `HookStatusClient.report(Context, Report)` sends a validated report through `ContentResolver.call()`.
- `HookStatusClient.query(Context, featureId)` returns the latest record or an empty result.
- `HookStatusProvider` accepts only `report` and `query` operations; it stores compact records in private app storage and calls `notifyChange()` after writes.

- [ ] **Step 1: Write the failing test**

```java
@Test
public void staleVersionReportIsNotActive() {
    HookFeatureState.Report report = HookFeatureState.Report.active(
            "apps.baidu_large", "com.baidu.searchbox", "42", "7");
    assertFalse(HookStatusRecord.isCurrent(
            report, "com.baidu.searchbox", "43", "7"));
}

@Test
public void unknownFeatureCannotBeAccepted() {
    assertFalse(HookStatusRecord.isKnownFeature("apps.unknown"));
}
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests io.github.pigerzhu.onelab.system.HookStatusRecordTest`

Expected: FAIL because the record validator does not exist.

- [ ] **Step 3: Implement the Provider and client**

Declare the Provider as exported without a broad read/write permission, because the Provider itself performs UID validation. For app callers, compare the calling UID packages with `targetPackage`; for system UID callers, require the target package to be in `HookFeatureCatalog` and the feature source to be system-side. Reject unknown IDs, mismatched app package, invalid state, missing versions, and malformed timestamps. Use `SharedPreferences` only as Provider storage; never expose the preference file to Hook processes.

- [ ] **Step 4: Run focused and existing system tests**

Run: `./gradlew testDebugUnitTest --tests io.github.pigerzhu.onelab.system.HookStatusRecordTest --tests io.github.pigerzhu.onelab.system.SettingsStoreTest`

Expected: PASS.

- [ ] **Step 5: Commit**

```text
git add app/src/main/AndroidManifest.xml app/src/main/java/io/github/pigerzhu/onelab/system app/src/test/java/io/github/pigerzhu/onelab/system/HookStatusRecordTest.java
git commit -m "feat: add authenticated hook status provider"
```

### Task 3: Add package/version state resolution and the reusable switch controller

**Files:**
- Create: `app/src/main/java/io/github/pigerzhu/onelab/feature/applications/HookFeatureSwitchController.java`
- Create: `app/src/main/java/io/github/pigerzhu/onelab/feature/applications/InstalledApplicationState.java`
- Test: `app/src/test/java/io/github/pigerzhu/onelab/feature/applications/HookFeatureSwitchStateTest.java`

**Interfaces:**
- `InstalledApplicationState.resolve(PackageSnapshot, HookFeatureCatalog.Entry, HookStatusRecord)` returns one of the three UI states.
- `HookFeatureSwitchController.bind(MaterialSwitch, Entry, SettingsStore)` binds refresh, click, pending confirmation, timeout rollback, and user-facing Toast behavior.
- `HookFeatureSwitchController.refresh()` rechecks package installation and current versions.

- [ ] **Step 1: Write failing state tests**

```java
@Test
public void missingPackageIsUnavailable() {
    assertEquals(UNAVAILABLE, resolve(null, null));
}

@Test
public void installedWithoutReportIsAvailableOff() {
    assertEquals(AVAILABLE_OFF, resolve(packageVersion("12"), null));
}

@Test
public void matchingActiveReportIsActive() {
    assertEquals(ACTIVE, resolve(packageVersion("12"), activeReport("12")));
}
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests io.github.pigerzhu.onelab.feature.applications.HookFeatureSwitchStateTest`

Expected: FAIL because the resolver and controller do not exist.

- [ ] **Step 3: Implement the resolver and controller**

Use `PackageManager.getApplicationInfo()` and `getPackageInfo()` for the current Android user. `AVAILABLE_OFF` must be returned for installed packages with no matching `ACTIVE` record. When the user enables a switch, write the setting quietly, poll the Provider for a bounded short period, and set `checked=true` only after a matching `ACTIVE` report. On failure write `0`, set `checked=false`, and show a short Chinese Toast. Do not call root or start/stop the target app.

- [ ] **Step 4: Run focused tests**

Run: `./gradlew testDebugUnitTest --tests io.github.pigerzhu.onelab.feature.applications.HookFeatureSwitchStateTest`

Expected: PASS for missing package, installed-off, active, stale version, dependency, and rollback cases.

- [ ] **Step 5: Commit**

```text
git add app/src/main/java/io/github/pigerzhu/onelab/feature/applications app/src/test/java/io/github/pigerzhu/onelab/feature/applications/HookFeatureSwitchStateTest.java
git commit -m "feat: add three-state application feature switches"
```

### Task 4: Add application Hook reports

**Files:**
- Modify: `app/src/main/java/io/github/pigerzhu/onelab/hook/applications/BiliFoldGateHook.java`
- Modify: `app/src/main/java/io/github/pigerzhu/onelab/hook/applications/BaiduLargeScreenHook.java`
- Modify: `app/src/main/java/io/github/pigerzhu/onelab/hook/applications/XhsFoldVideoHook.java`
- Modify: `app/src/main/java/io/github/pigerzhu/onelab/hook/applications/XiaomiShopFoldHook.java`
- Modify: `app/src/main/java/io/github/pigerzhu/onelab/hook/applications/TongchengSplitRulesHook.java`
- Modify: `app/src/main/java/io/github/pigerzhu/onelab/hook/applications/GalleryLabsHook.java`
- Modify: `app/src/main/java/io/github/pigerzhu/onelab/hook/core/HookUtils.java`
- Test: `app/src/test/java/io/github/pigerzhu/onelab/hook/applications/HookFeatureReportMappingTest.java`

**Interfaces:**
- `HookUtils.reportFeature(Context, featureId, state)` reports using the target process package and target version.
- Each Hook reports `LOADED` only after its stable hook installation succeeds, `ACTIVE` after its setting observer applies the enabled state, and `FAILED` once on installation failure.

- [ ] **Step 1: Write failing mapping tests**

```java
@Test
public void xhsFeaturesRemainIndependent() {
    assertNotEquals("apps.xhs.home", "apps.xhs.video");
}

@Test
public void biliTabletReportUsesGateDependency() {
    assertEquals("apps.bili.fold", HookFeatureCatalog.entry("apps.bili.tablet").dependencyFeatureId());
}
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests io.github.pigerzhu.onelab.hook.applications.HookFeatureReportMappingTest`

Expected: FAIL until all report IDs and mapping helpers exist.

- [ ] **Step 3: Add reports at existing installation and observer boundaries**

Do not add per-frame or per-layout reporting. Do not widen any Hook scope. B站 tablet can report `ACTIVE` only when its own observer confirms the dependency is active. XHS home and video use separate feature IDs. Preserve fail-open behavior and existing one-time logs.

- [ ] **Step 4: Run all Hook mapping and existing Hook tests**

Run: `./gradlew testDebugUnitTest --tests io.github.pigerzhu.onelab.hook.applications.HookFeatureReportMappingTest --tests io.github.pigerzhu.onelab.hook.applications.BaiduWindowPolicyTest --tests io.github.pigerzhu.onelab.hook.applications.BiliWindowPolicyTest`

Expected: PASS.

- [ ] **Step 5: Commit**

```text
git add app/src/main/java/io/github/pigerzhu/onelab/hook/applications app/src/main/java/io/github/pigerzhu/onelab/hook/core/HookUtils.java app/src/test/java/io/github/pigerzhu/onelab/hook/applications/HookFeatureReportMappingTest.java
git commit -m "feat: report application hook activation state"
```

### Task 5: Add system split-rule reports

**Files:**
- Modify: `app/src/main/java/io/github/pigerzhu/onelab/hook/samsung/SamsungSplitRulesHook.java`
- Modify: `app/src/main/java/io/github/pigerzhu/onelab/hook/samsung/SamsungSplitRuleCatalog.java`
- Modify: `app/src/main/java/io/github/pigerzhu/onelab/hook/core/HookUtils.java`
- Test: `app/src/test/java/io/github/pigerzhu/onelab/hook/samsung/SamsungSplitRuleStateTest.java`

**Interfaces:**
- `SamsungSplitRulesHook` reports one catalog feature only after the repository, resolver, target package, and all required rules are applied.
- `SamsungSplitRuleCatalog.RuleSet` exposes its feature ID and target package for validation; existing setting keys and rule pairs remain unchanged.

- [ ] **Step 1: Write failing rule-state tests**

```java
@Test
public void repositoryWriteAloneIsNotActive() {
    assertFalse(SamsungSplitRuleState.isActive(false, true, true));
}

@Test
public void activeRequiresRepositoryResolverAndRules() {
    assertTrue(SamsungSplitRuleState.isActive(true, true, true));
}
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests io.github.pigerzhu.onelab.hook.samsung.SamsungSplitRuleStateTest`

Expected: FAIL because the rule-state predicate does not exist.

- [ ] **Step 3: Report actual system-side application**

Keep the existing One UI 8/8.5 controller selection and lazy initialization. Set `ACTIVE` only after the rule application path confirms the target repository and rule set. On disabled state, report the feature as inactive. Do not claim success from a setting write, constructor hook, or a package appearing in a candidate list alone.

- [ ] **Step 4: Run focused and Samsung tests**

Run: `./gradlew testDebugUnitTest --tests io.github.pigerzhu.onelab.hook.samsung.SamsungSplitRuleStateTest`

Expected: PASS.

- [ ] **Step 5: Commit**

```text
git add app/src/main/java/io/github/pigerzhu/onelab/hook/samsung app/src/main/java/io/github/pigerzhu/onelab/hook/core/HookUtils.java app/src/test/java/io/github/pigerzhu/onelab/hook/samsung/SamsungSplitRuleStateTest.java
git commit -m "feat: report applied Samsung split rules"
```

### Task 6: Connect all application cards and dependencies

**Files:**
- Modify: `app/src/main/java/io/github/pigerzhu/onelab/feature/applications/BiliFoldGateScreen.java`
- Modify: `app/src/main/java/io/github/pigerzhu/onelab/feature/applications/BaiduLargeScreenScreen.java`
- Modify: `app/src/main/java/io/github/pigerzhu/onelab/feature/applications/XhsFoldVideoScreen.java`
- Modify: `app/src/main/java/io/github/pigerzhu/onelab/feature/applications/XiaomiShopFoldScreen.java`
- Modify: `app/src/main/java/io/github/pigerzhu/onelab/feature/applications/TongchengSplitRulesScreen.java`
- Modify: `app/src/main/java/io/github/pigerzhu/onelab/feature/applications/CtripSplitRulesScreen.java`
- Modify: `app/src/main/java/io/github/pigerzhu/onelab/feature/applications/UmetripSplitRulesScreen.java`
- Modify: `app/src/main/java/io/github/pigerzhu/onelab/feature/applications/MeituanSplitRulesScreen.java`
- Modify: `app/src/main/java/io/github/pigerzhu/onelab/feature/applications/ZhuanzhuanSplitRulesScreen.java`
- Modify: `app/src/main/java/io/github/pigerzhu/onelab/feature/experiment/GalleryLabsScreen.java`
- Test: `app/src/test/java/io/github/pigerzhu/onelab/feature/applications/ApplicationFeatureCatalogTest.java`

**Interfaces:**
- Every application card constructs `HookFeatureSwitchController` with its catalog entry.
- B站 tablet switch is disabled unless the B站 main feature is `ACTIVE`.
- Card subtitles no longer expose Hook implementation terms.

- [ ] **Step 1: Write failing integration mapping tests**

```java
@Test
public void everyApplicationCardUsesAnInstalledPackageEntry() {
    for (String featureId : HookFeatureCatalog.applicationFeatureIds()) {
        assertNotNull(HookFeatureCatalog.find(featureId));
        assertFalse(HookFeatureCatalog.find(featureId).targetPackage().isEmpty());
    }
}
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests io.github.pigerzhu.onelab.feature.applications.ApplicationFeatureCatalogTest`

Expected: FAIL until all cards are connected to the catalog.

- [ ] **Step 3: Replace direct setting toggles**

Remove each card's direct `setChecked` and `setOnCheckedChangeListener` implementation. Preserve card layout and titles. Remove implementation-specific subtitles from this page. Use normal user-facing messages for pending, success, and rollback. Keep existing B站 and XHS dependency behavior through the controller rather than duplicated listeners.

- [ ] **Step 4: Run focused UI mapping tests**

Run: `./gradlew testDebugUnitTest --tests io.github.pigerzhu.onelab.feature.applications.ApplicationFeatureCatalogTest`

Expected: PASS.

- [ ] **Step 5: Commit**

```text
git add app/src/main/java/io/github/pigerzhu/onelab/feature/applications app/src/main/java/io/github/pigerzhu/onelab/feature/experiment/GalleryLabsScreen.java app/src/test/java/io/github/pigerzhu/onelab/feature/applications/ApplicationFeatureCatalogTest.java
git commit -m "feat: connect application cards to trusted states"
```

### Task 7: Full verification and local test build

**Files:**
- Test or modify only if a failing regression is found: existing affected test files.
- Documentation update only if runtime behavior exposes a reusable failure mode: `docs/TROUBLESHOOTING.md`.

- [ ] **Step 1: Run the complete unit-test suite**

Run: `./gradlew testDebugUnitTest`

Expected: exit code 0 with all tests passing.

- [ ] **Step 2: Check formatting and tracked scope**

Run: `git diff --check; git status --short; git diff --stat HEAD~6..HEAD`

Expected: no whitespace errors and only the planned production, test, manifest, and contract files are changed.

- [ ] **Step 3: Build both artifacts**

Run: `./gradlew assembleDebug assembleRelease`

Expected: both tasks exit 0; no unsigned release APK is treated as publishable.

- [ ] **Step 4: Verify no automatic activation**

Install only with `adb install --user 0 -r app/build/outputs/apk/debug/app-debug.apk`, reopen the application page, and confirm every feature without a current `ACTIVE` report is closed. Do not clear data, reboot, or change LSPosed scope during this check.

- [ ] **Step 5: If verification exposes a regression, stop and create one focused local fix commit containing only the failing test and its owning production file; otherwise leave the tree unchanged. No GitHub push or release is part of this plan.**
