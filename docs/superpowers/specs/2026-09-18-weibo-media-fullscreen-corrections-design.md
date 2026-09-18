# 微博媒体全屏修正设计

## 背景

微博 `16.3.2` 的图片全屏例外已能阻止图片预览进入三星分栏右栏，但实机验收发现：

- 视频使用独立的 `com.sina.weibo.story.multiv2.core.MediaCoreV2Activity`，现有规则未覆盖；
- 横屏进入 `MediaPreviewActivity` 时，容器和图片先按纵向绘制，再切换为横向。

事件日志证明 `MediaPreviewActivity` 在首帧前请求 `SCREEN_ORIENTATION_PORTRAIT`；APK
Manifest 也为该 Activity 声明了 portrait。微博的 `forceOrientationPortrait()` 只有在内部
全屏播放 Fragment 已存在时才返回 `false`，首次进入时该条件尚未成立。

## 目标行为

- 图片和视频预览均脱离三星 ActivityGroup，占用完整 Task。
- 图片预览从第一帧开始使用进入时的设备方向，并在打开期间持续跟随设备旋转。
- 视频保留微博自己的 portrait、sensor-landscape 等方向请求和播放器旋转按钮行为。
- 返回后恢复原有微博分栏；关闭总开关或微博子开关后完全保留三星原行为。

## 设计

继续只使用 `system_server` 中的 `SamsungSplitRulesHook`，不增加微博应用进程作用域。

1. 将 `MediaCoreV2Activity` 加入微博规则集的全屏 Activity 集合。现有两个启动入口仍会
   阻止它加入 ActivityGroup，但不会向三星仓库注入或删除微博 pair。
2. 为规则集增加独立的“跟随系统方向 Activity”集合，只包含图片预览 Activity，不包含
   `MediaCoreV2Activity`。
3. `ActivityStarter.reparentActivitiesToActivityGroupIfNeeded()` 收到目标 ActivityRecord 时，
   如果目标属于该集合且功能已启用，则在首次恢复和绘制前调用该 ActivityRecord 的稳定
   `setRequestedOrientation(SCREEN_ORIENTATION_UNSPECIFIED)`，覆盖 Manifest 的 portrait。
4. Hook `ActivityRecord.setRequestedOrientation(int)`。仅当功能已启用、目标属于图片方向集合、
   且请求值为 portrait 时提前返回；其他方向值、其他 Activity 和关闭功能后的调用全部放行。

`ActivityRecord.setRequestedOrientation(int)` 是当前 One UI framework 中由
`ActivityClientController` 使用的直接入口。处理发生在低频 Activity 方向请求上，不读取
磁盘或 Settings；开关状态继续使用现有 ContentObserver 缓存。

## 失败处理与兼容

- 找不到 `ActivityRecord.setRequestedOrientation(int)` 时记录一次安装失败并保持系统原行为。
- ActivityRecord 的包名和 ActivityInfo 名称均使用已有稳定字段读取路径。
- 不 Hook 微博私有方法，不依赖混淆类，不扫描 View，不修改窗口 bounds。
- 视频方向请求不经过图片方向过滤，避免破坏横屏播放和退出横屏。

## 测试与验收

自动测试覆盖：

- 微博视频 Activity 属于全屏例外；
- 只有图片 Activity 属于跟随系统方向集合；
- 过滤策略只拒绝目标图片 Activity 的 portrait 请求；
- 总开关或微博子开关关闭时不应用上述行为。

实机验收覆盖：

- 横屏和竖屏分别首次进入图片，首帧方向正确且无二次旋转；
- 图片打开期间旋转设备，容器和图片同步跟随；
- 视频从右栏进入后占满 Task，播放器横竖屏切换仍正常；
- 图片、视频返回后分栏和滚动位置恢复；
- 关闭微博子开关后恢复三星原始行为。
