<p>
  <a href="README.md"><img alt="简体中文" src="https://img.shields.io/badge/简体中文-0969da"></a>
  <a href="README.zh-TW.md"><img alt="繁體中文" src="https://img.shields.io/badge/繁體中文-6f42c1"></a>
  <a href="README.en.md"><img alt="English" src="https://img.shields.io/badge/English-1f883d"></a>
</p>

# OneLab

One UI feature extensions and foldable app adaptations for Samsung devices.

OneLab is an LSPosed module for Samsung devices. It focuses on features the system
cannot open directly or that have no customization entry point, and it improves how
some apps behave on the unfolded Galaxy Fold display, in split layouts, and in half-fold mode.

Many Android apps already contain large-screen, two-pane, or foldable layouts, but these
features may be restricted by the device model, system vendor, region, screen orientation,
or internal app configuration. OneLab restores these native capabilities where possible
and adds more customization entry points for Samsung devices.

> OneLab has left beta. The current stable release is 1.0.
>
> The project is tested primarily on a Samsung Galaxy Z Fold6 running One UI 8.0,
> with additional compatibility checks against One UI 8.5 firmware. Features that depend
> on app internals may still require adaptation after firmware or app updates.

## Features

### Network & connection

- Custom captive portal close delay

### Performance & thermals

- Enhanced processing speed
- SIOP performance cap bypass
- Manual GPU frequency range control using the frequencies supported by the current device
- Game heat budget adjustment
- Hidden SDHMS thermal controls

### System UI

- Remember the position and size of pop-up windows
- Use the full cover screen when unfolded
- Per-app refresh rate policy
- Per-app custom aspect ratio
- App split view ratio
- Cover screen content

### Apps and foldable adaptations

OneLab currently provides large-screen, split-layout, or half-fold adaptations for 17 apps,
including Ctrip, QQ, Bilibili, Xiaohongshu, Instagram, TikTok, NetEase Cloud Music, Lark,
and others.

- Converts existing foldable declarations from selected apps into Samsung split-view rules
- Improves native split layouts and Samsung system switch integration for QQ, ITHome, Hupu,
  and Tongcheng Travel
- Enables existing large-screen or foldable layouts in Bilibili, Baidu, Xiaohongshu,
  and Xiaomi Store
- Enables side-panel video comments in Instagram and TikTok
- Supports TikTok's portrait side panel and live drawer avoidance
- Bridges Samsung Fold posture information to NetEase Cloud Music's built-in half-fold player
- Supports custom pane ratios in Lark's in-app two-pane layout
- Provides generic split-ratio support for apps such as WeChat, JD.com, and Coolapk

The feature switches for QQ, ITHome, and Hupu are managed from Samsung Settings:

> Settings → Advanced features → Labs → App split view

ITHome's own experimental split-view switch is synchronized with the Samsung system switch.

These features have not been removed from OneLab. The module still provides foldable
detection, split rules, state synchronization, and ratio support in the background; only
the switches are managed centrally by Samsung Settings.

An app must already contain the corresponding large-screen or split-layout implementation.
OneLab cannot create a complete large-screen interface for an app that has none. Using a
recent app version is recommended.

### Experiments

- Show Samsung Gallery Labs
- Optional Simplified Chinese translation for Gallery Labs
- Cover screen edge rejection parameters

The Gallery Labs translation does not depend on a fixed Gallery version number. Existing
items generally remain translated after an update as long as their names stay unchanged.
New items that are not covered yet remain in English without affecting other translations.

## Requirements

- Samsung One UI device
- Android 13 or newer
- Root
- LSPosed, Xposed API 100

Some features depend on specific Samsung services, hardware capabilities or target app
versions, so they will not behave identically on every device.

## Language

OneLab supports Simplified Chinese, Traditional Chinese, and English.

To read the app in English, use the per-app language preference of Android 13+:
**Settings › Apps › OneLab › Language**, or the **Language** entry on the OneLab
appearance settings page, which opens the same system screen.

## Installation

1. Download and install the APK. [OneLab 1.0 APK](https://github.com/pigerzhu/OneLab/releases/download/v1.0/OneLab-v1.0.apk)
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

The report also carries a snapshot of the Samsung split view list, an app
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
