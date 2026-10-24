package com.tungsten.fclauncher;

import static android.content.Context.MODE_PRIVATE;
import static com.tungsten.fclauncher.utils.Architecture.ARCH_X86;
import static com.tungsten.fclauncher.utils.Architecture.is64BitsDevice;
import com.tungsten.fclauncher.utils.Architecture;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.system.ErrnoException;
import android.system.Os;
import android.util.ArrayMap;
import android.util.Log;

import com.mio.data.Renderer;
import com.oracle.dalvik.VMLauncher;
import com.tungsten.fclauncher.bridge.FCLBridge;
import com.tungsten.fclauncher.plugins.DriverPlugin;
import com.tungsten.fclauncher.plugins.FFmpegPlugin;
import com.tungsten.fclauncher.plugins.NativeLibPlugin;
import com.tungsten.fclauncher.utils.Architecture;
import com.tungsten.fclauncher.utils.FCLPath;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FCLauncher {

    private static int FCL_VERSION_CODE = -1;

    private static void log(FCLBridge bridge, String log) {
        bridge.getCallback().onLog(log + "\n");
    }

    private static void printTaskTitle(FCLBridge bridge, String task) {
        log(bridge, "==================== " + task + " ====================");
    }

    private static void logStartInfo(FCLConfig config, FCLBridge bridge, String task) {
        printTaskTitle(bridge, "Start " + task);
        log(bridge, "Renderer: " + bridge.getRenderer());
        log(bridge, "Device: " + getDeviceName());
        log(bridge, "Architecture: " + Architecture.archAsString(Architecture.getDeviceArchitecture()));
        log(bridge, "CPU: " + getSocName());
        log(bridge, "Android SDK: " + Build.VERSION.SDK_INT);
        log(bridge, "Language: " + Locale.getDefault());

        PackageManager pm = config.getContext().getPackageManager();
        try {
            PackageInfo packageInfo = pm.getPackageInfo(config.getContext().getPackageName(), 0);
            FCL_VERSION_CODE = packageInfo.versionCode;
            log(bridge, "FCL Version Code: " + FCL_VERSION_CODE);
        } catch (PackageManager.NameNotFoundException e) {
            log(bridge, "FCL Version Code: Can't get current version code, exception = " + e.getMessage());
        }
    }

    private static void logModList(FCLBridge bridge) {
        printTaskTitle(bridge, "Mods");
        log(bridge, bridge.getModSummary());
        bridge.setModSummary(null);
    }

    private static Map<String, String> readJREReleaseProperties(String javaPath) throws IOException {
        Map<String, String> jreReleaseMap = new ArrayMap<>();
        BufferedReader jreReleaseReader = new BufferedReader(new FileReader(javaPath + "/release"));
        String currLine;
        while ((currLine = jreReleaseReader.readLine()) != null) {
            if (currLine.contains("=")) {
                String[] keyValue = currLine.split("=");
                jreReleaseMap.put(keyValue[0], keyValue[1].replace("\"", ""));
            }
        }
        jreReleaseReader.close();
        return jreReleaseMap;
    }

    public static String getJavaLibDir(String javaPath) throws IOException {
        String architecture = readJREReleaseProperties(javaPath).get("OS_ARCH");
        if (Architecture.archAsInt(architecture) == ARCH_X86) {
            architecture = "i386/i486/i586";
        }
        String libDir = "/lib";
        if (architecture == null) {
            throw new IOException("Unsupported architecture!");
        }
        for (String arch : architecture.split("/")) {
            File file = new File(javaPath, "lib/" + arch);
            if (file.exists() && file.isDirectory()) {
                libDir = "/lib/" + arch;
            }
        }
        return libDir;
    }

    private static String getJvmLibDir(String javaPath) throws IOException {
        String jvmLibDir;
        String path = (isJDK8(javaPath) ? "/jre" : "") + getJavaLibDir(javaPath);
        File jvmFile = new File(javaPath + path + "/server/libjvm.so");
        jvmLibDir = jvmFile.exists() ? "/server" : "/client";
        return jvmLibDir;
    }

    private static String getLibraryPath(Context context, String javaPath, String pluginLibPath, String lwjglVersion) throws IOException {
        String nativeDir = context.getApplicationInfo().nativeLibraryDir;
        String libDirName = is64BitsDevice() ? "lib64" : "lib";
        String javaLibDir = getJavaLibDir(javaPath);
        String jvmLibDir = getJvmLibDir(javaPath);
        String jliLibDir = "/jli";
        String split = ":";
        String jreLibDir;
        if (isJDK8(javaPath)) {
            jreLibDir = javaPath +
                    "/jre" +
                    javaLibDir +
                    jvmLibDir +
                    split +

                    javaPath +
                    "/jre" +
                    javaLibDir;
        } else {
            jreLibDir = javaPath +
                    javaLibDir +
                    jvmLibDir;
        }

        String nativeLibPaths = NativeLibPlugin.getPaths(split);

        String lwjglNatives = lwjglVersion != null && !lwjglVersion.isEmpty()
                ? FCLPath.LWJGL_DIR + "/" + lwjglVersion + "/natives/" + Architecture.archAsStringAndroid(Architecture.getDeviceArchitecture()) + split
                : "";

        return javaPath +
                javaLibDir +
                split +

                javaPath +
                javaLibDir +
                jliLibDir +
                split +

                jreLibDir +
                split +

                "/system/" +
                libDirName +
                split +

                "/vendor/" +
                libDirName +
                split +

                "/vendor/" +
                libDirName +
                "/hw" +
                split +

                "/system_ext/" +
                libDirName +
                split +

                context.getDir("runtime", 0).getAbsolutePath() + "/jna" +
                split +

                ((pluginLibPath != null && !pluginLibPath.isEmpty()) ? pluginLibPath + split : "") +

                ((!nativeLibPaths.isEmpty() ? nativeLibPaths + split : "")) +

                FCLPath.MOD_RUNTIME_DIR +
                split +

                lwjglNatives +

                nativeDir;
    }

    private static String getLibraryPath(Context context, String pluginLibPath, String lwjglVersion) {
        String nativeDir = context.getApplicationInfo().nativeLibraryDir;
        String libDirName = is64BitsDevice() ? "lib64" : "lib";
        String split = ":";
        String nativeLibPaths = NativeLibPlugin.getPaths(split);
        String lwjglNatives = lwjglVersion != null && !lwjglVersion.isEmpty()
                ? FCLPath.LWJGL_DIR + "/" + lwjglVersion + "/natives/" + Architecture.archAsStringAndroid(Architecture.getDeviceArchitecture()) + split
                : "";
        return "/system/" +
                libDirName +
                split +

                "/vendor/" +
                libDirName +
                split +

                "/vendor/" +
                libDirName +
                "/hw" +
                split +

                "/system_ext/" +
                libDirName +
                split +

                context.getDir("runtime", 0).getAbsolutePath() + "/jna" +
                split +

                ((pluginLibPath != null && !pluginLibPath.isEmpty()) ? pluginLibPath + split : "") +

                ((!nativeLibPaths.isEmpty() && !nativeLibPaths.equals("null") ? nativeLibPaths + split : "")) +

                FCLPath.MOD_RUNTIME_DIR +
                split +

                lwjglNatives +

                nativeDir;
    }

    private static String[] rebaseArgs(FCLConfig config) throws IOException {
        ArrayList<String> argList = new ArrayList<>(Arrays.asList(config.getArgs()));
        argList.add(0, config.getJavaPath() + "/bin/java");
        String[] args = new String[argList.size()];
        for (int i = 0; i < argList.size(); i++) {
            String a = argList.get(i);
            String libraryPath = getLibraryPath(config.getContext(), config.getJavaPath(), config.getRenderer().getPath(), config.getLwjglVersion());
            if (argList.get(i).contains("-Djava.library.path")) {
                a = "-Djava.library.path=${natives_directory}";
            }
            a = a.replace("${natives_directory}", libraryPath);
            args[i] = config.getRenderer() == null ? a : a.replace("${gl_lib_name}", config.getRenderer().getGLPath());
        }
        return args;
    }

    private static void addCommonEnv(FCLConfig config, HashMap<String, String> envMap) {
        if (FCL_VERSION_CODE != -1) {
            envMap.put("FCL_VERSION_CODE", FCL_VERSION_CODE + "");
        }
        envMap.put("HOME", config.getLogDir());
        envMap.put("JAVA_HOME", config.getJavaPath());
        envMap.put("FCL_NATIVEDIR", config.getContext().getApplicationInfo().nativeLibraryDir);
        envMap.put("POJAV_NATIVEDIR", config.getContext().getApplicationInfo().nativeLibraryDir);
        envMap.put("DRIVER_PATH", DriverPlugin.getSelected().getPath());
        envMap.put("TMPDIR", config.getContext().getCacheDir().getAbsolutePath());
        envMap.put("PATH", config.getJavaPath() + "/bin:" + Os.getenv("PATH"));
        envMap.put("LD_LIBRARY_PATH", getLibraryPath(config.getContext(), config.getRenderer().getPath(), config.getLwjglVersion()));
        envMap.put("FORCE_VSYNC", "false");

        // Native mod env var
        envMap.put("MOD_ANDROID_RUNTIME", FCLPath.MOD_RUNTIME_DIR == null ? "" : FCLPath.MOD_RUNTIME_DIR);

        FFmpegPlugin.discover(config.getContext());
        if (FFmpegPlugin.isAvailable) {
            envMap.put("PATH", FFmpegPlugin.libraryPath + ":" + envMap.get("PATH"));
            envMap.put("LD_LIBRARY_PATH", FFmpegPlugin.libraryPath + ":" + envMap.get("LD_LIBRARY_PATH"));
        }
        if (config.isUseVKDriverSystem()) {
            envMap.put("VULKAN_DRIVER_SYSTEM", "1");
        }
        if (config.isPojavBigCore()) {
            envMap.put("POJAV_BIG_CORE_AFFINITY", "1");
        }
    }

    private static void addModLoaderEnv(FCLConfig config, HashMap<String, String> envMap) {
        if (config.getInstalledModLoaders() == null)
            return;

        if (config.getInstalledModLoaders().isInstallForge()) {
            envMap.put("INST_FORGE", "1");
        }
        if (config.getInstalledModLoaders().isInstallCleanroom()) {
            envMap.put("INST_CLEANROOM", "1");
        }
        if (config.getInstalledModLoaders().isInstallNeoForge()) {
            envMap.put("INST_NEOFORGE", "1");
        }
        if (config.getInstalledModLoaders().isInstallLiteLoader()) {
            envMap.put("INST_LITELOADER", "1");
        }
        if (config.getInstalledModLoaders().isInstallFabric()) {
            envMap.put("INST_FABRIC", "1");
        }
        if (config.getInstalledModLoaders().isInstallOptiFine()) {
            envMap.put("INST_OPTIFINE", "1");
        }
        if (config.getInstalledModLoaders().isInstallQuilt()) {
            envMap.put("INST_QUILT", "1");
        }
    }

    private static void addRendererEnv(FCLConfig config, HashMap<String, String> envMap) {
        addRendererEnvInner(config, envMap);
        // SDL 需要独立的 GL/EGL 库绝对路径：
        // MC 26.x（RenderPearl）校验 SDL_GL_GetProcAddress("glGetError") 与 LWJGL provider 同源，
        // SDL 侧必须解析自渲染器 GL 库（NGGL4ES=libng_gl4es.so）。插件渲染器同样生效。
        Renderer renderer = config.getRenderer();
        String egl = envMap.get("POJAVEXEC_EGL");
        // MobileGlues 的 EGL 对 SDL 的 window surface 创建不兼容（SDL 用它建 surface 失败 →
        // MC Window "Invalid window"）；SDL 改回系统 EGL 建窗口，GL 函数仍经
        // SDL_OPENGL_LIBRARY + versesdl 代理解析自渲染器库；同样不向 SDL 注入
        // MobileGlues 作为 EGL 库。
        boolean mobileGluesEgl = egl != null && egl.contains("mobileglues");
        if (egl != null && !egl.startsWith("/") && !mobileGluesEgl) {
            // 仅当 EGL 库真实打包在渲染器目录内才给 SDL 绝对路径（如 ANGLE）；
            // 系统 Mali libEGL.so 不在目录内，交 SDL 按自身默认解析（与 FCL 一致）
            File candidate = new File(rendererLibPath(renderer), egl);
            if (candidate.isFile()) {
                egl = candidate.getAbsolutePath();
            } else {
                egl = null;
            }
        }
        if (egl != null && !mobileGluesEgl) {
            envMap.put("SDL_EGL_LIBRARY", egl);
        }
        String gl = renderer.getGlName();
        if (!gl.isEmpty()) {
            if (!gl.startsWith("/")) {
                gl = rendererLibPath(renderer) + "/" + gl;
            }
            envMap.put("SDL_OPENGL_LIBRARY", gl);
        }
    }

    /**
     * 渲染器库所在目录：插件渲染器用其自身 lib 目录，内置渲染器用主 APK native 目录
     */
    private static String rendererLibPath(Renderer renderer) {
        String pluginPath = renderer.getPath();
        return pluginPath.isEmpty() ? FCLPath.NATIVE_LIB_DIR : pluginPath;
    }

    /**
     * MobileGlues 官方要求：运行 MC 26.3-snapshot-3 及以后必须开启 "Ignore shader/program
     * error"（config.json 键 enableNoError=3 → NoErrorConfig.Level2 → 驱动日志 ignoreError=2
     * Full）。驱动启动时从 {MG_DIR_PATH}/config.json 读取（MG_DIR_PATH 默认 /sdcard/MG），
     * 缺省 config.json 走内置默认（ignoreError=0，26.3 下 shader 编译报错即中断）。
     * 优先写 /sdcard/MG/config.json（与官方插件共用配置，保留用户已有键）；写入失败时
     * 降级到应用外部私有目录并设 MG_DIR_PATH 指向它。
     */
    private static void setupMobileGluesConfig(Context context, HashMap<String, String> envMap) {
        if (writeMobileGluesConfig(new File("/sdcard/MG/config.json"))) {
            return;
        }
        File baseDir = context.getExternalFilesDir(null);
        if (baseDir == null) {
            baseDir = context.getFilesDir();
        }
        File appMgDir = new File(baseDir, "MG");
        if (writeMobileGluesConfig(new File(appMgDir, "config.json"))) {
            envMap.put("MG_DIR_PATH", appMgDir.getAbsolutePath());
        }
    }

    private static boolean writeMobileGluesConfig(File configFile) {
        try {
            JSONObject config = configFile.exists()
                    ? new JSONObject(readFileString(configFile))
                    : new JSONObject();
            config.put("enableNoError", 3);
            File parent = configFile.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            FileWriter writer = new FileWriter(configFile);
            writer.write(config.toString(2));
            writer.close();
            return true;
        } catch (Exception e) {
            Log.e("FCLauncher", "Write MobileGlues config failed: " + e.getMessage());
            return false;
        }
    }

    private static String readFileString(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = new BufferedReader(new FileReader(file));
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append('\n');
        }
        reader.close();
        return sb.toString();
    }

    private static void addRendererEnvInner(FCLConfig config, HashMap<String, String> envMap) {
        Renderer renderer = config.getRenderer();
        if (!renderer.getPath().isEmpty()) {
            String eglName = renderer.getEglName();
            if (eglName.startsWith("/")) {
                eglName = renderer.getPath() + eglName;
            }
            List<String> envList;
            envMap.put("POJAVEXEC_EGL", eglName);
            envList = renderer.getPojavEnv();
            if (envList != null) {
                envList.forEach(env -> {
                    String[] split = env.split("=");
                    if (split[0].equals("DLOPEN") || split.length < 2) {
                        return;
                    }
                    if (split[0].equals("LIB_MESA_NAME")) {
                        envMap.put(split[0], renderer.getPath() + "/" + split[1]);
                    } else if (split[0].equals("MESA_LIBRARY")) {
                        envMap.put(split[0], renderer.getPath() + "/" + split[1]);
                    } else {
                        envMap.put(split[0], split[1]);
                    }
                });
            }
            // 插件形式的 MobileGlues：同样需要写入驱动配置（忽略 shader 错误）
            if (renderer.getGlName().toLowerCase().contains("mobileglues")
                    || renderer.getEglName().toLowerCase().contains("mobileglues")) {
                setupMobileGluesConfig(config.getContext(), envMap);
            }
            return;
        }
        if (renderer.isEqual(Renderer.ID_GL4ES) || renderer.isEqual(Renderer.ID_VGPU)) {
            envMap.put("LIBGL_ES", "2");
            envMap.put("LIBGL_MIPMAP", "3");
            envMap.put("LIBGL_NORMALIZE", "1");
            envMap.put("LIBGL_NOINTOVLHACK", "1");
            envMap.put("LIBGL_NOERROR", "1");
            if (renderer.getId().equals(Renderer.ID_GL4ES)) {
                envMap.put("POJAV_RENDERER", "opengles2");
            } else {
                envMap.put("POJAV_RENDERER", "opengles2_vgpu");
            }
        } else if (renderer.isEqual(Renderer.ID_NGGL4ES)) {
            envMap.put("LIBGL_USE_MC_COLOR", "1");
            envMap.put("DLOPEN", "libspirv-cross-c-shared.so");
            envMap.put("LIBGL_GL", "31");
            envMap.put("LIBGL_ES", "3");
            envMap.put("LIBGL_NORMALIZE", "1");
            envMap.put("LIBGL_NOINTOVLHACK", "1");
            envMap.put("LIBGL_NOERROR", "1");
            // TODO: set NGG_DIR_PATH to custom path
            envMap.put("POJAV_RENDERER", "opengles3");
            envMap.put("POJAVEXEC_EGL", "libEGL.so");
        } else {
            envMap.put("MESA_GLSL_CACHE_DIR", config.getContext().getCacheDir().getAbsolutePath());
            envMap.put("MESA_GL_VERSION_OVERRIDE", renderer.isEqual(Renderer.ID_VIRGL) ? "4.3" : "4.6");
            envMap.put("MESA_GLSL_VERSION_OVERRIDE", renderer.isEqual(Renderer.ID_VIRGL) ? "430" : "460");
            envMap.put("force_glsl_extensions_warn", "true");
            envMap.put("allow_higher_compat_version", "true");
            envMap.put("allow_glsl_extension_directive_midshader", "true");
            envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "zink");
            envMap.put("VTEST_SOCKET_NAME", new File(config.getContext().getCacheDir().getAbsolutePath(), ".virgl_test").getAbsolutePath());
            if (renderer.isEqual(Renderer.ID_VIRGL)) {
                envMap.put("POJAV_RENDERER", "gallium_virgl");
                envMap.put("OSMESA_NO_FLUSH_FRONTBUFFER", "1");
            } else if (renderer.isEqual(Renderer.ID_ZINK)) {
                envMap.put("POJAV_RENDERER", "opengles3_desktopgl_zink_kopper");
                envMap.put("LIBGL_ES", "3");
                envMap.put("POJAVEXEC_EGL", renderer.getEglName());
            } else if (renderer.isEqual(Renderer.ID_FREEDRENO)) {
                envMap.put("POJAV_RENDERER", "gallium_freedreno");
            }
        }
    }

    private static void setEnv(FCLConfig config, FCLBridge bridge, boolean render) {
        HashMap<String, String> envMap = new HashMap<>();
        addCommonEnv(config, envMap);
        addModLoaderEnv(config, envMap);
        if (render) {
            addRendererEnv(config, envMap);
        }
        addCustomEnv(config, envMap);
        printTaskTitle(bridge, "Env Map");
        for (String key : envMap.keySet()) {
            log(bridge, "Env: " + key + "=" + envMap.get(key));
            bridge.setenv(key, envMap.get(key));
        }
    }

    private static void addCustomEnv(FCLConfig config, HashMap<String, String> envMap) {
        SharedPreferences preferences = config.getContext().getSharedPreferences("launcher", MODE_PRIVATE);
        String[] env = preferences.getString("env", "").split("\n");
        for (String e : env) {
            try {
                String[] split = e.split("=", 2);
                if (split.length == 2) {
                    envMap.put(split[0], split[1]);
                }
            } catch (Throwable ignore) {
            }
        }

    }

    private static void setUpJavaRuntime(FCLConfig config, FCLBridge bridge) throws IOException {
        printTaskTitle(bridge, "DLOPEN");
        String javaLibDir = config.getJavaPath() + getJavaLibDir(config.getJavaPath());
        String jliLibDir = new File(javaLibDir + "/jli/libjli.so").exists() ? javaLibDir + "/jli" : javaLibDir;
        if (isJDK8(config.getJavaPath()))
            javaLibDir = config.getJavaPath() + "/jre" + getJavaLibDir(config.getJavaPath());
        String jvmLibDir = javaLibDir + getJvmLibDir(config.getJavaPath());
        // dlopen jre
        bridge.dlopen(jliLibDir + "/libjli.so");
        bridge.dlopen(jvmLibDir + "/libjvm.so");
        bridge.dlopen(javaLibDir + "/libfreetype.so");
        bridge.dlopen(javaLibDir + "/libverify.so");
        bridge.dlopen(javaLibDir + "/libjava.so");
        bridge.dlopen(javaLibDir + "/libnet.so");
        bridge.dlopen(javaLibDir + "/libnio.so");
        bridge.dlopen(javaLibDir + "/libawt.so");
        bridge.dlopen(javaLibDir + "/libawt_headless.so");
        bridge.dlopen(javaLibDir + "/libfontmanager.so");
        for (File file : locateLibs(new File(config.getJavaPath()))) {
            bridge.dlopen(file.getAbsolutePath());
        }
    }

    public static ArrayList<File> locateLibs(File path) {
        ArrayList<File> returnValue = new ArrayList<>();
        File[] list = path.listFiles();
        if (list != null) {
            for (File f : list) {
                if (f.isFile() && f.getName().endsWith(".so")) {
                    returnValue.add(f);
                } else if (f.isDirectory()) {
                    returnValue.addAll(locateLibs(f));
                }
            }
        }
        return returnValue;
    }

    private static void setupGraphicAndSoundEngine(FCLConfig config, FCLBridge bridge) {
        String nativeDir = config.getContext().getApplicationInfo().nativeLibraryDir;

        bridge.dlopen(nativeDir + "/libopenal.so");
        if (!config.getRenderer().getPath().isEmpty()) {
            List<String> envList = config.getRenderer().getPojavEnv();
            if (envList != null) {
                envList.forEach(env -> {
                    String[] split = env.split("=");
                    if (split[0].equals("DLOPEN")) {
                        String[] libs = split[1].split(",");
                        for (String lib : libs) {
                            bridge.dlopen(config.getRenderer().getPath() + "/" + lib);
                        }
                    }
                });
            }
            long handle = bridge.dlopen(config.getRenderer().getGLPath());
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                try {
                    Os.setenv("RENDERER_HANDLE", handle + "", true);
                } catch (ErrnoException ignore) {
                }
            }
//            bridge.dlopen(RendererPlugin.getSelected().getPath() + "/" + RendererPlugin.getSelected().getEglName());
        } else {
            bridge.dlopen(nativeDir + "/" + config.getRenderer().getGlName());
        }
    }

    private static void launch(FCLConfig config, FCLBridge bridge, String task) throws IOException {
        printTaskTitle(bridge, task + " Arguments");
        String[] args = rebaseArgs(config);
        boolean javaArgs = true;
        int mainClass = 0;
        boolean isToken = false;
        for (String arg : args) {
            if (javaArgs)
                javaArgs = !arg.equals("mio.Wrapper");
            String title = task.equals("Minecraft") ? javaArgs ? "Java" : task : task;
            String prefix = title + " argument: ";
            if (task.equals("Minecraft") && !javaArgs && mainClass < 2) {
                mainClass++;
                prefix = "MainClass: ";
            }
            if (isToken) {
                isToken = false;
                log(bridge, prefix + "***");
                continue;
            }
            if (arg.equals("--accessToken"))
                isToken = true;
            log(bridge, prefix + arg);
        }
        bridge.setLdLibraryPath(getLibraryPath(config.getContext(), config.getJavaPath(), config.getRenderer().getPath(), config.getLwjglVersion()));
        bridge.setupExitTrap(bridge);
        FCLBridge.initializeHooks();
        log(bridge, "Hook success");
        int exitCode = VMLauncher.launchJVM(args);
        bridge.onExit(exitCode);
    }

    public static FCLBridge launchMinecraft(FCLConfig config) {

        // initialize FCLBridge
        FCLBridge bridge = new FCLBridge();
        bridge.setLogPath(config.getLogDir() + "/latest_game.log");
        bridge.setGameDir(config.getWorkingDir());

        // 注意：不再在此预加载 poemav exec。FCLBridge.execute() 内部顺序已保证
        // CallbackBridge.setFCLBridge（ART 侧加载 poemav exec）先于 gameThread.start()
        // （游戏 JVM 加载）。人工 preload 会引入额外时序/线程副作用（类初始化需 Looper、
        // bionic SONAME 复用导致 JNI_OnLoad 分支错乱），反而制造崩溃点。
        // poemav exec 已改用 fcl_orig 源码构建版：pojavInit 不调用 updateMonitorSize，
        // 即使 JVM 侧 runtime 分支未初始化 vmGlfwClass 也不会空指针崩溃。

        Thread gameThread = new Thread(() -> {
            try {
                logStartInfo(config, bridge, "Minecraft");
                logModList(bridge);

                // env
                setEnv(config, bridge, true);

                // setup java runtime
                setUpJavaRuntime(config, bridge);

                // setup graphic and sound engine
                setupGraphicAndSoundEngine(config, bridge);

                // set working directory
                log(bridge, "Working directory: " + config.getWorkingDir());
                bridge.chdir(config.getWorkingDir());

                // launch game
                launch(config, bridge, "Minecraft");
            } catch (Throwable e) {
                // 启动线程死亡必须通知宿主：任何异常（含 UnsatisfiedLinkError 等错误）都
                // 经 onExit 上报，否则 JVM 秒退时宿主收不到退出事件、崩溃分析页无法触发
                e.printStackTrace();
                bridge.onExit(1);
            }
        });

        gameThread.setPriority(Thread.MAX_PRIORITY);
        bridge.setThread(gameThread);

        return bridge;
    }

    public static FCLBridge launchJarExecutor(FCLConfig config) {

        // initialize FCLBridge
        FCLBridge bridge = new FCLBridge();
        bridge.setLogPath(config.getLogDir() + "/latest_jar_executor.log");
        Thread javaGUIThread = new Thread(() -> {
            try {

                logStartInfo(config, bridge, "Jar Executor");

                // env
                setEnv(config, bridge, true);

                // setup java runtime
                setUpJavaRuntime(config, bridge);

                // setup graphic and sound engine
                setupGraphicAndSoundEngine(config, bridge);

                // set working directory
                log(bridge, "Working directory: " + config.getWorkingDir());
                bridge.chdir(config.getWorkingDir());

                // launch jar executor
                launch(config, bridge, "Jar Executor");
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        bridge.setThread(javaGUIThread);

        return bridge;
    }

    public static FCLBridge launchAPIInstaller(FCLConfig config) {

        // initialize FCLBridge
        FCLBridge bridge = new FCLBridge();
        bridge.setLogPath(config.getLogDir() + "/latest_api_installer.log");
        Thread apiInstallerThread = new Thread(() -> {
            try {

                logStartInfo(config, bridge, "API Installer");

                // env
                setEnv(config, bridge, false);

                // setup java runtime
                setUpJavaRuntime(config, bridge);

                // set working directory
                log(bridge, "Working directory: " + config.getWorkingDir());
                bridge.chdir(config.getWorkingDir());

                // launch api installer
                launch(config, bridge, "API Installer");
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        bridge.setThread(apiInstallerThread);

        return bridge;
    }

    public static String getSocName() {
        String name = null;
        try {
            Process process = Runtime.getRuntime().exec("getprop ro.soc.model");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            name = reader.readLine();
            reader.close();
        } catch (Exception ignore) {
        }
        return (name == null || name.trim().isEmpty()) ? Build.HARDWARE : name;
    }

    public static String getDeviceName() {
        String modelName = Build.MODEL;
        String manufacturer = Build.MANUFACTURER;
        String product = Build.PRODUCT;
        if (!manufacturer.isEmpty()) {
            manufacturer = Character.toUpperCase(manufacturer.charAt(0))
                    + manufacturer.substring(1).toLowerCase();
        }
        return String.format("%s %s %s", manufacturer, product, modelName);
    }

    public static boolean isJDK8(String javaPath) {
        return new File(javaPath, "jre").exists() && new File(javaPath, "bin/javac").exists();
    }

}