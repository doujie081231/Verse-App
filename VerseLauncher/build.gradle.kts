plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.verse.explorer.launcher"
    compileSdk = 35
    ndkVersion = "29.0.14206865"

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        // 与 JvmLauncher 一致:同一套 CMake 产出 verseexec(JVMLoader native)
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                abiFilters += listOf("x86_64", "arm64-v8a")
            }
        }
        ndk {
            abiFilters += listOf("x86_64", "arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        jniLibs {
            pickFirsts += "**/*.so"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // 单元测试跑在本地 JVM：android.jar 为桩实现，默认调用抛 "not mocked"。
        // 开启后桩方法返回默认值（0/null/false），使触及 android.util.Log 的
        // 生产代码可在纯 JVM 测试中运行
        unitTests.isReturnDefaultValues = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

tasks.register<Copy>("copyBootstrapJarToAssets") {
    dependsOn("bundleLibRuntimeToJarDebug")

    from(layout.buildDirectory.file("intermediates/runtime_library_classes_jar/debug/bundleLibRuntimeToJarDebug/classes.jar"))
    into(file("$rootDir/VerseApp/src/main/assets/game"))
    rename { "verse-bootstrap.jar" }
}

dependencies {
    implementation(project(":VerseCore"))
    implementation(project(":JvmLauncher"))
    implementation(project(":FclCore"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
