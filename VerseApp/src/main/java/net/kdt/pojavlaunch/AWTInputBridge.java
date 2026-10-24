package net.kdt.pojavlaunch;

import org.lwjgl.glfw.CallbackBridge;

/**
 * 宿主适配：AWT 输入桥接改写为走 org.lwjgl.glfw.CallbackBridge（与 LWJGL 模式一致），
 * 避免依赖原生 pojavexec_awt 库。
 */
public class AWTInputBridge {
    public static final int EVENT_TYPE_CHAR = 1000;
    public static final int EVENT_TYPE_CURSOR_POS = 1003;
    public static final int EVENT_TYPE_KEY = 1005;
    public static final int EVENT_TYPE_MOUSE_BUTTON = 1006;

    public static void sendKey(char keychar, int keycode) {
        sendKey(keychar, keycode, 1);
        sendKey(keychar, keycode, 0);
    }

    public static void sendKey(char keychar, int keycode, int state) {
        int glfwKey = AWTToGLFW(keycode);
        if (glfwKey != 0) {
            CallbackBridge.sendKeycode(glfwKey, state == 1 ? keychar : '\u0000', 0, 0, state == 1);
        }
    }

    public static void sendChar(char keychar) {
        CallbackBridge.sendChar(keychar, 0);
    }

    public static void sendMousePress(int awtButtons, boolean isDown) {
        CallbackBridge.sendMouseButton(awtButtons, isDown);
    }

    public static void sendMousePress(int awtButtons) {
        sendMousePress(awtButtons, true);
        sendMousePress(awtButtons, false);
    }

    public static void sendMousePos(int x, int y) {
        CallbackBridge.sendCursorPos(x, y);
    }

    private static int AWTToGLFW(int awtVk) {
        switch (awtVk) {
            case 8: return 19;      // BACK_SPACE -> GLFW_KEY_BACKSPACE
            case 10: return 257;    // ENTER -> GLFW_KEY_ENTER
            case 27: return 256;    // ESCAPE
            default: return 0;
        }
    }

    private static native String nativeClipboardReceived();

    private static native void nativeMoveWindow(int xoff, int yoff);

    public static native void nativeSendData(int type, int i1, int i2, int i3, int i4);
}