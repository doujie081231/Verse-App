/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.verse.explorer.launcher.fcl.launch;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.google.gson.GsonBuilder;
import com.mio.JavaManager;
import com.mio.data.Renderer;
import com.tungsten.fclauncher.FCLConfig;
import com.tungsten.fclauncher.FCLauncher;
import com.tungsten.fclauncher.bridge.FCLBridge;
import com.tungsten.fclauncher.plugins.NativeLibPlugin;
import com.tungsten.fclauncher.utils.Architecture;
import com.verse.explorer.launcher.fcl.auth.AuthInfo;
import com.verse.explorer.launcher.fcl.game.Argument;
import com.verse.explorer.launcher.fcl.game.Arguments;
import com.verse.explorer.launcher.fcl.game.GameRepository;
import com.verse.explorer.launcher.fcl.game.JavaVersion;
import com.verse.explorer.launcher.fcl.game.LaunchOptions;
import com.verse.explorer.launcher.fcl.game.Version;
import com.verse.explorer.launcher.fcl.io.FileUtils;
import com.verse.explorer.launcher.fcl.io.IOUtils;
import com.verse.explorer.launcher.fcl.util.CommandBuilder;
import com.verse.explorer.launcher.fcl.util.FCLPath;
import com.verse.explorer.launcher.fcl.util.ServerAddress;
import com.verse.explorer.launcher.fcl.util.StringUtils;
import com.verse.explorer.launcher.fcl.util.versioning.GameVersionNumber;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class DefaultLauncher extends Launcher {
    private String jnaVersion;
    private String nativesDirectory;

    public DefaultLauncher(Context context, GameRepository repository, Version version, AuthInfo authInfo, LaunchOptions options) {
        super(context, repository, version, authInfo, options);
    }

    private CommandBuilder generateCommandLine() throws IOException {
        CommandBuilder res = new CommandBuilder();

        getCacioJavaArgs(res, version, options);

        res.addAllWithoutParsing(options.getJavaArguments().stream().filter(arg -> !arg.equals("noXmx")).collect(Collectors.toList()));

        if (options.getMaxMemory() != null && options.getMaxMemory() > 0)
            res.addDefault("-Xmx", options.getMaxMemory() + "m");

        if (options.getMinMemory() != null && options.getMinMemory() > 0
                && (options.getMaxMemory() == null || options.getMinMemory() <= options.getMaxMemory()))
            res.addDefault("-Xms", options.getMinMemory() + "m");

        Charset encoding = Charset.defaultCharset();
        String fileEncoding = res.addDefault("-Dfile.encoding=", encoding.name());
        if (fileEncoding != null && !"-Dfile.encoding=COMPAT".equals(fileEncoding)) {
            try {
                encoding = Charset.forName(fileEncoding.substring("-Dfile.encoding=".length()));
            } catch (Throwable ex) {
                Log.w("DefaultLauncher", "Bad file encoding", ex);
            }
        }
        if (options.getJava().getVersion() < 19) {
            res.addDefault("-Dsun.stdout.encoding=", encoding.name());
            res.addDefault("-Dsun.stderr.encoding=", encoding.name());
        } else {
            res.addDefault("-Dstdout.encoding=", encoding.name());
            res.addDefault("-Dstderr.encoding=", encoding.name());
        }

        res.addDefault("-Djava.rmi.server.useCodebaseOnly=", "true");
        res.addDefault("-Dcom.sun.jndi.rmi.object.trustURLCodebase=", "false");
        res.addDefault("-Dcom.sun.jndi.cosnaming.object.trustURLCodebase=", "false");

        String formatMsgNoLookups = res.addDefault("-Dlog4j2.formatMsgNoLookups=", "true");
        if (isUsingLog4j() && (options.isDebugLog() || !"-Dlog4j2.formatMsgNoLookups=false".equals(formatMsgNoLookups))) {
            res.addDefault("-Dlog4j.configurationFile=", getLog4jConfigurationFile().getAbsolutePath());
        }

        appendJvmArgs(res);

        res.addDefault("-Dminecraft.client.jar=", repository.getVersionJar(version).toString());

        if (Architecture.is32BitsDevice()) {
            res.addDefault("-Xss", "1m");
        }

        res.addDefault("-XX:ActiveProcessorCount=", String.valueOf(Runtime.getRuntime().availableProcessors()));

        // 规避 aarch64 JIT 编译器 bug（与 JvmArgsBuilder 的修复对齐）：
        // JDK 21/25 在 aarch64 上 C2 编译渲染相关热代码时可能触发
        // "assembler_aarch64.hpp guarantee(val < (1ULL << nbits)) failed: Field too big for insn"
        // 导致渲染线程启动期 libjvm.so 内 SIGSEGV（本机 26.2 + JDK 25 即复现此崩溃）。
        // 限制 JIT 到 C1 可彻底规避。
        if (options.getJava().getVersion() >= 21) {
            res.add("-XX:TieredStopAtLevel=1");
        }

        // 把 JVM 崩溃报告（hs_err_pid*.log）重定向到外部可访问目录，便于无 root 取回分析。
        try {
            File extBase = context.getExternalFilesDir(null);
            if (extBase != null) {
                File diagDir = new File(new File(extBase, "VerseExplorerX"), "logs");
                diagDir.mkdirs();
                res.addDefault("-XX:ErrorFile=", new File(diagDir, "hs_err_pid%p.log").getAbsolutePath());
            }
        } catch (Throwable ignored) {
        }

        res.addDefault("-Dfml.ignoreInvalidMinecraftCertificates=", "true");
        res.addDefault("-Dfml.ignorePatchDiscrepancies=", "true");

        JavaVersion javaVersion = options.getJava().isAuto() ? JavaManager.getSuitableJavaVersion(version) : options.getJava();
        res.addDefault("-Dext.net.resolvPath=", FCLPath.JAVA_PATH + "/resolv.conf");
        res.addDefault("-Djava.io.tmpdir=", FCLPath.CACHE_DIR);
        res.addDefault("-Dos.name=", "Linux");
        res.addDefault("-Dos.version=Android-", Build.VERSION.RELEASE);
        // 让 LWJGL 的 org.lwjgl.opengl 绑定加载渲染器的 GL 库（FCLauncher.rebaseArgs 会把 ${gl_lib_name}
        // 替换为 renderer.getGLPath()）。缺少该参数时 LWJGL 默认找 libGL.so.1，Android 上不存在，导致
        // GL.create 抛 UnsatisfiedLinkError: Failed to locate library: libGL.so.1。
        res.addDefault("-Dorg.lwjgl.opengl.libname=", "${gl_lib_name}");
        res.addDefault("-Dorg.lwjgl.freetype.libname=", context.getApplicationInfo().nativeLibraryDir + "/libfreetype.so");
        res.addDefault("-Dfml.earlyprogresswindow=", "false");
        res.addDefault("-Dglfwstub.windowWidth=", options.getWidth() + "");
        res.addDefault("-Dglfwstub.windowHeight=", options.getHeight() + "");
        res.addDefault("-Dglfwstub.initEgl=", "false");
        res.addDefault("-Dloader.disable_forked_guis=", "true");
        res.addDefault("-Duser.home=", options.getGameDir().getAbsolutePath());
        res.addDefault("-Duser.language=", System.getProperty("user.language"));
        res.addDefault("-Duser.country=", Locale.getDefault().getCountry());
        res.addDefault("-Duser.timezone=", TimeZone.getDefault().getID());
        res.addDefault("-Dorg.lwjgl.vulkan.libname=", "libvulkan.so");
        res.addDefault("-Dorg.lwjgl.spvc.libname=", "spirv-cross-c-shared");
        res.addDefault("-Dsodium.checks.issue2561=", "false");
        res.addDefault("-Djdk.lang.Process.launchMechanism=", "FORK");
        res.addDefault("-Dcpu.name=", FCLauncher.getSocName());
        NativeLibPlugin.getJVMEnv().forEach((k, v) -> res.addDefault(k + "=", v));
        if (nativesDirectory != null && !nativesDirectory.isEmpty()) {
            res.addDefault("-Djava.library.path=", nativesDirectory);
            res.addDefault("-Dorg.lwjgl.librarypath=", nativesDirectory);
        }
        // LWJGL 分两个模块加载：
        // 1) system 模块（org.lwjgl.libname=lwjgl）：加载 liblwjgl.so，提供 JNI.call* 等
        //    ffi 桥接方法以及 ThreadLocalUtil.setupEnvData。
        // 2) glfw 模块（org.lwjgl.glfw.libname）：游戏侧 GLFW.class 静态块硬编码
        //    System.loadLibrary("pojavexec")，此参数不生效；对齐 FCL，保留 poemav exec。
        //    启动崩溃根因是"JVM 先于 ART 加载 poemav exec"导致 JNI_OnLoad 分支反转，
        //    已由 FCLauncher 在 JVM 启动前同步预加载 CallbackBridge(Art 侧) 修复
        //    （见 FCLauncher.preloadCallbackBridge 注释），无需更换 glfw 库。
        //
        // 注意：pojavexec 使用裸名，不能带 "lib" 前缀或 ".so" 后缀。
        res.addDefault("-Dorg.lwjgl.glfw.libname=", "pojavexec");
        res.addDefault("-Dorg.lwjgl.libname=", "lwjgl");
        res.addDefault("-Dorg.lwjgl.util.Debug=", "true");
        res.addDefault("-Dorg.lwjgl.util.DebugLoader=", "true");
        File libJna = new File(FCLPath.RUNTIME_DIR, "jna");
        if (jnaVersion != null && !jnaVersion.isEmpty()) {
            libJna = new File(libJna, jnaVersion);
        }
        res.addDefault("-Djna.boot.library.path=", libJna.exists() ? libJna.getAbsolutePath() : context.getApplicationInfo().nativeLibraryDir);

        if (getInjectorArg() != null && options.isBeGesture()) {
            res.addDefault("-Dfcl.injector=", getInjectorArg());
        }

        if (repository.getGameVersion(version).isPresent() && repository.getGameVersion(version).get().equals("1.7.2")) {
            res.addDefault("-Dsort.patch=", "true");
        }

        res.add("-javaagent:" + FCLPath.LIB_PATCHER_PATH);

        Set<String> classpath = repository.getClasspath(version);
        replaceJnaWithLocal(classpath);
        replaceLwjglWithLocal(classpath);
        classpath.add(FCLPath.MIO_LAUNCH_WRAPPER);
        File jar = repository.getVersionJar(version);
        if (!jar.exists() || !jar.isFile()) {
            String inherits = version.getInheritsFrom();
            if (inherits != null && !inherits.isEmpty()) {
                jar = repository.getVersionJar(inherits);
            }
        }
        classpath.add(jar.getAbsolutePath());
        // 注：Forge 1.20.5+ / NeoForge 的 module 参数（-p / --add-modules）由加载器
        // 版本 JSON 的 jvm 参数自带（如 NeoForge -p security:asm:bootstraplauncher），
        // 此处仅负责展开 ${library_directory}/${classpath} 等占位符即可，不自行注入，
        // 否则把全部 classpath 当 module path 会触发 log4j 等模块导出冲突。

        Path gameAssets = repository.getActualAssetDirectory(version.getId(), version.getAssetIndex().getId());
        Map<String, String> configuration = getConfigurations();
        configuration.put("${classpath}", String.join(File.pathSeparator, classpath));
        configuration.put("${game_assets}", gameAssets.toAbsolutePath().toString());
        configuration.put("${assets_root}", gameAssets.toAbsolutePath().toString());

        configuration.put("${natives_directory}", nativesDirectory != null ? nativesDirectory : "");
        List<String> jvmArgs = Arguments.parseArguments(version.getArguments().map(Arguments::getJvm).orElseGet(this::getDefaultJVMArguments), configuration);
        // NeoForge/Forge（bootstraplauncher 链路）：把实际加入 classpath 的游戏 JAR 文件名
        // 追加到 -DignoreList，让 bootstraplauncher 跳过 Jigsaw 模块扫描。
        // -DignoreList 字面量只覆盖补丁版本目录名（如 1.21.1-neoforge-21.1.1.jar），但实际
        // 游戏 JAR 沿 inheritsFrom 解析为原版 jar（1.21.1.jar），文件名为模块名 _1._21._1，
        // 与 Mojang 补丁 srg jar 的 minecraft 模块导出同一包 → ResolutionException。
        // 对应 FCL MaintainTask.updateIgnoreList 的 ${primary_jar} 语义。
        for (int i = 0; i < jvmArgs.size(); i++) {
            String arg = jvmArgs.get(i);
            if (arg.startsWith("-DignoreList=")) {
                String ignoreList = arg.substring("-DignoreList=".length());
                String jarName = jar.getName();
                if (!ignoreList.contains(jarName)) {
                    jvmArgs.set(i, "-DignoreList=" + ignoreList + "," + jarName);
                }
                break;
            }
        }
        res.addAll(jvmArgs);
        Arguments argumentsFromAuthInfo = authInfo.getLaunchArguments(options);
        if (argumentsFromAuthInfo != null && argumentsFromAuthInfo.getJvm() != null && !argumentsFromAuthInfo.getJvm().isEmpty())
            res.addAll(Arguments.parseArguments(argumentsFromAuthInfo.getJvm(), configuration));

        if (javaVersion.getVersion() != JavaVersion.JAVA_VERSION_8) {
            res.add("--add-exports");
            String pkg = version.getMainClass().substring(0, version.getMainClass().lastIndexOf("."));
            res.add(pkg + "/" + pkg + "=ALL-UNNAMED");
        }

        res.add("mio.Wrapper");
        res.add(version.getMainClass());

        res.addAll(Arguments.parseStringArguments(version.getMinecraftArguments().map(StringUtils::tokenize).orElseGet(ArrayList::new), configuration));

        Map<String, Boolean> features = getFeatures();
        version.getArguments().map(Arguments::getGame).ifPresent(arguments -> res.addAll(Arguments.parseArguments(arguments, configuration, features)));
        if (version.getMinecraftArguments().isPresent()) {
            res.addAll(Arguments.parseArguments(this.getDefaultGameArguments(), configuration, features));
        }
        if (argumentsFromAuthInfo != null && argumentsFromAuthInfo.getGame() != null && !argumentsFromAuthInfo.getGame().isEmpty())
            res.addAll(Arguments.parseArguments(argumentsFromAuthInfo.getGame(), configuration, features));

        String address = options.getServerIp();
        if (StringUtils.isNotBlank(address)) {
            try {
                ServerAddress parsed = ServerAddress.parse(address);
                if (GameVersionNumber.compare(repository.getGameVersion(version).orElse("0.0"), "1.20") < 0) {
                    res.add("--server");
                    res.add(parsed.getHost());
                    res.add("--port");
                    res.add(parsed.getPort() >= 0 ? String.valueOf(parsed.getPort()) : "25565");
                } else {
                    res.add("--quickPlayMultiplayer");
                    res.add(parsed.getPort() < 0 ? address + ":25565" : address);
                }
            } catch (IllegalArgumentException e) {
                Log.w("DefaultLauncher", "Invalid server address: " + address + "\n" + e);
            }
        }

        res.addAllWithoutParsing(Arguments.parseStringArguments(options.getGameArguments(), configuration));

        res.removeIf(it -> getForbiddens().containsKey(it) && getForbiddens().get(it).get());
        return res;
    }

    public static void getCacioJavaArgs(CommandBuilder res, Version version, LaunchOptions options) {
        JavaVersion javaVersion;
        if (options.getJava().isAuto()) {
            javaVersion = JavaManager.getSuitableJavaVersion(version);
        } else {
            javaVersion = options.getJava();
        }
        boolean isJava8 = javaVersion.getVersion() == JavaVersion.JAVA_VERSION_8;

        res.addDefault("-Djava.awt.headless=", "false");
        res.addDefault("-Dcacio.managed.screensize=", options.getWidth() + "x" + options.getHeight());
        res.addDefault("-Dcacio.font.fontmanager=", "sun.awt.X11FontManager");
        res.addDefault("-Dcacio.font.fontscaler=", "sun.font.FreetypeFontScaler");
        res.addDefault("-Dswing.defaultlaf=", "javax.swing.plaf.nimbus.NimbusLookAndFeel");
        if (isJava8) {
            res.addDefault("-Dawt.toolkit=", "net.java.openjdk.cacio.ctc.CTCToolkit");
            res.addDefault("-Djava.awt.graphicsenv=", "net.java.openjdk.cacio.ctc.CTCGraphicsEnvironment");
        } else {
            res.addDefault("-Dawt.toolkit=", "com.github.caciocavallosilano.cacio.ctc.CTCToolkit");
            res.addDefault("-Djava.awt.graphicsenv=", "com.github.caciocavallosilano.cacio.ctc.CTCGraphicsEnvironment");
            res.addDefault("-javaagent:", FCLPath.CACIOCAVALLO_17_DIR + "/cacio-agent.jar");

            res.add("--add-exports=java.desktop/java.awt=ALL-UNNAMED");
            res.add("--add-exports=java.desktop/java.awt.peer=ALL-UNNAMED");
            res.add("--add-exports=java.desktop/sun.awt.image=ALL-UNNAMED");
            res.add("--add-exports=java.desktop/sun.java2d=ALL-UNNAMED");
            res.add("--add-exports=java.desktop/java.awt.dnd.peer=ALL-UNNAMED");
            res.add("--add-exports=java.desktop/sun.awt=ALL-UNNAMED");
            res.add("--add-exports=java.desktop/sun.awt.event=ALL-UNNAMED");
            res.add("--add-exports=java.desktop/sun.awt.datatransfer=ALL-UNNAMED");
            res.add("--add-exports=java.desktop/sun.font=ALL-UNNAMED");
            res.add("--add-exports=java.base/sun.security.action=ALL-UNNAMED");
            res.add("--add-opens=java.base/java.util=ALL-UNNAMED");
            res.add("--add-opens=java.desktop/java.awt=ALL-UNNAMED");
            res.add("--add-opens=java.desktop/sun.font=ALL-UNNAMED");
            res.add("--add-opens=java.desktop/sun.java2d=ALL-UNNAMED");
            res.add("--add-opens=java.base/java.lang.reflect=ALL-UNNAMED");
            res.add("--add-opens=java.base/java.net=ALL-UNNAMED");
        }

        StringBuilder cacioClasspath = new StringBuilder();
        cacioClasspath.append("-Xbootclasspath/").append(isJava8 ? "p" : "a");
        File cacioDir = new File(isJava8 ? FCLPath.CACIOCAVALLO_8_DIR : FCLPath.CACIOCAVALLO_17_DIR);
        if (cacioDir.exists() && cacioDir.isDirectory()) {
            for (File file : Objects.requireNonNull(cacioDir.listFiles())) {
                if (file.getName().endsWith(".jar")) {
                    cacioClasspath.append(":").append(file.getAbsolutePath());
                }
            }
        }
        res.add(cacioClasspath.toString());
    }

    public String getInjectorArg() {
        try {
            String map = IOUtils.readFullyAsString(DefaultLauncher.class.getResourceAsStream("/assets/map.json"));
            InjectorMap injectorMap = new GsonBuilder()
                    .setPrettyPrinting()
                    .create()
                    .fromJson(map, InjectorMap.class);
            Optional<InjectorMap.MapInfo> mapInfo = injectorMap.getMaps().stream()
                    .filter(it -> {
                        String versionTypeId = version.getAssetIndex().getId();
                        if (versionTypeId.equals("legacy") || versionTypeId.equals("pre-1.6")) {
                            versionTypeId = repository.getGameVersion(version).orElse("");
                        }
                        if (versionTypeId.equals("1.8")
                                && repository.getGameVersion(version).isPresent()
                                && (repository.getGameVersion(version).get().equals("1.8.8")
                                || repository.getGameVersion(version).get().equals("1.8.9"))) {
                            versionTypeId = "1.8.8";
                        }
                        if (versionTypeId.equals("1.9")
                                && repository.getGameVersion(version).isPresent()
                                && repository.getGameVersion(version).get().equals("1.9.4")) {
                            versionTypeId = "1.9.4";
                        }
                        return it.getId().equals(versionTypeId);
                    })
                    .findFirst();
            return mapInfo.map(it -> it.getArgument().getArgument(version, repository.getGameVersion(version).orElse(null))).orElse(null);
        } catch (IOException e) {
            Log.w("DefaultLauncher", "Failed to get game map", e);
            return null;
        }
    }

    public Map<String, Boolean> getFeatures() {
        return Collections.singletonMap(
                "has_custom_resolution",
                options.getHeight() != null && options.getHeight() != 0 && options.getWidth() != null && options.getWidth() != 0
        );
    }

    private final Map<String, Supplier<Boolean>> forbiddens = new HashMap<String, Supplier<Boolean>>() {{
        put("-Xincgc", () -> options.getJava().getVersion() >= 9);
    }};

    protected Map<String, Supplier<Boolean>> getForbiddens() {
        return forbiddens;
    }

    protected List<Argument> getDefaultJVMArguments() {
        return Arguments.DEFAULT_JVM_ARGUMENTS;
    }

    protected List<Argument> getDefaultGameArguments() {
        return Arguments.DEFAULT_GAME_ARGUMENTS;
    }

    protected void appendJvmArgs(CommandBuilder result) {
    }

    /**
     * 将 classpath 中 MC 自带的 jna 核心 jar 替换为 FCL 本地 Android 兼容版 jna.jar。
     * MC 的 jna jar（如 1.20.1 的 jna-5.12.1）内 libjnidispatch.so 是 Linux glibc 版，
     * 在 Android 上无法加载；而 FCL 的 jna.jar（7.x，native 7.0.4）为 Android 定制编译、可直接加载。
     * 若同时保留 MC 的 jna jar 与 FCL 的 7.0.4 native，会触发 JNA 版本校验失败
     * （Expected: 6.1.4, Found: 7.0.4）。因此需要把 Java 层也统一为本机 7.x jna.jar。
     * jna-platform 保留 MC 原版（FCL jna.jar 不含 platform 类），其依赖的 jna 核心 API 在 7.x 中向后兼容。
     */
    private void replaceJnaWithLocal(Set<String> classpath) {
        File localJna = new File(FCLPath.JNA_PATH, "jna.jar");
        if (!localJna.exists()) return;
        Set<String> temp = new LinkedHashSet<>();
        for (String entry : classpath) {
            if (entry.contains("net/java/dev/jna/jna/")) {
                temp.add(localJna.getAbsolutePath());
            } else {
                temp.add(entry);
            }
        }
        classpath.clear();
        classpath.addAll(temp);
    }

    /**
     * 将 classpath 中的官方 LWJGL 各模块 jar 替换为本地 LWJGL-Pojav 套装并置于最前。
     * 套装（lwjgl.jar / lwjgl-glfw.jar / lwjgl-opengl.jar / lwjgl-vma.jar /
     * lwjgl-spvc.jar / lwjgl-shaderc.jar ...）迁移自 FCL LWJGL-Pojav，
     * 与 Pojav patched liblwjgl*.so / libpojavexec.so 配对；
     * lwjgl-glfw.jar 的 GLFW 含 glfwGetMonitorName（MC 1.19.4+/26.x Monitor 必需）。
     * 官方 org.lwjgl 模块（3.4.x）的 ThreadLocalUtil 与 patched native 符号不一致，
     * 会在 GL.createCapabilities 时抛 UnsatisfiedLinkError，必须整体剔除。
     *
     * 对齐 Pojav 的 lwjglx 老版本方案：本地 lwjgl-lwjglx.jar 额外提供 LWJGL2 API 兼容层
     * （org.lwjgl.opengl.Display、org.lwjgl.input.Mouse/Keyboard、org.lwjgl.Sys 等），
     * 因此老版本（1.12.2 及以下，依赖 lwjgl-2.9.4/jinput/lwjgl_util）的官方 jar
     * 也必须一并替换并剔除。否则官方 lwjgl2 jar 会按名加载 liblwjgl.so（LWJGL2 ABI），
     * 与 natives 目录中部署的 LWJGL3 patched liblwjgl.so 冲突，渲染窗口初始化直接
     * SIGSEGV（App 进程静默退出，无任何 Java 异常日志）。
     */
    private void replaceLwjglWithLocal(Set<String> classpath) {
        File lwjglDir = new File(FCLPath.LWJGL_DIR);
        if (!lwjglDir.isDirectory()) return;
        File[] localLwjglJars = lwjglDir.listFiles((dir, name) -> name.endsWith(".jar") && new File(dir, name).length() > 1024);
        if (localLwjglJars == null || localLwjglJars.length == 0) return;
        Arrays.sort(localLwjglJars, Comparator.comparing(File::getName));
        Set<String> temp = new LinkedHashSet<>();
        for (File jar : localLwjglJars) {
            temp.add(jar.getAbsolutePath());
        }
        for (String entry : classpath) {
            if (entry.contains("/org/lwjgl/")
                    || entry.contains("/jinput/")
                    || entry.contains("lwjgl_util")) {
                continue;
            }
            temp.add(entry);
        }
        classpath.clear();
        classpath.addAll(temp);
    }

    private boolean isUsingLog4j() {
        return GameVersionNumber.compare(repository.getGameVersion(version).orElse("1.7"), "1.7") >= 0;
    }

    public File getLog4jConfigurationFile() {
        return new File(repository.getVersionRoot(version.getId()), "log4j2.xml");
    }

    public void extractLog4jConfigurationFile() throws IOException {
        File targetFile = getLog4jConfigurationFile();
        InputStream source;

        if (GameVersionNumber.compare(repository.getGameVersion(version).orElse("1.7"), "1.12") < 0) {
            if (options.isDebugLog()) {
                source = DefaultLauncher.class.getResourceAsStream("/assets/game/log4j2-1.7-debug.xml");
            } else {
                source = DefaultLauncher.class.getResourceAsStream("/assets/game/log4j2-1.7.xml");
            }
        } else {
            if (options.isDebugLog()) {
                source = DefaultLauncher.class.getResourceAsStream("/assets/game/log4j2-1.12-debug.xml");
            } else {
                source = DefaultLauncher.class.getResourceAsStream("/assets/game/log4j2-1.12.xml");
            }
        }

        try (InputStream input = source; OutputStream output = new FileOutputStream(targetFile)) {
            IOUtils.copyTo(input, output);
        }
    }

    protected Map<String, String> getConfigurations() {
        String uuid = options.getUuid().replace("-", "");
        boolean customUuid = uuid.length() == 32;
        Map<String, String> config = new HashMap<>();
        config.put("${auth_player_name}", authInfo.getUsername());
        config.put("${auth_session}", authInfo.getAccessToken());
        config.put("${auth_access_token}", authInfo.getAccessToken());
        config.put("${auth_uuid}", customUuid ? options.getUuid() : authInfo.getUUID().toString());
        config.put("${version_name}", Optional.ofNullable(options.getVersionName()).orElse(version.getId()));
        config.put("${profile_name}", Optional.ofNullable(options.getProfileName()).orElse("Minecraft"));
        config.put("${version_type}", Optional.ofNullable(options.getVersionType()).orElse(version.getType().getId()));
        config.put("${game_directory}", repository.getRunDirectory(version.getId()).getAbsolutePath());
        config.put("${user_type}", "msa");
        config.put("${assets_index_name}", version.getAssetIndex().getId());
        config.put("${user_properties}", authInfo.getUserProperties());
        config.put("${resolution_width}", options.getWidth().toString());
        config.put("${resolution_height}", options.getHeight().toString());
        config.put("${library_directory}", repository.getLibrariesDirectory(version).getAbsolutePath());
        config.put("${classpath_separator}", File.pathSeparator);
        config.put("${primary_jar}", repository.getVersionJar(version).getAbsolutePath());
        config.put("${language}", Locale.getDefault().toString());
        config.put("${file_separator}", File.pathSeparator);
        config.put("${primary_jar_name}", FileUtils.getName(repository.getVersionJar(version).toPath()));
        return config;
    }

    @Override
    public FCLBridge launch() throws IOException, InterruptedException {
        // 文件级诊断日志：launch() 崩溃在 JVM 创建前的 native 层时（进程被杀），
        // VerseLogger/launch.log 也不可写，只能靠这个独立文件定位逐步崩溃点。
        diag("launch() begin");
        Log.i("DefaultLauncher", "[launch-step] generateCommandLine ...");
        final CommandBuilder command = generateCommandLine();
        diag("generateCommandLine ok");

        List<String> rawCommandLine = command.asList();
        diag("command.asList ok, size=" + rawCommandLine.size());

        if (rawCommandLine.stream().anyMatch(StringUtils::isBlank)) {
            throw new IllegalStateException("Illegal command line " + rawCommandLine);
        }
        Log.i("DefaultLauncher", "[launch-step] command ok, args=" + rawCommandLine.size());
        diag("command blank check ok");

        if (isUsingLog4j()) {
            extractLog4jConfigurationFile();
        }
        diag("log4j extraction done");

        String[] finalArgs = rawCommandLine.toArray(new String[0]);
        Log.i("DefaultLauncher", "[launch-step] finalArgs=" + finalArgs.length);
        diag("finalArgs ok, n=" + finalArgs.length);

        Renderer renderer = options.getRenderer() != null
                ? Renderer.valueOf(options.getRenderer())
                : Renderer.GLES;
        // zink 依赖 Vulkan。现代 Android 模拟器（host GPU / swiftshader_indirect）
        // 均提供 vulkan.ranchu.so，因此 zink 可正常工作；仅当系统确无 Vulkan 驱动
        // 时才回退到不依赖 Vulkan 的 NGGL4ES（软 GL 渲染，走 GLES3）。
        if (renderer.isEqual(Renderer.ID_ZINK) && isEmulator() && !hasVulkanDriver()) {
            renderer = Renderer.valueOf(Renderer.ID_NGGL4ES);
        }
        Log.i("DefaultLauncher", "[launch-step] renderer=" + renderer.getId() + ", config 创建...");
        FCLConfig config = new FCLConfig(
                context,
                FCLPath.LOG_DIR,
                options.getJava().getJavaPath(version),
                repository.getRunDirectory(version.getId()).getAbsolutePath(),
                renderer,
                finalArgs
        );
        config.setUseVKDriverSystem(options.isVKDriverSystem());
        config.setPojavBigCore(options.isPojavBigCore());
        config.setInstalledModLoaders(new FCLConfig.InstalledModLoaders(
                false, false, false, false, false, false, false
        ));
        diag("FCLConfig 创建完成, 调用 FCLauncher.launchMinecraft ... 渲染库将在此加载 dlopen");
        return FCLauncher.launchMinecraft(config);
    }

    /** 文件级诊断日志：写到外部可访问目录，进程被杀后仍可读取（JVM 前 native 崩溃定位用） */
    private void diag(String msg) {
        try {
            File extBase = context.getExternalFilesDir(null);
            if (extBase == null) return;
            File diagDir = new File(new File(extBase, "VerseExplorerX"), "logs");
            diagDir.mkdirs();
            try (FileOutputStream fos = new FileOutputStream(new File(diagDir, "launch_diag.log"), true)) {
                String line = "[" + System.currentTimeMillis() + "] " + msg + "\n";
                fos.write(line.getBytes("UTF-8"));
            }
        } catch (Exception ignored) {
        }
    }

    public void setJnaVersion(String jnaVersion) {
        this.jnaVersion = jnaVersion;
    }

    public void setNativesDirectory(String nativesDirectory) {
        this.nativesDirectory = nativesDirectory;
    }

    private static boolean isEmulator() {
        String hardware = Build.HARDWARE == null ? "" : Build.HARDWARE.toLowerCase();
        return hardware.contains("ranchu") || hardware.contains("goldfish");
    }

    /** 检测系统是否存在可用的 Vulkan 驱动（模拟器 host GPU 提供 vulkan.ranchu.so） */
    private static boolean hasVulkanDriver() {
        try {
            final String[] drivers = new String[]{
                    "/system/lib64/hw/vulkan.ranchu.so",
                    "/vendor/lib64/hw/vulkan.ranchu.so",
                    "/system/lib64/vulkan/vulkan.ranchu.so",
                    "/vendor/lib64/vulkan/vulkan.ranchu.so"
            };
            for (String driver : drivers) {
                if (new File(driver).exists()) return true;
            }
            // Android 13+ 通过 VK_ICD_FILENAMES / apex 提供驱动，避免硬编码路径时直接放行
            // （GRALLOC/ramd 均已被 SurfaceFlinger 断言，模拟器容器默认配置了 Vulkan loader）
            return System.getProperty("ro.hardware.vulkan") != null ||
                    android.os.Build.VERSION.SDK_INT >= 33;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
