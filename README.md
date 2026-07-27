# Zellij Keyboard

A custom Android keyboard built for navigating [Zellij](https://zellij.dev) terminal sessions from your phone. Features gesture pads for one-swipe pane/tab navigation, sticky Ctrl/Alt modifiers, and a dark terminal-friendly theme.

---

## Features

| Feature | How |
|---------|-----|
| **Pane focus** (Ctrl+Arrow) | Swipe ↑↓←→ on **FOCUS** pad |
| **Fullscreen toggle** (Ctrl+H) | Tap **FOCUS** pad |
| **Close pane** (Ctrl+W) | Long-press **FOCUS** pad |
| **New tab** (Ctrl+T) | Swipe ↑ on **TABS** pad |
| **Close tab/pane** (Ctrl+W) | Swipe ↓ on **TABS** pad |
| **Previous tab** (Ctrl+P) | Swipe ← on **TABS** pad |
| **Next tab** (Ctrl+N) | Swipe → on **TABS** pad |
| **Tab key** | Tap **TABS** pad |
| **Rename tab** (Ctrl+R) | Long-press **TABS** pad |
| **Ctrl/Alt combos** | Tap **Ctrl** or **Alt** (sticky), then type any key |
| **Esc** | Dedicated **Esc** button |
| **Typing** | Full QWERTY + symbols layout |

---

## Build (Android Studio — easiest)

1. **Install Android Studio** from [developer.android.com/studio](https://developer.android.com/studio)
2. **Open** this folder (`zellij-keyboard`) in Android Studio
3. **Build → Build Bundle(s) / APK(s) → Build APK(s)**
4. Grab the APK from `app/build/outputs/apk/debug/app-debug.apk`

## Build (command line)

Requires Android SDK + Java 17.

```bash
# From the project root
./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk
```

If the Gradle wrapper is missing, generate it first:
```bash
gradle wrapper --gradle-version 8.0
```

---

## Install

Transfer the APK to your phone and install it. Then enable it:

**Settings → System → Languages & input → On-screen keyboard → Manage keyboards → Toggle "Zellij Keyboard"**

Then switch to it in any text field by tapping the keyboard icon in the bottom-right of your navigation bar (or long-pressing the space bar on some phones).

---

## Project Structure

```
zellij-keyboard/
├── app/src/main/
│   ├── java/com/zellij/keyboard/
│   │   ├── ZellijKeyboardService.java   # Main IME service
│   │   └── GesturePadView.java          # Swipe/tap/long-press detector
│   ├── res/
│   │   ├── layout/keyboard_main.xml     # Keyboard UI
│   │   ├── xml/qwerty.xml               # QWERTY key layout
│   │   ├── xml/symbols.xml              # Symbols key layout
│   │   ├── xml/method.xml               # IME declaration
│   │   └── drawable+values/             # Theme & styling
│   └── AndroidManifest.xml
└── build.gradle + settings.gradle
```

---

## Troubleshooting

**"App not installed"**
→ You may need to allow "Install unknown apps" for your file manager.

**Keyboard doesn't show up**
→ Make sure it's enabled in **Settings → System → Languages & input → On-screen keyboard**. Then use the keyboard switcher button (bottom-right) to select it.

**Gestures not working in my terminal**
→ The terminal emulator in your PWA must properly handle `Ctrl+Arrow` key events. Most `xterm.js`-based terminals do. If yours doesn't, the keyboard is sending the events correctly — the terminal is just not interpreting them.

**Ctrl/Alt stay active after one key**
→ That's by design — they're "sticky" modifiers. Tap Ctrl, then tap a key. Ctrl auto-clears after the keypress. If you want it persistent, that's a one-line change in `ZellijKeyboardService.java`.

---

## License

Do whatever you want with this. It's yours.
