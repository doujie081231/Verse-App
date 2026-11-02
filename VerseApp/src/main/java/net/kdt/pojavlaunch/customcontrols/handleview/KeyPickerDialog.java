package net.kdt.pojavlaunch.customcontrols.handleview;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import net.kdt.pojavlaunch.EfficientAndroidLWJGLKeycode;
import net.kdt.pojavlaunch.LwjglGlfwKeycode;

import java.util.List;

/**
 * 键值选择器：直接点虚拟键盘上的按键，替代原来「一个键一个键往下滑」的下拉列表。
 *
 * 布局与真实键盘一致（Esc/F1-F12、数字行、QWERTY 三行、修饰键行、方向键），
 * 顶部再给一排特殊键（鼠标左右键、视角等）。点击即选中并回填到调用方的 Spinner。
 *
 * 索引口径与 {@link EditControlSideDialog} 里 loadValues 的写法完全一致：
 * 特殊键在前（{@code ControlData.buildSpecialButtonArray()}），键盘键在后，
 * 因此 adapterIndex = EfficientAndroidLWJGLKeycode.getIndexByValue(keycode) + specialCount。
 */
public final class KeyPickerDialog {

    public interface OnKeyPicked {
        void onPicked(int adapterIndex);
    }

    /** 与 PC 键盘同序的按键表 */
    private static final int[][] KEYBOARD_ROWS = new int[][]{
            {LwjglGlfwKeycode.GLFW_KEY_ESCAPE, LwjglGlfwKeycode.GLFW_KEY_F1, LwjglGlfwKeycode.GLFW_KEY_F2,
                    LwjglGlfwKeycode.GLFW_KEY_F3, LwjglGlfwKeycode.GLFW_KEY_F4, LwjglGlfwKeycode.GLFW_KEY_F5,
                    LwjglGlfwKeycode.GLFW_KEY_F6, LwjglGlfwKeycode.GLFW_KEY_F7, LwjglGlfwKeycode.GLFW_KEY_F8,
                    LwjglGlfwKeycode.GLFW_KEY_F9, LwjglGlfwKeycode.GLFW_KEY_F10, LwjglGlfwKeycode.GLFW_KEY_F11,
                    LwjglGlfwKeycode.GLFW_KEY_F12},
            {LwjglGlfwKeycode.GLFW_KEY_GRAVE_ACCENT, LwjglGlfwKeycode.GLFW_KEY_1, LwjglGlfwKeycode.GLFW_KEY_2,
                    LwjglGlfwKeycode.GLFW_KEY_3, LwjglGlfwKeycode.GLFW_KEY_4, LwjglGlfwKeycode.GLFW_KEY_5,
                    LwjglGlfwKeycode.GLFW_KEY_6, LwjglGlfwKeycode.GLFW_KEY_7, LwjglGlfwKeycode.GLFW_KEY_8,
                    LwjglGlfwKeycode.GLFW_KEY_9, LwjglGlfwKeycode.GLFW_KEY_0, LwjglGlfwKeycode.GLFW_KEY_MINUS,
                    LwjglGlfwKeycode.GLFW_KEY_EQUAL, LwjglGlfwKeycode.GLFW_KEY_BACKSPACE},
            {LwjglGlfwKeycode.GLFW_KEY_TAB, LwjglGlfwKeycode.GLFW_KEY_Q, LwjglGlfwKeycode.GLFW_KEY_W,
                    LwjglGlfwKeycode.GLFW_KEY_E, LwjglGlfwKeycode.GLFW_KEY_R, LwjglGlfwKeycode.GLFW_KEY_T,
                    LwjglGlfwKeycode.GLFW_KEY_Y, LwjglGlfwKeycode.GLFW_KEY_U, LwjglGlfwKeycode.GLFW_KEY_I,
                    LwjglGlfwKeycode.GLFW_KEY_O, LwjglGlfwKeycode.GLFW_KEY_P,
                    LwjglGlfwKeycode.GLFW_KEY_LEFT_BRACKET, LwjglGlfwKeycode.GLFW_KEY_RIGHT_BRACKET,
                    LwjglGlfwKeycode.GLFW_KEY_BACKSLASH},
            {LwjglGlfwKeycode.GLFW_KEY_CAPS_LOCK, LwjglGlfwKeycode.GLFW_KEY_A, LwjglGlfwKeycode.GLFW_KEY_S,
                    LwjglGlfwKeycode.GLFW_KEY_D, LwjglGlfwKeycode.GLFW_KEY_F, LwjglGlfwKeycode.GLFW_KEY_G,
                    LwjglGlfwKeycode.GLFW_KEY_H, LwjglGlfwKeycode.GLFW_KEY_J, LwjglGlfwKeycode.GLFW_KEY_K,
                    LwjglGlfwKeycode.GLFW_KEY_L, LwjglGlfwKeycode.GLFW_KEY_SEMICOLON,
                    LwjglGlfwKeycode.GLFW_KEY_APOSTROPHE, LwjglGlfwKeycode.GLFW_KEY_ENTER},
            {LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT, LwjglGlfwKeycode.GLFW_KEY_Z, LwjglGlfwKeycode.GLFW_KEY_X,
                    LwjglGlfwKeycode.GLFW_KEY_C, LwjglGlfwKeycode.GLFW_KEY_V, LwjglGlfwKeycode.GLFW_KEY_B,
                    LwjglGlfwKeycode.GLFW_KEY_N, LwjglGlfwKeycode.GLFW_KEY_M, LwjglGlfwKeycode.GLFW_KEY_COMMA,
                    LwjglGlfwKeycode.GLFW_KEY_PERIOD, LwjglGlfwKeycode.GLFW_KEY_SLASH,
                    LwjglGlfwKeycode.GLFW_KEY_RIGHT_SHIFT},
            {LwjglGlfwKeycode.GLFW_KEY_LEFT_CONTROL, LwjglGlfwKeycode.GLFW_KEY_LEFT_ALT,
                    LwjglGlfwKeycode.GLFW_KEY_SPACE, LwjglGlfwKeycode.GLFW_KEY_RIGHT_ALT,
                    LwjglGlfwKeycode.GLFW_KEY_RIGHT_CONTROL,
                    LwjglGlfwKeycode.GLFW_KEY_UP, LwjglGlfwKeycode.GLFW_KEY_LEFT,
                    LwjglGlfwKeycode.GLFW_KEY_DOWN, LwjglGlfwKeycode.GLFW_KEY_RIGHT},
    };

    private KeyPickerDialog() {
    }

    /** 键帽上显示的短标签（Android 名称太长的做映射，其余保持原样） */
    private static String labelOf(int keycode) {
        switch (keycode) {
            case LwjglGlfwKeycode.GLFW_KEY_ESCAPE: return "Esc";
            case LwjglGlfwKeycode.GLFW_KEY_GRAVE_ACCENT: return "`";
            case LwjglGlfwKeycode.GLFW_KEY_MINUS: return "-";
            case LwjglGlfwKeycode.GLFW_KEY_EQUAL: return "=";
            case LwjglGlfwKeycode.GLFW_KEY_BACKSPACE: return "⌫";
            case LwjglGlfwKeycode.GLFW_KEY_TAB: return "Tab";
            case LwjglGlfwKeycode.GLFW_KEY_LEFT_BRACKET: return "[";
            case LwjglGlfwKeycode.GLFW_KEY_RIGHT_BRACKET: return "]";
            case LwjglGlfwKeycode.GLFW_KEY_BACKSLASH: return "\\";
            case LwjglGlfwKeycode.GLFW_KEY_CAPS_LOCK: return "Caps";
            case LwjglGlfwKeycode.GLFW_KEY_SEMICOLON: return ";";
            case LwjglGlfwKeycode.GLFW_KEY_APOSTROPHE: return "'";
            case LwjglGlfwKeycode.GLFW_KEY_ENTER: return "Enter";
            case LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT: return "Shift";
            case LwjglGlfwKeycode.GLFW_KEY_RIGHT_SHIFT: return "Shift";
            case LwjglGlfwKeycode.GLFW_KEY_COMMA: return ",";
            case LwjglGlfwKeycode.GLFW_KEY_PERIOD: return ".";
            case LwjglGlfwKeycode.GLFW_KEY_SLASH: return "/";
            case LwjglGlfwKeycode.GLFW_KEY_LEFT_CONTROL: return "Ctrl";
            case LwjglGlfwKeycode.GLFW_KEY_RIGHT_CONTROL: return "Ctrl";
            case LwjglGlfwKeycode.GLFW_KEY_LEFT_ALT: return "Alt";
            case LwjglGlfwKeycode.GLFW_KEY_RIGHT_ALT: return "Alt";
            case LwjglGlfwKeycode.GLFW_KEY_SPACE: return "空格";
            case LwjglGlfwKeycode.GLFW_KEY_UP: return "↑";
            case LwjglGlfwKeycode.GLFW_KEY_DOWN: return "↓";
            case LwjglGlfwKeycode.GLFW_KEY_LEFT: return "←";
            case LwjglGlfwKeycode.GLFW_KEY_RIGHT: return "→";
            default: {
                int index = EfficientAndroidLWJGLKeycode.getIndexByValue(keycode);
                if (index < 0) return "?";
                String name = EfficientAndroidLWJGLKeycode.generateKeyName()[index];
                return name.length() <= 3 ? name : name.replace("_", " ");
            }
        }
    }

    private static int dp(Context context, float value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics());
    }

    /**
     * 弹出虚拟键盘。
     *
     * @param specialCount 特殊键数量（适配器前缀长度）
     * @param specialNames 特殊键名称（可为空）
     * @param currentIndex 当前选中的适配器下标（用于高亮）
     * @param callback     选中后回调适配器下标
     */
    public static void show(Context context,
                            int specialCount,
                            List<String> specialNames,
                            int currentIndex,
                            OnKeyPicked callback) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#F21B1B1F"));
        bg.setCornerRadius(dp(context, 14));
        root.setBackground(bg);

        TextView title = new TextView(context);
        title.setText("选择键值");
        title.setTextColor(Color.parseColor("#F0F0F0"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView hint = new TextView(context);
        hint.setText("直接点按键即可；长按属性面板里的键值栏可选冷门键");
        hint.setTextColor(Color.parseColor("#99FFFFFF"));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        hint.setPadding(0, dp(context, 2), 0, dp(context, 8));
        root.addView(hint);

        ScrollView scroll = new ScrollView(context);
        LinearLayout keyboard = new LinearLayout(context);
        keyboard.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(keyboard);

        // 特殊键（鼠标左右键、视角等）：横向一排，超出可左右滑
        if (specialNames != null && !specialNames.isEmpty()) {
            HorizontalScrollView specialScroll = new HorizontalScrollView(context);
            specialScroll.setHorizontalScrollBarEnabled(false);
            LinearLayout specialRow = new LinearLayout(context);
            specialRow.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = 0; i < specialNames.size(); i++) {
                final int index = i;
                TextView chip = buildKey(context, specialNames.get(i));
                chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                chip.setSelected(index == currentIndex);
                chip.setOnClickListener(v -> {
                    callback.onPicked(index);
                    dialog.dismiss();
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 32));
                lp.rightMargin = dp(context, 6);
                specialRow.addView(chip, lp);
            }
            specialScroll.addView(specialRow);
            keyboard.addView(specialScroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        for (int[] row : KEYBOARD_ROWS) {
            LinearLayout rowLayout = new LinearLayout(context);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            for (int keycode : row) {
                TextView key = buildKey(context, labelOf(keycode));
                int valueIndex = EfficientAndroidLWJGLKeycode.getIndexByValue(keycode);
                if (valueIndex < 0) {
                    // 当前映射表里没有这个键（设备差异）：灰掉，避免选到无效值
                    key.setAlpha(0.3f);
                    key.setEnabled(false);
                } else {
                    final int adapterIndex = valueIndex + specialCount;
                    key.setSelected(adapterIndex == currentIndex);
                    key.setOnClickListener(v -> {
                        callback.onPicked(adapterIndex);
                        dialog.dismiss();
                    });
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, dp(context, 38), 1f);
                lp.rightMargin = dp(context, 3);
                lp.leftMargin = dp(context, 3);
                rowLayout.addView(key, lp);
            }
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.topMargin = dp(context, 5);
            keyboard.addView(rowLayout, rowLp);
        }

        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView cancel = new TextView(context);
        cancel.setText("取消");
        cancel.setTextColor(Color.parseColor("#BBFFFFFF"));
        cancel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(0, dp(context, 10), 0, dp(context, 4));
        cancel.setOnClickListener(v -> dialog.dismiss());
        root.addView(cancel);

        dialog.setContentView(root);
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawableResource(android.R.color.transparent);
                int width = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.94f);
                window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });
        dialog.show();
    }

    /** 键帽外观：深色底，选中态高亮（用 StateListDrawable 实现） */
    private static TextView buildKey(Context context, String text) {
        TextView key = new TextView(context);
        key.setText(text);
        key.setGravity(Gravity.CENTER);
        key.setTextColor(Color.parseColor("#F0F0F0"));
        key.setTextSize(TypedValue.COMPLEX_UNIT_SP, text.length() > 2 ? 10 : 13);
        key.setSingleLine(true);
        key.setPadding(dp(context, 6), 0, dp(context, 6), 0);

        StateListDrawable bg = new StateListDrawable();
        GradientDrawable selected = keyBackground("#3D6DF2");
        GradientDrawable normal = keyBackground("#2E2E36");
        bg.addState(new int[]{android.R.attr.state_selected}, selected);
        bg.addState(new int[]{}, normal);
        key.setBackground(bg);
        return key;
    }

    private static GradientDrawable keyBackground(String color) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(color));
        bg.setCornerRadius(10f);
        return bg;
    }
}
