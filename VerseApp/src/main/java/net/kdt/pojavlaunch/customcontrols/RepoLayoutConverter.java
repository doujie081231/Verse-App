package net.kdt.pojavlaunch.customcontrols;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.kdt.pojavlaunch.LwjglGlfwKeycode;
import net.kdt.pojavlaunch.Tools;

import org.lwjgl.glfw.CallbackBridge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * 布局仓库 json（viewGroups/viewData 结构，坐标为千分比、键码为 Linux 风格扫描码）
 * 到应用 v8 布局（CustomControls）的转换。
 *
 * 语义对齐布局仓库格式定义：
 * - xPosition/yPosition ∈ [0,1000]，按钮左上角 = (屏幕宽-按钮宽) * xPosition/1000（纵向同理），
 *   恰好等于本应用表达式的 ${right}/${bottom} 乘以千分比，天然分辨率无关；
 * - 百分比尺寸 = 参考边（SCREEN_HEIGHT→screen_height）* size/1000，换算成 dp 落盘；
 * - ABSOLUTE 尺寸即 dp 值，直接采用；
 * - 键码需从扫描码体系换算到 GLFW 键码（键位表见 keyMap）；
 * - 鼠标码 1000/1001/1002/1003/1004 对应左/中/右键与滚轮；
 * - openMenu/input/quickInput 映射到 SPECIALBTN_*；autoKeep 映射为切换按钮；
 * - 方向控件映射为摇杆数据（方向键码沿用摇杆内置的 WASD 行为）。
 */
public final class RepoLayoutConverter {

    private RepoLayoutConverter() {}

    /** 是否为布局仓库格式 */
    public static boolean isRepoLayout(JsonObject root) {
        return root != null && root.has("viewGroups") && root.has("controllerVersion");
    }

    /** 转换为 v8 布局；无可用控件时仍返回空布局（保证编辑器可用） */
    public static CustomControls convert(JsonObject root) {
        CustomControls out = new CustomControls();
        out.version = 8;
        out.mControlDataList = new ArrayList<>();
        out.mJoystickDataList = new ArrayList<>();
        out.mDrawerDataList = new ArrayList<>();

        int screenW = CallbackBridge.physicalWidth > 0 ? CallbackBridge.physicalWidth : 2400;
        int screenH = CallbackBridge.physicalHeight > 0 ? CallbackBridge.physicalHeight : 1080;
        float density = Tools.dpToPx(1f);

        JsonArray groups = asArray(root.get("viewGroups"));
        if (groups != null) {
            for (JsonElement ge : groups) {
                JsonObject group = asObj(ge);
                if (group == null) continue;
                // 隐藏图层（备选按键层）不进入默认布局
                if ("INVISIBLE".equals(asString(group.get("visibility"), "VISIBLE"))) continue;
                JsonObject viewData = asObj(group.get("viewData"));
                if (viewData == null) continue;

                JsonArray buttons = asArray(viewData.get("buttonList"));
                if (buttons != null) {
                    for (JsonElement be : buttons) {
                        JsonObject b = asObj(be);
                        if (b == null) continue;
                        ControlData d = convertButton(b, screenW, screenH, density);
                        if (d != null) out.mControlDataList.add(d);
                    }
                }
                JsonArray directions = asArray(viewData.get("directionList"));
                if (directions != null) {
                    for (JsonElement de : directions) {
                        JsonObject dir = asObj(de);
                        if (dir == null) continue;
                        ControlJoystickData j = convertDirection(dir, screenW, screenH, density);
                        if (j != null) out.mJoystickDataList.add(j);
                    }
                }
            }
        }
        return out;
    }

    // ===== 按钮 =====

    private static ControlData convertButton(JsonObject button, int screenW, int screenH, float density) {
        JsonObject base = asObj(button.get("baseInfo"));
        if (base == null) return null;

        ControlData d = new ControlData();
        d.name = asString(button.get("text"), "");
        applyGeometry(d, base, screenW, screenH, density);
        applyVisibility(d, asString(base.get("visibilityType"), "ALWAYS"));

        JsonObject event = asObj(button.get("event"));
        if (event != null) {
            JsonObject press = asObj(event.get("pressEvent"));
            ArrayList<Integer> keycodes = new ArrayList<>();
            if (press != null) {
                JsonArray codes = asArray(press.get("outputKeycodes"));
                if (codes != null) {
                    for (JsonElement ke : codes) {
                        int mapped = mapKeycode(optInt(ke, -1));
                        if (mapped != Integer.MIN_VALUE) keycodes.add(mapped);
                    }
                }
                if (truthy(press.get("openMenu"))) keycodes.add(ControlData.SPECIALBTN_MENU);
                if (truthy(press.get("input")) || truthy(press.get("quickInput"))) {
                    keycodes.add(ControlData.SPECIALBTN_KEYBOARD);
                }
                if (truthy(press.get("autoKeep")) && !keycodes.isEmpty()) d.isToggle = true;
            }
            if (truthy(event.get("swipable"))) d.isSwipeable = true;
            if (!keycodes.isEmpty()) {
                // d.keycodes 已由 ControlData 构造函数固定为 4 个槽位（空槽为 GLFW_KEY_UNKNOWN）。
                // 这里只覆盖前若干槽、保留槽位长度，避免导入后按键槽位被截短，导致编辑面板
                // 只能修改第一个键位（键码下拉的越界守卫会丢弃其余槽位的写入）。
                for (int i = 0; i < keycodes.size() && i < d.keycodes.length; i++) {
                    d.keycodes[i] = keycodes.get(i);
                }
            }
        }
        return d;
    }

    // ===== 方向控件（映射为摇杆；方向键码由摇杆内置 WASD 行为提供） =====

    private static ControlJoystickData convertDirection(JsonObject direction, int screenW, int screenH, float density) {
        JsonObject base = asObj(direction.get("baseInfo"));
        if (base == null) return null;

        ControlJoystickData j = new ControlJoystickData();
        applyGeometry(j, base, screenW, screenH, density);
        applyVisibility(j, asString(base.get("visibilityType"), "ALWAYS"));
        return j;
    }

    // ===== 几何与通用字段 =====

    /** 千分比位置直接映射到 ${right}/${bottom} 表达式；尺寸按参考边换算为 dp */
    private static void applyGeometry(ControlData d, JsonObject base, int screenW, int screenH, float density) {
        float widthPx = sizePx(base, true, screenW, screenH, density);
        float heightPx = sizePx(base, false, screenW, screenH, density);
        d.setWidth(widthPx);
        d.setHeight(heightPx);

        float xRatio = clamp(optInt(base.get("xPosition"), 0), 0, 1000) / 1000f;
        float yRatio = clamp(optInt(base.get("yPosition"), 0), 0, 1000) / 1000f;
        d.dynamicX = "(" + trimRatio(xRatio) + ") * ${right}";
        d.dynamicY = "(" + trimRatio(yRatio) + ") * ${bottom}";
    }

    /** 百分比尺寸 = 参考边 * size/1000；ABSOLUTE 尺寸为 dp 直取 */
    private static float sizePx(JsonObject base, boolean isWidth, int screenW, int screenH, float density) {
        if ("ABSOLUTE".equals(asString(base.get("sizeType"), "PERCENTAGE"))) {
            float dp = optInt(base.get(isWidth ? "absoluteWidth" : "absoluteHeight"), 50);
            return clamp((int) dp, 10, 500) * density;
        }
        JsonObject perc = asObj(base.get(isWidth ? "percentageWidth" : "percentageHeight"));
        int size = perc == null ? 300 : clamp(optInt(perc.get("size"), 300), 10, 3000);
        String reference = perc == null ? "SCREEN_WIDTH" : asString(perc.get("reference"), "SCREEN_WIDTH");
        float ref = "SCREEN_HEIGHT".equals(reference) ? screenH : screenW;
        return ref * size / 1000f;
    }

    private static void applyVisibility(ControlData d, String visibilityType) {
        // ALWAYS 常显；IN_GAME 仅 grabbing（游戏内）显示；IN_MENU 仅菜单态显示
        switch (visibilityType) {
            case "IN_GAME":
                d.displayInGame = true;
                d.displayInMenu = false;
                break;
            case "IN_MENU":
                d.displayInGame = false;
                d.displayInMenu = true;
                break;
            case "ALWAYS":
            default:
                d.displayInGame = true;
                d.displayInMenu = true;
                break;
        }
    }

    // ===== 键码换算（扫描码 → GLFW；鼠标/滚轮 → SPECIALBTN_*） =====

    private static int mapKeycode(int fclCode) {
        if (fclCode < 0) return Integer.MIN_VALUE;
        switch (fclCode) {
            case 1000: return ControlData.SPECIALBTN_MOUSEPRI;
            case 1001: return ControlData.SPECIALBTN_MOUSEMID;
            case 1002: return ControlData.SPECIALBTN_MOUSESEC;
            case 1003: return ControlData.SPECIALBTN_SCROLLUP;
            case 1004: return ControlData.SPECIALBTN_SCROLLDOWN;
            default: return KEY_MAP.containsKey(fclCode) ? KEY_MAP.get(fclCode) : Integer.MIN_VALUE;
        }
    }

    /** 扫描码 → {@link LwjglGlfwKeycode} 键码对照表 */
    private static final Map<Integer, Integer> KEY_MAP = new HashMap<>();

    static {
        int[][] table = {
            {57, LwjglGlfwKeycode.GLFW_KEY_SPACE}, {40, LwjglGlfwKeycode.GLFW_KEY_APOSTROPHE},
            {51, LwjglGlfwKeycode.GLFW_KEY_COMMA}, {12, LwjglGlfwKeycode.GLFW_KEY_MINUS},
            {52, LwjglGlfwKeycode.GLFW_KEY_PERIOD}, {53, LwjglGlfwKeycode.GLFW_KEY_SLASH},
            {11, LwjglGlfwKeycode.GLFW_KEY_0}, {2, LwjglGlfwKeycode.GLFW_KEY_1},
            {3, LwjglGlfwKeycode.GLFW_KEY_2}, {4, LwjglGlfwKeycode.GLFW_KEY_3},
            {5, LwjglGlfwKeycode.GLFW_KEY_4}, {6, LwjglGlfwKeycode.GLFW_KEY_5},
            {7, LwjglGlfwKeycode.GLFW_KEY_6}, {8, LwjglGlfwKeycode.GLFW_KEY_7},
            {9, LwjglGlfwKeycode.GLFW_KEY_8}, {10, LwjglGlfwKeycode.GLFW_KEY_9},
            {39, LwjglGlfwKeycode.GLFW_KEY_SEMICOLON}, {13, LwjglGlfwKeycode.GLFW_KEY_EQUAL},
            {30, LwjglGlfwKeycode.GLFW_KEY_A}, {48, LwjglGlfwKeycode.GLFW_KEY_B},
            {46, LwjglGlfwKeycode.GLFW_KEY_C}, {32, LwjglGlfwKeycode.GLFW_KEY_D},
            {18, LwjglGlfwKeycode.GLFW_KEY_E}, {33, LwjglGlfwKeycode.GLFW_KEY_F},
            {34, LwjglGlfwKeycode.GLFW_KEY_G}, {35, LwjglGlfwKeycode.GLFW_KEY_H},
            {23, LwjglGlfwKeycode.GLFW_KEY_I}, {36, LwjglGlfwKeycode.GLFW_KEY_J},
            {37, LwjglGlfwKeycode.GLFW_KEY_K}, {38, LwjglGlfwKeycode.GLFW_KEY_L},
            {50, LwjglGlfwKeycode.GLFW_KEY_M}, {49, LwjglGlfwKeycode.GLFW_KEY_N},
            {24, LwjglGlfwKeycode.GLFW_KEY_O}, {25, LwjglGlfwKeycode.GLFW_KEY_P},
            {16, LwjglGlfwKeycode.GLFW_KEY_Q}, {19, LwjglGlfwKeycode.GLFW_KEY_R},
            {31, LwjglGlfwKeycode.GLFW_KEY_S}, {20, LwjglGlfwKeycode.GLFW_KEY_T},
            {22, LwjglGlfwKeycode.GLFW_KEY_U}, {47, LwjglGlfwKeycode.GLFW_KEY_V},
            {17, LwjglGlfwKeycode.GLFW_KEY_W}, {45, LwjglGlfwKeycode.GLFW_KEY_X},
            {21, LwjglGlfwKeycode.GLFW_KEY_Y}, {44, LwjglGlfwKeycode.GLFW_KEY_Z},
            {26, LwjglGlfwKeycode.GLFW_KEY_LEFT_BRACKET}, {27, LwjglGlfwKeycode.GLFW_KEY_RIGHT_BRACKET},
            {43, LwjglGlfwKeycode.GLFW_KEY_BACKSLASH}, {41, LwjglGlfwKeycode.GLFW_KEY_GRAVE_ACCENT},
            {1, LwjglGlfwKeycode.GLFW_KEY_ESCAPE}, {28, LwjglGlfwKeycode.GLFW_KEY_ENTER},
            {15, LwjglGlfwKeycode.GLFW_KEY_TAB}, {14, LwjglGlfwKeycode.GLFW_KEY_BACKSPACE},
            {110, LwjglGlfwKeycode.GLFW_KEY_INSERT}, {111, LwjglGlfwKeycode.GLFW_KEY_DELETE},
            {106, LwjglGlfwKeycode.GLFW_KEY_RIGHT}, {105, LwjglGlfwKeycode.GLFW_KEY_LEFT},
            {108, LwjglGlfwKeycode.GLFW_KEY_DOWN}, {103, LwjglGlfwKeycode.GLFW_KEY_UP},
            {104, LwjglGlfwKeycode.GLFW_KEY_PAGE_UP}, {109, LwjglGlfwKeycode.GLFW_KEY_PAGE_DOWN},
            {102, LwjglGlfwKeycode.GLFW_KEY_HOME}, {107, LwjglGlfwKeycode.GLFW_KEY_END},
            {58, LwjglGlfwKeycode.GLFW_KEY_CAPS_LOCK}, {70, LwjglGlfwKeycode.GLFW_KEY_SCROLL_LOCK},
            {69, LwjglGlfwKeycode.GLFW_KEY_NUM_LOCK}, {99, LwjglGlfwKeycode.GLFW_KEY_PRINT_SCREEN},
            {119, LwjglGlfwKeycode.GLFW_KEY_PAUSE},
            {59, LwjglGlfwKeycode.GLFW_KEY_F1}, {60, LwjglGlfwKeycode.GLFW_KEY_F2},
            {61, LwjglGlfwKeycode.GLFW_KEY_F3}, {62, LwjglGlfwKeycode.GLFW_KEY_F4},
            {63, LwjglGlfwKeycode.GLFW_KEY_F5}, {64, LwjglGlfwKeycode.GLFW_KEY_F6},
            {65, LwjglGlfwKeycode.GLFW_KEY_F7}, {66, LwjglGlfwKeycode.GLFW_KEY_F8},
            {67, LwjglGlfwKeycode.GLFW_KEY_F9}, {68, LwjglGlfwKeycode.GLFW_KEY_F10},
            {87, LwjglGlfwKeycode.GLFW_KEY_F11}, {88, LwjglGlfwKeycode.GLFW_KEY_F12},
            {183, LwjglGlfwKeycode.GLFW_KEY_F13}, {184, LwjglGlfwKeycode.GLFW_KEY_F14},
            {185, LwjglGlfwKeycode.GLFW_KEY_F15}, {186, LwjglGlfwKeycode.GLFW_KEY_F16},
            {187, LwjglGlfwKeycode.GLFW_KEY_F17}, {188, LwjglGlfwKeycode.GLFW_KEY_F18},
            {189, LwjglGlfwKeycode.GLFW_KEY_F19}, {190, LwjglGlfwKeycode.GLFW_KEY_F20},
            {191, LwjglGlfwKeycode.GLFW_KEY_F21}, {192, LwjglGlfwKeycode.GLFW_KEY_F22},
            {193, LwjglGlfwKeycode.GLFW_KEY_F23}, {194, LwjglGlfwKeycode.GLFW_KEY_F24},
            {82, LwjglGlfwKeycode.GLFW_KEY_KP_0}, {79, LwjglGlfwKeycode.GLFW_KEY_KP_1},
            {80, LwjglGlfwKeycode.GLFW_KEY_KP_2}, {81, LwjglGlfwKeycode.GLFW_KEY_KP_3},
            {75, LwjglGlfwKeycode.GLFW_KEY_KP_4}, {76, LwjglGlfwKeycode.GLFW_KEY_KP_5},
            {77, LwjglGlfwKeycode.GLFW_KEY_KP_6}, {71, LwjglGlfwKeycode.GLFW_KEY_KP_7},
            {72, LwjglGlfwKeycode.GLFW_KEY_KP_8}, {73, LwjglGlfwKeycode.GLFW_KEY_KP_9},
            {83, LwjglGlfwKeycode.GLFW_KEY_KP_DECIMAL}, {98, LwjglGlfwKeycode.GLFW_KEY_KP_DIVIDE},
            {55, LwjglGlfwKeycode.GLFW_KEY_KP_MULTIPLY}, {74, LwjglGlfwKeycode.GLFW_KEY_KP_SUBTRACT},
            {78, LwjglGlfwKeycode.GLFW_KEY_KP_ADD}, {96, LwjglGlfwKeycode.GLFW_KEY_KP_ENTER},
            {117, LwjglGlfwKeycode.GLFW_KEY_KP_EQUAL},
            {42, LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT}, {29, LwjglGlfwKeycode.GLFW_KEY_LEFT_CONTROL},
            {56, LwjglGlfwKeycode.GLFW_KEY_LEFT_ALT}, {125, LwjglGlfwKeycode.GLFW_KEY_LEFT_SUPER},
            {54, LwjglGlfwKeycode.GLFW_KEY_RIGHT_SHIFT}, {97, LwjglGlfwKeycode.GLFW_KEY_RIGHT_CONTROL},
            {100, LwjglGlfwKeycode.GLFW_KEY_RIGHT_ALT}, {126, LwjglGlfwKeycode.GLFW_KEY_RIGHT_SUPER},
        };
        for (int[] pair : table) KEY_MAP.put(pair[0], pair[1]);
    }

    // ===== 解析工具 =====

    private static JsonObject asObj(JsonElement e) {
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    private static JsonArray asArray(JsonElement e) {
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : null;
    }

    private static String asString(JsonElement e, String fallback) {
        return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
    }

    private static boolean truthy(JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsBoolean();
    }

    private static int optInt(JsonElement e, int fallback) {
        try {
            return e != null && e.isJsonPrimitive() ? Integer.parseInt(e.getAsString().trim()) : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /** 千分比转小数字符串，去掉多余尾零（0.35 而非 0.35000000x） */
    private static String trimRatio(float ratio) {
        String s = String.format(java.util.Locale.US, "%.4f", ratio);
        return s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
    }
}
