package com.tungsten.fclauncher.utils;

import android.os.Build;

public class Architecture {

    public static final int UNSUPPORTED_ARCH = -1;
    public static final int ARCH_ARM64 = 0x1;
    public static final int ARCH_ARM = 0x2;
    public static final int ARCH_X86 = 0x4;
    public static final int ARCH_X86_64 = 0x8;

    public static boolean is64BitsDevice() {
        return Build.SUPPORTED_64_BIT_ABIS.length != 0;
    }

    public static boolean is32BitsDevice() {
        return !is64BitsDevice();
    }

    public static int getDeviceArchitecture() {
        if (isx86Device()) {
            return is64BitsDevice() ? ARCH_X86_64 : ARCH_X86;
        }
        return is64BitsDevice() ? ARCH_ARM64 : ARCH_ARM;
    }

    public static boolean isx86Device() {
        String[] ABI = is64BitsDevice() ? Build.SUPPORTED_64_BIT_ABIS : Build.SUPPORTED_32_BIT_ABIS;
        int comparedArch = is64BitsDevice() ? ARCH_X86_64 : ARCH_X86;
        for (String str : ABI) {
            if (archAsInt(str) == comparedArch) return true;
        }
        return false;
    }

    public static boolean isArmDevice() {
        return !isx86Device();
    }

    public static int archAsInt(String arch) {
        arch = arch.toLowerCase().trim().replace(" ", "");
        if (arch.contains("arm64") || arch.equals("aarch64")) return ARCH_ARM64;
        if (arch.contains("arm") || arch.equals("aarch32")) return ARCH_ARM;
        if (arch.contains("x86_64") || arch.contains("amd64")) return ARCH_X86_64;
        if (arch.contains("x86") || (arch.startsWith("i") && arch.endsWith("86"))) return ARCH_X86;
        return UNSUPPORTED_ARCH;
    }

    public static String archAsString(int arch) {
        if (arch == ARCH_ARM64) return "arm64";
        if (arch == ARCH_ARM) return "arm";
        if (arch == ARCH_X86_64) return "x86_64";
        if (arch == ARCH_X86) return "x86";
        return "UNSUPPORTED_ARCH";
    }

    public static String archAsStringAndroid(int arch) {
        if (arch == ARCH_ARM64) return "arm64-v8a";
        if (arch == ARCH_ARM) return "armeabi-v7a";
        if (arch == ARCH_X86_64) return "x86_64";
        if (arch == ARCH_X86) return "x86";
        return "UNSUPPORTED_ARCH";
    }
}
