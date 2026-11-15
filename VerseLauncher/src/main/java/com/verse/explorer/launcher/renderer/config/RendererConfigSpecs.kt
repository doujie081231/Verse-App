package com.verse.explorer.launcher.renderer.config

import com.verse.explorer.launcher.renderer.RendererManager

/**
 * 各内置渲染器的配置规范登记表。
 *
 * 只登记「确实有可调参数」的渲染器：其它渲染器的参数由环境变量固定，
 * 没有暴露给用户的价值，`of()` 返回 null，界面就不显示配置入口。
 */
object RendererConfigSpecs {

    /**
     * MobileGlues 的参数，键名与驱动读取的 config.json 一致。
     *
     * 只收驱动稳定支持的键；多 draw 顺序这类需要拖拽排序的复合项没有纳入，
     * 它们对普通玩家是负收益，等有明确需求再加。
     */
    val MOBILE_GLUES: RendererConfigSpec = RendererConfigSpec(
        rendererId = RendererManager.ID_MOBILEGLUES,
        version = 1,
        fields = listOf(
            RendererConfigField(
                id = "enableNoError",
                title = "忽略着色器错误",
                description = "驱动编译着色器失败时是否继续运行。新版本游戏不开这项容易出现进世界即中断。",
                kind = ConfigFieldKind.CHOICE,
                defaultValue = 3,
                target = ConfigTarget.Json("enableNoError"),
                options = listOf(
                    ConfigOption(0, "自动"),
                    ConfigOption(1, "不忽略"),
                    ConfigOption(2, "忽略着色器错误"),
                    ConfigOption(3, "忽略着色器与程序错误")
                ),
                group = "兼容"
            ),
            RendererConfigField(
                id = "sfpewLayer",
                title = "SimpleFPEWrapper 兼容层",
                description = "接管 GL/EGL 调用，为使用固定管线（GL 1.x）的老版本与模组补兼容路径。"
                    + "默认关闭：MobileGlues 自身已能启动新版本，兼容层留作遇到固定管线问题时手动开启。",
                kind = ConfigFieldKind.SWITCH,
                defaultValue = false,
                target = ConfigTarget.Local("sfpewLayer"),
                group = "兼容"
            ),
            RendererConfigField(
                id = "enableANGLE",
                title = "ANGLE 后端",
                description = "让驱动通过 ANGLE 走上层 GLES。部分 Adreno 设备开启后更稳，Mali 一般用不到。",
                kind = ConfigFieldKind.CHOICE,
                defaultValue = 0,
                target = ConfigTarget.Json("enableANGLE"),
                options = listOf(
                    ConfigOption(0, "尽量关闭"),
                    ConfigOption(1, "尽量开启"),
                    ConfigOption(2, "强制关闭"),
                    ConfigOption(3, "强制开启")
                ),
                group = "兼容"
            ),
            RendererConfigField(
                id = "sfpewRelaxedContext",
                title = "宽松上下文",
                description = "兼容层放宽 EGL 上下文属性校验，个别驱动在严格模式下建不出上下文时开启。",
                kind = ConfigFieldKind.SWITCH,
                defaultValue = false,
                target = ConfigTarget.Env("SFPEW_RELAXED_CONTEXT"),
                group = "兼容",
                omitWhenFalse = true
            ),
            RendererConfigField(
                id = "MG_COUNT_LAUNCH",
                title = "启动时统计绘制调用",
                description = "驱动启动阶段统计 draw call，用于排查性能问题；关闭可略过这部分开销。",
                kind = ConfigFieldKind.SWITCH,
                defaultValue = true,
                target = ConfigTarget.Env("MG_COUNT_LAUNCH"),
                group = "性能"
            ),
            RendererConfigField(
                id = "angleDepthClearFixMode",
                title = "深度清除修正",
                description = "修正 ANGLE 后端下的深度缓冲清除问题，画面出现闪烁或深度错乱时开启。",
                kind = ConfigFieldKind.SWITCH,
                defaultValue = false,
                target = ConfigTarget.Json("angleDepthClearFixMode"),
                group = "兼容"
            ),
            RendererConfigField(
                id = "multidrawOrder",
                title = "多重绘制顺序",
                description = "驱动尝试各种 multidraw 实现时的优先级。默认按能力从高到低；"
                    + "遇到绘制错乱或性能异常时改成 native 优先，可绕开部分驱动的间接绘制缺陷。",
                kind = ConfigFieldKind.CHOICE,
                defaultValue = 0,
                target = ConfigTarget.JsonArray(
                    "multidrawOrder",
                    listOf(
                        listOf(
                            "native", "multiindirect", "multibasevertex", "multiarrays",
                            "indirect", "basevertex", "unroll", "compute"
                        ),
                        listOf("native", "multiarrays", "unroll")
                    )
                ),
                options = listOf(
                    ConfigOption(0, "默认（完整优先级）"),
                    ConfigOption(1, "native 优先（兼容）")
                ),
                group = "图形"
            ),
            RendererConfigField(
                id = "customGLVersion",
                title = "对外 GL 版本",
                description = "驱动向游戏声明自己支持的 OpenGL 版本。自动即可，除非某个整合包明确要求更高版本。",
                kind = ConfigFieldKind.CHOICE,
                defaultValue = 0,
                target = ConfigTarget.Json("customGLVersion"),
                options = listOf(
                    ConfigOption(0, "自动"),
                    ConfigOption(46, "OpenGL 4.6"),
                    ConfigOption(45, "OpenGL 4.5"),
                    ConfigOption(44, "OpenGL 4.4"),
                    ConfigOption(43, "OpenGL 4.3"),
                    ConfigOption(42, "OpenGL 4.2"),
                    ConfigOption(41, "OpenGL 4.1"),
                    ConfigOption(40, "OpenGL 4.0"),
                    ConfigOption(33, "OpenGL 3.3"),
                    ConfigOption(32, "OpenGL 3.2")
                ),
                group = "图形"
            ),
            RendererConfigField(
                id = "maxGlslCacheSize",
                title = "着色器缓存上限（MB）",
                description = "缓存的已编译着色器数量上限，调大能减少重复编译，代价是占用更多存储。",
                kind = ConfigFieldKind.NUMBER,
                defaultValue = 32,
                target = ConfigTarget.Json("maxGlslCacheSize"),
                range = -1..512,
                group = "性能"
            ),
            RendererConfigField(
                id = "enableExtTimerQuery",
                title = "Timer Query 扩展",
                description = "提供 GPU 计时查询，部分光影与性能模组会用到。",
                kind = ConfigFieldKind.SWITCH,
                defaultValue = true,
                target = ConfigTarget.Json("enableExtTimerQuery"),
                group = "扩展"
            ),
            RendererConfigField(
                id = "enableExtComputeShader",
                title = "计算着色器扩展",
                description = "暴露 compute shader。个别模组需要，开启后可能增加驱动负担。",
                kind = ConfigFieldKind.SWITCH,
                defaultValue = false,
                target = ConfigTarget.Json("enableExtComputeShader"),
                group = "扩展"
            ),
            RendererConfigField(
                id = "enableExtDirectStateAccess",
                title = "直接状态访问扩展",
                description = "暴露 DSA 接口，可减少状态切换开销；老驱动上开启可能出问题。",
                kind = ConfigFieldKind.SWITCH,
                defaultValue = false,
                target = ConfigTarget.Json("enableExtDirectStateAccess"),
                group = "扩展"
            ),
            RendererConfigField(
                id = "fsr1Setting",
                title = "FSR 1 缩放",
                description = "用 FSR 1 做画面缩放，帧率不够时可以开一档，画质会略有损失。",
                kind = ConfigFieldKind.CHOICE,
                defaultValue = 0,
                target = ConfigTarget.Json("fsr1Setting"),
                options = listOf(
                    ConfigOption(0, "关闭"),
                    ConfigOption(1, "超高质量"),
                    ConfigOption(2, "质量"),
                    ConfigOption(3, "均衡"),
                    ConfigOption(4, "性能")
                ),
                group = "画质"
            )
        )
    )

    private val all: Map<String, RendererConfigSpec> = listOf(MOBILE_GLUES).associateBy { it.rendererId }

    /** 该渲染器是否可配置；不可配置时界面不显示入口 */
    fun of(rendererId: String): RendererConfigSpec? =
        all.entries.firstOrNull { it.key.equals(rendererId, ignoreCase = true) }?.value
}
