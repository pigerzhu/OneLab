# GPU 频率范围实验实施计划

> **供自动化开发代理使用：** 必须使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans`，逐任务执行本计划。所有步骤使用复选框跟踪。

**目标：** 在 SDHMS 进程中验证三星 GPU 最低/最高 DVFS 投票能否持续限定 Fold6 的 GPU 运行范围，并仅在验证通过后接入 OneLab 实验室页面。

**架构：** 普通应用通过 `Settings.Global` 保存实验开关和频率范围；SDHMS 进程中的独立控制器通过反射持有两个 `SemDvfsManager` 请求，候选最低类型为 `16`，已确认最高类型为 `17`。控制器以设置观察器驱动状态变化，不轮询 sysfs；纯 Java 范围模型和状态机与 Android 反射边界分离，便于单元测试和失败撤回。

**技术栈：** Java、Android Settings、LSPosed/Xposed、三星 `SemDvfsManager` 反射接口、Material `RangeSlider`、JUnit 4、ADB/KGSL 只读验证。

## 全局约束

- 从 `dev` 建立独立 worktree 和实验分支，禁止直接在 `dev` 实现生产代码。
- 文档、状态文案和提交说明使用中文；代码标识符和三星接口名保留英文。
- 不修改现有 SDHMS `GPUFreqMax` 上限滑块及其设置键。
- 不写 KGSL sysfs，不运行周期性覆盖循环，不修改 Scene 配置。
- OneLab 应用进程不得依赖后台常驻服务。
- 最低频率不得高于最高频率；两值相等表示锁频请求。
- 设置仅在滑动结束后提交，不在拖动过程中连续写入。
- 异常时开放失败并释放 OneLab 自己持有的请求。
- 类型 `16` 真机验证失败时立即停止，不实施 UI，不增加 sysfs 兜底。
- 安装仅面向 Android 主用户 `0`；不自动重启、不清数据、不修改 LSPosed 作用域。

---

### 任务 1：频率范围契约与纯 Java 校验

**文件：**
- 新建：`app/src/main/java/io/github/pigerzhu/onelab/contract/GpuFrequencyRange.java`
- 修改：`app/src/main/java/io/github/pigerzhu/onelab/contract/SettingsKeys.java`
- 新建测试：`app/src/test/java/io/github/pigerzhu/onelab/contract/GpuFrequencyRangeTest.java`

**接口：**
- 产出：`GpuFrequencyRange.normalize(int requestedMinMhz, int requestedMaxMhz)`
- 产出：`int minMhz()`、`int maxMhz()`、`boolean isLocked()`
- 产出设置键：`KEY_ENABLE_GPU_RANGE_EXPERIMENT`、`KEY_GPU_RANGE_MIN_MHZ`、`KEY_GPU_RANGE_MAX_MHZ`、`KEY_GPU_RANGE_RUNTIME_STATUS`
- 默认范围：最低 `80 MHz`，最高 `1000 MHz`

- [ ] **步骤 1：先写范围校验失败测试**

```java
@Test
public void normalizeSnapsToSupportedFrequencies() {
    GpuFrequencyRange range = GpuFrequencyRange.normalize(400, 910);
    assertEquals(422, range.minMhz());
    assertEquals(903, range.maxMhz());
}

@Test
public void normalizeKeepsMinimumAtOrBelowMaximum() {
    GpuFrequencyRange range = GpuFrequencyRange.normalize(950, 500);
    assertEquals(950, range.minMhz());
    assertEquals(950, range.maxMhz());
    assertTrue(range.isLocked());
}

@Test
public void equalEndpointsRepresentLock() {
    assertTrue(GpuFrequencyRange.normalize(422, 422).isLocked());
}
```

- [ ] **步骤 2：运行测试并确认失败**

运行：

```powershell
.\gradlew.bat testDebugUnitTest --tests "io.github.pigerzhu.onelab.contract.GpuFrequencyRangeTest"
```

预期：因 `GpuFrequencyRange` 尚不存在而编译失败。

- [ ] **步骤 3：实现最小范围模型与设置键**

`GpuFrequencyRange` 必须从 `SettingsKeys.SDHMS_GPU_FREQS_MHZ` 选择最近频点；距离相同时选择较高频点。若归一化后的最低值大于最高值，将最高值提升到最低值，不交换用户正在调整的下限语义。

```java
public static GpuFrequencyRange normalize(int requestedMinMhz, int requestedMaxMhz) {
    int min = nearestSupported(requestedMinMhz);
    int max = nearestSupported(requestedMaxMhz);
    if (min > max) {
        max = min;
    }
    return new GpuFrequencyRange(min, max);
}
```

- [ ] **步骤 4：运行单元测试并确认通过**

运行：同步骤 2。

预期：3 项测试全部通过。

- [ ] **步骤 5：提交任务 1**

```powershell
git add -- app/src/main/java/io/github/pigerzhu/onelab/contract/GpuFrequencyRange.java app/src/main/java/io/github/pigerzhu/onelab/contract/SettingsKeys.java app/src/test/java/io/github/pigerzhu/onelab/contract/GpuFrequencyRangeTest.java
git commit -m "experiment: define GPU frequency range contract"
```

---

### 任务 2：可测试的 DVFS 投票状态机与三星反射边界

**文件：**
- 新建：`app/src/main/java/io/github/pigerzhu/onelab/hook/system/GpuDvfsVoteBackend.java`
- 新建：`app/src/main/java/io/github/pigerzhu/onelab/hook/system/SamsungGpuDvfsVoteBackend.java`
- 新建：`app/src/main/java/io/github/pigerzhu/onelab/hook/system/GpuFrequencyRangeController.java`
- 新建测试：`app/src/test/java/io/github/pigerzhu/onelab/hook/system/GpuFrequencyRangeControllerTest.java`

**接口：**
- `GpuDvfsVoteBackend.acquireMinimum(int mhz)`：创建/更新类型 `16` 请求并返回是否成功。
- `GpuDvfsVoteBackend.acquireMaximum(int mhz)`：创建/更新类型 `17` 请求并返回是否成功。
- `GpuDvfsVoteBackend.releaseAll()`：只释放当前后端创建的请求。
- `GpuFrequencyRangeController.apply(boolean enabled, GpuFrequencyRange range)`：返回 `DISABLED`、`ACTIVE`、`MIN_UNAVAILABLE`、`MAX_UNAVAILABLE` 或 `FAILED`。

- [ ] **步骤 1：先写状态机失败测试**

```java
@Test
public void activeRequiresBothVotes() {
    FakeBackend backend = new FakeBackend(true, true);
    GpuFrequencyRangeController controller = new GpuFrequencyRangeController(backend);
    assertEquals(Status.ACTIVE,
            controller.apply(true, GpuFrequencyRange.normalize(231, 770)));
    assertEquals(231, backend.minimum);
    assertEquals(770, backend.maximum);
}

@Test
public void minimumFailureReleasesEveryOwnedVote() {
    FakeBackend backend = new FakeBackend(false, true);
    GpuFrequencyRangeController controller = new GpuFrequencyRangeController(backend);
    assertEquals(Status.MIN_UNAVAILABLE,
            controller.apply(true, GpuFrequencyRange.normalize(231, 770)));
    assertTrue(backend.released);
}

@Test
public void disablingReleasesVotes() {
    FakeBackend backend = new FakeBackend(true, true);
    GpuFrequencyRangeController controller = new GpuFrequencyRangeController(backend);
    assertEquals(Status.DISABLED,
            controller.apply(false, GpuFrequencyRange.normalize(80, 1000)));
    assertTrue(backend.released);
}
```

- [ ] **步骤 2：运行测试并确认失败**

运行：

```powershell
.\gradlew.bat testDebugUnitTest --tests "io.github.pigerzhu.onelab.hook.system.GpuFrequencyRangeControllerTest"
```

预期：因状态机和后端接口尚不存在而编译失败。

- [ ] **步骤 3：实现最小状态机**

状态机先申请最低频率，再申请最高频率；任一步失败都调用 `releaseAll()`。重复应用相同范围时不得重复释放和申请。

- [ ] **步骤 4：实现三星反射后端**

后端仅通过明确签名调用：

```java
Class<?> type = XposedHelpers.findClass(
        "com.samsung.android.os.SemDvfsManager", classLoader);
Object vote = XposedHelpers.callStaticMethod(
        type, "createInstance", context, tag, dvfsType);
int[] supported = (int[]) XposedHelpers.callMethod(
        vote, "getSupportedFrequencyForSsrm");
XposedHelpers.callMethod(vote, "setDvfsValue", mhz);
XposedHelpers.callMethod(vote, "acquire");
```

最低类型固定为 `16`，标签为 `OneLab_GPU_FREQ_MIN`；最高类型固定为 `17`，标签为 `OneLab_GPU_FREQ_MAX`。申请前必须确认目标频率存在于对应 `supported` 数组。释放使用明确的 `release()`，异常返回失败，不枚举任意方法。

- [ ] **步骤 5：运行状态机测试并确认通过**

运行：同步骤 2。

预期：全部通过；测试不加载 Android 或 Xposed 运行时类。

- [ ] **步骤 6：提交任务 2**

```powershell
git add -- app/src/main/java/io/github/pigerzhu/onelab/hook/system/GpuDvfsVoteBackend.java app/src/main/java/io/github/pigerzhu/onelab/hook/system/SamsungGpuDvfsVoteBackend.java app/src/main/java/io/github/pigerzhu/onelab/hook/system/GpuFrequencyRangeController.java app/src/test/java/io/github/pigerzhu/onelab/hook/system/GpuFrequencyRangeControllerTest.java
git commit -m "experiment: add Samsung GPU DVFS vote controller"
```

---

### 任务 3：接入 SDHMS 设置观察器并建立真机硬门

**文件：**
- 修改：`app/src/main/java/io/github/pigerzhu/onelab/hook/system/SdhmsHookConfig.java`
- 修改：`app/src/main/java/io/github/pigerzhu/onelab/hook/system/SdhmsThermalHook.java`
- 修改：`app/src/main/java/io/github/pigerzhu/onelab/diagnostics/RuntimeCompatibilityReport.java`
- 修改测试：`app/src/test/java/io/github/pigerzhu/onelab/diagnostics/RuntimeCompatibilityReportTest.java`

**接口：**
- `SdhmsHookConfig.Snapshot` 增加 `gpuRangeExperimentEnabled` 和 `GpuFrequencyRange gpuRange`。
- `SdhmsThermalHook` 在获得可靠 `Context` 后只初始化一次控制器。
- 运行状态写入 `KEY_GPU_RANGE_RUNTIME_STATUS`，值为状态枚举的小写稳定名称。

- [ ] **步骤 1：扩展诊断失败测试**

加入断言，确认运行日志中的 `GPU range DVFS minimum unavailable` 和
`GPU range DVFS active: 231-770MHz` 能被兼容性报告提取。

- [ ] **步骤 2：运行诊断测试并确认失败**

运行：

```powershell
.\gradlew.bat testDebugUnitTest --tests "io.github.pigerzhu.onelab.diagnostics.RuntimeCompatibilityReportTest"
```

预期：新状态尚未被报告识别。

- [ ] **步骤 3：注册三个设置观察项并接入控制器**

观察以下键：

```text
onelab_gpu_range_experiment
onelab_gpu_range_min_mhz
onelab_gpu_range_max_mhz
```

设置变化时使用已有 `ContentObserver` 刷新快照，并在 SDHMS 主线程 Handler 上调用
控制器。禁止在 `GPUFreqMax.k(...)` Hook 的每次调用中重新申请 DVFS 请求。

- [ ] **步骤 4：加入一次性运行日志与状态发布**

只在状态变化时记录：

```text
GPU range DVFS active: <min>-<max>MHz
GPU range DVFS minimum unavailable
GPU range DVFS maximum unavailable
GPU range DVFS released
```

Settings 状态写入失败不得影响 SDHMS 原始行为。

- [ ] **步骤 5：运行测试、完整构建并安装实验 APK**

运行：

```powershell
git diff --check
.\gradlew.bat testDebugUnitTest assembleDebug assembleRelease
& 'D:\platform-tools\adb.exe' install --user 0 -r '.\app\build\outputs\apk\debug\app-debug.apk'
```

预期：测试和构建通过，安装返回 `Success`。不执行重启。

- [ ] **步骤 6：提交任务 3**

```powershell
git add -- app/src/main/java/io/github/pigerzhu/onelab/hook/system/SdhmsHookConfig.java app/src/main/java/io/github/pigerzhu/onelab/hook/system/SdhmsThermalHook.java app/src/main/java/io/github/pigerzhu/onelab/diagnostics/RuntimeCompatibilityReport.java app/src/test/java/io/github/pigerzhu/onelab/diagnostics/RuntimeCompatibilityReportTest.java
git commit -m "experiment: probe persistent GPU DVFS range"
```

- [ ] **步骤 7：用户重启后执行真机硬门验证**

先通过 ADB 写入保守测试范围 `231-770 MHz`，再读取 LSPosed 日志与以下节点：

```text
/sys/class/kgsl/kgsl-3d0/min_clock_mhz
/sys/class/kgsl/kgsl-3d0/max_clock_mhz
/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq
```

连续观察至少 30 秒，并覆盖空闲与短时 GPU 负载。随后关闭实验，确认范围恢复。

**硬门：** 只有类型 `16` 与 `17` 均成功、频率始终在范围内、关闭后恢复，才能继续任务 4。否则停止，记录失败证据并撤回实验生产代码。

---

### 任务 4：验证通过后接入实验室 UI 与诊断目录

**文件：**
- 新建：`app/src/main/java/io/github/pigerzhu/onelab/feature/experiment/GpuFrequencyRangeScreen.java`
- 修改：`app/src/main/java/io/github/pigerzhu/onelab/MainActivity.java`
- 修改：`app/src/main/java/io/github/pigerzhu/onelab/diagnostics/DiagnosticCatalog.java`
- 修改：`app/src/main/java/io/github/pigerzhu/onelab/diagnostics/DiagnosticReport.java`

**接口：**
- `GpuFrequencyRangeScreen.card()` 返回实验室页面卡片。
- UI 使用一个 `RangeSlider`，两个 thumb 分别对应最低与最高频点索引。
- UI 读取 `KEY_GPU_RANGE_RUNTIME_STATUS`，只显示“未启用”“已生效”或“不可用”，不暴露类型编号等内部术语。

- [ ] **步骤 1：实现独立实验卡片**

`RangeSlider` 配置：

```java
rangeSlider.setValueFrom(0f);
rangeSlider.setValueTo(SettingsKeys.SDHMS_GPU_FREQS_MHZ.length - 1);
rangeSlider.setStepSize(1f);
rangeSlider.setValues(minIndex, maxIndex);
rangeSlider.setLabelFormatter(value -> frequencyAt(value) + "MHz");
```

总开关仅在两个频率设置均保存成功后保持打开。运行状态不是 `active` 时，显示“设置已保存，重启后验证”或“当前设备不可用”，不得伪装为已生效。

- [ ] **步骤 2：将卡片加入实验功能页面**

在 `MainActivity` 构造 `GpuFrequencyRangeScreen`，并在 `showExperimentsPage(...)` 中加入
`root.addView(gpuFrequencyRangeScreen.card())`。不要放入正式“性能与温控”页面。

- [ ] **步骤 3：补充诊断输出**

诊断目录记录实验开关、请求最低值、请求最高值和运行状态；运行状态缺失时输出
`unavailable`，不得根据设置开关推断已生效。

- [ ] **步骤 4：运行完整验证**

```powershell
git diff --check
.\gradlew.bat testDebugUnitTest assembleDebug assembleRelease
```

人工检查：最低值不能越过最高值；两端合并显示锁频；拖动过程不写设置；关闭开关后
控制器释放投票；横竖屏和内外屏页面无重叠。

- [ ] **步骤 5：提交任务 4**

```powershell
git add -- app/src/main/java/io/github/pigerzhu/onelab/feature/experiment/GpuFrequencyRangeScreen.java app/src/main/java/io/github/pigerzhu/onelab/MainActivity.java app/src/main/java/io/github/pigerzhu/onelab/diagnostics/DiagnosticCatalog.java app/src/main/java/io/github/pigerzhu/onelab/diagnostics/DiagnosticReport.java
git commit -m "experiment: expose verified GPU frequency range"
```

---

### 任务 5：最终回归与研究记录

**文件：**
- 修改：`docs/ONEUI_8_5_CHECKLIST.md`
- 修改：`docs/TROUBLESHOOTING.md`
- 修改：`docs/NEXT_STEPS.md`

**接口：** 无生产接口；记录 One UI 8 真机结论与 One UI 8.5 的证据级别。

- [ ] **步骤 1：记录真实结论**

若成功，记录 Fold6 型号、One UI 构建、支持频点、类型 `16/17` 的运行结果、Scene
共存结果和释放结果。One UI 8.5 只能标为静态未验证，不得从 One UI 8 外推为真机兼容。

若失败，记录失败阶段、日志和停止条件；明确禁止未来使用周期性 sysfs 覆盖作为替代。

- [ ] **步骤 2：执行最终验证**

```powershell
git diff --check
.\gradlew.bat testDebugUnitTest assembleDebug assembleRelease
git status --short
```

预期：测试与两个构建全部成功；无 APK、日志、反编译目录或设备文件被纳入提交。

- [ ] **步骤 3：提交文档**

```powershell
git add -- docs/ONEUI_8_5_CHECKLIST.md docs/TROUBLESHOOTING.md docs/NEXT_STEPS.md
git commit -m "docs: record GPU DVFS range experiment"
```
