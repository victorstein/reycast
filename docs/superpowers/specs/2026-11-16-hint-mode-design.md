# ReyCast hint mode — design

Date: 2026-11-16. Status: approved.

## Goal

Homerow-style keyboard clicking on the Galaxy Tab S8 Ultra (DeX, One UI 8 / Android 16): a hotkey
shows short letter labels over every clickable element on screen; typing a label clicks that
element. It is a second mode of ReyCast, reusing its accessibility service, overlay window type and
broadcast summon, so DeX's 5-app cap and overlay force-hiding do not apply
(see the ReyCast design spec, `2026-11-15-reycast-design.md`).

## Scope

In v1:
- Hint labels over clickable, long-clickable and editable elements in every on-screen window
  (apps, DeX taskbar, status bar).
- Typing a label clicks; Shift + the label's last letter long-presses.
- Editable targets also receive input focus.

Not in v1: search by element text, scroll mode, external-monitor display, labels inside content
that is not in the accessibility tree (Termux terminal area, games, canvas-drawn UIs).

## Hotkey

On the Corne (ZMK, `victorstein/zmk-config-corne`), holding layers 1 and 2 activates the `NVIM`
conditional layer; its `f` position is `&kp LA(TILDE)`, which sends **Left Alt + Left Shift +
Grave**.

`~/Documents/gen_evdev.py` gains one map: an evdev trigger on Left Alt + Left Shift + Grave
(modifiers `DO_NOT_CONSUME`, same as the existing maps), no app constraints, action INTENT
broadcast `#Intent;action=dev.victorstein.reycast.HINTS;component=dev.victorstein.reycast/.ToggleReceiver;end`
targeting BROADCAST_RECEIVER. The regenerated `mac-shortcuts.json` is imported in Key Mapper with
Restore → Replace.

Known cost: Key Mapper consumes this combo in every app, Termux → ssh → herdr included.

## Architecture

| Unit | Responsibility | Depends on |
|------|----------------|------------|
| `ToggleReceiver` (changed) | Routes `…TOGGLE` to the launcher and `…HINTS` to hint mode. | `ReyCastOverlay` |
| `ReyCastOverlay` (changed) | Owns at most one open session of either kind; summoning hint mode while it is open closes it. Hint mode requires the accessibility service, otherwise a toast. | `HintSession`, `HintTargets` |
| `HintTargets` | Walks every window's node tree via the service and returns the targets (node, screen rect, kind). | Android accessibility APIs |
| `HintLabels` | Pure: target count → labels. | nothing |
| `HintFilter` | Pure: labels + typed prefix → visible labels, or the full match. | nothing |
| `HintSession` | One open hint overlay: badges, key handling, closing, acting on the match. | `HintLabels`, `HintFilter`, the service |

`HintLabels` and `HintFilter` are plain Kotlin with unit tests on the Mac.

### Accessibility service config

`reycast_accessibility_service.xml` adds `android:canRetrieveWindowContent="true"`,
`android:canPerformGestures="true"` and `android:accessibilityFlags="flagRetrieveInteractiveWindows"`.
Event types stay minimal; the service reads windows on demand, it does not listen to events.

### Collecting targets

For each window from `service.windows`, skipping windows of type `TYPE_ACCESSIBILITY_OVERLAY`
(ReyCast's own and other services'):
- Walk the root's tree. A node is a target when it is visible to the user, enabled, and clickable,
  long-clickable or editable.
- Its rect is `getBoundsInScreen` intersected with the window's screen bounds; empty rects are dropped.
- Targets with identical rects are deduplicated, keeping the first.

Order: windows in the order the service returns them, nodes in tree order, so labels are stable for
a given screen.

### Labels

Alphabet `sadfjklewcmpgh` (home row first). All labels have the same length L, the smallest with
14^L ≥ target count (1 letter up to 14 targets, 2 up to 196, 3 beyond). Equal length makes the set
prefix-free, so a full match acts immediately. Labels are assigned in target order.

### Overlay

- A full-screen `TYPE_ACCESSIBILITY_OVERLAY` window created from the service context, transparent,
  focusable, `FLAG_LAYOUT_IN_SCREEN` with `fitInsetsTypes = 0` so view coordinates equal screen
  coordinates.
- One badge (small high-contrast rounded label) per target at the target rect's top-left, clamped to
  stay on screen.
- Keys (key-down, handled in the root's `dispatchKeyEvent` like the launcher):
  - A letter from the alphabet extends the typed prefix. Badges that do not start with it hide; the
    typed part of the remaining badges dims. A letter that matches no label is ignored.
  - Shift held with the letter that completes a label → long-press.
  - Backspace removes the last typed letter.
  - Esc closes. Modifier keys alone (the summon chord's release) are ignored.
- Also closes on: summoning hint mode again, focus loss, or a click on the overlay.

### Acting

On a full match the overlay closes first, then:
- Click: `performAction(ACTION_CLICK)`; long-press: `ACTION_LONG_CLICK`.
- Editable target: additionally `ACTION_FOCUS` so typing goes to it.
- If the action returns false (or the node went stale), `dispatchGesture` a tap at the rect's centre
  (long-press: a 600 ms stroke).
- If the gesture is also rejected, a toast says the element could not be clicked.

### Errors and edge cases

- Service off → toast asking to enable ReyCast's accessibility service; nothing opens.
- No targets → toast "Nothing clickable on screen"; nothing opens.
- The launcher panel open when hint mode is summoned → the panel closes and hint mode opens (one
  session at a time).
- Collection runs on the main thread when summoned; its duration is logged like the launcher's
  first frame. If it proves slow on heavy screens, move it off the main thread.

## Install

`scripts/install.sh` is unchanged; it re-enables the service after the update if Android dropped
it. Because the service now reads window content, Android may show its "can view and control
screen" warning when the service is enabled.

## Testing

Unit (Mac): `HintLabels` (counts 0, 1, 14, 15, 196, 197; prefix-free; alphabet order) and
`HintFilter` (prefix narrowing, no-match ignored, full match, backspace).

On the tablet (verify with `screencap`, never `uiautomator dump`, which suspends accessibility
services):
- Labels appear and click correctly in Chrome (page links and toolbar), Settings, Slack and the DeX
  taskbar.
- Shift long-press opens a context menu (e.g. a Chrome link).
- An editable field receives focus and typed text.
- With 5 apps open on the desk, hint mode still shows and clicks.
- Combo again and Esc close the overlay; nothing is clicked.
