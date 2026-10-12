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
// ????? -Darch=arm64 ? local.properties ?? arch=arm64
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
        versionCode = 199
        versionName = "2.4.63"

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
            // ???????:?? run-as ???? + kill -3/debuggerd ???????????
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

    // backdrop 2.0.0 ?????? lifecycle lint ???,? AGP 8.7.3 ?? lint ???
    // (IncompatibleClassChangeError: NonNullableMutableLiveDataDetector),?????? lint
    lint {
        checkReleaseBuilds = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // FCL ??:????????? nativeLibraryDir,??
        // 1) ??? availableRenderers ??? File.exists() ???????(????????)
        // 2) NativesDeployer ??? nativeLibraryDir ???????????(???????????)
        jniLibs {
            useLegacyPackaging = true
            // lwjgl ??? Pojav ???(lwjgl-glfw-classes.jar + Pojav liblwjgl*.so):
            // lwjgl3-natives-release.aar ?????? lwjgl native,???? jniLibs ?? Pojav ??
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
                    // ? arch ??????,?? -Darch ????? merge assets,?? JRE ??????
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
    // ???????/?????????????(??+??+??+????)
    implementation("io.github.kyant0:backdrop-android:2.0.0")
    implementation("io.github.kyant0:shapes-android:1.2.0")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("io.coil-kt:coil-compose:2.7.0")
    // SVG ?????(???????):coil-svg ? androidsvg ? runtime ???,????????????
    implementation("io.coil-kt:coil-svg:2.7.0")
    implementation("com.caverock:androidsvg-aar:1.4")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("org.tukaani:xz:1.10")
    // Markdown ??(?? PC marked ?:Modrinth body ? CommonMark)
    implementation("io.noties.markwon:core:4.6.2")
    // Pojav ????????
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
