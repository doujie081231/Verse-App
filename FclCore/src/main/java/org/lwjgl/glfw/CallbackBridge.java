package org.lwjgl.glfw;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.util.DisplayMetrics;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import com.tungsten.fcl.FCLApplication;
import com.tungsten.fcl.game.sdl.SdlBridge;
import com.tungsten.fclauncher.bridge.FCLBridge;
import com.tungsten.fclauncher.keycodes.EfficientAndroidLWJGLKeycode;
import com.tungsten.fclauncher.keycodes.LwjglGlfwKeycode;
import com.tungsten.fclauncher.keycodes.LwjglKeycodeMap;

import org.libsdl.app.SDLActivity;
import org.libsdl.app.SDLSurface;

import java.util.function.Consumer;

import dalvik.annotation.optimization.CriticalNative;

public class CallbackBridge {
    public static final Choreographer sChoreographer = Choreographer.getInstance();
    private static FCLBridge fclBridge = null;
    private static boolean isGrabbing = false;
    private static final Consumer<Boolean> grabListener = isGrabbing -> CallbackBridge.fclBridge.setCursorMode(isGrabbing ? FCLBridge.CursorDisabled : FCLBridge.CursorEnabled);
    // 触控控件抓取监听（Pojav 触控模块迁移：ControlButton/Touchpad/HotbarView 等注册）
    private static final java.util.List<GrabListener> grabListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** 鼠标指针形状监听（原生 glfwSetCursor 上报的 GLFW 标准光标编号） */
    private static volatile java.util.function.IntConsumer cursorShapeListener = null;

    public static void setCursorShapeListener(java.util.function.IntConsumer listener) {
        cursorShapeListener = listener;
    }

    /** 由原生侧（nglfwSetCursor）上报光标形状，转发到 UI 层以切换指针图片 */
    @androidx.annotation.Keep
    @SuppressWarnings("unused")
    public static void onCursorShapeChanged(final int shape) {
        sChoreographer.postFrameCallbackDelayed((time) -> {
            java.util.function.IntConsumer listener = cursorShapeListener;
            if (listener != null) {
                try {
                    listener.accept(shape);
                } catch (Exception ignored) {
                }
            }
        }, 16);
    }

    public static void addGrabListener(GrabListener listener) {
        if (listener != null) {
            grabListeners.add(listener);
            listener.onGrabState(isGrabbing);
        }
    }

    public static void removeGrabListener(GrabListener listener) {
        grabListeners.remove(listener);
    }

    public static final int CLIPBOARD_COPY = 2000;
    public static final int CLIPBOARD_PASTE = 2001;
    public static final int CLIPBOARD_OPEN = 2002;

    // SDL launcher integration（游戏 JVM 侧 org.lwjgl.sdl.SDLInit 通过 nativeNotifyLauncher 通知）
    public static final int NOTIF_TYPE_SDL = 0;
    public static final int ACTION_INIT_LAUNCHER_INTEGRATION = 0;
    public static final int ACTION_SEND_TEXTBOX_RECT = 1;

    public static volatile int windowWidth, windowHeight;
    public static volatile int physicalWidth, physicalHeight;
    public static float mouseX, mouseY, deltaX, deltaY;
    private static int sMouseButtonState = 0;
    public volatile static boolean holdingAlt, holdingCapslock, holdingCtrl,
            holdingNumlock, holdingShift;

    public static void putMouseEventWithCoords(int button, float x, float y) {
        putMouseEventWithCoords(button, true, x, y);
        sChoreographer.postFrameCallbackDelayed(l -> putMouseEventWithCoords(button, false, x, y), 33);
    }

    public static void putMouseEventWithCoords(int button, boolean isDown, float x, float y /* , int dz, long nanos */) {
        sendCursorPos(x, y);
        sendMouseKeycode(button, CallbackBridge.getCurrentMods(), isDown);
    }


    // ===== Verse 输入通道 =====
    // Verse 的输入最终统一经 verseexec 的 nativeSendData 进入 verseDispatchInput
    // （事件写入共享 VerseEnviron 队列 / 直接回调 g_env->callbacks），
    // 与游戏侧 GLFW stub（nglfwSetKeyCallback 等注册到 VERSE_CB_*）对接。
    // 事件类型与 poetryj compat.cpp 的 EVENT_TYPE_* 保持一致。
    public static final int EVENT_TYPE_CHAR = 1000;
    public static final int EVENT_TYPE_CHAR_MODS = 1001;
    public static final int EVENT_TYPE_CURSOR_ENTER = 1002;
    public static final int EVENT_TYPE_CURSOR_POS = 1003;
    public static final int EVENT_TYPE_FRAMEBUFFER_SIZE = 1004;
    public static final int EVENT_TYPE_KEY = 1005;
    public static final int EVENT_TYPE_MOUSE_BUTTON = 1006;
    public static final int EVENT_TYPE_SCROLL = 1007;
    public static final int EVENT_TYPE_WINDOW_SIZE = 1008;
    public static final int EVENT_TYPE_CURSOR_DELTA = 1009;

    // private Verse 通道占位（保留 nativeSendVerseData 声明，但 send* 改回原件走 pojavexec 环境）
    private static native void nativeSendVerseData(int type, int i1, int i2, int i3, int i4);

    public static void sendCursorPos(float x, float y) {
        float dx = x - mouseX;
        float dy = y - mouseY;
        mouseX = x;
        mouseY = y;
        deltaX = dx;
        deltaY = dy;
        nativeSendCursorPos(mouseX, mouseY);
        if (!SdlBridge.getSdlEnabled()) return;
        if (isGrabbing()) {
            // 鼠标被游戏捕获（锁指针转视角）时下发相对增量
            SDLActivity.onNativeMouse(0, MotionEvent.ACTION_MOVE, dx, dy, true);
        } else {
            SDLActivity.onNativeMouse(0, MotionEvent.ACTION_MOVE, x, y, false);
        }
    }

    /**
     * 在当前光标位置上叠加增量后下发。
     * 陀螺仪（GyroControl）和手指滑动都用相对增量，二者叠加而不是互相覆盖，
     * 避免抓取态下按「按下坐标 + 手指位移」下发绝对坐标时把陀螺仪位移冲掉。
     */
    public static void sendCursorDelta(float dx, float dy) {
        sendCursorPos(mouseX + dx, mouseY + dy);
    }

    public static void sendKeycode(int keycode, char keychar, int scancode, int modifiers, boolean isDown) {
        // 键码直通：Verse 与 Pojav 触控模块均使用标准 GLFW 键码（如 GLFW_KEY_A=65），
        // 不再经过 FCL 旧键码映射表的二次转换（原转换会查表失败而吞掉按键）。
        if (keycode != 0) {
            nativeSendKey(keycode, scancode, isDown ? 1 : 0, modifiers);
        }
        if (isDown && !Character.isISOControl(keychar)) {
            nativeSendCharMods(keychar, modifiers);
            nativeSendChar(keychar);
        }
        // SDL 输入双路：基于 GLFW 键码换算 Android 键码
        if (!SdlBridge.getSdlEnabled()) return;
        int androidKeycode = EfficientAndroidLWJGLKeycode.getSdlAndroidKeycode(keycode);
        if (androidKeycode == KeyEvent.KEYCODE_UNKNOWN) return;
        try {
            if (isDown) {
                SDLActivity.onNativeKeyDown(androidKeycode);
            } else {
                SDLActivity.onNativeKeyUp(androidKeycode);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void sendChar(char keychar, int modifiers) {
        nativeSendCharMods(keychar, modifiers);
        nativeSendChar(keychar);
        if (!SdlBridge.getSdlEnabled()) return;
        SDLActivity.onNativeKeyDown(EfficientAndroidLWJGLKeycode.getAndroidKeycode(keychar));
        SDLActivity.onNativeKeyUp(EfficientAndroidLWJGLKeycode.getAndroidKeycode(keychar));
    }

    public static void sendKeyPress(int keyCode, int modifiers, boolean status) {
        sendKeyPress(keyCode, 0, modifiers, status);
    }

    public static void sendKeyPress(int keyCode, int scancode, int modifiers, boolean status) {
        sendKeyPress(keyCode, '\u0000', scancode, modifiers, status);
    }

    public static void sendKeyPress(int keyCode, char keyChar, int scancode, int modifiers, boolean status) {
        CallbackBridge.sendKeycode(keyCode, keyChar, scancode, modifiers, status);
    }

    public static void sendKeyPress(int keyCode) {
        sendKeyPress(keyCode, CallbackBridge.getCurrentMods(), true);
        sendKeyPress(keyCode, CallbackBridge.getCurrentMods(), false);
    }

    public static void sendMouseButton(int button, boolean status) {
        CallbackBridge.sendMouseKeycode(button, CallbackBridge.getCurrentMods(), status);
    }

    public static void sendMouseKeycode(int button, int modifiers, boolean isDown) {
        android.util.Log.i("VerseX", "[Bridge] sendMouseKeycode button=" + button + " isDown=" + isDown + " mods=" + modifiers);
        try {
            nativeSendMouseButton(button, isDown ? 1 : 0, modifiers);
        } catch (Throwable t) {
            android.util.Log.e("VerseX", "[Bridge] nativeSendMouseButton failed: " + t);
        }
        // SDL 输入双路（按键状态累积后一次性上报，SDL 需要 MotionEvent.getButtonState()）
        if (!SdlBridge.getSdlEnabled()) return;
        int aKey = -1;
        switch (button) {
            case LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT:
                aKey = MotionEvent.BUTTON_PRIMARY;
                break;
            case LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_RIGHT:
                aKey = MotionEvent.BUTTON_SECONDARY;
                break;
            case LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_MIDDLE:
                aKey = MotionEvent.BUTTON_TERTIARY;
                break;
            case LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_5:
                aKey = MotionEvent.BUTTON_BACK;
                break;
            case LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_4:
                aKey = MotionEvent.BUTTON_FORWARD;
                break;
        }
        if (aKey != -1) {
            if (isDown) {
                sMouseButtonState |= aKey;
            } else {
                sMouseButtonState &= ~aKey;
            }
            SDLActivity.onNativeMouse(sMouseButtonState, isDown ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_UP, mouseX, mouseY, false);
        }
    }

    public static void sendMouseKeycode(int keycode) {
        sendMouseKeycode(keycode, CallbackBridge.getCurrentMods(), true);
        sendMouseKeycode(keycode, CallbackBridge.getCurrentMods(), false);
    }

    public static void sendScroll(double xoffset, double yoffset) {
        nativeSendScroll(xoffset, yoffset);
        // SDL 输入双路
        if (!SdlBridge.getSdlEnabled()) return;
        SDLActivity.onNativeMouse(0, MotionEvent.ACTION_SCROLL, (float) xoffset, (float) yoffset, false);
    }

    public static void sendUpdateWindowSize(int w, int h) {
        windowWidth = w;
        windowHeight = h;
        nativeSendScreenSize(w, h);
    }

    public static boolean isGrabbing() {
        // Avoid going through the JNI each time.
        return isGrabbing;
    }

    // Called from JRE side
    @SuppressWarnings("unused")
    public static @Nullable String accessAndroidClipboard(int type, String copy) {
        Activity activity = FCLApplication.getCurrentActivity();
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        String result = null;
        switch (type) {
            case CLIPBOARD_COPY:
                ClipData clip = ClipData.newPlainText("FCL Clipboard", copy);
                clipboard.setPrimaryClip(clip);
                break;
            case CLIPBOARD_PASTE:
                if (clipboard.hasPrimaryClip() && clipboard.getPrimaryClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
                    result = clipboard.getPrimaryClip().getItemAt(0).getText().toString();
                } else {
                    result = "";
                }
                break;
            case CLIPBOARD_OPEN:
                FCLBridge.openLink(copy);
                break;
        }
        return result;
    }


    public static int getCurrentMods() {
        int currMods = 0;
        if (holdingAlt) {
            currMods |= LwjglGlfwKeycode.GLFW_MOD_ALT;
        }
        if (holdingCapslock) {
            currMods |= LwjglGlfwKeycode.GLFW_MOD_CAPS_LOCK;
        }
        if (holdingCtrl) {
            currMods |= LwjglGlfwKeycode.GLFW_MOD_CONTROL;
        }
        if (holdingNumlock) {
            currMods |= LwjglGlfwKeycode.GLFW_MOD_NUM_LOCK;
        }
        if (holdingShift) {
            currMods |= LwjglGlfwKeycode.GLFW_MOD_SHIFT;
        }
        return currMods;
    }

    public static void setModifiers(int keyCode, boolean isDown) {
        switch (keyCode) {
            case LwjglGlfwKeycode.KEY_LEFT_SHIFT:
                CallbackBridge.holdingShift = isDown;
                break;

            case LwjglGlfwKeycode.KEY_LEFT_CONTROL:
                CallbackBridge.holdingCtrl = isDown;
                break;

            case LwjglGlfwKeycode.KEY_LEFT_ALT:
                CallbackBridge.holdingAlt = isDown;
                break;

            case LwjglGlfwKeycode.KEY_CAPS_LOCK:
                CallbackBridge.holdingCapslock = isDown;
                break;

            case LwjglGlfwKeycode.KEY_NUM_LOCK:
                CallbackBridge.holdingNumlock = isDown;
                break;
        }
    }

    public static void setFCLBridge(FCLBridge fclBridge) {
        CallbackBridge.fclBridge = fclBridge;
    }

    //Called from JRE side
    // 从 Pojav/FCL 迁移：libpojavexec（预编译 Pojav 版）JNI_OnLoad 的 dalvik 分支会
    // GetStaticMethodID(onDirectInputEnable)，缺少该方法会导致异常挂起、输入注册全链失败。
    // Verse 无 direct gamepad 机制，仅需方法存在（空实现）。
    @SuppressWarnings("unused")
    private static void onDirectInputEnable() {
    }

    //Called from JRE side
    @SuppressWarnings("unused")
    private static void onGrabStateChanged(final boolean grabbing) {
        isGrabbing = grabbing;
        sChoreographer.postFrameCallbackDelayed((time) -> {
            // If the grab re-changed, skip notify process
            if (isGrabbing != grabbing) {
                return;
            }
            synchronized (grabListener) {
                grabListener.accept(isGrabbing);
                for (GrabListener l : grabListeners) {
                    try {
                        l.onGrabState(isGrabbing);
                    } catch (Exception ignored) {
                    }
                }
            }
        }, 16);

    }

    @CriticalNative
    public static native void nativeSetUseInputStackQueue(boolean useInputStackQueue);

    @CriticalNative
    private static native boolean nativeSendChar(char codepoint);

    // GLFW: GLFWCharModsCallback deprecated, but is Minecraft still use?
    @CriticalNative
    private static native boolean nativeSendCharMods(char codepoint, int mods);

    @CriticalNative
    private static native void nativeSendKey(int key, int scancode, int action, int mods);

    // private static native void nativeSendCursorEnter(int entered);
    @CriticalNative
    private static native void nativeSendCursorPos(float x, float y);

    @CriticalNative
    private static native void nativeSendMouseButton(int button, int action, int mods);

    @CriticalNative
    private static native void nativeSendScroll(double xoffset, double yoffset);

    @CriticalNative
    private static native void nativeSendScreenSize(int width, int height);

    public static native void nativeSetWindowAttrib(int attrib, int value);

    /** 输入就绪开关（绑定 verseexec 的 Java_org_lwjgl_glfw_CallbackBridge_nativeSetInputReady） */
    public static native boolean nativeSetInputReady(boolean ready);

    public static native void setupBridgeWindow(Object surface);

    public static native int getFps();

    // ===== FCL 1.3.3.5 pojavexec 需要的 native 声明（80096 JNI_OnLoad 逐个 GetStaticMethodID/RegisterNatives，
    // Verse 类若缺 → CallbackBridge.<clinit> NoSuchMethodError 崩溃；空实现/不调用无害） =====
    public static native void nativeResetInputState();

    public static native void nativeSetGrabbing(boolean grab);

    public static native String nativeClipboard(int action, byte[] copy);

    public static native long nativeCreateGamepadAxisBuffer();

    public static native long nativeCreateGamepadButtonBuffer();

    public static native boolean nativeEnableGamepadDirectInput();

    /**
     * 游戏 JVM 侧 org.lwjgl.sdl.SDLInit（LWJGL 3.4.1 SDL 绑定）调用的通知入口，
     * 转发到 {@link #notifyLauncher}（dex 实现优先于 LWJGL 组件内的 native 声明）。
     */
    @SuppressWarnings("unused")
    public static void nativeNotifyLauncher(int type, int... action) {
        notifyLauncher(type, action);
    }

    /**
     * 由 JRE 侧（org.lwjgl.sdl.SDLInit / pojavexec sdl_hook）调用的通知入口。
     * @return 通知是否处理成功
     */
    @SuppressWarnings("unused")
    public static boolean notifyLauncher(int type, int[] action) {
        if (action == null || action.length == 0) {
            android.util.Log.w("VerseX", "SDL notification has no action");
            return false;
        }
        if (type != NOTIF_TYPE_SDL) {
            return false;
        }
        if (action[0] == ACTION_INIT_LAUNCHER_INTEGRATION) {
            if (!SdlBridge.markSdlInitialized()) {
                return true;
            }
            try {
                android.util.Log.i("VerseX", "FCL: loading SDL3");
                System.loadLibrary("SDL3");
                android.util.Log.i("VerseX", "FCL: loading SDL2");
                System.loadLibrary("SDL2");
                // 26.3 SDL 渲染路径：SDL 初始化发生在游戏首帧之前，而此时
                // GameActivity.onSurfaceTextureSizeChanged 的 pushEventWindow 仍被
                // graphicOutputShown 门控（首帧前不推送），导致 CallbackBridge.windowWidth
                // 恒为 0 → SDLSurface "Window size: 0x0" → SDL 窗口/GL 上下文创建异常。
                // 这里在 SDL JNI 绑定前补发真实窗口尺寸（对齐 FCL JVMActivity 在
                // onSurfaceTextureAvailable 无条件设置 CallbackBridge.windowWidth）。
                ensureSdlWindowSize();
                // 26.3 RenderPearl glGetError mismatch：LWJGL 的 GL provider（libng_gl4es.so）
                // 与 SDL_GL_GetProcAddress（SDL3 走系统 eglGetProcAddress）不是同一实现。
                // versedl 在 dlsym 出口 + SDL_GL_GetProcAddress 符号处重定向 gl* 到 gl4es。
                // 必须在 SDL3 已 dlopen 之后安装（bytehook_hook_single 针对已加载库补丁）。
                try {
                    org.libsdl.app.SDL.nativeInstallSdlHooks();
                    android.util.Log.i("VerseX", "FCL: versedl SDL GL hooks installed");
                } catch (Throwable t) {
                    android.util.Log.w("VerseX", "FCL: versedl SDL GL hooks failed: " + t);
                }
                android.util.Log.i("VerseX", "FCL: setting up SDL JNI");
                SdlBridge.setupJNI();
                android.util.Log.i("VerseX", "FCL: binding SDL surface");
                SdlBridge.setSdlEnabled(true);
                SDLSurface surface = SDLActivity.getSDLSurface();
                if (surface != null) {
                    surface.surfaceChanged();
                    if (windowWidth > 0 && windowHeight > 0) {
                        surface.nativeResize(windowWidth, windowHeight);
                    }
                }
                android.util.Log.i("VerseX", "FCL: SDL support enabled!");
                return true;
            } catch (Throwable e) {
                SdlBridge.setSdlEnabled(false);
                SdlBridge.clearSdlInitialized();
                android.util.Log.e("VerseX", "FCL: SDL launcher integration is unavailable", e);
            }
        }
        return false;
    }

    /** 复位 SDL 桥接状态（SdlBridge.reset 时调用，防止旧 Surface/旧初始化状态残留） */
    public static void clearSdlBridgeState() {
        sMouseButtonState = 0;
        deltaX = 0f;
        deltaY = 0f;
    }

    /**
     * SDL 渲染路径（MC 26.3+）：SDL_Init 发生在游戏首帧前，pushEventWindow 被
     * graphicOutputShown 门控尚未推送，windowWidth/windowHeight 恒为 0。
     * 从当前 Activity 的真实显示尺寸补发，使 SDLSurface "Window size" 非零。
     */
    private static void ensureSdlWindowSize() {
        if (windowWidth > 0 && windowHeight > 0) {
            return;
        }
        try {
            Activity activity = SDLActivity.getContext();
            if (activity != null) {
                DisplayMetrics dm = new DisplayMetrics();
                WindowManager wm = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
                if (wm != null) {
                    wm.getDefaultDisplay().getRealMetrics(dm);
                    windowWidth = dm.widthPixels;
                    windowHeight = dm.heightPixels;
                    android.util.Log.i("VerseX", "FCL: ensureSdlWindowSize -> " + windowWidth + "x" + windowHeight);
                }
            }
        } catch (Throwable t) {
            android.util.Log.w("VerseX", "FCL: ensureSdlWindowSize failed: " + t);
        }
        if (windowWidth <= 0 || windowHeight <= 0) {
            windowWidth = 1280;
            windowHeight = 720;
        }
    }

    static {
        // 保持只加载 pojavexec：其 fcl_orig 环境为游戏侧 LWJGL 的真实输入出口
        // （critical_send_* 读取 pojav_environ->GLFW_invoke_* 并受 isInputReady 门控）。
        // 误加载 verseexec 会让 nativeSetInputReady 绑定到 verse 环境，无法开启输入。
        System.loadLibrary("pojavexec");
    }
}

