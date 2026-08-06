<p>
  <a href="README.md"><img alt="简体中文" src="https://img.shields.io/badge/简体中文-0969da"></a>
  <a href="README.zh-TW.md"><img alt="繁體中文" src="https://img.shields.io/badge/繁體中文-6f42c1"></a>
  <a href="README.en.md"><img alt="English" src="https://img.shields.io/badge/English-1f883d"></a>
</p>

# OneLab

三星 One UI 功能扩展与折叠屏应用适配模块。

OneLab 是一个面向三星设备的 LSPosed 模块，重点补充系统中无法直接打开、
缺少自定义入口的功能，并改善部分应用在 Galaxy Fold 展开屏上的使用体验。

> 当前版本为公开测试版，主要在 Samsung Galaxy Z Fold6、One UI 8.0 上验证。
> 系统固件或目标应用升级后，部分功能可能需要重新适配。

## 主要功能

### 网络与连接

- 自定义认证页面关闭延迟

### 性能与温控

- Enhanced processing 处理速度
- SIOP 性能限频拦截

### 系统界面

- 记住弹出窗口的位置与大小
- 展开时使用完整外屏
- 按应用设置刷新率策略
- 按应用自定义宽高比
- 应用分栏比例
- 外屏显示内容

### 应用与折叠屏适配

- 三星图库开发者 Labs
- 哔哩哔哩大屏适配入口
- 小红书折叠屏首页与新版视频帖布局
- QQ 折叠屏布局识别
- 携程旅行、航旅纵横、美团、同程旅行、转转的三星分屏规则
- 小米商城原生折叠屏能力

### 实验功能

- 游戏热预算调整
- SDHMS 隐藏温控
- 外屏侧边防误触参数

## 使用要求

- Samsung One UI 设备
- Android 13 或更高版本
- Root
- LSPosed, Xposed API 100

部分功能依赖特定三星服务、硬件能力或目标应用版本，因此不会在所有设备上产生相同效果。

## 安装

1. 下载并安装 APK。[OneLab 0.1.0 Beta 4 APK](https://github.com/pigerzhu/OneLab/releases/download/v0.1.0-beta.4/OneLab-v0.1.0-beta.4.apk)
2. 在 LSPosed 中启用 OneLab。
3. 按实际使用的功能配置作用域。
4. 重启对应应用；涉及系统框架或三星系统服务时重启手机。
5. 打开 OneLab 配置需要的功能。

不要同时启用会修改相同设置或 Hook 相同方法的模块。升级前建议保留上一版 APK，便于出现兼容问题时回退。

## 问题反馈

遇到问题时，请在 OneLab 设置中的“诊断与反馈”执行：

1. 点击“开始记录”。
2. 复现问题。
3. 点击“停止记录”。
4. 点击“生成并分享”。

报告会保存到 `下载/OneLab/`。提交 Issue 时请同时提供：

- OneLab 版本
- 手机型号和 One UI 版本
- 目标应用名称及版本
- LSPosed 作用域
- 清晰的复现步骤
- 预期结果与实际结果
- 诊断报告

诊断报告会过滤常见账号与网络敏感字段，但上传前仍建议自行检查内容。

Beta 4 起，报告还会附带三星分屏列表快照、应用资格与比例配置对照，以及设备运行时状态，便于定位应用未出现在列表中或比例未生效的问题。

## 构建

项目使用 Gradle Wrapper：

```powershell
.\gradlew.bat :app:assembleDebug
```

APK 输出目录：

```text
app/build/outputs/apk/debug/
```

开发规范和目录约定见 [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)。

## 注意事项

OneLab 包含实验性系统功能。错误的温控、性能或窗口参数可能造成发热、耗电增加、
应用崩溃或界面异常。请逐项启用并确认效果，出现问题时先关闭对应功能并重启设备。
