package com.verse.explorer.launcher.fcl.game;

public class GameJavaVersion {
    private final int majorVersion;
    private final String component;

    public static final int JAVA_VERSION_8 = 8;
    public static final int JAVA_VERSION_16 = 16;
    public static final int JAVA_VERSION_17 = 17;
    public static final int JAVA_VERSION_21 = 21;

    public GameJavaVersion(int majorVersion, String component) {
        this.majorVersion = majorVersion;
        this.component = component;
    }

    public int getMajorVersion() { return majorVersion; }
    public String getComponent() { return component; }
}
