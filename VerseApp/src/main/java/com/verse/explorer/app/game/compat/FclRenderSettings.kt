package com.verse.explorer.app.game.compat

import com.tungsten.fcl.setting.MenuSetting
import com.verse.explorer.launcher.runtime.RenderSettings

/** 控制设置存储仍由宿主维护，运行后端只读取渲染尺寸协议。 */
internal object FclRenderSettings : RenderSettings {
    override val scale: Double get() = MenuSetting.windowScale
    override fun resolveSize(width: Int, height: Int) = MenuSetting.resolveRenderSize(width, height)
}
