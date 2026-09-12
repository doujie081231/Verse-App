package net.kdt.pojavlaunch;

import com.verse.explorer.app.R;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import org.lwjgl.glfw.CallbackBridge;

/**
 * 宿主适配：代表游戏渲染面，供 Pojav 触控模块引用。
 * 挂接在 Verse GameActivity 的触控覆盖层中（R.id.main_game_render_view）。
 * 输入经 org.lwjgl.glfw.CallbackBridge 直达游戏 GLFW 窗口。
 */
public class MinecraftGLSurface extends View {

    public MinecraftGLSurface(Context ctx) {
        super(ctx);
    }

    public MinecraftGLSurface(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
    }

    /** 鼠标按键（GLFW 键码已由调用方转换）直发游戏 */
    public static boolean sendMouseButtonUnconverted(int button, boolean isDown) {
        try {
            CallbackBridge.sendMouseButton(button, isDown);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}