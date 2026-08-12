# 应用功能可信开关状态设计

> 本文是 OneLab 1.0 应用程序页面可信开关的设计规范。第一阶段只覆盖应用折叠屏适配、应用分屏规则和应用侧 Hook，不修改性能、温控或应用分栏比例页面。

## 目标

让应用页面的开关反映功能的实际状态，而不是仅反映 `Settings.Global` 是否写入成功。用户应能知道功能是否命中；目标应用未安装时，对应功能必须不可操作；启用失败时开关必须自动回退到关闭。

## 状态模型

每个功能使用三个用户可见状态：

```text
UNAVAILABLE  目标应用未安装，开关禁用
AVAILABLE_OFF 目标应用已安装，但当前功能尚未确认生效，开关可操作且关闭
ACTIVE        当前版本的 Hook 已加载并确认功能已生效，开关打开
```

设置值不能单独产生 `ACTIVE`。状态判定必须同时满足：

- 目标包已安装并启用；
- 目标应用版本与回传记录一致；
- OneLab 版本与回传记录一致；
- 应用侧 Hook 已加载，或系统侧规则已实际应用；
- 对启用操作，Hook 已确认读取到启用状态。

旧版本记录在应用升级、OneLab 更新或目标应用卸载后自动失效。

## 跨进程状态协议

OneLab UI、应用进程 Hook 和 `system_server` 位于不同进程。新增专用状态 Provider：

```text
content://io.github.pigerzhu.onelab.hook-status
```

Hook 通过 `ContentResolver.call()` 回传以下字段：

```text
featureId       OneLab 稳定功能 ID
targetPackage   目标包名
targetVersion   目标应用版本号
moduleVersion   OneLab 版本号
state           LOADED、ACTIVE 或 FAILED
timestamp       回传时间
```

安全要求：

- Provider 使用 `Binder.getCallingUid()` 校验调用者；
- 应用 Hook 只能回传自身包名，不能通过参数冒充其他应用；
- `system_server` 回传时，目标包必须属于 OneLab 已登记的系统分屏规则；
- Provider 不接受任意未知 `featureId`；
- UI 查询时再次校验目标包和当前版本；
- Provider 只保存紧凑状态，不保存类名、Binder 参数或混淆实现细节；
- 不使用无权限广播作为唯一状态来源。

## 状态回传时机

- Hook 安装成功后回传 `LOADED`；
- Hook 观察到功能已打开并完成对应初始化后回传 `ACTIVE`；
- 找不到稳定入口、安装失败或初始化失败时回传 `FAILED`；
- 关闭功能后回传关闭状态，UI 可立即显示关闭；
- 目标 Hook 尚未启动时，不得伪造成功，开关保持 `AVAILABLE_OFF`，提示用户启动或重启目标应用后重试。

系统侧分屏功能只有在规则仓库、解析器和实际规则应用均成功后，才能回传 `ACTIVE`。单纯写入设置或调用成功不等于规则已生效。

## UI 控制器

新增可复用的应用功能开关控制器。每张卡只声明：

- 功能 ID；
- 目标包名；
- 设置键；
- 状态来源；
- 可选依赖功能。

控制器统一负责：

- 页面进入和恢复时刷新包安装状态与可信状态；
- 通过 `PackageManager` 判断目标包是否安装；
- 根据三态设置开关的 `enabled` 与 `checked`；
- 开启时写入设置并等待当前版本状态回传；
- 回传失败、超时或写入失败时回写关闭并恢复 UI；
- 卸载后立即禁用开关；
- 使用普通用户语言显示 Toast，不展示类名、Binder、AndroidX 或混淆术语。

用户操作语义：

1. 未安装：开关灰显，不可点击。
2. 已安装但未确认：开关可点击，当前保持关闭。
3. 点击开启：只有收到当前版本的 `ACTIVE` 后才显示打开。
4. 开启失败：自动关闭设置和 UI，并提示“功能未生效，请重启应用后重试”。
5. 点击关闭：设置成功写回关闭后立即显示关闭，不依赖 Hook 继续在线。

## 功能映射

| 功能 | 目标包 | 状态来源 | 依赖 |
|---|---|---|---|
| 哔哩哔哩原生大屏 | `tv.danmaku.bili` | B站 Hook | 无 |
| 哔哩哔哩平板布局 | `tv.danmaku.bili` | B站 Hook | 原生大屏 |
| 百度折叠屏适配 | `com.baidu.searchbox` | 百度 Hook | 无 |
| 小红书首页布局 | `com.xingin.xhs` | 小红书 Hook | 无 |
| 小红书视频帖布局 | `com.xingin.xhs` | 小红书 Hook | 无 |
| 小米商城折叠屏适配 | `com.xiaomi.shop` | 小米商城 Hook | 无 |
| 同程旅行分屏 | `com.tongcheng.android` | 应用 Hook与系统规则 | 无 |
| 携程分屏 | `ctrip.android.view` | `system_server` 分屏规则 | 无 |
| 航旅纵横分屏 | `com.umetrip.android.msky.app` | `system_server` 分屏规则 | 无 |
| 美团分屏 | `com.sankuai.meituan` | `system_server` 分屏规则 | 无 |
| 转转分屏 | `com.wuba.zhuanzhuan` | `system_server` 分屏规则 | 无 |

小红书首页和视频帖必须独立记录状态，不能用一个功能的成功结果覆盖另一个功能。B站平板布局在主 Hook 未达到 `ACTIVE` 时不可进入打开状态。

## 提交拆分

生产实现按以下独立提交拆分，每个提交都必须可单独构建和测试：

1. 状态契约、Provider、版本新鲜度和状态存储。
2. 应用包安装检测及通用三态开关控制器。
3. 应用侧 Hook 的加载、启用和失败回传。
4. `system_server` 分屏规则的实际应用回传。
5. 应用页面接入、回退提示和依赖开关处理。
6. 状态判定、版本失效、卸载禁用和失败回退测试。

当前阶段不处理：

- 性能与温控开关；
- USB PD 旁路充电；
- 应用分栏比例页面；
- 应用 Hook 主进程范围审计；
- 自动启动或强停目标应用。

## 测试要求

必须覆盖以下行为：

- 未安装目标包时开关禁用；
- 已安装但无回传时开关可用且关闭；
- 旧目标版本的 `ACTIVE` 记录不能使新版本显示打开；
- 旧 OneLab 版本的记录不能继续生效；
- Hook 回传 `ACTIVE` 后开关才变为打开；
- 设置写入成功但没有 `ACTIVE` 时自动回退；
- Hook 回传 `FAILED` 时自动回退；
- 目标应用卸载后页面刷新为不可用；
- B站平板布局不能绕过主功能状态；
- 小红书两个子功能状态互不覆盖；
- 系统规则未实际应用时不能回传成功。

验证命令必须包括：

```text
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew assembleRelease
git diff --check
```

安装测试只允许使用 Android 主用户 `0`，且不能因为打开页面或安装 APK 自动启用任何功能。
