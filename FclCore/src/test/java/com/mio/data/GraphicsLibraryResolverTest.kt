package com.mio.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * LWJGL / SDL 的 GL 库同源选择测试。
 *
 * 回归背景：MC 26.3 的 RenderPearl 在 GlBackend 里比较
 * `GL.getFunctionProvider().getFunctionAddress("glGetError")` 与
 * `SDL_GL_GetProcAddress("glGetError")`，地址不一致即判定 OpenGL 后端不可用
 * （真机报 "glGetError mismatch"）。SimpleFPEWrapper 开启时 SDL 用的是兼容层，
 * LWJGL 必须同步切到兼容层，否则两侧实现不同。
 */
class GraphicsLibraryResolverTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `兼容层开启且文件存在时切到兼容层`() {
        val sfpew = tempFolder.newFile("libSimpleFPEWrapper.so")

        val resolved = GraphicsLibraryResolver.resolve(
            "libmobileglues.so", true, sfpew.absolutePath
        )

        assertEquals(sfpew.absolutePath, resolved)
    }

    @Test
    fun `兼容层关闭时使用渲染器库`() {
        val sfpew = tempFolder.newFile("libSimpleFPEWrapper.so")

        val resolved = GraphicsLibraryResolver.resolve(
            "libmobileglues.so", false, sfpew.absolutePath
        )

        assertEquals("libmobileglues.so", resolved)
    }

    @Test
    fun `兼容层路径缺失时回落渲染器库`() {
        assertEquals(
            "libmobileglues.so",
            GraphicsLibraryResolver.resolve("libmobileglues.so", true, null)
        )
        assertEquals(
            "libmobileglues.so",
            GraphicsLibraryResolver.resolve("libmobileglues.so", true, "")
        )
        assertEquals(
            "渲染器库不能因为兼容层路径不存在被清空",
            "libmobileglues.so",
            GraphicsLibraryResolver.resolve(
                "libmobileglues.so", true, File(tempFolder.root, "missing.so").absolutePath
            )
        )
    }
}
