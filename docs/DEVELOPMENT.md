# OneLab development rules

This document defines where code belongs and the safety rules for adding features.
It applies to production sources under `app/src/main`.

## Package layout

| Location | Responsibility |
| --- | --- |
| `io.github.pigerzhu.onelab.MainActivity` | Activity lifecycle, page navigation, transitions, and feature composition only. |
| `io.github.pigerzhu.onelab.navigation` | Page stack, predictive back, large-screen sidebar, transitions, and reusable application-picker navigation. |
| `io.github.pigerzhu.onelab.feature.connectivity` | Network and captive-portal controls shown by the app. |
| `io.github.pigerzhu.onelab.feature.performance` | Processing speed, thermal controls, and game heat-budget screens. |
| `io.github.pigerzhu.onelab.feature.window` | Window management, refresh rate, aspect ratio, and foldable cover-display features. |
| `io.github.pigerzhu.onelab.feature.applications` | User-facing controls for one specific third-party or Samsung application. |
| `io.github.pigerzhu.onelab.feature.experiment` | Explicitly experimental user-facing controls. |
| `io.github.pigerzhu.onelab.feature.diagnostics` | Diagnostic recording and report UI. |
| `io.github.pigerzhu.onelab.system` | Privileged or device-specific I/O. Shell commands, Binder/service calls, Settings access wrappers, and Samsung service clients live here. No view construction. |
| `io.github.pigerzhu.onelab.contract` | Setting keys and data contracts shared between the app UI and hook processes. No Android component or I/O code. |
| `io.github.pigerzhu.onelab.hook.Entry` | Stable LSPosed package dispatcher. Its class name must stay compatible with `assets/xposed_init`. |
| `io.github.pigerzhu.onelab.hook.core` | Hook constants and reflection/context utilities shared across hook processes. |
| `io.github.pigerzhu.onelab.hook.system` | Android or Samsung service hooks, including WindowManager, captive portal, GOS, and SDHMS. |
| `io.github.pigerzhu.onelab.hook.samsung` | Samsung framework policies shared by multiple applications, such as split-view rule translation. |
| `io.github.pigerzhu.onelab.hook.applications` | Hooks scoped to one application package. |
| `io.github.pigerzhu.onelab.ui` | Reusable visual components and theme primitives. No feature settings keys or privileged operations. |
| `app/src/main/res` | Android resources. Shared icons and framework-required strings belong here. |
| `tools` | Repeatable repository maintenance scripts. Generated files must name their generator. |
| `analysis` and `apks` | Reverse-engineering evidence only. Production code must not load files from these directories. |

Do not create generic `Utils`, `Manager`, or `Helper` classes. Name a class after the
specific responsibility it owns. A new feature normally starts in the matching `feature`
domain; add a `system/FeatureClient` only when privileged I/O would otherwise be mixed
into the UI.

## Dependency direction

Normal app code follows this direction:

```text
MainActivity -> feature Screen/Presenter -> system client
                                 |
                                 +-> ui components
```

Hook code is a separate runtime boundary:

```text
hook.Entry -> scoped hook package -> hook.core
```

- UI and hooks read shared setting keys from `contract/SettingsKeys`.
- Hooks must not call `Shell`, construct UI, or hold Activity references.
- Screens may configure hooks through Settings, but never call hook classes directly.
- `MainActivity` must not contain feature-specific Settings keys or shell commands.

## File and class rules

- One top-level production class per Java file.
- A Screen owns one page or a small feature group and lives in the matching `feature` package.
- Navigation classes must not contain feature setting keys or privileged I/O.
- Application hooks belong in `hook.applications`; cross-application Samsung policy belongs
  in `hook.samsung`.
- Prefer package-private classes and methods until another package genuinely needs the API.
- At 400 lines, review a file for mixed responsibilities. Above 600 lines, split it before adding another feature.
- Keep constants beside their owner. Put cross-process setting keys in `contract/SettingsKeys`.
- Comments explain device behavior, invariants, or reverse-engineered contracts, not obvious Java statements.

## Privileged operation rules

- Opening a page must not request root. Root is allowed only after an explicit user action.
- Never run `su`, Binder I/O, package scans, or device-service calls on the main thread.
- Preserve the previous value before changing experimental firmware or service settings.
- Every risky setting needs a bounded input range and a recovery path.
- A success message requires write-back verification when the target can silently reject writes.
- On failure, restore the visible control to the last confirmed state.
- Shell values must be quoted; do not concatenate untrusted text into a root command.
- Installation and package-component commands must target user 0 unless another user was explicitly requested.

## Hook safety rules

- Avoid broad method enumeration when a stable class and method are known.
- Never perform Settings or disk reads in a hot WindowManager/SystemUI method. Observe once, cache, and update the cache from a `ContentObserver`.
- Fail open: if parsing or reflection fails, preserve Samsung's original behavior.
- Scope each hook to the smallest required package/process.
- Do not block Binder, render, input, or animation threads.
- Log installation failures once; do not emit per-frame or per-call logs.

## UI rules

- Use `Ui` and existing Material components before adding a new visual primitive.
- Entry cards navigate; they do not also expose detailed controls.
- A control must reflect the last confirmed system state, not merely the requested state.
- Controls that write continuously must defer the write until interaction ends unless live updates are essential.
- New nested pages must register their owning parent through
  `MainActivity.setNestedBackAction(...)`.

## Localization rules

- Simplified Chinese is the language of the project. It lives in `res/values/strings.xml`,
  which stays the default resource set and the fallback for any unmatched locale.
  Taiwan Traditional Chinese lives in `res/values-zh-rTW/strings.xml`, and English lives
  in `res/values-en/strings.xml`. A translation never replaces the default.
- Every user-visible string lives in resources. Do not hardcode user-visible text in Java.
- Every new or changed user-visible string must update `res/values/strings.xml`,
  `res/values-zh-rTW/strings.xml`, and `res/values-en/strings.xml` in the same commit.
  Taiwan Traditional Chinese follows Samsung Taiwan One UI terminology instead of a
  mechanical script conversion. Do not merge a feature that relies on another locale's
  fallback for newly introduced UI.
- When a control or page is removed, remove its unused keys from all language files.
  Do not retain translations for UI that no longer exists.
- Text assembled from parts uses a single format string with positional arguments
  (`%1$s`, `%2$d`), never string concatenation, so word order stays translatable.
- Counted text uses `<plurals>` rather than a manually formatted number.
- The app follows the system language. Users pick a specific one through the per-app
  language preference of Android 13+, declared by `res/xml/locales_config.xml`; the
  appearance settings page only links to that system screen. Do not add a private
  language toggle.
- Samsung's per-app language picker can expand a declared locale into regional variants.
  For example, a `zh-CN` declaration can leave an app-level `zh-Hans-MO` selection even
  though neither the system locale list nor `locales_config.xml` contains Macau. The
  picker's "All languages" section can also show language-family entries rather than a
  second flat copy of every declared locale. Before treating a missing row as clipping or
  a resource error, compare the UI hierarchy with both of these commands:

  ```text
  cmd locale get-app-locales <package> --user 0
  cmd locale get-app-localeconfig <package> --user 0
  ```

  The first reports the persisted app selection; the second reports only a runtime
  LocaleConfig override and may be `null` while the manifest LocaleConfig remains active.
- Public documentation follows the same rule: `README.md` stays the original document and
  translations live beside it as `README.<lang>.md`, linked from the original.
- Diagnostic report content (`DiagnosticReport`, `RuntimeCompatibilityReport`) stays in
  English on purpose: it is a machine-readable artifact attached to GitHub issues, not UI.

## Verification and commits

Before committing a behavioral change:

1. Run `git diff --check`.
2. Confirm Java contains no newly hardcoded user-visible text and that the default and
   English resources use matching keys and format placeholders.
3. Build `testDebugUnitTest`, `assembleDebug`, `lintDebug`, and `assembleRelease`.
4. Before publishing, sign the distribution APK with the same certificate as the
   previous public version and verify it with `apksigner --print-certs`. Never publish
   an `app-release-unsigned.apk` artifact.
5. Install with `adb install --user 0 -r` when a phone is connected.
6. Confirm installation did not activate an experimental setting by itself.
7. After uploading, download the public APK again and verify its signature, signer
   certificate, package name, version code, version name, and SHA-256.
8. Review the final diff for unrelated generated or analysis files.

Keep commits limited to one coherent behavior or refactor. A structural refactor must preserve
settings keys, defaults, hook scope, and user-visible behavior unless the commit explicitly says
otherwise.

Keep public documentation focused on stable architecture, supported behavior, and
reproducible build or contribution instructions.
