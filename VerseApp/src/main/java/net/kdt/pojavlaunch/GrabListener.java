package net.kdt.pojavlaunch;

/**
 * 抓取监听：继承 org.lwjgl.glfw.GrabListener，
 * 由宿主 CallbackBridge 统一派发。
 */
public interface GrabListener extends org.lwjgl.glfw.GrabListener {
    void onGrabState(boolean isGrabbing);
}