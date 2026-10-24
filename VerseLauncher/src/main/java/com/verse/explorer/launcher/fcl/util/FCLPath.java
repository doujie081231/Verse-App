package com.verse.explorer.launcher.fcl.util;

import android.content.Context;

import java.io.File;

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
    public static String AUTHLIB_INJECTOR;

    public static void loadPaths(Context context, String gameDirPath) {
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
        AUTHLIB_INJECTOR = PLUGIN_DIR + "/authlib-injector.jar";

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

    private static boolean init(String path) {
        if (!new File(path).exists()) {
            return new File(path).mkdirs();
        }
        return true;
    }
}
