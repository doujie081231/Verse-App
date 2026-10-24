package com.tungsten.fclauncher.utils;

import android.content.Context;

import java.io.File;

/**
 * FCL 原版 FCLauncher 的 FCLPath（宿主版）。
 *
 * 与原版差异：路径由 Verse 宿主在启动时注入（FclCoreHost 调用 loadPaths 或 setPaths），
 * 不依赖 fcl 模块的运行期初始化顺序，适配直搬 fclcore 下载模块的“宿主注入”约定。
 */
public class FCLPath {

    public static Context CONTEXT;

    public static String NATIVE_LIB_DIR;
    public static String LOG_DIR;
    public static String CACHE_DIR;
    public static String RUNTIME_DIR;
    public static String MOD_RUNTIME_DIR;
    public static String JAVA_PATH;
    public static String JNA_PATH;
    public static String LWJGL_DIR;
    public static String CACIOCAVALLO_8_DIR;
    public static String CACIOCAVALLO_17_DIR;
    public static String FILES_DIR;
    public static String PLUGIN_DIR;
    public static String LIB_PATCHER_PATH;
    public static String MIO_LAUNCH_WRAPPER;

    /** JRE 架构名（aarch64/x86_64 等），由宿主注入，用于定位 jre-<ver>-<arch> 目录 */
    public static String JRE_ARCH = "aarch64";

    /** 与 Verse 的 com.verse.explorer.launcher.fcl.util.FCLPath.loadPaths 一致的全量路径计算 */
    public static void loadPaths(Context context) {
        CONTEXT = context;
        NATIVE_LIB_DIR = context.getApplicationInfo().nativeLibraryDir;
        FILES_DIR = context.getFilesDir().getAbsolutePath();

        File externalFilesDir = context.getExternalFilesDir(null);
        if (externalFilesDir == null) {
            externalFilesDir = new File(context.getFilesDir(), "external");
        }

        LOG_DIR = new File(externalFilesDir, "logs").getAbsolutePath();
        CACHE_DIR = context.getCacheDir().getAbsolutePath() + "/fcl";
        RUNTIME_DIR = context.getDir("runtime", 0).getAbsolutePath();
        JAVA_PATH = RUNTIME_DIR;
        JNA_PATH = RUNTIME_DIR + "/jna";
        LWJGL_DIR = RUNTIME_DIR + "/lwjgl";
        CACIOCAVALLO_8_DIR = RUNTIME_DIR + "/caciocavallo";
        CACIOCAVALLO_17_DIR = RUNTIME_DIR + "/caciocavallo17";
        MOD_RUNTIME_DIR = context.getDir("runtime_mod", 0).getAbsolutePath();
        PLUGIN_DIR = FILES_DIR + "/plugins";
        LIB_PATCHER_PATH = PLUGIN_DIR + "/MioLibPatcher.jar";
        MIO_LAUNCH_WRAPPER = PLUGIN_DIR + "/MioLaunchWrapper.jar";

        init(LOG_DIR);
        init(CACHE_DIR);
        init(RUNTIME_DIR);
        init(MOD_RUNTIME_DIR);
        init(LWJGL_DIR);
        init(CACIOCAVALLO_8_DIR);
        init(CACIOCAVALLO_17_DIR);
        init(FILES_DIR);
        init(PLUGIN_DIR);
    }

    /** 宿主注入：由安装器/Kotlin 侧按需覆盖部分关键路径 */
    public static void setPaths(String cacheDir, String javaPath, String runtimeDir, String filesDir, String nativeLibraryDir) {
        CACHE_DIR = cacheDir;
        JAVA_PATH = javaPath;
        RUNTIME_DIR = runtimeDir;
        FILES_DIR = filesDir;
        NATIVE_LIB_DIR = nativeLibraryDir;
        JNA_PATH = runtimeDir + "/jna";
        LWJGL_DIR = runtimeDir + "/lwjgl";
        CACIOCAVALLO_8_DIR = runtimeDir + "/caciocavallo";
        CACIOCAVALLO_17_DIR = runtimeDir + "/caciocavallo17";
    }

    private static boolean init(String path) {
        if (!new File(path).exists()) {
            return new File(path).mkdirs();
        }
        return true;
    }
}