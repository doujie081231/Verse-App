package net.kdt.pojavlaunch.utils;

/**
 * 宿主适配：读取 MC options.txt 的 GUI 缩放（触控模块用 getMcScale 缩放热键栏）。
 * 简化实现：直接返回 1.0（不随 MC 设置缩放）。
 */
public class MCOptionUtils {

    public interface MCOptionListener {
        void onOptionChanged();
    }

    public static void load(String optionsFilePath) {
        // 简化：不解析 options.txt
    }

    public static void load() {
    }

    public static void addMCOptionListener(MCOptionListener listener) {
    }

    public static void removeMCOptionListener(MCOptionListener listener) {
    }

    /** 读取 guiScale 设置（1 = 自动/小） */
    public static int getMcScale() {
        return 1;
    }
}