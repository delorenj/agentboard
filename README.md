# agentboard

Agentboard is a purpose-built Android input method editor (IME) for operating
terminal sessions and Zellij workflows from a phone. It is a voice-first
control surface with stacked tab and pane joysticks, direct agent launchers,
context-aware session continuation, and essential terminal controls.

Navigation and shell launch commands are emitted as physical Android key
events. Dictated text is inserted through the active input connection.

## Gesture controls

Two full-width joystick zones remain visible in both compact and expanded deck
states:

| Zone | Gesture | Emitted keys | Zellij action |
| --- | --- | --- | --- |
| `TABS` (top) | Swipe left | `Ctrl+Shift+,` | Previous tab |
| `TABS` (top) | Swipe right | `Ctrl+Shift+.` | Next tab |
| `PANES` (bottom) | Swipe up | `Ctrl+Shift+Up` | Focus pane above |
| `PANES` (bottom) | Swipe down | `Ctrl+Shift+Down` | Focus pane below |
| `PANES` (bottom) | Swipe left | `Ctrl+Shift+Left` | Focus left, or cross to the left tab at an edge |
| `PANES` (bottom) | Swipe right | `Ctrl+Shift+Right` | Focus right, or cross to the right tab at an edge |

Tabs ignores vertical swipes. Both zones ignore taps and holds, preventing
accidental destructive actions. Cancelled or ambiguous gestures emit nothing.

The pane-edge behavior comes from Zellij 0.44's `MoveFocusOrTab` action. The
active `~/.config/zellij/config.kdl` must include these bindings in a
`shared_among "normal" "locked"` block:

```kdl
bind "Ctrl Shift ," { GoToPreviousTab; }
bind "Ctrl Shift ." { GoToNextTab; }
bind "Ctrl Shift left" { MoveFocusOrTab "left"; }
bind "Ctrl Shift down" { MoveFocus "down"; }
bind "Ctrl Shift up" { MoveFocus "up"; }
bind "Ctrl Shift right" { MoveFocusOrTab "right"; }
```

## Voice and command deck

`Mic` is the primary text-input action. On first use, Agentboard opens a small
Android permission bridge. Granting microphone access resumes listening
automatically; after a denial, `Mic` can reopen the prompt. Speech recognition
runs only while requested and inserts the best transcription into the current
field. Tap `Mic` while listening to cancel.

`Continue` emits the shell-safe executable name `agent-continue` as physical
key events and then emits Enter. Agentboard does not try to read the host PWD
or `.lastagent` from Android. The host-side helper resolves the terminal's
strict current directory, reads its marker, and resumes the recorded CLI:

| Marker | Resume invocation |
| --- | --- |
| `claude` | `claude -c` |
| `codex` | `codex resume --last` |
| `kimi` | `kimi -c` |
| `agy` or `gemini` | `gemini -c` |
| `hermes` | `hermes -c` |

The expanded deck exposes `Claude`, `Gemini`, `Codex`, `Hermes`, and `Kimi`
launch buttons plus `Esc`, `Ctrl`, `Alt`, `Tab`, Backspace, and Enter. The
collapse control hides those secondary rows but keeps both joysticks, `Mic`,
and `Continue` available.

`Ctrl` and `Alt` are one-shot controls. Tap either to arm it and tap again to
disarm. The next emitted terminal key or joystick shortcut merges the armed
modifiers, then clears both. Cancelled or ignored input preserves them.
Starting or finishing an input view resets all one-shot state.

## Development lifecycle

Mise tasks are the supported operator entrypoints for development, build,
test, and device deployment. Install
[mise](https://mise.jdx.dev/getting-started.html), provide an Android SDK with
platform 36 through `ANDROID_HOME` or `ANDROID_SDK_ROOT`, then bootstrap:

```bash
mise run setup
```

Mise installs and activates the pinned Java 17 toolchain. Use `mise tasks` to
discover the complete task interface. Primary local workflows are:

```bash
mise run dev
mise run test
mise run lint
mise run check
mise run clean
mise run build
```

- `dev` creates a fast debug APK at the current version without changing it.
- `test` and `lint` are independently runnable and never mutate the version.
- `check` composes the complete non-mutating test and lint quality gate.
- `clean` removes generated build output.
- `build` bumps the patch version once, reruns the quality gate, and creates
  `app/build/outputs/apk/debug/app-debug.apk`.

The current Android configuration uses `minSdk 23`, `targetSdk 36`, application
ID `com.zellij.keyboard`, and IME service
`com.zellij.keyboard.ZellijKeyboardService`.

## Versioning

`VERSION` is the authoritative semantic version manifest. Gradle derives
`versionName` directly from it and encodes its components as
`major*1,000,000 + minor*1,000 + patch` for Android's monotonic `versionCode`.

Use the mise version tasks:

```bash
mise run version
mise run version:check
mise run version:bump-patch
mise run version:bump-minor
mise run version:bump-major
mise run version:sync
```

Local builds deliberately do not track or create Git tags.

## Install and select the IME

With exactly one authorized Android device attached, run:

```bash
mise run deploy:debug
```

The task sequences a versioned build, APK replacement, IME enablement, and
active-IME selection. As a manual alternative, install the APK, enable
**Agentboard** in Android's on-screen keyboard settings, open a text field, use
the system input-method picker, and select **Agentboard**.

## Architecture

`ZellijKeyboardService` extends Android's `InputMethodService` and owns the IME
lifecycle, speech recognizer, state, and active input connection. Navigation,
terminal, and shell-launch output uses paired physical `KeyEvent` objects
through `sendKeyEvent`. Voice recognition is the intentional exception: its
transcription uses `commitText` because it is already multi-character text.

The UI uses purpose-built Android framework Views: `LinearLayout` and `Button`
for the command deck, plus a custom `View` for each joystick zone. Framework
Views keep IME startup direct and expose touch and accessibility APIs without
an interop boundary. Core gesture, command, state, layout, and key-event
planning remains immutable Kotlin so JVM tests can run without an Android
device.

- `ZellijKeyboardService.kt` owns lifecycle, recognition, and output.
- `TerminalKeyboardView.kt` builds the compact/expanded command deck.
- `GesturePadView.kt` handles touch classification and accessibility actions.
- `MicrophonePermissionActivity.kt` requests runtime audio permission.
- `core/` contains platform-independent gestures, state, layouts, and plans.
- `input/` converts logical commands into Android key-event plans.
- `app/src/test/` contains JVM regression tests.

The QWERTY/symbol planner remains in core for now but is no longer rendered by
the IME. This keeps the refactor reversible while the voice-first deck is
validated on-device.

## Validation status

Local validation covers pure Kotlin behavior, Android compilation, lint, and
APK construction. The current gate runs 52 JVM tests with zero failures and
Android lint with zero errors; the only lint warning is the intentionally
pinned Gradle 8.14.3 versus available 8.14.5. Device installation, IME
selection, physical event delivery, xterm.js behavior, speech-provider
behavior, runtime permission UX, real Zellij integration, TalkBack, and
portrait/landscape ergonomics remain device-only seams until explicitly tested.

## Security and privacy

Android grants an enabled IME privileged access to user input. Treat any
keyboard APK as highly trusted software: inspect and build it yourself, and do
not enable an APK from an untrusted source.

Agentboard requests `RECORD_AUDIO` for the explicit `Mic` action but requests
no network permission. The installed Android speech-recognition provider
controls any network use and retention associated with transcription.
Agentboard itself does not store audio or dictated text.
