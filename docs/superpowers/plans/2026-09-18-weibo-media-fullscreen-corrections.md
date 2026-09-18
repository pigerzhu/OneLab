# Weibo Media Fullscreen Corrections Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Weibo video previews fullscreen and make image previews follow the device orientation from their first frame.

**Architecture:** Extend the existing cached `SamsungSplitRuleCatalog.RuleSet` with a separate set of activities whose portrait requests should be neutralized. `SamsungSplitRulesHook` continues to own all behavior in `system_server`: the existing split-rule launch hook resets image orientation before resume, and a low-frequency `ActivityRecord.setRequestedOrientation(int)` hook rejects later portrait requests for those image activities only.

**Tech Stack:** Java 17, Android framework/Xposed hooks, JUnit 4, Gradle Android plugin.

## Global Constraints

- Do not add `com.sina.weibo` to the LSPosed application scope.
- Do not scan Views, modify window bounds, or read Settings in orientation or rendering hot paths.
- Both `onelab_split_image_fullscreen` and `onelab_weibo_image_fullscreen` must be enabled.
- Video keeps its own portrait and sensor-landscape orientation requests.
- Failure must preserve Samsung and Weibo behavior.

---

### Task 1: Model Weibo Video And Image Orientation Policy

**Files:**
- Modify: `app/src/main/java/io/github/pigerzhu/onelab/hook/samsung/SamsungSplitRuleCatalog.java`
- Modify: `app/src/test/java/io/github/pigerzhu/onelab/hook/samsung/SamsungSplitRuleCatalogTest.java`

**Interfaces:**
- Consumes: existing `RuleSet.enabled`, `fullscreenActivities`, and the Weibo master/sub-switch relationship.
- Produces: `RuleSet.followDeviceOrientationActivities` and `RuleSet.shouldIgnorePortraitRequest(String activityName, int requestedOrientation)`.

- [ ] **Step 1: Write the failing catalog tests**

Add literal assertions that the Weibo fullscreen set contains
`com.sina.weibo.story.multiv2.core.MediaCoreV2Activity`, while the direction-following set contains the three image viewer activities and excludes the video Activity. Add truth-table assertions that portrait (`1`) is rejected only for a listed image Activity when `rules.enabled` is true; unspecified (`-1`), sensor-landscape (`6`), video, and disabled rules are allowed.

- [ ] **Step 2: Run the catalog test and verify RED**

```powershell
$env:JAVA_HOME='D:\rednote\tools\jdk-21.0.11+10'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat testDebugUnitTest --tests 'io.github.pigerzhu.onelab.hook.samsung.SamsungSplitRuleCatalogTest'
```

Expected: compilation or assertion failure because the orientation policy and video fullscreen entry do not exist.

- [ ] **Step 3: Implement the minimal catalog policy**

Extend `RuleSet` constructors with an immutable `followDeviceOrientationActivities` set, defaulting to `Collections.emptySet()` for existing applications. Implement:

```java
boolean shouldIgnorePortraitRequest(String activityName, int requestedOrientation) {
    return enabled.get()
            && requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            && followDeviceOrientationActivities.contains(activityName);
}
```

Keep Android constants out of the pure catalog by defining package-private numeric constants in a focused policy if importing `ActivityInfo` prevents the local unit test from running. Add `MediaCoreV2Activity` only to `fullscreenActivities`; add the three image Activity names to `followDeviceOrientationActivities`.

- [ ] **Step 4: Run the catalog test and verify GREEN**

Run the Step 2 command. Expected: PASS.

### Task 2: Apply Orientation Policy Before First Frame And At Runtime

**Files:**
- Modify: `app/src/main/java/io/github/pigerzhu/onelab/hook/samsung/SamsungSplitRulesHook.java`
- Test: `app/src/test/java/io/github/pigerzhu/onelab/hook/samsung/SamsungSplitRuleCatalogTest.java`

**Interfaces:**
- Consumes: `RuleSet.shouldIgnorePortraitRequest(String, int)` and `followDeviceOrientationActivities` from Task 1.
- Produces: system-server behavior at `ActivityStarter.reparentActivitiesToActivityGroupIfNeeded(...)` and `ActivityRecord.setRequestedOrientation(int)`.

- [ ] **Step 1: Add a failing decision test for launch-time neutralization**

Add a pure catalog method and assertions:

```java
assertTrue(rules.shouldFollowDeviceOrientation(
        "com.sina.weibo.preview.MediaPreviewActivity"));
assertFalse(rules.shouldFollowDeviceOrientation(
        "com.sina.weibo.story.multiv2.core.MediaCoreV2Activity"));
```

The method must include `enabled.get()` so disabling either switch prevents launch-time mutation.

- [ ] **Step 2: Run the catalog test and verify RED**

Run the Task 1 Step 2 command. Expected: compilation or assertion failure because `shouldFollowDeviceOrientation` does not exist.

- [ ] **Step 3: Implement launch-time orientation neutralization**

Install the orientation hook independently and cache whether its exact method was found. In the
existing `reparentActivitiesToActivityGroupIfNeeded` before-hook, after resolving the target
package/activity and before returning for a forced-fullscreen target, call the following only when
that cached installation state is true:

```java
if (shouldFollowDeviceOrientation(packageName, activityName)) {
    XposedHelpers.callMethod(
            targetRecord,
            "setRequestedOrientation",
            SCREEN_ORIENTATION_UNSPECIFIED);
}
```

Use the same verified ActivityRecord method that the orientation sub-hook resolved. If the optional
sub-hook cannot be installed, log that installation failure once, skip both direction mutations, and
continue installing and applying the fullscreen exceptions.

- [ ] **Step 4: Implement runtime portrait filtering**

Resolve `com.android.server.wm.ActivityRecord` and hook its exact one-argument
`setRequestedOrientation(int)` method in an independently guarded installer. Cache the resolved
`Method` for the launch-time call. In `beforeHookedMethod`, read package/class through the existing
stable helpers and call `param.setResult(null)` only when the catalog policy rejects the request.
Allow `-1`, `6`, all video requests, all unrelated packages, and disabled rules.

- [ ] **Step 5: Run focused tests and verify GREEN**

Run the Task 1 Step 2 command. Expected: PASS.

### Task 3: Full Verification, Installation, And Runtime Evidence

**Files:**
- Update local ignored research: `docs/WEIBO_FOLD_RESEARCH.md`

**Interfaces:**
- Consumes: completed production behavior from Tasks 1-2.
- Produces: built APK, installed user-0 module, persistent LSPosed evidence, and documented manual acceptance status.

- [ ] **Step 1: Run repository verification**

```powershell
git diff --check
$env:JAVA_HOME='D:\rednote\tools\jdk-21.0.11+10'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug assembleRelease
```

Expected: all tasks pass with no new lint errors.

- [ ] **Step 2: Install to Android user 0 and preserve settings**

Read both feature settings before installation, install with:

```powershell
D:\platform-tools\adb.exe install --user 0 -r D:\OneLab\app\build\outputs\apk\debug\app-debug.apk
```

Read the settings again. Expected: both remain `1`; never clear application data.

- [ ] **Step 3: Reload only `system_server` and verify hook installation**

Use the documented local LSPosed/system-server reload method from `docs/TROUBLESHOOTING.md`, then inspect the persistent module log. Expected: `SamsungSplitRules` installs with no exception from the ActivityRecord orientation hook.

- [ ] **Step 4: Collect manual acceptance evidence**

Verify on the unfolded inner display: horizontal and vertical image entry has the correct first-frame orientation; rotating while open follows the device; `MediaCoreV2Activity` occupies the full Task; its player rotation controls still work; returning restores the split layout. Treat installation logs alone as insufficient.

- [ ] **Step 5: Document and commit**

Update `docs/WEIBO_FOLD_RESEARCH.md` with runtime evidence but keep the ignored research file out of Git. Stage only the scoped production and test files, then commit with a behavior-focused message.
