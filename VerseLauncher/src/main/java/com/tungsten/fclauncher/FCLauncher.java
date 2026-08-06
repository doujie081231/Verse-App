package com.tungsten.fclauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.ArrayMap;
import android.util.Log;

import com.mio.data.Renderer;
import com.tungsten.fclauncher.bridge.FCLBridge;
import com.tungsten.fclauncher.plugins.DriverPlugin;
import com.tungsten.fclauncher.plugins.FFmpegPlugin;
import com.tungsten.fclauncher.plugins.NativeLibPlugin;
import com.tungsten.fclauncher.utils.Architecture;
import com.tungsten.fclauncher.utils.FCLPath;
import com.verse.explorer.terracotta.TerracottaAndroidAPI;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FCLauncher {

    private static final String TAG = "FCLauncher";

    private static int FCL_VERSION_CODE = -1;

    private static void log(String msg) {
        Log.i(TAG, msg);
    }

    private static void printTaskTitle(String task) {
        log("==================== " + task + " ====================");
    }

    private static void logStartInfo(FCLConfig config, String task) {
        printTaskTitle("Start " + task);
        log("Renderer: " + config.getRenderer().getId());
        log("Device: " + getDeviceName());
        log("Architecture: " + Architecture.archAsString(Architecture.getDeviceArchitecture()));
        log("CPU: " + getSocName());
        log("Android SDK: " + Build.VERSION.SDK_INT);
        log("Language: " + Locale.getDefault());

        PackageManager pm = config.getContext().getPackageManager();
        try {
            PackageInfo packageInfo = pm.getPackageInfo(config.getContext().getPackageName(), 0);
            FCL_VERSION_CODE = packageInfo.versionCode;
            log("FCL Version Code: " + FCL_VERSION_CODE);
        } catch (PackageManager.NameNotFoundException e) {
            log("FCL Version Code: Can't get current version code, exception = " + e.getMessage());
        }
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
        if (Architecture.archAsInt(architecture) == Architecture.ARCH_X86) {
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

    private static String getLibraryPath(Context context, String javaPath, String pluginLibPath) throws IOException {
        String nativeDir = context.getApplicationInfo().nativeLibraryDir;
        String libDirName = Architecture.is64BitsDevice() ? "lib64" : "lib";
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

                nativeDir;
    }

    private static String getLibraryPath(Context context, String pluginLibPath) {
        String nativeDir = context.getApplicationInfo().nativeLibraryDir;
        String libDirName = Architecture.is64BitsDevice() ? "lib64" : "lib";
        String split = ":";
        String nativeLibPaths = NativeLibPlugin.getPaths(split);
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

                nativeDir;
    }

    private static String[] rebaseArgs(FCLConfig config) throws IOException {
        ArrayList<String> argList = new ArrayList<>(Arrays.asList(config.getArgs()));
        String[] args = new String[argList.size()];
        for (int i = 0; i < argList.size(); i++) {
            String a = argList.get(i);
            String libraryPath = getLibraryPath(config.getContext(), config.getJavaPath(), config.getRenderer().getPath());
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
        envMap.put("PATH", config.getJavaPath() + "/bin:" + System.getenv("PATH"));
        envMap.put("LD_LIBRARY_PATH", getLibraryPath(config.getContext(), config.getRenderer().getPath()));
        envMap.put("FORCE_VSYNC", "false");

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
        Renderer renderer = config.getRenderer();
        if (renderer == null) return;

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
        printTaskTitle("Env Map");
        for (String key : envMap.keySet()) {
            log("Env: " + key + "=" + envMap.get(key));
            bridge.setenv(key, envMap.get(key));
        }
    }

    private static void addCustomEnv(FCLConfig config, HashMap<String, String> envMap) {
        SharedPreferences preferences = config.getContext().getSharedPreferences("launcher", Context.MODE_PRIVATE);
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
        printTaskTitle("DLOPEN");
        String javaLibDir = config.getJavaPath() + getJavaLibDir(config.getJavaPath());
        String jliLibDir = new File(javaLibDir + "/jli/libjli.so").exists() ? javaLibDir + "/jli" : javaLibDir;
        if (isJDK8(config.getJavaPath()))
            javaLibDir = config.getJavaPath() + "/jre" + getJavaLibDir(config.getJavaPath());
        String jvmLibDir = javaLibDir + getJvmLibDir(config.getJavaPath());
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
        if (config.getRenderer() != null && !config.getRenderer().getPath().isEmpty()) {
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
                    android.system.Os.setenv("RENDERER_HANDLE", handle + "", true);
                } catch (android.system.ErrnoException ignore) {
                }
            }
        } else if (config.getRenderer() != null) {
            bridge.dlopen(nativeDir + "/" + config.getRenderer().getGlName());
        }
    }

    private static void launch(FCLConfig config, FCLBridge bridge, String task) throws IOException {
        printTaskTitle(task + " Arguments");
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
                log(prefix + "***");
                continue;
            }
            if (arg.equals("--accessToken"))
                isToken = true;
            log(prefix + arg);
        }
        bridge.setLdLibraryPath(getLibraryPath(config.getContext(), config.getJavaPath(), config.getRenderer().getPath()));
        bridge.setupExitTrap(bridge);
        log("Hook success");
        int exitCode = TerracottaAndroidAPI.nativeLaunchJVM(config.getJavaPath(), args);
        bridge.onExit(exitCode);
    }

    public static FCLBridge launchMinecraft(FCLConfig config) {
        FCLBridge bridge = new FCLBridge();
        bridge.setLogPath(config.getLogDir() + "/latest_game.log");
        Thread gameThread = new Thread(() -> {
            try {
                logStartInfo(config, "Minecraft");

                setEnv(config, bridge, true);
                setUpJavaRuntime(config, bridge);
                setupGraphicAndSoundEngine(config, bridge);

                log("Working directory: " + config.getWorkingDir());
                bridge.chdir(config.getWorkingDir());

                launch(config, bridge, "Minecraft");
            } catch (IOException e) {
                Log.e(TAG, "Minecraft launch failed", e);
            }
        });
        gameThread.setPriority(Thread.MAX_PRIORITY);
        bridge.setThread(gameThread);
        return bridge;
    }

    public static FCLBridge launchJarExecutor(FCLConfig config) {
        FCLBridge bridge = new FCLBridge();
        bridge.setLogPath(config.getLogDir() + "/latest_jar_executor.log");
        Thread javaGUIThread = new Thread(() -> {
            try {
                logStartInfo(config, "Jar Executor");
                setEnv(config, bridge, true);
                setUpJavaRuntime(config, bridge);
                setupGraphicAndSoundEngine(config, bridge);
                log("Working directory: " + config.getWorkingDir());
                bridge.chdir(config.getWorkingDir());
                launch(config, bridge, "Jar Executor");
            } catch (IOException e) {
                Log.e(TAG, "Jar Executor launch failed", e);
            }
        });
        bridge.setThread(javaGUIThread);
        return bridge;
    }

    public static FCLBridge launchAPIInstaller(FCLConfig config) {
        FCLBridge bridge = new FCLBridge();
        bridge.setLogPath(config.getLogDir() + "/latest_api_installer.log");
        Thread apiInstallerThread = new Thread(() -> {
            try {
                logStartInfo(config, "API Installer");
                setEnv(config, bridge, false);
                setUpJavaRuntime(config, bridge);
                log("Working directory: " + config.getWorkingDir());
                bridge.chdir(config.getWorkingDir());
                launch(config, bridge, "API Installer");
            } catch (IOException e) {
                Log.e(TAG, "API Installer launch failed", e);
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
