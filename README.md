# agentboard

agentboard is a purpose-built Android input method editor (IME) for operating
terminal sessions and Zellij workflows from a phone. It provides a complete
QWERTY and symbols keyboard, one-shot modifiers, and two gesture pads that emit
physical Android key events. The receiving terminal and Zellij configuration
determine what those events do.

## Gesture controls

The `FOCUS` and `TABS` pads emit the following exact key combinations:

| Pad | Gesture | Emitted keys | Intended Zellij action |
| --- | --- | --- | --- |
| `FOCUS` | Tap | `Ctrl+H` | Toggle pane fullscreen |
| `FOCUS` | Touch and hold | `Ctrl+W` | Close pane |
| `FOCUS` | Swipe up | `Ctrl+Up Arrow` | Focus pane above |
| `FOCUS` | Swipe down | `Ctrl+Down Arrow` | Focus pane below |
| `FOCUS` | Swipe left | `Ctrl+Left Arrow` | Focus pane left |
| `FOCUS` | Swipe right | `Ctrl+Right Arrow` | Focus pane right |
| `TABS` | Tap | `Tab` | Send Tab |
| `TABS` | Touch and hold | `Ctrl+R` | Rename tab |
| `TABS` | Swipe up | `Ctrl+T` | New tab |
| `TABS` | Swipe down | `Ctrl+W` | Close tab |
| `TABS` | Swipe left | `Ctrl+P` | Previous tab |
| `TABS` | Swipe right | `Ctrl+N` | Next tab |

Cancelled or ambiguous gestures emit nothing.

## Modifier and layer behavior

`Ctrl`, `Alt`, and `Shift` are one-shot controls:

- Tap `Ctrl` or `Alt` to arm it, and tap it again to disarm it. They arm
  independently, so you can use both on the same command.
- The next emitted key or gesture merges the armed `Ctrl` and `Alt` modifiers,
  then clears both. A cancelled action, ignored input, or missing input
  connection preserves them.
- Tap `Shift` to select the alternate character for the next character key.
  That character clears `Shift`. Named keys such as `Esc`, `Tab`, `Enter`,
  Backspace, and Space preserve it.
- Tap `#+=` to show symbols and `ABC` to return to letters. Changing layers
  preserves `Shift`, `Ctrl`, and `Alt`; it doesn't automatically return to the
  previous layer.
- Starting or finishing an input view resets the layer and all one-shot state.

## Development lifecycle

Mise tasks are the only supported operator entrypoints for development, build,
test, and device deployment. Install
[mise](https://mise.jdx.dev/getting-started.html), provide an Android SDK with
platform 36 through `ANDROID_HOME` or `ANDROID_SDK_ROOT`, then bootstrap the
repository:

```bash
mise run setup
```

Mise installs and activates the pinned Java 17 toolchain. The setup task checks
that Java and Android platform 36 are available and warms the Gradle wrapper
cache. Use `mise tasks` to discover the full task interface.

The primary local workflows are:

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
- `build` bumps the patch version once, reruns the quality gate, and creates the
  versioned debug APK at `app/build/outputs/apk/debug/app-debug.apk`.

The tasks internally drive the repository's Gradle wrapper; direct Gradle
invocations are implementation details, not supported operator procedures.

The current Android configuration uses `minSdk 23`, `targetSdk 36`, application
ID `com.zellij.keyboard`, and IME service
`com.zellij.keyboard.ZellijKeyboardService`.

## Versioning

`VERSION` is the authoritative semantic version manifest. Gradle derives
`versionName` directly from it and encodes its components as
`major*1,000,000 + minor*1,000 + patch` for a monotonic Android `versionCode`.
Minor and patch components are limited to 0–999, and the final code must fit
Android's supported positive integer range.

Use only the mise version tasks:

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

With exactly one authorized Android device attached, run the complete debug
deployment:

```bash
mise run deploy:debug
```

The deployment task strictly sequences a versioned build, APK replacement, IME
enablement, and active-IME selection. Its internal phases use Android platform
tools; direct `adb` invocations are implementation details, not supported
operator procedures.

As a manual alternative, install the APK, open the device's keyboard settings,
enable **Agentboard** under the on-screen or managed keyboards list, open a
text field, invoke the system input-method picker, and select **Agentboard**.
Android vendors label and locate these settings differently.

## Architecture

`ZellijKeyboardService` extends Android's `InputMethodService` because the
keyboard must participate in the platform IME lifecycle and send key events
through the active `InputConnection`. Every output path uses paired physical
`KeyEvent` objects through `sendKeyEvent`; the service doesn't insert text with
`commitText`.

The UI uses purpose-built Android framework Views: `LinearLayout` and `Button`
for the keyboard, plus a custom `View` for each gesture pad. Compose was not
chosen because this is a small, latency-sensitive input surface: framework
Views avoid a Compose runtime and dependency layer, keep IME startup direct,
and expose touch and accessibility APIs without an interop boundary. Core
gesture, state, layout, and key-event planning remain immutable Kotlin so JVM
tests can exercise them without an Android device.

The implementation is organized as follows:

- `app/src/main/kotlin/com/zellij/keyboard/ZellijKeyboardService.kt` owns the
  IME lifecycle and connects UI intent to the active input connection.
- `TerminalKeyboardView.kt` builds and updates the native keyboard hierarchy.
- `GesturePadView.kt` handles touch classification and accessibility actions.
- `core/` contains platform-independent gesture mappings, keyboard state,
  layouts, and command planning.
- `input/` converts logical commands into deterministic Android key-event plans
  and dispatches them through `InputConnection`.
- `app/src/main/res/` contains the IME declaration, strings, dimensions,
  colors, styles, and drawables.
- `app/src/test/` contains JVM tests for gestures, state transitions, layouts,
  event planning, and dispatch behavior.
- `app/src/main/AndroidManifest.xml` registers the exported, permission-gated
  IME service.

The original Java prototype is no longer in the working tree, but remains
available in Git history.

## Validation status

Local validation currently confirms the pure Kotlin behavior and Android build
artifacts:

- `mise run version:check`, `test`, `lint`, `check`, and `build` succeed.
- Six JVM suites run 47 tests with zero failures or errors.
- The debug APK is produced at the documented path.
- Android lint reports zero errors and one upgrade warning: Gradle 8.14.5 is
  available while the wrapper remains on 8.14.3.

No device or terminal integration claim is made. Installation, IME
enablement/selection, physical event delivery on Android, xterm.js handling,
real Zellij bindings, TalkBack behavior, and portrait/landscape layout remain
unvalidated seams.

## Security and privacy

Android grants an enabled IME privileged access to user input. Treat any
keyboard APK as highly trusted software: inspect and build it yourself, and
don't enable an APK from an untrusted source. This manifest requests no network
permission, and the current implementation emits physical key events instead
of committing or storing text, but those facts don't make a general privacy
guarantee for future builds.
