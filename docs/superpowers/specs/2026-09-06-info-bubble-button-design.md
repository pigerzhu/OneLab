# Reusable Info Bubble Button Design

## Goal

Add a reusable circular information button that opens a small anchored message bubble without blocking interaction with the rest of the page. Use it beside the Qishui Music title to disclose the tested application version.

## Component

Create `InfoBubbleButton` under `io.github.pigerzhu.onelab.ui`. The component owns a compact icon button and one anchored `PopupWindow`. Its public construction contract accepts a `Context` and message text; it contains no Qishui Music setting or package logic.

The button reuses the existing `ic_info` circle-contained letter-i icon and has an accessibility description. The popup uses the existing OneLab surface, text, corner-radius, spacing, and elevation conventions. The message wraps within a bounded width instead of expanding across the page.

## Interaction

Clicking the icon opens a bubble anchored immediately below the icon. Clicking the icon again closes it. An open bubble automatically closes after four seconds.

The popup is non-focusable and has no full-screen scrim or touch-catching container. Opening it must not disable the Qishui switch, navigation, scrolling, or other page controls. When the owning view detaches, pending dismissal work is cancelled and the popup is dismissed to avoid retaining its Activity.

## Qishui Integration

Place the information button directly after the `qishui_music_title` text, followed by flexible horizontal space and the existing switch. The bubble text is:

```text
仅在20.7.0版本测试，其余版本可能不可用
```

Add equivalent Taiwan Traditional Chinese and English resources. Keep all visible strings in resources.

## Testing

Add a focused component contract test for the four-second timeout, toggle-to-dismiss behavior, non-focusable popup requirement, and detach cleanup. Extend locale resource coverage for the new message and accessibility description. Run unit tests, Debug build, lint, and Release build, then install the Debug APK to Android user 0 without clearing data.
