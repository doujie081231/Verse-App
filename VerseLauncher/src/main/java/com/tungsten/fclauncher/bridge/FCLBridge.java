package com.tungsten.fclauncher.bridge;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;

import com.verse.explorer.launcher.bridge.CallbackBridge;
import com.verse.explorer.terracotta.TerracottaAndroidAPI;
import com.verse.explorer.terracotta.TerracottaExitCallback;

import java.io.Serializable;

public class FCLBridge implements Serializable, TerracottaExitCallback {

    private static final String TAG = "FCLBridge";

    public static final int DEFAULT_WIDTH = 1280;
    public static final int DEFAULT_HEIGHT = 720;

    public static final int HIT_RESULT_TYPE_UNKNOWN = 0;
    public static final int HIT_RESULT_TYPE_MISS = 1;
    public static final int HIT_RESULT_TYPE_BLOCK = 2;
    public static final int HIT_RESULT_TYPE_ENTITY = 3;

    public static final int INJECTOR_MODE_ENABLE = 1;
    public static final int INJECTOR_MODE_DISABLE = 0;

    public static final int KeyPress = 2;
    public static final int KeyRelease = 3;
    public static final int ButtonPress = 4;
    public static final int ButtonRelease = 5;
    public static final int MotionNotify = 6;
    public static final int KeyChar = 7;
    public static final int ConfigureNotify = 22;
    public static final int FCLMessage = 37;

    public static final int Button1 = 0;
    public static final int Button2 = 1;
    public static final int Button3 = 2;
    public static final int Button4 = 3;
    public static final int Button5 = 4;
    public static final int Button6 = 5;
    public static final int Button7 = 6;

    public static final int CursorEnabled = 1;
    public static final int CursorDisabled = 0;

    private FCLBridgeCallback callback;
    private double scaleFactor = 1.0;
    private String gameDir;
    private String logPath;
    private String renderer;
    private String java;
    private Surface surface;
    private boolean surfaceDestroyed;
    private transient Handler handler;
    private transient Thread thread;
    private String modSummary;
    private boolean hasTouchController = false;

    public FCLBridge() {
    }

    public int redirectStdio(String path) {
        try {
            return TerracottaAndroidAPI.nativeRedirectStdio(path);
        } catch (Throwable e) {
            Log.e(TAG, "redirectStdio failed", e);
            return -1;
        }
    }

    public int chdir(String path) {
        try {
            return TerracottaAndroidAPI.nativeChdir(path);
        } catch (Throwable e) {
            Log.e(TAG, "chdir failed", e);
            return -1;
        }
    }

    public void setenv(String key, String value) {
        try {
            TerracottaAndroidAPI.nativeSetenv(key, value);
        } catch (Throwable e) {
            Log.e(TAG, "setenv failed: " + key + "=" + value, e);
        }
    }

    public long dlopen(String path) {
        try {
            return TerracottaAndroidAPI.nativeDlopen(path);
        } catch (Throwable e) {
            Log.e(TAG, "dlopen failed: " + path, e);
            return 0;
        }
    }

    public void setLdLibraryPath(String path) {
        try {
            TerracottaAndroidAPI.nativeSetLdLibraryPath(path);
        } catch (Throwable e) {
            Log.e(TAG, "setLdLibraryPath failed", e);
        }
    }

    public void setupExitTrap(FCLBridge bridge) {
        try {
            TerracottaAndroidAPI.nativeSetupExitTrap(bridge);
        } catch (Throwable e) {
            Log.e(TAG, "setupExitTrap failed", e);
        }
    }

    public void setThread(Thread thread) {
        this.thread = thread;
    }

    public Thread getThread() {
        return thread;
    }

    public FCLBridgeCallback getCallback() {
        return callback;
    }

    public void execute(Surface surface, FCLBridgeCallback callback) {
        this.handler = new Handler();
        this.callback = callback;
        this.surface = surface;
        CallbackBridge.setFCLBridge(this);
        receiveLog("==================== Before Start ====================\n");
        receiveLog("invoke redirectStdio\n");
        int errorCode = redirectStdio(getLogPath());
        if (errorCode != 0) {
            receiveLog("Can't exec redirectStdio! Error code: " + errorCode + "\n");
        }
        if (surface != null) {
            handleWindow();
        }
        if (thread != null) {
            thread.start();
        }
    }

    public void pushEventMouseButton(int button, boolean press) {
        CallbackBridge.sendMouseButton(button, press);
    }

    public void pushEventPointer(int x, int y) {
        CallbackBridge.sendCursorPos((double) x, (double) y);
    }

    public void pushEventPointer(float x, float y) {
        CallbackBridge.sendCursorPos((double) x, (double) y);
    }

    public void pushEventKey(int keyCode, int keyChar, boolean press) {
        CallbackBridge.setModifiers(keyCode, press);
        CallbackBridge.sendKeycode(keyCode, (char) keyChar, 0, CallbackBridge.getCurrentMods(), press);
    }

    public void pushEventChar(char keyChar) {
        CallbackBridge.sendChar(keyChar, 0);
    }

    public void pushEventWindow(int width, int height) {
        CallbackBridge.sendUpdateWindowSize(width, height);
    }

    @Override
    public void onExit(int code) {
        if (callback != null) {
            callback.onLog("\nOpenJDK exited with code : " + code + "\n");
            callback.onExit(code);
        }
    }

    public void setCursorMode(int mode) {
        if (callback != null) {
            callback.onCursorModeChange(mode);
        }
    }

    public void setPrimaryClipString(String string) {
        try {
            Context ctx = getBridgeContext();
            if (ctx == null) return;
            ClipboardManager clipboard = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("FCL Clipboard", string);
            clipboard.setPrimaryClip(clip);
        } catch (Exception e) {
            Log.w(TAG, "Failed to set clipboard", e);
        }
    }

    public String getPrimaryClipString() {
        try {
            Context ctx = getBridgeContext();
            if (ctx == null) return null;
            ClipboardManager clipboard = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (!clipboard.hasPrimaryClip()) return null;
            ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
            return item.getText() != null ? item.getText().toString() : null;
        } catch (Exception e) {
            Log.w(TAG, "Failed to get clipboard", e);
            return null;
        }
    }

    private Context getBridgeContext() {
        return com.verse.explorer.launcher.fcl.util.FCLPath.CONTEXT;
    }

    public void setScaleFactor(double scaleFactor) {
        this.scaleFactor = scaleFactor;
    }

    public double getScaleFactor() {
        return scaleFactor;
    }

    public void setGameDir(String gameDir) {
        this.gameDir = gameDir;
    }

    public String getGameDir() {
        return gameDir;
    }

    public void setRenderer(String renderer) {
        this.renderer = renderer;
    }

    public String getRenderer() {
        return renderer;
    }

    public void setJava(String java) {
        this.java = java;
    }

    public String getJava() {
        return java;
    }

    public void setSurfaceDestroyed(boolean surfaceDestroyed) {
        this.surfaceDestroyed = surfaceDestroyed;
    }

    public boolean isSurfaceDestroyed() {
        return surfaceDestroyed;
    }

    public String getLogPath() {
        return logPath;
    }

    public void setLogPath(String logPath) {
        this.logPath = logPath;
    }

    public void receiveLog(String log) {
        if (callback != null) {
            callback.onLog(log);
        }
    }

    public String getModSummary() {
        return modSummary;
    }

    public void setModSummary(String modSummary) {
        this.modSummary = modSummary;
    }

    public boolean hasTouchController() {
        return hasTouchController;
    }

    public void setHasTouchController(boolean hasTouchController) {
        this.hasTouchController = hasTouchController;
    }

    private void handleWindow() {
        if (gameDir != null) {
            receiveLog("invoke setFCLNativeWindow\n");
            CallbackBridge.setupBridgeWindow(surface);
        } else {
            receiveLog("start Android AWT Renderer thread\n");
        }
    }
}
