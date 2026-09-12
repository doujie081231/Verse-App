package net.kdt.pojavlaunch.prefs;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 宿主适配：Pojav 触控模块引用的启动器偏好 shim，默认值对齐 Pojav 默认。
 * 后续如需持久化可在此接入 SharedPreferences。
 */
public class LauncherPreferences {

    public static SharedPreferences DEFAULT_PREF;

    // 触控/按键
    public static float PREF_BUTTONSIZE = 100f;
    public static boolean PREF_BUTTON_ALL_CAPS = true;
    public static float PREF_SCALE_FACTOR = 1.0f;
    public static float PREF_DEADZONE_SCALE = 1.0f;

    // 鼠标/陀螺仪
    public static float PREF_MOUSESPEED = 1.0f;
    public static float PREF_MOUSESCALE = 1.0f;
    public static boolean PREF_DISABLE_GESTURES = false;
    public static boolean PREF_DISABLE_SWAP_HAND = false;
    public static boolean PREF_GYRO_INVERT_X = false;
    public static boolean PREF_GYRO_INVERT_Y = false;
    public static int PREF_GYRO_SAMPLE_RATE = 10;
    public static float PREF_GYRO_SENSITIVITY = 5.0f;
    public static boolean PREF_GYRO_SMOOTHING = false;
    public static int PREF_LONGPRESS_TRIGGER = 500;

    // 控制器
    public static String PREF_DEFAULTCTRL_PATH = null;

    public static void init(Context ctx) {
        DEFAULT_PREF = ctx.getSharedPreferences("verse_launcher_prefs", Context.MODE_PRIVATE);
        if (DEFAULT_PREF.contains("buttonsize")) {
            PREF_BUTTONSIZE = DEFAULT_PREF.getFloat("buttonsize", PREF_BUTTONSIZE);
        }
        if (DEFAULT_PREF.contains("defaultCtrl")) {
            PREF_DEFAULTCTRL_PATH = DEFAULT_PREF.getString("defaultCtrl", null);
        }
    }
}