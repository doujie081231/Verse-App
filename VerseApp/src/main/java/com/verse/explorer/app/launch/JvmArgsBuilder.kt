package com.verse.explorer.app.launch

object JvmArgsBuilder {
    const val DEFAULT_MAX_MEMORY = 2048
    const val DEFAULT_MIN_MEMORY = 512

    val GC_TYPES = listOf("G1GC", "Shenandoah", "ZGC", "SerialGC", "ParallelGC")

    val SUPPORTED_GC: Map<Int, List<String>> = mapOf(
        8 to listOf("SerialGC", "ParallelGC", "ConcMarkSweepGC", "G1GC"),
        11 to listOf("SerialGC", "ParallelGC", "G1GC", "Shenandoah"),
        17 to listOf("SerialGC", "ParallelGC", "G1GC", "Shenandoah", "ZGC"),
        21 to listOf("SerialGC", "ParallelGC", "G1GC", "Shenandoah", "ZGC"),
        25 to listOf("SerialGC", "ParallelGC", "G1GC", "Shenandoah", "ZGC")
    )

    private val VERSION_SPECIFIC_ARGS: Map<Int, List<String>> = mapOf(
        8 to listOf(
            "-Djava.util.Arrays.useLegacyMergeSort=true"
        ),
        11 to listOf(
            "-XX:G1NewSizePercent=20",
            "-XX:G1ReservePercent=20",
            "-XX:MaxGCPauseMillis=50",
            "-XX:G1HeapRegionSize=32M"
        ),
        17 to listOf(
            "-Dlog4j2.formatMsgNoLookups=true"
        ),
        21 to listOf(
            "-Dlog4j2.formatMsgNoLookups=true"
        ),
        25 to listOf(
            "-Dlog4j2.formatMsgNoLookups=true"
        )
    )

    fun getDefaultJvmArgs(javaVersion: Int): String {
        val args = mutableListOf<String>()

        val versionArgs = VERSION_SPECIFIC_ARGS[javaVersion] ?: VERSION_SPECIFIC_ARGS[17]!!
        args.addAll(versionArgs)

        return args.joinToString(" ")
    }

    fun getJvmArgsForGC(javaVersion: Int, gcType: String): String {
        val args = mutableListOf<String>()

        when (gcType) {
            "Shenandoah" -> {
                args.add("-XX:+UseShenandoahGC")
                args.add("-XX:ShenandoahGCHeuristics=adaptive")
                args.add("-XX:ShenandoahAllocSpikeFactor=4")
                args.add("-XX:ShenandoahPacing=true")
            }
            "ZGC" -> {
                args.add("-XX:+UseZGC")
                args.add("-XX:+ZGenerational")
                args.add("-XX:ConcGCThreads=4")
                args.add("-XX:ParallelGCThreads=4")
            }
            "SerialGC" -> {
                args.add("-XX:+UseSerialGC")
            }
            "ParallelGC" -> {
                args.add("-XX:+UseParallelGC")
            }
            else -> {
                args.add("-XX:+UseG1GC")
                args.add("-XX:+UnlockExperimentalVMOptions")
                args.add("-XX:G1NewSizePercent=20")
                args.add("-XX:G1ReservePercent=20")
                args.add("-XX:MaxGCPauseMillis=50")
                args.add("-XX:G1HeapRegionSize=32M")
            }
        }

        if (javaVersion <= 8) {
            args.add("-XX:MaxPermSize=256M")
        }

        if (javaVersion >= 17) {
            args.add("-Dlog4j2.formatMsgNoLookups=true")
        }

        return args.joinToString(" ")
    }

    fun applyCustomJvmArgs(baseArgs: String, customArgs: String): String {
        if (customArgs.isBlank()) return baseArgs
        return "$baseArgs $customArgs"
    }

    fun parseMemoryArgs(maxMemory: Int, minMemory: Int): String {
        return "-Xmx${maxMemory}M -Xms${minMemory}M"
    }

    fun getCommonSystemProps(): List<String> = listOf(
        "-Dfml.ignoreInvalidMinecraftCertificates=true",
        "-Dfml.ignorePatchDiscrepancies=true",
        "-Dfml.earlyprogresswindow=false",
        "-Djava.awt.headless=true",
        "-Djava.rmi.server.useCodebaseOnly=true",
        "-Dcom.sun.jndi.rmi.object.trustURLCodebase=false",
        "-Dcom.sun.jndi.cosnaming.object.trustURLCodebase=false",
        "-Dfile.encoding=UTF-8",
        "-Dsun.stdout.encoding=UTF-8",
        "-Dsun.stderr.encoding=UTF-8"
    )

    fun buildFullJvmArgs(
        javaVersion: Int,
        maxMemory: Int = DEFAULT_MAX_MEMORY,
        minMemory: Int = DEFAULT_MIN_MEMORY,
        gcType: String = "G1GC",
        customArgs: String = ""
    ): String {
        val parts = mutableListOf<String>()

        parts.add(parseMemoryArgs(maxMemory, minMemory))
        parts.add(getJvmArgsForGC(javaVersion, gcType))
        parts.addAll(getCommonSystemProps())
        parts.add(getDefaultJvmArgs(javaVersion))

        return applyCustomJvmArgs(parts.joinToString(" "), customArgs)
    }

    fun getJvmArgsPreview(javaVersion: Int, gcType: String): List<String> {
        val args = mutableListOf<String>()
        args.add("-Xmx${DEFAULT_MAX_MEMORY}M")
        args.add("-Xms${DEFAULT_MIN_MEMORY}M")

        when (gcType) {
            "Shenandoah" -> {
                args.add("-XX:+UseShenandoahGC")
                args.add("-XX:ShenandoahGCHeuristics=adaptive")
            }
            "ZGC" -> {
                args.add("-XX:+UseZGC")
                args.add("-XX:+ZGenerational")
            }
            "SerialGC" -> args.add("-XX:+UseSerialGC")
            "ParallelGC" -> args.add("-XX:+UseParallelGC")
            else -> {
                args.add("-XX:+UseG1GC")
                args.add("-XX:+UnlockExperimentalVMOptions")
                args.add("-XX:G1NewSizePercent=20")
                args.add("-XX:MaxGCPauseMillis=50")
            }
        }

        if (javaVersion <= 8) args.add("-XX:MaxPermSize=256M")
        if (javaVersion >= 17) args.add("-Dlog4j2.formatMsgNoLookups=true")

        return args
    }
}