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

# slf4j 在 Android 无实现绑定（StaticLoggerBinder 缺失为反射探测，正常现象）
-dontwarn org.slf4j.**
