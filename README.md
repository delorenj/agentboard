# agentboard

agentboard is a purpose-built Android input method editor (IME) for operating
terminal sessions and Zellij workflows from a phone. It provides a complete
QWERTY and symbols keyboard, one-shot modifiers, and two gesture pads that emit
physical Android key events. The receiving terminal and Zellij configuration
determine what those events do.

## Gesture controls

The `FOCUS` and `TABS` pads emit the following exact key combinations:

| Pad | Gesture | Emitted keys |
| --- | --- | --- |
| `FOCUS` | Tap | `Ctrl+H` |
| `FOCUS` | Touch and hold | `Ctrl+W` |
| `FOCUS` | Swipe up | `Ctrl+Up Arrow` |
| `FOCUS` | Swipe down | `Ctrl+Down Arrow` |
| `FOCUS` | Swipe left | `Ctrl+Left Arrow` |
| `FOCUS` | Swipe right | `Ctrl+Right Arrow` |
| `TABS` | Tap | `Tab` |
| `TABS` | Touch and hold | `Ctrl+R` |
| `TABS` | Swipe up | `Ctrl+T` |
| `TABS` | Swipe down | `Ctrl+W` |
| `TABS` | Swipe left | `Ctrl+P` |
| `TABS` | Swipe right | `Ctrl+N` |

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

## Build and test

Install these prerequisites before building:

- JDK 17 or newer. The project compiles Java and Kotlin bytecode for Java 17.
- Android SDK with compile SDK 36 installed.
- An Android SDK path configured for Gradle, typically through `ANDROID_HOME`,
  `ANDROID_SDK_ROOT`, or `local.properties`.

From the repository root, run the complete local validation:

```bash
./gradlew testDebugUnitTest assembleDebug lintDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The current Android configuration uses `minSdk 23`, `targetSdk 36`, application
ID `com.zellij.keyboard`, and IME service
`com.zellij.keyboard.ZellijKeyboardService`.

## Install and select the IME

The following commands install the debug APK, enable the actual service
component, and select it as the active IME. Run them only from a host with
Android platform tools and an authorized device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell ime enable com.zellij.keyboard/.ZellijKeyboardService
adb shell ime set com.zellij.keyboard/.ZellijKeyboardService
```

As a manual alternative, install the APK, open the device's keyboard settings,
enable **Zellij Keyboard** under the on-screen or managed keyboards list, open a
text field, invoke the system input-method picker, and select **Zellij
Keyboard**. Android vendors label and locate these settings differently.

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

- `./gradlew testDebugUnitTest assembleDebug lintDebug` succeeds.
- Six JVM suites run 42 tests with zero failures or errors.
- The debug APK is produced at the documented path.
- Android lint reports 0 errors and 3 narrow warnings: `languageTag` requires
  API 24 while `minSdk` is 23, Gradle 8.14.3 has an 8.14.5 update available,
  and the application doesn't define an icon.

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
