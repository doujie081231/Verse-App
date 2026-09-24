package com.tungsten.fcl.control;

/**
 * FCL 鼠标控制模式（menu_right.xml mouse_mode_spinner，原样移植自
 * .fcl-temp/FCL/src/main/java/com/tungsten/fcl/control/MouseMoveMode.java）。
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
