package com.tungsten.fcl.control;

/**
 * FCL 触控手势模式（menu_right.xml gesture_mode_spinner，原样移植自
 * .fcl-temp/FCL/src/main/java/com/tungsten/fcl/control/GestureMode.java）。
 * BUILD(0)=建筑模式，FIGHT(1)=战斗模式。
 */
public enum GestureMode {
    BUILD(0), FIGHT(1);

    private final int id;

    GestureMode(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static GestureMode getById(int id) {
        for (GestureMode mode : values()) {
            if (mode.id == id) {
                return mode;
            }
        }
        return BUILD;
    }
}
