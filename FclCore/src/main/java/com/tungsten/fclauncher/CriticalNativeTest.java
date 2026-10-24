package com.tungsten.fclauncher;

import androidx.annotation.Keep;

import dalvik.annotation.optimization.CriticalNative;

/**
 * 检测 ART 是否支持 CriticalNative 调用约定。
 * libpojavexec 的 registerFunctions() 会 FindClass 本类并注册 testCriticalNative，
 * 通过 invokeTest() 调用后依据 native 收到的参数形态（critical 无 env/clazz 参数）
 * 决定注册 critical 还是 noncritical 输入函数组（与输入桥的 @CriticalNative 方法约定一致）。
 */
@Keep
public class CriticalNativeTest {
    @CriticalNative
    public static native void testCriticalNative(int arg0, int arg1);

    public static void invokeTest() {
        testCriticalNative(0, 0);
    }
}