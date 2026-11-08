package net.kdt.pojavlaunch;

import android.content.Context;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * 触控模块的宿主适配类（仅保留触控所需的成员）：
 * 输入出口仍走 org.lwjgl.glfw.CallbackBridge，不在本类处理。
 */
public final class Tools {
    public static final Gson GLOBAL_GSON = new GsonBuilder().setLenient().create();

    public static DisplayMetrics currentDisplayMetrics = new DisplayMetrics();

    /** 触控布局存储目录 */
    public static String CTRLMAP_PATH = "";

    private static Context sContext;

    public static void init(Context context) {
        sContext = context.getApplicationContext();
        currentDisplayMetrics = context.getResources().getDisplayMetrics();

        // 触控布局放在应用专属外部目录（Android/data/<包名>/files/control_layouts）：
        // 内部私有目录（/data/user/0/...）文件管理器完全看不到，用户没法自己替换键位。
        // 外部目录不可写时（个别 ROM 的 FUSE 挂载异常）回退内部目录，保证还能用。
        File legacyDir = new File(context.getFilesDir(), "control_layouts");
        File externalBase = context.getExternalFilesDir(null);
        File targetDir = null;
        if (externalBase != null) {
            File candidate = new File(externalBase, "control_layouts");
            if (ensureWritable(candidate)) {
                targetDir = candidate;
            }
        }
        if (targetDir == null) {
            targetDir = legacyDir;
        }
        CTRLMAP_PATH = targetDir.getAbsolutePath();
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }
        // 老版本把布局存在内部目录，升级后一次性搬过去，避免用户「布局全没了」
        if (!targetDir.equals(legacyDir) && legacyDir.isDirectory()) {
            migrateDirectory(legacyDir, targetDir);
        }
    }

    /** 目录可写探针：外部目录在个别 ROM 上 mkdir 成功但写入会 EACCES */
    private static boolean ensureWritable(File dir) {
        try {
            if (!dir.exists() && !dir.mkdirs()) {
                return false;
            }
            File probe = new File(dir, ".write_probe");
            FileOutputStream out = new FileOutputStream(probe);
            out.write(1);
            out.close();
            return probe.delete();
        } catch (Exception e) {
            return false;
        }
    }

    /** 平铺目录逐文件搬迁：目标同名文件已存在时以目标为准，不覆盖 */
    private static void migrateDirectory(File from, File to) {
        File[] files = from.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            File dest = new File(to, file.getName());
            if (dest.exists()) {
                continue;
            }
            try {
                java.nio.file.Files.copy(file.toPath(), dest.toPath());
                file.delete();
            } catch (Exception ignored) {
                // 单个文件搬失败保留原文件，下次启动再试
            }
        }
    }

    public static boolean isAndroid8OrHigher() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }

    public static boolean isValidString(String s) {
        return s != null && !s.isEmpty();
    }

    public static int dpToPx(float dp) {
        return (int) (dp * currentDisplayMetrics.density);
    }

    public static int pxToDp(float px) {
        return (int) (px / currentDisplayMetrics.density);
    }

    public static String read(File f) throws IOException {
        return new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    public static String read(String path) throws IOException {
        return read(new File(path));
    }

    public static void write(String path, String content) throws IOException {
        File f = new File(path);
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    public static void showError(Context ctx, Throwable th) {
        Log.e("VerseX-Tools", "Error", th);
        if (ctx != null) {
            Toast.makeText(ctx, th == null ? "错误" : String.valueOf(th.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    public static void showError(Context ctx, Throwable th, boolean showDontShowAgain) {
        showError(ctx, th);
    }

    /** 供 MCOptionUtils 等使用的游戏目录（宿主适配：返回应用游戏根目录） */
    public static String getGameDirPath(String profileId) {
        if (sContext == null) return "";
        return new File(sContext.getFilesDir(), "game").getAbsolutePath();
    }

    public static File getDir(String path) {
        return new File(path);
    }

    public static void openURL(Context ctx, String url) {
        try {
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url));
            i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception ignored) {
        }
    }

    /** 把任务投递到主线程（控制编辑 UI 使用） */
    public static void runOnUiThread(Runnable runnable) {
        if (sContext == null) {
            runnable.run();
            return;
        }
        android.os.Handler mainHandler = new android.os.Handler(sContext.getMainLooper());
        mainHandler.post(runnable);
    }
}