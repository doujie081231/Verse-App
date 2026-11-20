package dev.horizonlab.horizon;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.util.Locale;
import java.util.Random;

/**
 * Shared look of the setup and profile screens, in the style of the game's own menus: square
 * stone buttons with a light top edge and a dark bottom edge, dark see-through panels, a pixel
 * font (Monocraft, SIL OFL, bundled in assets/fonts) with a drop shadow, and the title
 * panorama behind everything.
 *
 * Nothing of Mojang's ships in the APK: the panorama is read at run time from the files the
 * player's own game download put in the game folder, and without them a background drawn here
 * (a dirt-like pixel pattern) stands in.
 */
final class Ui {
    static final int BG = Color.rgb(18, 18, 22);
    static final int CARD = Color.argb(200, 16, 16, 20);
    static final int EDGE = Color.rgb(0, 0, 0);
    static final int GREEN = Color.rgb(85, 255, 85);
    static final int GREEN_DARK = Color.rgb(44, 110, 40);
    static final int TEXT = Color.rgb(255, 255, 255);
    static final int MUTED = Color.rgb(170, 170, 170);
    static final int WARN = Color.rgb(255, 255, 85);
    static final int SHADOW = Color.rgb(40, 40, 40);

    private static Typeface font;

    private Ui() {}

    static boolean ru() {
        return "ru".equals(Locale.getDefault().getLanguage());
    }

    /** 中文文案表：按英文原文映射，未覆盖的条目自动回落英文（上游更新不会因此报错） */
    private static final java.util.Map<String, String> ZH = new java.util.HashMap<>();

    static {
        ZH.put("No access to the Horizon folder", "无法访问 Horizon 文件夹");
        ZH.put("Grant access", "授予权限");
        ZH.put("App folder", "使用应用目录");
        ZH.put("First launch", "首次启动");
        ZH.put("The game needs the Minecraft 26.3 files. They will be downloaded automatically, about 700 MB. Wi-Fi is best.", "游戏需要 Minecraft 26.3 的文件，将自动下载，约 700 MB。建议连接 Wi-Fi。");
        ZH.put("Download game files", "下载游戏文件");
        ZH.put("Pick horizon-derived.zip", "选择 horizon-derived.zip");
        ZH.put("No file picker app found.", "没有找到文件选择器应用。");
        ZH.put("Copy the error text", "复制错误信息");
        ZH.put("Copied ✓", "已复制 ✓");
        ZH.put("Vulkan driver (experimental)", "Vulkan 驱动（实验性）");
        ZH.put("Profile: name and skin", "档案：名称与皮肤");
        ZH.put("This build has no Horizon data pack address. Put horizon-derived.zip in the game folder (Android/data/dev.horizonlab.horizon/files) or install a build that has one.", "此构建没有内置 Horizon 数据包地址。请把 horizon-derived.zip 放到游戏目录（Android/data/com.verse.explorer/files/horizon），或安装带地址的构建。");
        ZH.put("Copying the archive…", "正在复制压缩包…");
        ZH.put("the file did not open", "文件无法打开");
        ZH.put("this is not a zip archive", "这不是 zip 压缩包");
        ZH.put("The archive is in place. Tap Download game files.", "压缩包已就位，点「下载游戏文件」继续。");
        ZH.put("Could not copy the archive: ", "复制压缩包失败：");
        ZH.put("Not enough space. About 1.5 GB is needed, free: ", "存储空间不足：需要约 1.5 GB，当前可用：");
        ZH.put(" MB.", "MB。");
        ZH.put("Mobile data", "移动数据");
        ZH.put("You are not on Wi-Fi. Download about 700 MB over mobile data?", "当前不是 Wi-Fi 网络，要用移动数据下载约 700 MB 吗？");
        ZH.put("Download", "下载");
        ZH.put("Cancel", "取消");
        ZH.put("Preparing", "准备中");
        ZH.put("Downloading the game", "正在下载游戏");
        ZH.put("Unpacking", "正在解包");
        ZH.put("Sounds, fonts and languages", "音效、字体与语言");
        ZH.put("Horizon data", "Horizon 数据");
        ZH.put("Ready", "就绪");
        ZH.put("Preparing", "准备中");
        ZH.put("The phone ran out of space.", "手机存储空间不足。");
        ZH.put("The Horizon data pack was not found. Put horizon-derived.zip in the game folder and tap Retry.", "未找到 Horizon 数据包。请把 horizon-derived.zip 放进游戏目录后点重试。");
        ZH.put("A file is missing on the server (404). This build's link is out of date: update Horizon.", "服务器上缺少文件（404）。此构建的链接已过期，请更新 Horizon。");
        ZH.put("The server refused the file. Wait a few minutes or switch networks (VPN or mobile data).", "服务器拒绝了请求。请稍等几分钟，或切换网络（VPN 或移动数据）。");
        ZH.put("The download server is down for now. Try again later.", "下载服务器暂时不可用，请稍后再试。");
        ZH.put("A secure connection failed. Check the phone's date and time, or switch networks.", "安全连接失败。请检查手机日期时间，或切换网络。");
        ZH.put("The data pack is damaged. Delete horizon-derived.zip from the game folder and tap Retry.", "数据包已损坏。请从游戏目录删除 horizon-derived.zip 后点重试。");
        ZH.put("A file arrived damaged. Tap Retry.", "有文件下载损坏，请点重试。");
        ZH.put("Could not reach the server. Check the connection and tap Retry.", "无法连接服务器。请检查网络后点重试。");
        ZH.put("Something went wrong. Tap Retry, the download continues where it stopped.", "出错了。点重试会从断点继续下载。");
        ZH.put(" MB", "MB");
        ZH.put(" MB/s", "MB/s");
        ZH.put(" files", "个文件");
        ZH.put("The download stopped", "下载已停止");
        ZH.put("Details: ", "详情：");
        ZH.put("Retry", "重试");
    }

    /** 系统语言为中文时优先用中文表 */
    private static boolean zhLocale() {
        String lang = java.util.Locale.getDefault().getLanguage();
        return lang != null && lang.toLowerCase(java.util.Locale.ROOT).startsWith("zh");
    }

    static String t(String ru, String en) {
        if (ru()) return ru;
        if (zhLocale()) {
            String zh = ZH.get(en);
            if (zh != null) return zh;
        }
        return en;
    }

    static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** The pixel font; the system's monospace when the asset can't be read. */
    static Typeface font(Context c) {
        if (font == null) {
            try {
                font = Typeface.createFromAsset(c.getAssets(), "fonts/Monocraft.ttf");
            } catch (Exception e) {
                font = Typeface.MONOSPACE;
            }
        }
        return font;
    }

    /** One "game pixel" on screen. */
    static int px(Context c) {
        return Math.max(2, dp(c, 2));
    }

    /**
     * A panel or field: `fill` with a one-pixel `stroke` rim (none for 0) and, for a field, the
     * sunken look of the game's text boxes. The radius is ignored: everything is square here.
     */
    static Drawable rounded(Context c, int fill, int stroke, float radius) {
        return new Panel(px(c) / 2 + 1, fill, stroke);
    }

    /** A full-screen panorama with a centered panel; returns the panel to fill. */
    static LinearLayout card(android.app.Activity a) {
        FrameLayout root = new FrameLayout(a);
        root.addView(new Panorama(a), new FrameLayout.LayoutParams(-1, -1));
        android.widget.ScrollView scroll = new android.widget.ScrollView(a);
        scroll.setFillViewport(true);
        FrameLayout center = new FrameLayout(a);
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        int p = dp(a, 20);
        card.setPadding(p, p, p, p);
        card.setBackground(rounded(a, CARD, Color.rgb(90, 90, 90), 0));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                Math.min(dp(a, 540), a.getResources().getDisplayMetrics().widthPixels - dp(a, 32)),
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        lp.setMargins(0, dp(a, 16), 0, dp(a, 16));
        center.addView(card, lp);
        scroll.addView(center);
        root.addView(scroll);
        a.setContentView(root);
        return card;
    }

    static TextView text(Context c, String s, float size, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        // The pixel font reads larger than the system one at the same size.
        t.setTextSize(size * 0.92f);
        t.setTextColor(color);
        t.setTypeface(font(c));
        t.getPaint().setFakeBoldText(bold && size >= 18);
        t.setShadowLayer(0.01f, px(c) / 2f + 0.5f, px(c) / 2f + 0.5f, shade(color));
        t.setLineSpacing(0, 1.15f);
        t.setPadding(0, dp(c, 4), 0, dp(c, 4));
        return t;
    }

    /** The text's drop shadow: a quarter of its colour, as the game draws it. */
    static int shade(int color) {
        return Color.rgb(Color.red(color) / 4, Color.green(color) / 4, Color.blue(color) / 4);
    }

    static Button button(Context c, String s, boolean primary) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(primary ? 16 : 14);
        b.setTextColor(new android.content.res.ColorStateList(
                new int[][] {new int[] {-android.R.attr.state_enabled}, new int[] {}},
                new int[] {Color.rgb(160, 160, 160), primary ? Color.WHITE : Color.rgb(224, 224, 224)}));
        b.setTypeface(font(c));
        b.setShadowLayer(0.01f, px(c) / 2f + 0.5f, px(c) / 2f + 0.5f, Color.rgb(56, 56, 56));
        b.setBackground(new StoneButton(px(c), primary));
        b.setMinHeight(dp(c, primary ? 52 : 42));
        b.setStateListAnimator(null);
        b.setSoundEffectsEnabled(true);
        return b;
    }

    static LinearLayout.LayoutParams gap(Context c, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, top);
        return lp;
    }

    /** A section header: a pixel divider and a caption (empty title: divider only). */
    static void section(LinearLayout card, String title) {
        Context c = card.getContext();
        View line = new View(c);
        line.setBackgroundColor(Color.argb(160, 120, 120, 120));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(c) / 2 + 1);
        lp.topMargin = dp(c, 16);
        lp.bottomMargin = dp(c, 6);
        card.addView(line, lp);
        if (title.isEmpty()) return;
        TextView t = text(c, title, 13, WARN, false);
        card.addView(t);
    }

    /** A square panel with a dark rim and a faint inner light line. */
    static final class Panel extends Drawable {
        private final Paint paint = new Paint();
        private final int unit, fill, stroke;

        Panel(int unit, int fill, int stroke) {
            this.unit = unit;
            this.fill = fill;
            this.stroke = stroke;
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            paint.setColor(fill);
            c.drawRect(b, paint);
            if (stroke == 0) return;
            paint.setColor(Color.BLACK);
            edges(c, b.left, b.top, b.right, b.bottom, unit);
            paint.setColor(stroke);
            edges(c, b.left + unit, b.top + unit, b.right - unit, b.bottom - unit, unit);
        }

        private void edges(Canvas c, int l, int t, int r, int bo, int w) {
            c.drawRect(l, t, r, t + w, paint);
            c.drawRect(l, bo - w, r, bo, paint);
            c.drawRect(l, t, l + w, bo, paint);
            c.drawRect(r - w, t, r, bo, paint);
        }

        @Override
        public boolean getPadding(Rect padding) {
            return false;
        }

        @Override public void setAlpha(int a) {}
        @Override public void setColorFilter(ColorFilter f) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /**
     * The game's button, drawn: a black outline, a stone face with a lighter top-left edge and a
     * darker bottom-right one; lighter with a white outline while pressed or focused, flat grey
     * when disabled. The primary button (Play) is green.
     */
    static final class StoneButton extends Drawable {
        private final Paint paint = new Paint();
        private final int unit;
        private final boolean primary;
        private boolean pressed, focused, enabled = true;

        StoneButton(int unit, boolean primary) {
            this.unit = unit;
            this.primary = primary;
        }

        @Override
        public boolean isStateful() {
            return true;
        }

        @Override
        protected boolean onStateChange(int[] state) {
            boolean p = false, f = false, e = false;
            for (int s : state) {
                if (s == android.R.attr.state_pressed) p = true;
                if (s == android.R.attr.state_focused || s == android.R.attr.state_hovered) f = true;
                if (s == android.R.attr.state_enabled) e = true;
            }
            boolean changed = p != pressed || f != focused || e != enabled;
            pressed = p;
            focused = f;
            enabled = e;
            if (changed) invalidateSelf();
            return changed;
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            int u = unit;
            int face, hi, lo;
            if (!enabled) {
                face = Color.rgb(44, 44, 44);
                hi = Color.rgb(60, 60, 60);
                lo = Color.rgb(34, 34, 34);
            } else if (primary) {
                face = pressed || focused ? Color.rgb(84, 168, 60) : Color.rgb(60, 133, 39);
                hi = pressed || focused ? Color.rgb(132, 214, 102) : Color.rgb(99, 178, 72);
                lo = Color.rgb(30, 82, 22);
            } else {
                face = pressed || focused ? Color.rgb(126, 136, 191) : Color.rgb(111, 111, 111);
                hi = pressed || focused ? Color.rgb(170, 180, 230) : Color.rgb(164, 164, 164);
                lo = pressed || focused ? Color.rgb(78, 86, 140) : Color.rgb(78, 78, 78);
            }
            paint.setColor(pressed || focused ? Color.WHITE : Color.BLACK);
            c.drawRect(b, paint);
            paint.setColor(lo);
            c.drawRect(b.left + u, b.top + u, b.right - u, b.bottom - u, paint);
            paint.setColor(hi);
            c.drawRect(b.left + u, b.top + u, b.right - 2 * u, b.bottom - 2 * u, paint);
            paint.setColor(face);
            c.drawRect(b.left + 2 * u, b.top + 2 * u, b.right - 2 * u, b.bottom - 2 * u, paint);
        }

        @Override
        public boolean getPadding(Rect padding) {
            int p = unit * 4;
            padding.set(p, unit * 2, p, unit * 2);
            return true;
        }

        @Override public void setAlpha(int a) {}
        @Override public void setColorFilter(ColorFilter f) {}
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }

    /**
     * The title screen's background: the panorama's four side faces in a row, blurred and
     * darkened, panning slowly (as the 1.12 title menu did). The faces come from the game
     * folder (`resources/assets/minecraft/textures/gui/title/background/panorama_0..3.png`, put
     * there by the player's game download); a pixel pattern drawn here stands in without them.
     */
    static final class Panorama extends View {
        private Bitmap strip;
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Rect src = new Rect(), dst = new Rect();
        private final long start = android.os.SystemClock.uptimeMillis();
        private boolean loaded;

        Panorama(Context c) {
            super(c);
        }

        private void load() {
            loaded = true;
            File dir = new File(SetupActivity.home(getContext()), "resources/assets/minecraft/textures/gui/title/background");
            Bitmap[] faces = new Bitmap[4];
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            try {
                for (int i = 0; i < 4; i++) {
                    faces[i] = BitmapFactory.decodeFile(new File(dir, "panorama_" + i + ".png").getPath(), o);
                    if (faces[i] == null) throw new Exception("face " + i);
                }
                // Small first: scaling up a small copy with filtering is the blur.
                int s = 96;
                strip = Bitmap.createBitmap(s * 5, s, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(strip);
                Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
                for (int i = 0; i < 5; i++) {
                    Bitmap f = faces[i % 4];
                    c.drawBitmap(f, new Rect(0, 0, f.getWidth(), f.getHeight()), new Rect(i * s, 0, (i + 1) * s, s), p);
                }
                for (Bitmap f : faces) f.recycle();
            } catch (Throwable t) {
                strip = pattern();
            }
        }

        /** A dirt-like pattern of brown pixels, drawn here: no game file needed. */
        private static Bitmap pattern() {
            int s = 16;
            Bitmap tile = Bitmap.createBitmap(s * 5, s, Bitmap.Config.ARGB_8888);
            Random r = new Random(7);
            int[] tones = {Color.rgb(89, 61, 41), Color.rgb(108, 76, 52), Color.rgb(72, 50, 34), Color.rgb(121, 85, 58), Color.rgb(96, 96, 96)};
            for (int y = 0; y < s; y++) {
                for (int x = 0; x < s; x++) {
                    int v = r.nextInt(100);
                    int tone = v < 4 ? tones[4] : v < 30 ? tones[2] : v < 70 ? tones[0] : v < 92 ? tones[1] : tones[3];
                    for (int k = 0; k < 5; k++) tile.setPixel(k * s + x, y, tone);
                }
            }
            return tile;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            postInvalidateOnAnimation();
        }

        @Override
        protected void onDraw(Canvas c) {
            if (!loaded) load();
            int w = getWidth(), h = getHeight();
            int sh = strip.getHeight();
            boolean pixelArt = sh <= 16;
            paint.setFilterBitmap(!pixelArt);
            if (pixelArt) {
                // Tiles of the pattern at 4 screen pixels a texel, like the old options background.
                int cell = Math.max(32, dp(getContext(), 32));
                src.set(0, 0, sh, sh);
                for (int y = 0; y < h; y += cell) {
                    for (int x = 0; x < w; x += cell) {
                        dst.set(x, y, x + cell, y + cell);
                        c.drawBitmap(strip, src, dst, paint);
                    }
                }
                c.drawColor(Color.argb(150, 0, 0, 0));
            } else {
                // The strip fills the height; it pans one face every 40 seconds and wraps.
                float scale = h / (float) sh;
                float face = sh * scale;
                float t = ((android.os.SystemClock.uptimeMillis() - start) % 160_000L) / 40_000f;
                float off = t * face;
                float viewInStrip = w / scale;
                int left = (int) (off / scale) % (sh * 4);
                int right = Math.min(strip.getWidth(), left + (int) Math.ceil(viewInStrip));
                src.set(left, 0, right, sh);
                dst.set(0, 0, (int) ((right - left) * scale), h);
                c.drawBitmap(strip, src, dst, paint);
                if (dst.right < w) {
                    src.set(0, 0, (int) Math.ceil((w - dst.right) / scale) + 1, sh);
                    dst.set(dst.right, 0, w + (int) scale, h);
                    c.drawBitmap(strip, src, dst, paint);
                }
                c.drawColor(Color.argb(110, 0, 0, 0));
                postInvalidateOnAnimation();
            }
        }
    }

    /** A square progress bar, 0..1000, in the game's green. */
    static final class Bar extends View {
        private final Paint paint = new Paint();
        private int value;

        Bar(Context c) {
            super(c);
            setMinimumHeight(dp(c, 14));
        }

        void set(int permille) {
            value = Math.max(0, Math.min(1000, permille));
            invalidate();
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), dp(getContext(), 14));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int u = px(getContext()) / 2 + 1;
            int w = getWidth(), h = getHeight();
            paint.setColor(Color.BLACK);
            canvas.drawRect(0, 0, w, h, paint);
            paint.setColor(Color.rgb(50, 50, 50));
            canvas.drawRect(u, u, w - u, h - u, paint);
            if (value > 0) {
                int end = u + (int) ((w - 2 * u) * value / 1000f);
                paint.setColor(Color.rgb(60, 133, 39));
                canvas.drawRect(u, u, end, h - u, paint);
                paint.setColor(Color.rgb(99, 178, 72));
                canvas.drawRect(u, u, end, u + (h - 2 * u) / 3, paint);
            }
        }
    }
}
