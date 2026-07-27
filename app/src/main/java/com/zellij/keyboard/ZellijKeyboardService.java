package com.zellij.keyboard;

import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.LinearLayout;

public class ZellijKeyboardService extends InputMethodService
        implements KeyboardView.OnKeyboardActionListener {

    private KeyboardView keyboardView;
    private Keyboard qwertyKeyboard;
    private Keyboard symbolsKeyboard;

    private Button ctrlButton;
    private Button altButton;
    private Button shiftButton;

    private GesturePadView focusPad;
    private GesturePadView tabPad;

    private boolean ctrlActive = false;
    private boolean altActive = false;
    private boolean capsOn = false;

    // ── Lifecycle ──────────────────────────────────────────────

    @Override
    public View onCreateInputView() {
        LinearLayout mainView = (LinearLayout) LayoutInflater.from(this)
                .inflate(R.layout.keyboard_main, null);

        setupToolbar(mainView);
        setupGesturePads(mainView);
        setupQwerty(mainView);

        return mainView;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        resetModifiers();
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        // Never steal the whole screen in landscape
        return false;
    }

    // ── UI Setup ───────────────────────────────────────────────

    private void setupToolbar(LinearLayout parent) {
        ctrlButton = parent.findViewById(R.id.key_ctrl);
        altButton = parent.findViewById(R.id.key_alt);
        shiftButton = parent.findViewById(R.id.key_shift_toolbar);

        Button escBtn = parent.findViewById(R.id.key_esc);
        Button tabBtn = parent.findViewById(R.id.key_tab);
        Button enterBtn = parent.findViewById(R.id.key_enter_toolbar);
        Button bsBtn = parent.findViewById(R.id.key_backspace_toolbar);
        Button spaceBtn = parent.findViewById(R.id.key_space_toolbar);

        escBtn.setOnClickListener(v -> sendEsc());
        ctrlButton.setOnClickListener(v -> toggleCtrl());
        altButton.setOnClickListener(v -> toggleAlt());
        tabBtn.setOnClickListener(v -> sendTab());
        enterBtn.setOnClickListener(v -> sendEnter());
        bsBtn.setOnClickListener(v -> sendKey(KeyEvent.KEYCODE_DEL));
        spaceBtn.setOnClickListener(v -> sendText(" "));
        shiftButton.setOnClickListener(v -> toggleCaps());
    }

    private void setupGesturePads(LinearLayout parent) {
        focusPad = parent.findViewById(R.id.focus_pad);
        tabPad = parent.findViewById(R.id.tab_pad);

        focusPad.setLabel("FOCUS");
        focusPad.setSubLabel("↑↓←→  tap=full");

        tabPad.setLabel("TABS");
        tabPad.setSubLabel("↑new ↓close ←prev →next");

        focusPad.setOnGestureListener(new GesturePadView.GestureListener() {
            @Override public void onSwipeUp()    { sendCtrlArrow(KeyEvent.KEYCODE_DPAD_UP); }
            @Override public void onSwipeDown()  { sendCtrlArrow(KeyEvent.KEYCODE_DPAD_DOWN); }
            @Override public void onSwipeLeft()  { sendCtrlArrow(KeyEvent.KEYCODE_DPAD_LEFT); }
            @Override public void onSwipeRight() { sendCtrlArrow(KeyEvent.KEYCODE_DPAD_RIGHT); }
            @Override public void onTap()        { sendCtrlKey(KeyEvent.KEYCODE_H); }
            @Override public void onLongPress()  { sendCtrlKey(KeyEvent.KEYCODE_W); }
        });

        tabPad.setOnGestureListener(new GesturePadView.GestureListener() {
            @Override public void onSwipeUp()    { sendCtrlKey(KeyEvent.KEYCODE_T); }
            @Override public void onSwipeDown()  { sendCtrlKey(KeyEvent.KEYCODE_W); }
            @Override public void onSwipeLeft()  { sendCtrlKey(KeyEvent.KEYCODE_P); }
            @Override public void onSwipeRight() { sendCtrlKey(KeyEvent.KEYCODE_N); }
            @Override public void onTap()        { sendTab(); }
            @Override public void onLongPress()  { sendCtrlKey(KeyEvent.KEYCODE_R); }
        });
    }

    private void setupQwerty(LinearLayout parent) {
        keyboardView = parent.findViewById(R.id.keyboard_view);
        qwertyKeyboard = new Keyboard(this, R.xml.qwerty);
        symbolsKeyboard = new Keyboard(this, R.xml.symbols);

        keyboardView.setKeyboard(qwertyKeyboard);
        keyboardView.setOnKeyboardActionListener(this);
        keyboardView.setPreviewEnabled(false);
    }

    // ── Modifiers ──────────────────────────────────────────────

    private void toggleCtrl() {
        ctrlActive = !ctrlActive;
        updateModifierVisuals();
    }

    private void toggleAlt() {
        altActive = !altActive;
        updateModifierVisuals();
    }

    private void toggleCaps() {
        capsOn = !capsOn;
        keyboardView.setShifted(capsOn);
        updateModifierVisuals();
    }

    private void resetModifiers() {
        ctrlActive = false;
        altActive = false;
        updateModifierVisuals();
    }

    private void updateModifierVisuals() {
        ctrlButton.setActivated(ctrlActive);
        ctrlButton.setAlpha(ctrlActive ? 1.0f : 0.7f);
        altButton.setActivated(altActive);
        altButton.setAlpha(altActive ? 1.0f : 0.7f);
        shiftButton.setActivated(capsOn);
        shiftButton.setAlpha(capsOn ? 1.0f : 0.7f);
    }

    // ── Input helpers ──────────────────────────────────────────

    private void sendEsc() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.commitText("\u001B", 1);
    }

    private void sendTab() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.commitText("\t", 1);
    }

    private void sendEnter() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
    }

    private void sendKey(int keyCode) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        int meta = 0;
        if (ctrlActive) meta |= KeyEvent.META_CTRL_LEFT_ON;
        if (altActive)  meta |= KeyEvent.META_ALT_LEFT_ON;

        long now = System.currentTimeMillis();
        ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta));
        ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP,   keyCode, 0, meta));

        clearStickyModifiers();
    }

    private void sendText(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.commitText(text, 1);
        clearStickyModifiers();
    }

    private void sendCtrlArrow(int arrowKey) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        long now = System.currentTimeMillis();
        // Press Ctrl
        ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT, 0, 0));
        // Press arrow with Ctrl meta
        ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, arrowKey, 0, KeyEvent.META_CTRL_LEFT_ON));
        ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP,   arrowKey, 0, KeyEvent.META_CTRL_LEFT_ON));
        // Release Ctrl
        ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, 0, 0));
    }

    private void sendCtrlKey(int keyCode) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        long now = System.currentTimeMillis();
        ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT, 0, 0));
        ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, KeyEvent.META_CTRL_LEFT_ON));
        ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP,   keyCode, 0, KeyEvent.META_CTRL_LEFT_ON));
        ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, 0, 0));
    }

    private void clearStickyModifiers() {
        if (ctrlActive) { ctrlActive = false; }
        if (altActive)  { altActive = false; }
        updateModifierVisuals();
    }

    // ── KeyboardView callbacks ─────────────────────────────────

    @Override
    public void onKey(int primaryCode, int[] keyCodes) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        if (primaryCode == Keyboard.KEYCODE_DELETE) {
            sendKey(KeyEvent.KEYCODE_DEL);
        } else if (primaryCode == Keyboard.KEYCODE_SHIFT) {
            toggleCaps();
        } else if (primaryCode == Keyboard.KEYCODE_DONE) {
            sendEnter();
        } else if (primaryCode == -2) {
            // switch to symbols
            keyboardView.setKeyboard(symbolsKeyboard);
        } else if (primaryCode == -3) {
            // switch to qwerty
            keyboardView.setKeyboard(qwertyKeyboard);
        } else {
            char c = (char) primaryCode;
            if (Character.isLetter(c) && capsOn) {
                c = Character.toUpperCase(c);
            }

            if (ctrlActive || altActive) {
                int keyCode = Character.toUpperCase(c);
                int meta = 0;
                if (ctrlActive) meta |= KeyEvent.META_CTRL_LEFT_ON;
                if (altActive)  meta |= KeyEvent.META_ALT_LEFT_ON;
                long now = System.currentTimeMillis();
                ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta));
                ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP,   keyCode, 0, meta));
            } else {
                ic.commitText(String.valueOf(c), 1);
            }
            clearStickyModifiers();
        }
    }

    @Override public void onPress(int primaryCode) { }
    @Override public void onRelease(int primaryCode) { }
    @Override public void onText(CharSequence text) { }
    @Override public void swipeLeft() { }
    @Override public void swipeRight() { }
    @Override public void swipeDown() { }
    @Override public void swipeUp() { }
}
