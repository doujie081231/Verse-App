package net.kdt.pojavlaunch;

import android.content.Context;
import android.util.Log;

/**
 * 宿主适配：Pojav 触控模块引用的 MainActivity 成员 shim。
 * 输入切换动作由 Verse GameActivity 承担。
 */
public class MainActivity {
    private static final String TAG = "VerseX-MainActivityShim";

    public static void switchKeyboardState() {
        Log.d(TAG, "switchKeyboardState() 由宿主处理");
    }

    public static void toggleMouse(Context ctx) {
        Log.d(TAG, "toggleMouse() 由宿主处理");
    }
}