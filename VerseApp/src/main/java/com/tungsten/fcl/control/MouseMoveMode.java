package com.tungsten.fcl.control;

/**
 * 鼠标控制模式（取值对应 menu_right.xml 的 mouse_mode_spinner）。
 * CLICK(0)=点击，SLIDE(1)=拖动。
 */
public enum MouseMoveMode {
    CLICK(0), SLIDE(1);

    private final int id;

    MouseMoveMode(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static MouseMoveMode getById(int id) {
        for (MouseMoveMode mode : values()) {
            if (mode.id == id) {
                return mode;
            }
        }
        return CLICK;
    }
}
