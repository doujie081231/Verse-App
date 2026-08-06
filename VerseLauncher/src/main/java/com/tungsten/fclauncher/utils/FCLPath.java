package com.tungsten.fclauncher.utils;

import android.content.Context;

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

    static {
        syncFromVerse();
    }

    private static void syncFromVerse() {
        com.verse.explorer.launcher.fcl.util.FCLPath source = null;
        try {
            CONTEXT = com.verse.explorer.launcher.fcl.util.FCLPath.CONTEXT;
        } catch (Exception e) {
            return;
        }
        loadFromVerse();
    }

    public static void loadFromVerse() {
        CONTEXT = com.verse.explorer.launcher.fcl.util.FCLPath.CONTEXT;
        NATIVE_LIB_DIR = com.verse.explorer.launcher.fcl.util.FCLPath.NATIVE_LIB_DIR;
        LOG_DIR = com.verse.explorer.launcher.fcl.util.FCLPath.LOG_DIR;
        CACHE_DIR = com.verse.explorer.launcher.fcl.util.FCLPath.CACHE_DIR;
        RUNTIME_DIR = com.verse.explorer.launcher.fcl.util.FCLPath.RUNTIME_DIR;
        MOD_RUNTIME_DIR = com.verse.explorer.launcher.fcl.util.FCLPath.MOD_RUNTIME_DIR;
        JAVA_PATH = com.verse.explorer.launcher.fcl.util.FCLPath.JAVA_PATH;
        JNA_PATH = com.verse.explorer.launcher.fcl.util.FCLPath.JNA_PATH;
        LWJGL_DIR = com.verse.explorer.launcher.fcl.util.FCLPath.LWJGL_DIR;
        CACIOCAVALLO_8_DIR = com.verse.explorer.launcher.fcl.util.FCLPath.CACIOCAVALLO_8_DIR;
        CACIOCAVALLO_17_DIR = com.verse.explorer.launcher.fcl.util.FCLPath.CACIOCAVALLO_17_DIR;
        FILES_DIR = com.verse.explorer.launcher.fcl.util.FCLPath.FILES_DIR;
        PLUGIN_DIR = com.verse.explorer.launcher.fcl.util.FCLPath.PLUGIN_DIR;
        LIB_PATCHER_PATH = com.verse.explorer.launcher.fcl.util.FCLPath.LIB_PATCHER_PATH;
        MIO_LAUNCH_WRAPPER = com.verse.explorer.launcher.fcl.util.FCLPath.MIO_LAUNCH_WRAPPER;
    }
}
