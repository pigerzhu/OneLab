# Diagnostic Coverage Design

## Goal

Make the diagnostic bundle cover every user-facing feature shipped from 1.0 through the current `dev` branch, and make future omissions fail a unit test.

## Audit Result

The 1.0 settings are represented by the current diagnostic catalog or an existing extra-state section. The 1.1 additions for Bilibili International, Kuaishou, and per-display refresh-rate ranges are also represented. `KEY_SPLIT_VIEW_ALLOWED_PACKAGES` is intentionally outside the generic catalog because `split-view.txt` reports that snapshot in detail.

The current branch has four real omissions:

- the split-image-fullscreen master setting;
- the Coolapk image-fullscreen setting and package version;
- the Xiaohongshu image-fullscreen setting and package version;
- the Qishui Music fold-mode state and package version.

## Design

Add the three `Settings.Global` switches to `DiagnosticCatalog.FEATURES`. This automatically reports their raw and interpreted values in `features.txt` and adds the two application packages to `packages.txt`.

Qishui Music does not use `Settings.Global`. Add its package contract to `QishuiMusicClient`, read only OneLab's own `qishui_music/enabled` preference, and append a single feature line to `features.txt`. Do not inspect Qishui Music private storage and do not execute root commands while generating a report. Add the package explicitly to `packages.txt`.

Increment `report_format` from 4 to 5 because the feature and package report contract gains new entries.

## Regression Boundary

Extend the catalog test so every public `SettingsKeys.KEY_*` string must be represented by a catalog feature/value or by the explicit `split-view.txt` exemption. Add focused assertions for the three image feature IDs, Qishui Music state formatting, its package entry, and report format 5.

Pure UI preferences such as theme and application-list sorting are not support diagnostics. Infrastructure changes such as asynchronous root writes and silent success feedback do not create feature state and remain outside the catalog.
