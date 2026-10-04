-keep class com.verse.explorer.** { *; }
-keepclassmembers class * {
    native <methods>;
}

# FclCore 桥接类整类 keep（R8 无法感知 native 反射与跨模块静态引用）：
# 曾裁剪 com.tungsten.fclauncher.bridge.FCLBridge.openLink(String)（NoSuchMethodError，
# 2.1.17 启动失败根因）—— native 层 awt_bridge.c 用 GetStaticMethodID("openLink") 反射调用，
# org.lwjgl.glfw.CallbackBridge（FclCore 版）也直接调用。整类 keep 防止同类方法被误裁。
-keep class com.tungsten.fclauncher.bridge.** { *; }
-keep class com.tungsten.fclauncher.utils.** { *; }
-keep class org.lwjgl.glfw.CallbackBridge { *; }

# FclCore 整包 keep（R8 无法感知 GSON 反射与类型强转；混淆会把模型字段改名导致
# 版本 JSON 解析失败 "Malformed version json"，并会破坏 ETag 下载缓存
# CacheRepository/ETagItem 的 Gson LinkedTreeMap→ETagItem 强转（ClassCastException）。
# 与 com.verse.explorer.** 整类 keep 同一思路，保名不保大小，避免逐个包补规则再踩坑。
-keep class com.tungsten.fclcore.** { *; }

# SDL3 集成（MC 26.3+ 渲染路径）：org.libsdl.app 的 JNI 方法名由 libSDL3.so
# 按 Java_org_libsdl_app_SDLActivity_xxx 命名绑定，混淆会破坏 native 链接；
# com.tungsten.fcl.game.sdl.SdlBridge 的 external 符号由 libpojavexec.so 提供，同样禁止混淆。
-keep class org.libsdl.app.** { *; }
-keep class com.tungsten.fcl.game.sdl.** { *; }
-keep class com.tungsten.fclauncher.keycodes.EfficientAndroidLWJGLKeycode { *; }

# Apple Liquid Glass（Kyant0/AndroidLiquidGlass Backdrop）整包 keep：
# 库内部用 RenderEffect/RuntimeShader(AGSL) 反射调用 + 自定义 Modifier.Node 链，
# 混淆会破坏 GraphicsLayer 导出/着色器管线，导致液态玻璃失效或崩溃。
-keep class com.kyant.backdrop.** { *; }
-keep class com.kyant.shapes.** { *; }

# slf4j 在 Android 无实现绑定（StaticLoggerBinder 缺失为反射探测，正常现象）
-dontwarn org.slf4j.**

# Vulkan capability checker: JNI native methods resolve by class/method name
-keep class com.verse.explorer.app.device.** { *; }
