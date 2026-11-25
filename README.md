# ReyCast

A keyboard-first companion for Android tablets used with a physical keyboard, built for Samsung DeX.
It started as a Raycast-style app launcher and grew a few more things that make a tablet feel like a
desktop:

- **Launcher**: summon a search box, type a few letters, press Enter.
- **Hint mode**: put a letter label on everything clickable on screen and click it by typing the label.
- **Scroll mode**: scroll any app with `j`/`k`, `d`/`u` and `gg`/`G`.
- **Links from another computer**: when you work on a remote machine over ssh, its `open <url>` can open
  the page in the tablet's browser, including OAuth logins that call back to `localhost`.
- **Notices**: that remote machine can pop a banner over every app, for example when a long task
  finishes.

Everything runs as an accessibility overlay, so it works over any app and DeX does not hide it. There
is no settings screen: shortcuts come from a key remapper, such as
[Key Mapper](https://github.com/keymapperorg/KeyMapper), sending broadcasts.

## Requirements

- Android 16 (API 36). Developed on a Galaxy Tab S8 Ultra with One UI 8, in DeX.
- A physical keyboard.
- `adb` access to the tablet (USB or wireless debugging). No root needed.
- To build: JDK 21 and the Android SDK.
- For links and notices: [Tailscale](https://tailscale.com) on the tablet and on the sending machine.

## Build and install

```sh
scripts/install.sh
```

It builds the debug APK, installs it over adb and then:

- grants "Display over other apps" (`appops set ... SYSTEM_ALERT_WINDOW allow`);
- enables the ReyCast accessibility service, keeping any services already enabled;
- creates a shared secret at `~/.config/reycast/link-token` (if missing) and pushes it to the tablet for
  the link server.

With several devices attached, set `ANDROID_SERIAL` first. `./gw` is a wrapper around `./gradlew` that
points at Homebrew's JDK 21 and the default SDK path on macOS; run the tests with
`./gw testDebugUnitTest`.

## Triggering it

Each feature is a broadcast to `dev.victorstein.reycast/.ToggleReceiver`:

| Action | Opens |
|---|---|
| `dev.victorstein.reycast.TOGGLE` | the launcher |
| `dev.victorstein.reycast.HINTS` | hint mode |
| `dev.victorstein.reycast.SCROLL` | scroll mode |

Sending the same broadcast again closes it. To try one from a computer:

```sh
adb shell am broadcast -a dev.victorstein.reycast.TOGGLE -n dev.victorstein.reycast/.ToggleReceiver
```

In Key Mapper, map a key to an **Intent** action with target **Broadcast receiver**, the action above,
and the component `dev.victorstein.reycast/.ToggleReceiver`. A broadcast (rather than launching an
activity) matters in DeX, which caps how many apps a desktop keeps open.

## Launcher

Fuzzy search over installed apps, ranked by how often and how recently you open them.

| Key | Does |
|---|---|
| typing | filters |
| `↑` `↓` or `Ctrl+K` `Ctrl+J` | move the selection |
| `Enter` | opens the app |
| `Esc` | closes |

## Hint mode

Yellow letter badges appear on every clickable element in every visible window (parts of windows hidden
behind others are skipped).

| Key | Does |
|---|---|
| a label's letters | clicks that element |
| `Shift` on the last letter | long-presses it |
| `Backspace` | undoes a letter |
| `Esc` | closes |

## Scroll mode

Scroll mode starts on the app you are in and outlines the area it will scroll. Scrolling is an injected
swipe that stops before lifting, so the page moves the same distance on every key press with no fling.

| Key | Does |
|---|---|
| `j` `k` or `↓` `↑` | scroll down / up a step (hold to repeat) |
| `d` `u`, `Page Down` `Page Up`, `Space` | half a page |
| `gg` `G` | top / bottom |
| `h` `l` or `←` `→` | sideways |
| `Tab` `Shift+Tab` | next / previous scrollable area |
| `Esc`, `Enter` or `q` | leave |

Keys pressed with `Alt`, `Ctrl` or `Cmd` go to the system, so app switching keeps working.

In a terminal app such as Termux, what a swipe does depends on the program: at a shell prompt it scrolls
the terminal's history, while full-screen programs (vim, tmux and the like) usually receive it as arrow
keys or mouse-wheel events.

## Links and notices from another machine

The accessibility service runs a small server on port **47011**. It accepts connections only from
Tailscale addresses (`100.64.0.0/10`) and only with the shared secret `scripts/install.sh` set up. Each
connection sends one line and gets `ok` or `no` back.

**Open a link** in the tablet's default browser (http and https only):

```sh
printf '%s %s\n' "$(cat ~/.config/reycast/link-token)" "https://example.com" | nc <tablet-tailscale-ip> 47011
```

If the link is an OAuth authorization whose `redirect_uri` is `http://localhost:PORT/...` or
`http://127.0.0.1:PORT/...`, the tablet relays its own `localhost:PORT` to the sender for 5 minutes, so
the login's callback reaches the program on the sending machine that started it. That program's port
must be reachable from the tablet over Tailscale during that time.

**Show a notice**, a banner at the top of the screen over every app:

```sh
printf '%s notify %s\n' "$(cat ~/.config/reycast/link-token)" "Build finished" | nc <tablet-tailscale-ip> 47011
```

The banner drops in with a spinning gradient border and a short bubble sound (played at media volume),
stays for 6 seconds and never takes keyboard focus. Tapping it opens Termux. A good use is a Claude Code
`Stop` or `Notification` hook on the machine you ssh into, so you hear when it finishes or needs you.

## Security notes

- The broadcast receiver is exported without a permission, so any app installed on the tablet can open
  hint or scroll mode and turn your next keystroke into a click or a scroll. Fine on a personal device;
  if you install apps you don't trust, add a per-install token to the broadcasts.
- The link server trusts anyone on your tailnet who holds the token. During an OAuth relay's 5 minutes,
  the callback port is reachable from the sending machine's tailnet side and from other apps on the
  tablet.

## Layout

- `app/src/main/java/dev/victorstein/reycast/core/`: plain Kotlin logic (matching, ranking, key handling,
  request parsing), covered by unit tests in `app/src/test/`.
- `app/src/main/java/dev/victorstein/reycast/android/`: Android-facing pieces (app catalog, accessibility
  tree walks, link server).
- `app/src/main/java/dev/victorstein/reycast/`: the overlay sessions and the accessibility service.
- `docs/superpowers/`: design notes and plans written while building it.
