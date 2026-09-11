# 小红书图片跨栏全屏热路径修复设计

## 问题与目标

当前 `XhsImageFullscreenHook` 在小红书主进程中 Hook 所有
`ViewGroup.addView(...)`。图文详情快速滚动时，列表的 View 创建和复用会持续进入该
Hook，并在主线程排队递归扫描 View 树。退出图片查看器时还会执行延迟扫描。实机通过关闭
小红书图片全屏开关完成对照后，快速滑动和返回卡顿消失。

修复后保留现有跨栏全屏功能，但滚动期间不得扫描 View 树，也不得 Hook
`ViewGroup.addView(...)`。检测必须由用户在 `NoteDetailActivity` 中完成一次点击触发。

## 事件驱动检测

`Activity.dispatchTouchEvent(...)` 只记录触摸序列，不在事件回调内扫描：

- `ACTION_DOWN` 保存坐标、时间并取消上一轮未执行的进入检测；
- 移动距离超过系统 touch slop、出现多指、收到 `CANCEL`，或按住超过长按阈值时，当前
  序列不再视为点击；
- 合格的 `ACTION_UP` 只安排一个延迟检测；延迟沿用原生进入动画所需的 500ms；
- 检测时从当前 Activity 的 decor view 做一次深度优先遍历，单次遍历同时判断
  RecyclerView、`photoImageViewLayout` 和 `mediaContainer`，不再对每个候选子树重复递归。

图片查看器已经报告可见时，左右滑动只记录退出检查所需状态，不安排进入扫描。返回键和
查看器内手势结束仍可安排一次退出确认，但同一 Activity 同时最多存在一个待处理任务。

## 生命周期与状态

仅主进程的 `NoteDetailActivity` 参与检测。Activity 暂停、销毁、功能关闭或切换到另一
实例时，取消待处理任务、清空弱引用和触摸状态；若此前已报告查看器可见，则发送一次
`visible=false`，让 `system_server` 恢复右栏边界。

已有广播契约和 `SamsungSplitRatioHook` 的边界切换保持不变。小红书原生图片 View、左右
切图、缩放、长按菜单和返回行为不被接管。

## 验证

- 单元测试覆盖点击判定：轻触接受，拖动、多指、取消和长按拒绝；
- 源码回归测试确保 `XhsImageFullscreenHook` 不再调用
  `hookAllMethods(ViewGroup.class, "addView", ...)`；
- 完整运行单元测试、Debug/Release 构建和 lint；
- 安装到 user 0，强停小红书以重载应用进程 Hook；
- 真机验证快速滚动、进入单图/多图、左右切图、长按、返回恢复及关闭开关后的原生行为。

