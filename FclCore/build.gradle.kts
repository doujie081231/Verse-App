plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    // 与 FCL 一致：R / BuildConfig 必须生成在 com.tungsten.fcl 包下，
    // 直搬源码里的 com.tungsten.fcl.R / BuildConfig / FCLApp 引用才能命中。
    namespace = "com.tungsten.fcl"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets["main"].java.setSrcDirs(listOf("src/main/java"))
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.annotation:annotation:1.8.2")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("commons-io:commons-io:2.15.1")
    implementation("org.apache.commons:commons-lang3:3.14.0")
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("org.tukaani:xz:1.9")
    implementation("org.jenkins-ci:constant-pool-scanner:1.2")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("org.glavo:chardet:2.5.0")
    implementation("com.github.junrar:junrar:7.5.5")
    implementation("com.moandjiezana.toml:toml4j:0.7.2")
    implementation("com.github.steveice10:opennbt:1.5")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("org.jetbrains:annotations:24.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}