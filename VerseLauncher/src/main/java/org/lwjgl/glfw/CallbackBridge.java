package org.lwjgl.glfw;

import java.nio.ByteBuffer;

public class CallbackBridge {

    public static final int CLIPBOARD_COPY = 2000;
    public static final int CLIPBOARD_PASTE = 2001;

    public static final int EVENT_TYPE_CHAR = 1000;
    public static final int EVENT_TYPE_CHAR_MODS = 1001;
    public static final int EVENT_TYPE_CURSOR_ENTER = 1002;
    public static final int EVENT_TYPE_CURSOR_POS = 1003;
    public static final int EVENT_TYPE_FRAMEBUFFER_SIZE = 1004;
    public static final int EVENT_TYPE_KEY = 1005;
    public static final int EVENT_TYPE_MOUSE_BUTTON = 1006;
    public static final int EVENT_TYPE_SCROLL = 1007;
    public static final int EVENT_TYPE_WINDOW_SIZE = 1008;

    public static final int ANDROID_TYPE_GRAB_STATE = 0;

    public static void sendCursorPos(double x, double y) {
        nativeSendData(false, EVENT_TYPE_CURSOR_POS, (int) x, (int) y, 0, 0);
    }

    public static void sendMouseButton(int button, int action) {
        nativeSendData(false, EVENT_TYPE_MOUSE_BUTTON, button, action, 0, 0);
    }

    public static void sendKey(int key, int scancode, int action, int mods) {
        nativeSendData(false, EVENT_TYPE_KEY, key, scancode, action, mods);
    }

    public static void sendChar(int codepoint, int mods) {
        nativeSendData(false, EVENT_TYPE_CHAR, codepoint, 0, 0, mods);
    }

    public static void sendScroll(double xoffset, double yoffset) {
        nativeSendData(false, EVENT_TYPE_SCROLL, (int)(xoffset * 1000), (int)(yoffset * 1000), 0, 0);
    }

    public static void sendWindowSize(int width, int height) {
        nativeSendData(false, EVENT_TYPE_WINDOW_SIZE, width, height, 0, 0);
    }

    public static void sendFramebufferSize(int width, int height) {
        nativeSendData(false, EVENT_TYPE_FRAMEBUFFER_SIZE, width, height, 0, 0);
    }

    public static native void nativeSendData(boolean isAndroid, int type, int i1, int i2, int i3, int i4);
    public static native boolean nativeSetInputReady(boolean ready);
    public static native String nativeClipboard(int action, byte[] copy);
    public static native void nativeSetGrabbing(boolean grab);
    public static native void nativeSetUseInputStackQueue(boolean use);
    public static native void nativeSetWindowAttrib(int attrib, int value);
    public static native int nativeGetFps();
}
