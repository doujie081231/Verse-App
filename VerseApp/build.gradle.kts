import com.android.build.api.variant.FilterConfiguration.FilterType.ABI
import com.android.build.gradle.tasks.MergeSourceSetFolders
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val localProperties = Properties().apply {
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        localPropsFile.inputStream().use { load(it) }
    }
}
// 架构过滤:可通过 -Darch=arm64 或 local.properties 中的 arch=arm64 指定
if (System.getProperty("arch") == null) {
    localProperties.getProperty("arch")?.let { System.setProperty("arch", it) }
}

android {
    namespace = "com.verse.explorer.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.verse.explorer"
        minSdk = 26
        targetSdk = 35
        versionCode = 18
        versionName = "0.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    splits {
        val arch = System.getProperty("arch", "all")
        if (arch != "all") {
            abi {
                isEnable = true
                reset()
                when (arch) {
                    "arm" -> include("armeabi-v7a")
                    "arm64" -> include("arm64-v8a")
                    "x86" -> include("x86")
                    "x86_64" -> include("x86_64")
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 便于崩溃分析:保留 run-as 数据访问 + kill -3/debuggerd 抓取现场堆栈
            isDebuggable = true
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // backdrop 2.0.0 的依赖会触发 lifecycle lint 误报,曾导致 lint 检查直接崩溃
    // (IncompatibleClassChangeError: NonNullableMutableLiveDataDetector),因此关闭 lint 检查
    lint {
        checkReleaseBuilds = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // 必须保留解压安装:运行时需要直接访问 nativeLibraryDir 下的 so 文件
        // 1) 渲染器枚举 availableRenderers 依赖 File.exists() 判断(压缩在 APK 内无法判断)
        // 2) NativesDeployer 需要从 nativeLibraryDir 部署动态库到游戏目录(无法读取压缩包)
        jniLibs {
            useLegacyPackaging = true
            // lwjgl 动态库存在多来源重复(lwjgl-glfw-classes.jar + liblwjgl*.so):
            // lwjgl3-natives-release.aar 自带的 lwjgl native 与 jniLibs 中的产物重复,按列表去重
            pickFirsts += listOf("**/liblwjgl*.so", "**/libfreetype.so", "**/libshaderc.so")
        }
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                val abi = output.getFilter(ABI)?.identifier ?: "all"
                output.outputFileName =
                    "Verse-${android.defaultConfig.versionName}-${variant.buildType}-${abi}.apk"

                val variantName = variant.name.replaceFirstChar { it.uppercaseChar() }
                afterEvaluate {
                    val task =
                        tasks.named("merge${variantName}Assets").get() as MergeSourceSetFolders
                    // 指定 arch 时,把该属性声明为 merge assets 的输入,使 JRE 运行时按架构重新过滤
                    task.inputs.property("verseArch", System.getProperty("arch", "all"))
                    task.doLast {
                        val arch = System.getProperty("arch", "all")
                        val assetsDir = task.outputDir.get().asFile
                        val jreList = listOf("jre8", "jre11", "jre17", "jre21", "jre25")
                        jreList.forEach { jre ->
                            val runtimeDir = File(assetsDir, "app_runtime/java/$jre")
                            runtimeDir.listFiles()?.forEach { file ->
                                if (arch != "all" &&
                                    file.name != "version" &&
                                    !file.name.contains("universal") &&
                                    file.name != "bin-${arch}.tar.xz"
                                ) {
                                    file.delete()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

// MaterialKolor 传递引入的 JB Compose Multiplatform material3 会抬高 androidx material3 的解析版本，
// 强制回 Compose BOM 2025.12.00 锚定的 1.4.0，避免既有 UI 代码（AlertDialog 等 API）漂移
configurations.all {
    resolutionStrategy {
        force("androidx.compose.material3:material3:1.4.0")
        force("androidx.compose.material3:material3-android:1.4.0")
    }
}

tasks.named("preBuild") {
    dependsOn(":VerseLauncher:copyBootstrapJarToAssets")
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))

    implementation(project(":VerseCore"))
    implementation(project(":VerseLibrary"))
    implementation(project(":VerseLauncher"))
    implementation(project(":JvmLauncher"))
    implementation(project(":LayerController"))
    implementation(project(":Terracotta"))
    implementation(project(":CardGrid"))
    implementation(project(":FclCore"))

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2025.12.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    // Apple Liquid Glass(Kyant0/AndroidLiquidGlass Backdrop 2.0.0,Apache 2.0):
    // 液态玻璃渲染依赖(背景+模糊+折射+高光的组合效果)
    implementation("io.github.kyant0:backdrop-android:2.0.0")
    implementation("io.github.kyant0:shapes-android:1.2.0")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("io.coil-kt:coil-compose:2.7.0")
    // 背景视频播放（ExoPlayer + PlayerView：裁剪铺满、循环、音量调节）
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    // 背景图片支持 GIF 动图（Coil GIF 解码器）
    implementation("io.coil-kt:coil-gif:2.7.0")
    // 自定义取色主题：按种子颜色与配色风格生成完整 Material 配色方案
    // （4.1.1 与本工程 AGP 8.13.2 / Compose BOM 2025.12.00 兼容；5.x 需 AGP 9.1+）
    implementation("com.materialkolor:material-kolor:4.1.1")
    // 页面切换动效库（fornewid/material-motion-compose，Apache 2.0）：
    // Material 动效规范的 Compose 实现（FadeThrough/SharedAxis），用于导航过渡
    implementation("io.github.fornewid:material-motion-compose-core:1.2.1")
    // SVG 渲染支持(用于矢量图标):coil-svg 与 androidsvg 均在运行时解析,无需预转换资源
    implementation("io.coil-kt:coil-svg:2.7.0")
    implementation("com.caverock:androidsvg-aar:1.4")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("org.tukaani:xz:1.10")
    // Markdown 渲染(与 PC 端一致:Modrinth body 使用 CommonMark 规则)
    implementation("io.noties.markwon:core:4.6.2")
    // 游戏内虚拟操控依赖
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.github.Mathias-Boulay:virtual-joystick-android:1.14")

    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
