/*
 * This file is part of SDL3 android-project java code.
 * Licensed under the zlib license: https://www.libsdl.org/license.php
 */

package org.libsdl.app;

import android.app.Activity;
import android.content.Context;

import java.lang.reflect.Method;

/**
    SDL library initialization
*/
public class SDL {

    // This function should be called first and sets up the native code
    // so it can call into the Java classes
    static public void setupJNI() {
        SDLActivity.nativeSetupJNI();
        SDLAudioManager.nativeSetupJNI();
        SDLControllerManager.nativeSetupJNI();
    }

    // This function should be called each time the activity is started
    static public void initialize() {
        setContext(null);

        SDLActivity.initialize();
        SDLAudioManager.initialize();
        SDLControllerManager.initialize();
    }

    // This function stores the current activity (SDL or not)
    static public void setContext(Activity context) {
        SDLAudioManager.setContext(context);
        mContext = context;
    }

    /**
     * SDL3 已 dlopen 后，安装 GL 函数指针对齐 hooks（versesdl 提供）：
     * 把 SDL_GL_GetProcAddress / dlsym 出口的 gl* 重定向到 libng_gl4es.so，
     * 使 MC 26.3 RenderPearl 的 glGetError mismatch 校验通过。
     */
    static public native void nativeInstallSdlHooks();

    /**
     * VKSHIM_ENABLE=1 时预加载 libvkshim.so（Vulkan 加载器包装层）并把句柄写入
     * VULKAN_PTR，使 SDL 与 LWJGL 的 Vulkan 加载经同一实例（RenderPearl 校验一致）。
     */
    static public native void nativeEnableVulkanShim();

    static public Activity getContext() {
        return mContext;
    }

    static void loadLibrary(String libraryName) throws UnsatisfiedLinkError, SecurityException, NullPointerException {
        loadLibrary(libraryName, mContext);
    }

    static void loadLibrary(String libraryName, Context context) throws UnsatisfiedLinkError, SecurityException, NullPointerException {

        if (libraryName == null) {
            throw new NullPointerException("No library name provided.");
        }

        try {
            // Let's see if we have ReLinker available in the project.  This is necessary for
            // some projects that have huge numbers of local libraries bundled, and thus may
            // trip a bug in Android's native library loader which ReLinker works around.  (If
            // loadLibrary works properly, ReLinker will simply use the normal Android method
            // internally.)
            //
            // To use ReLinker, just add it as a dependency.  For more information, see
            // https://github.com/KeepSafe/ReLinker for ReLinker's repository.
            //
            Class<?> relinkClass = context.getClassLoader().loadClass("com.getkeepsafe.relinker.ReLinker");
            Class<?> relinkListenerClass = context.getClassLoader().loadClass("com.getkeepsafe.relinker.ReLinker$LoadListener");
            Class<?> contextClass = context.getClassLoader().loadClass("android.content.Context");
            Class<?> stringClass = context.getClassLoader().loadClass("java.lang.String");

            // Get a 'force' instance of the ReLinker, so we can ensure libraries are reinstalled if
            // they've changed during updates.
            Method forceMethod = relinkClass.getDeclaredMethod("force");
            Object relinkInstance = forceMethod.invoke(null);
            Class<?> relinkInstanceClass = relinkInstance.getClass();

            // Actually load the library!
            Method loadMethod = relinkInstanceClass.getDeclaredMethod("loadLibrary", contextClass, stringClass, stringClass, relinkListenerClass);
            loadMethod.invoke(relinkInstance, context, libraryName, null, null);
        }
        catch (final Throwable e) {
            // Fall back
            try {
                System.loadLibrary(libraryName);
            }
            catch (final UnsatisfiedLinkError ule) {
                throw ule;
            }
            catch (final SecurityException se) {
                throw se;
            }
        }
    }

    protected static Activity mContext;
}
