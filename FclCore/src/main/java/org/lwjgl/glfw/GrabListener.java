package org.lwjgl.glfw;

/** 输入抓取状态监听（触控控件注册用） */
public interface GrabListener {
    void onGrabState(boolean isGrabbing);
}