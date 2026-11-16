# ReyCast hint mode — design

Date: 2026-11-16. Status: approved, revised after adversarial review.

## Goal

Homerow-style keyboard clicking on the Galaxy Tab S8 Ultra (DeX, One UI 8 / Android 16): a hotkey
shows short letter labels over every clickable element on screen; typing a label clicks that
element. It is a second mode of ReyCast, reusing its accessibility service, overlay window type and
broadcast summon, so DeX's 5-app cap and overlay force-hiding do not apply
(see the ReyCast design spec, `2026-11-15-reycast-design.md`).

## Scope

In v1:
- Hint labels over clickable, long-clickable and editable elements that the user can see, in every
  on-screen window (apps, DeX taskbar, status bar).
- Typing a label clicks; holding Shift on the label's final letter long-presses.
- Editable targets also receive input focus.

Not in v1: search by element text, scroll mode, external-monitor display.

Known limitations (platform, not fixed by v1):
- Content not in the accessibility tree gets no labels: Termux's terminal area, games, canvas UIs.
- Android 16 hides views marked `accessibilityDataSensitive` — which includes every view with
  `filterTouchesWhenObscured` — from services that are not accessibility tools. ReyCast keeps
  `isAccessibilityTool="false"` (Play Protect blocks non-tools that claim it), so permission dialogs,
  install confirmations and some banking/payment buttons get no labels.

## Step 0: device spike (done 2026-11-16, branch `spike/hint-mode`)

Run on the tablet (tablet mode, not DeX) with the new service config:
1. **Chrome page content: yes.** A Wikipedia page exposed its links (`android.view.View`, clickable,
   link text in `contentDescription`) alongside the toolbar. Footnote links appear twice with
   identical rects; the identical-rect dedupe covers them.
2. **Occlusion: not tested** (no overlapping windows outside DeX); covered by the device tests.
   While Chrome's overflow menu was open, `getWindows()` listed only the popup, not Chrome's main
   window.
3. **Menus vs focus: they survive.** With the overflow menu open, a focusable full-screen
   `TYPE_ACCESSIBILITY_OVERLAY` took focus and the popup window was still listed seconds later. The
   focusable overlay design stays; no `onKeyEvent` path.

Timing: warm walks of a 1,560-node Chrome page took 33–57 ms; the first walk after a page load or
reinstall took 0.9–1.5 s while Chrome built its accessibility tree. The taskbar and Termux took
~90 ms. Skipping subtrees outside the window bounds did not reduce the node count.

## Device verification (2026-11-16, DeX on, tablet screen)

All via adb plus one real-keyboard run by the user; no crashes in `logcat -b crash`.
- Real Corne combo (layer1+layer2+f) shows hints and the user could click with them.
- Settings: 28 targets, shown +258 ms; label click opened the right page; Esc and summon-again close.
- Chrome page (DeX, floating over Termux): 49–53 targets, shown +322–343 ms; a link label opened
  the link. Nothing in the covered part of Termux was labelled; the visible strip of a window below
  another was.
- Chrome overflow menu: stays open under the overlay, items labelled, a label opened History.
- Editable field (Chrome history search): label focused it and typed text landed in it.
- Shift on the final letter: opened Chrome's link context menu (long-press).
- Airplane mode switch is labelled on its own page; its row in the Connections list is not
  (that row is not exposed as clickable) — accepted.
- Not run: the 5-apps-per-desk cap, and the combo while Termux is focused.

Observed for later: badges at a link's top-left cover the first letters of inline link text.

## Hotkey

On the Corne (ZMK, `victorstein/zmk-config-corne`, used only with the tablet), holding layers 1 and
2 activates the `NVIM` conditional layer; its `f` position is `&kp LA(TILDE)`, which sends
**Left Alt + Left Shift + Grave**.

`~/Documents/gen_evdev.py` gains one map: an evdev trigger on Left Alt + Left Shift + Grave
(modifiers `DO_NOT_CONSUME`, same as the existing maps), no app constraints, action INTENT
broadcast `#Intent;action=dev.victorstein.reycast.HINTS;component=dev.victorstein.reycast/.ToggleReceiver;end`
targeting BROADCAST_RECEIVER. The regenerated `mac-shortcuts.json` is imported in Key Mapper with
Restore → Replace.

## Architecture

| Unit | Responsibility | Depends on |
|------|----------------|------------|
| `ToggleReceiver` (changed) | Routes `…TOGGLE` to the launcher and `…HINTS` to hint mode. The manifest intent-filter lists both actions. | `ReyCastOverlay` |
| `ReyCastOverlay` (changed) | Owns at most one open session of either kind (a small `Session { close() }` interface). Summoning the open kind closes it; summoning the other kind closes the open one and opens the new one. Hint mode requires the accessibility service, otherwise a toast. | `HintSession`, `HintTargets` |
| `HintTargets` | Walks every window's node tree via the service and returns the visible targets (node, screen rect, kind). | Android accessibility APIs |
| `HintLabels` | Pure: target count → labels. | nothing |
| `HintFilter` | Pure: labels + typed prefix → visible labels, or the full match. | nothing |
| `HintSession` | One open hint overlay: badges, key handling, closing, acting on the match. | `HintLabels`, `HintFilter`, the service |

`HintLabels` and `HintFilter` are plain Kotlin with unit tests on the Mac.

### Accessibility service config

`reycast_accessibility_service.xml` adds `android:canRetrieveWindowContent="true"`,
`android:canPerformGestures="true"` and `android:accessibilityFlags="flagRetrieveInteractiveWindows"`.
Event types stay minimal; the service reads windows on demand, it does not listen to events.

### Collecting targets

Runs on ReyCast's background executor (each `getChild` is a cross-process call; a heavy Chrome page
must not freeze the UI), then the overlay opens on the main thread.

- `service.windows` is ordered top-most first. Walk the windows in that order, keeping a running
  `Region` of the screen bounds of windows already walked. Windows of type
  `TYPE_ACCESSIBILITY_OVERLAY` are skipped and do not add to the region.
- In each window, walk the tree. A node is a target when it is visible to the user, enabled, and
  clickable, long-clickable or editable.
- Its rect is `getBoundsInScreen` intersected with the window's screen bounds. It is dropped when the
  rect is empty or its centre lies in the covered region (a window above hides it).
- Targets with identical rects are deduplicated, keeping the deepest node (the outer one is usually
  a ripple wrapper).

Order: window order, then tree order, so labels are stable for a given screen.

### Labels

Alphabet `sadfjklewcmpgh` (home row first, 14 letters). All labels have the same length L, the
smallest with 14^L ≥ target count. Equal length makes the set prefix-free, so a full match acts
immediately. Labels are assigned in target order.

### Overlay

- A full-screen `TYPE_ACCESSIBILITY_OVERLAY` window created from the service context, transparent,
  `FLAG_LAYOUT_IN_SCREEN | FLAG_NOT_TOUCHABLE`, `fitInsetsTypes = 0`. Not touchable, so taps — the
  user's and the fallback gesture — always reach the app beneath.
- Badge positions are target rects minus the root view's `getLocationOnScreen()`, clamped on screen.
  One small high-contrast rounded badge per target at the rect's top-left; when that point is
  within one badge size of an already placed badge, the badge goes at the rect's centre instead.
- Keys (key-down, handled in the root's `dispatchKeyEvent` like the launcher). Letters are matched
  by `keyCode` (A–Z), not by Unicode character, because the summon chord's Alt may still be held:
  - A letter from the alphabet extends the typed prefix. Badges that do not start with it hide; the
    typed part of the remaining badges dims. A letter that matches no label is ignored.
  - Shift held on the letter that completes a label → long-press. Shift on earlier letters is
    ignored.
  - Backspace removes the last typed letter.
  - Esc closes. Modifier-only keys (including the chord's release) are ignored.
- Closes on: Esc, summoning hint mode again, summoning the launcher, or focus loss (a click on the
  app beneath moves focus away).

### Acting

On a full match the overlay is removed first, then:
- `node.refresh()`; if it returns false the node is gone and the screen changed, so a toast says
  the element is no longer there. No blind tap.
- Click: `performAction(ACTION_CLICK)`; long-press: `ACTION_LONG_CLICK`.
- Editable target: additionally `ACTION_FOCUS` so typing goes to it.
- If the action returns false on a live node, `dispatchGesture` a tap at the rect's centre
  (long-press: a 600 ms stroke).
- If the gesture is also rejected, a toast says the element could not be clicked.

### Errors and edge cases

- Service off → toast asking to enable ReyCast's accessibility service; nothing opens.
- No targets → toast "Nothing clickable on screen"; nothing opens.
- Collection duration and first frame are logged like the launcher's first frame. The first
  summon on a freshly loaded Chrome page can take ~1 s (Chrome builds its tree lazily); accepted for v1.

## Install

`scripts/install.sh` is unchanged; it re-enables the service after the update if Android dropped
it. Enabling via `settings put` shows no consent dialog, but because the service now reads window
content, Android may post its periodic "can view and control your screen" notification for a
non-tool service.

## Testing

Unit (Mac): `HintLabels` (counts 0, 1, 14, 15, 196, 197; prefix-free; alphabet order) and
`HintFilter` (prefix narrowing, no-match ignored, full match, backspace).

On the tablet (verify with `screencap`, never `uiautomator dump`, which suspends accessibility
services):
- Labels appear and click correctly in Chrome (page links and toolbar), Settings, Slack and the DeX
  taskbar.
- Two overlapping DeX windows: no labels on the covered parts of the lower window.
- An open menu keeps its labels and a labelled menu item clicks.
- Shift long-press opens a context menu (e.g. a Chrome link).
- An editable field receives focus and typed text.
- A list row with an icon: both badges readable.
- With 5 apps open on the desk, hint mode still shows and clicks.
- Combo again and Esc close the overlay; nothing is clicked.
