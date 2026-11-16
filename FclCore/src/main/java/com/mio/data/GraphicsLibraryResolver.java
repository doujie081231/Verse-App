package com.mio.data;

import java.io.File;

/**
 * 选择交给 LWJGL 的 GL / EGL 库。
 *
 * <p>MC 26.3（RenderPearl）在 {@code GlBackend.loadLibrary()} 里做同源校验：
 * {@code GL.getFunctionProvider().getFunctionAddress("glGetError")} 必须与
 * {@code SDL_GL_GetProcAddress("glGetError")} 是同一个地址，否则抛
 * {@code BackendCreationException: glGetError mismatch}，游戏判定 OpenGL 后端不可用，
 * 回落到 Vulkan 后再报 {@code No supported graphics backend}。
 *
 * <p>所以 LWJGL 侧与 SDL 侧必须加载同一个 .so：
 * <ul>
 *   <li>SimpleFPEWrapper 兼容层开启时，SDL 的 GL/EGL 都指向兼容层（启动链把
 *       SDL_OPENGL_LIBRARY / SDL_EGL_LIBRARY 写成兼容层路径），LWJGL 也必须用兼容层，
 *       否则两侧一个拿到兼容层的包装函数、一个拿到驱动符号 → mismatch；</li>
 *   <li>关闭时两侧都用渲染器自己的 GL 库。</li>
 * </ul>
 *
 * <p>实现为纯函数，兼容层路径缺失/文件不存在时一律回落到渲染器库，
 * 避免因为一个配置项把启动拦下来。
 */
public final class GraphicsLibraryResolver {

    private GraphicsLibraryResolver() {
    }

    /**
     * @param rendererLibrary 渲染器自身的 GL/EGL 库路径（可为相对库名）
     * @param sfpewEnabled    启动链是否已挂载 SimpleFPEWrapper 兼容层
     * @param sfpewLibrary    兼容层库的绝对路径
     * @return 应当交给 LWJGL 的库路径
     */
    public static String resolve(String rendererLibrary, boolean sfpewEnabled, String sfpewLibrary) {
        if (sfpewEnabled && isUsable(sfpewLibrary)) {
            return sfpewLibrary;
        }
        return rendererLibrary;
    }

    static boolean isUsable(String path) {
        return path != null && !path.isEmpty() && new File(path).isFile();
    }
}
