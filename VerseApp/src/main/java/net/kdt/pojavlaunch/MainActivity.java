package net.kdt.pojavlaunch;

import android.content.Context;
import android.util.Log;

/**
 * 宿主适配：触控模块引用的 MainActivity 成员 shim。
 * 输入切换动作由 Verse GameActivity 承担（启动游戏时注入 hostHandler）。
 */
public class MainActivity {
    private static final String TAG = "VerseX-MainActivityShim";

    /** 宿主注入的特殊按钮动作（GameActivity 设置），Pojav 布局的 Keyboard/Mouse 特殊按钮经此转发 */
    public interface SpecialButtonHost {
        /** @param action "keyboard"=切换软键盘（TouchCharInput），"mouse"=切换虚拟鼠标 */
        void onSpecialButton(String action);
    }

    public static volatile SpecialButtonHost hostHandler;

    public static void switchKeyboardState() {
        SpecialButtonHost host = hostHandler;
        if (host != null) {
            host.onSpecialButton("keyboard");
        } else {
            Log.d(TAG, "switchKeyboardState() 宿主未注册");
        }
    }

    public static void toggleMouse(Context ctx) {
        SpecialButtonHost host = hostHandler;
        if (host != null) {
            host.onSpecialButton("mouse");
        } else {
            Log.d(TAG, "toggleMouse() 宿主未注册");
        }
    }
}