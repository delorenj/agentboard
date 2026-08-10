# agentboard

Agentboard is a purpose-built Android input method editor (IME) for operating
terminal sessions and Zellij workflows from a phone. It is a voice-first,
button-free control surface made from two full-width gesture zones: tabs on
top and panes on the bottom.

Navigation is sent as authenticated semantic actions to the host-side
`zellij-driver` bridge. Shell continuation still uses physical Android key
events, while dictated text is inserted through the active input connection.

## Gesture controls

The two equal-height gesture zones are always present, with or without the
Android keyboard picker or other system UI visible:

| Zone | Gesture | Bridge action | Zellij action |
| --- | --- | --- | --- |
| `TABS` (top) | Swipe left | `tab-previous` | Previous tab |
| `TABS` (top) | Swipe right | `tab-next` | Next tab |
| `TABS` (top) | Tap | None | Start or cancel microphone dictation |
| `TABS` (top) | Hold | `agent-continue`, then `Enter` | Resume the last agent |
| `PANES` (bottom) | Swipe up | `pane-up` | Focus pane above |
| `PANES` (bottom) | Swipe down | `pane-down` | Focus pane below |
| `PANES` (bottom) | Swipe left | `pane-left` | Focus left, or cross to the left tab at an edge |
| `PANES` (bottom) | Swipe right | `pane-right` | Focus right, or cross to the right tab at an edge |

The top zone ignores vertical swipes. The bottom zone ignores taps and holds.
Cancelled or ambiguous gestures emit nothing.

The IME does not guess which pane is at an edge. The bridge invokes Zellij's
native `move-focus-or-tab` action for horizontal pane swipes and `move-focus`
for vertical pane swipes. No custom Zellij keybindings are required.

## Voice and continuation

Tap the top zone to use the microphone as the primary text-input action. On
first use, Agentboard opens a small
Android permission bridge. Granting microphone access resumes listening
automatically; after a denial, another tap can reopen the prompt. Speech
recognition runs only while requested and inserts the best transcription into
the current field. Tap the top zone while listening to cancel.

Holding the top zone emits the shell-safe executable name `agent-continue` as
physical key events and then emits Enter. Agentboard does not try to read the
host PWD or `.lastagent` from Android. The host-side helper resolves the
terminal's strict current directory, reads its marker, and resumes the recorded
CLI:

| Marker | Resume invocation |
| --- | --- |
| `claude` | `claude -c` |
| `codex` | `codex resume --last` |
| `kimi` | `kimi -c` |
| `agy` or `gemini` | `gemini -c` |
| `hermes` | `hermes -c` |

The companion bridge runs beside Zellij Web and translates the six allowlisted
HTTP actions into Zellij's supported CLI control API. Agentboard calls
`https://z.delo.sh/agentboard/v1/action` on a single background executor so
rapid gestures stay ordered. The bridge accepts no arbitrary command or text
payload.

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

Navigation builds require the bearer credential at build time. It is embedded
in the local APK but is never committed to source:

```bash
ZELLIJ_DRIVER_TOKEN="..." mise run dev
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
lifecycle, speech recognizer, state, and active input connection. Navigation
uses the HTTPS Zellij bridge. Shell continuation uses paired physical
`KeyEvent` objects through `sendKeyEvent`. Voice transcription uses
`commitText` because it is already multi-character text.

The UI uses a `LinearLayout` containing two flat custom Android `View` gesture
zones. It renders no buttons, cards, joystick shapes, directional controls, or
QWERTY rows—only quiet `TABS` and `PANES` labels plus transient status text.
Framework Views keep IME startup direct and expose touch and accessibility APIs
without an interop boundary.
Core gesture and key-event planning remains immutable Kotlin so JVM tests can
run without an Android device.

- `ZellijKeyboardService.kt` owns lifecycle, recognition, and output.
- `TerminalKeyboardView.kt` builds the two equal gesture zones.
- `GesturePadView.kt` handles touch classification and accessibility actions.
- `MicrophonePermissionActivity.kt` requests runtime audio permission.
- `core/` contains platform-independent gestures, state, layouts, and plans.
- `input/ZellijBridgeClient.kt` performs authenticated navigation requests.
- The remaining `input/` code converts continuation commands into key events.
- `app/src/test/` contains JVM regression tests.

The older QWERTY/symbol and agent-launch planning models remain in core for now
but are no longer rendered by the IME. This keeps the refactor reversible while
the gesture-only surface is validated on-device.

## Validation status

Local validation covers pure Kotlin behavior, Android compilation, lint, and
APK construction. The current gate runs 66 JVM tests with zero failures and
Android lint with zero errors; the only lint warning is the intentionally
pinned Gradle 8.14.3 versus available 8.14.5. The bridge has also been tested
through the public Cloudflare and Traefik path against the real `Workspace`
session.

On-device validation on a Galaxy S24 Ultra covers APK replacement, IME
enablement and selection, input-view rendering, tab swipes, four-direction pane
swipes, both horizontal edge crossings, and physical-key continuation output.
Speech-provider behavior, runtime permission UX, TalkBack, and a focused
portrait/landscape ergonomics pass remain device-only seams.

## Security and privacy

Android grants an enabled IME privileged access to user input. Treat any
keyboard APK as highly trusted software: inspect and build it yourself, and do
not enable an APK from an untrusted source.

Agentboard requests `RECORD_AUDIO` for the explicit microphone action and
`INTERNET` for the Zellij bridge. The bridge credential is compiled into local
APK output, so APKs must not be distributed. The installed Android
speech-recognition provider controls any network use and retention associated
with transcription. Agentboard itself does not store audio or dictated text.
