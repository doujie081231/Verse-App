package org.lwjgl.glfw;

/** 输入抓取状态监听（Pojav 触控模块迁移用） */
public interface GrabListener {
    void onGrabState(boolean isGrabbing);
}