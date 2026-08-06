<p>
  <a href="README.md"><img alt="简体中文" src="https://img.shields.io/badge/简体中文-0969da"></a>
  <a href="README.zh-TW.md"><img alt="繁體中文" src="https://img.shields.io/badge/繁體中文-6f42c1"></a>
  <a href="README.en.md"><img alt="English" src="https://img.shields.io/badge/English-1f883d"></a>
</p>

# OneLab

One UI feature extensions and foldable app adaptations for Samsung devices.

OneLab is an LSPosed module for Samsung devices. It focuses on features the system
cannot open directly or that have no customization entry point, and it improves how
some apps behave on the unfolded Galaxy Fold display.

> This is a public beta, validated mainly on the Samsung Galaxy Z Fold6 running One UI 8.0.
> Some features may need to be adapted again after a firmware or target app update.

## Features

### Network & connection

- Custom captive portal close delay

### Performance & thermals

- Enhanced processing speed
- SIOP performance cap bypass

### System UI

- Remember the position and size of pop-up windows
- Use the full cover screen when unfolded
- Per-app refresh rate policy
- Per-app custom aspect ratio
- App split view ratio
- Cover screen content

### Apps and foldable adaptations

- Samsung Gallery developer Labs
- Bilibili large-screen entry point
- Xiaohongshu foldable home feed and new video post layout
- QQ foldable layout detection
- Samsung split view rules for Ctrip, Umetrip, Meituan, Tongcheng Travel and Zhuanzhuan
- Native foldable capabilities of the Xiaomi Store

### Experiments

- Game heat budget adjustment
- SDHMS hidden thermal controls
- Cover screen edge rejection parameters

## Requirements

- Samsung One UI device
- Android 13 or newer
- Root
- LSPosed, Xposed API 100

Some features depend on specific Samsung services, hardware capabilities or target app
versions, so they will not behave identically on every device.

## Language

Simplified Chinese is the default language of the app: it is what OneLab shows unless
another available language matches the device. English is available as a translation.

To read the app in English, use the per-app language preference of Android 13+:
**Settings › Apps › OneLab › Language**, or the **Language** entry on the OneLab
appearance settings page, which opens the same system screen.

## Installation

1. Download and install the APK. [OneLab 0.1.0 Beta 4 APK](https://github.com/pigerzhu/OneLab/releases/download/v0.1.0-beta.4/OneLab-v0.1.0-beta.4.apk)
2. Enable OneLab in LSPosed.
3. Configure the scope according to the features you actually use.
4. Restart the corresponding app; reboot the phone when the system framework or a
   Samsung system service is involved.
5. Open OneLab and configure the features you need.

Do not enable modules that change the same settings or hook the same methods at the same
time. Keeping the previous APK before upgrading is recommended, so you can roll back if a
compatibility problem shows up.

## Reporting issues

When you run into a problem, use "Diagnostics & feedback" in the OneLab settings:

1. Tap "Start recording".
2. Reproduce the issue.
3. Tap "Stop recording".
4. Tap "Generate and share".

The report is saved to `Download/OneLab/`. When you open an issue, please also provide:

- The OneLab version
- The phone model and One UI version
- The name and version of the target app
- The LSPosed scope
- Clear reproduction steps
- The expected result and the actual result
- The diagnostic report

The diagnostic report filters common account and network sensitive fields, but reviewing
its content before uploading is still recommended.

Since Beta 4, the report also carries a snapshot of the Samsung split view list, an app
eligibility and ratio configuration comparison, and the device runtime state, which makes
it easier to diagnose apps missing from the list or ratios that do not take effect.

## Building

The project uses the Gradle Wrapper:

```powershell
.\gradlew.bat :app:assembleDebug
```

APK output directory:

```text
app/build/outputs/apk/debug/
```

Development conventions and directory layout are documented in [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).

## Warning

OneLab contains experimental system features. Wrong thermal, performance or window
parameters can cause heat, higher battery drain, app crashes or interface glitches.
Enable one item at a time and confirm the result; if something breaks, turn the
corresponding feature off first and reboot the device.
