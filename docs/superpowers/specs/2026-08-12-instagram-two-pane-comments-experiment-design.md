# Instagram Two-Pane Comments Experiment Design

## Goal

Verify whether Instagram `415.0.0.36.76` can activate its existing Reels
two-pane comments layout on the unfolded Fold6 by overriding only the two
MobileConfig gates identified during static analysis.

## Scope

- Target only `com.instagram.android` and only its main process.
- Treat the implementation as a version-specific experiment, not a general
  Instagram compatibility feature.
- Do not add a OneLab UI toggle for this first validation.
- Do not modify Instagram data, device density, `Configuration`, model
  identity, Samsung split rules, or LSPosed state automatically.
- Install the resulting OneLab APK only to Android user `0`.

## Hook Design

Add an `InstagramTwoPaneCommentsHook` dispatched only for the Instagram
package. Install after `Application.attach` so Instagram's class loader is
available, and refuse secondary processes.

For Instagram `415.0.0.36.76`, locate `X.C67822lt` and its exact method:

```text
boolean B9T(X.C06500On, long)
```

Run the original method first. Replace its result with `true` only when the
long argument is one of these keys:

- `36325123995030568`: adaptive large-screen gate used on hinge devices.
- `36325123993916442`: Reels two-pane comments gate.

Every other MobileConfig query must retain its original result. A missing
class, missing exact method, signature mismatch, or callback error must fail
open and leave Instagram behavior unchanged.

## Compatibility Boundary

The long configuration keys are the semantic anchors. The class and method
names are current-version R8 names and therefore compatibility grade D. The
hook must log that it is a version-specific installation and must not search
for or hook every boolean MobileConfig method.

The experiment requires the user to enable `com.instagram.android` in the
OneLab LSPosed scope and restart Instagram. OneLab may declare Instagram in
its recommended Xposed scope, but must not edit the active LSPosed database.

## Testing

Create a pure Java policy with one responsibility: decide whether a long key
is one of the two forced gates. Unit tests must prove both keys match and
representative unrelated keys do not match before production Hook code is
written.

Build verification consists of the focused policy test, the complete unit
test suite, lint, and debug APK assembly. Runtime verification requires:

1. LSPosed log evidence that the exact hook installed in Instagram's main
   process.
2. One-time log evidence that each target key was observed.
3. UI hierarchy or screenshot evidence that Reels selected
   `CommentsTwoPaneLayout` instead of the bottom sheet.
4. Opening, closing, scrolling, back navigation, rotation, fold, and unfold
   checks without an Instagram crash.

If either key is never observed, the exact method cannot be installed, or the
two-pane layout does not appear, report the experiment as unverified or
failed rather than adding broader hooks.

## Rollback

Remove Instagram from the OneLab LSPosed scope and force-stop/reopen
Instagram. Because the experiment does not mutate Instagram configuration or
data, no data restoration is required.
