package com.verse.explorer.launcher.fcl.game;

/**
 * A {@link JavaVersion} that always resolves to the actual JRE directory
 * chosen by Verse (instead of the fixed FCL runtime layout).
 */
public class VerseJavaVersion extends JavaVersion {

    private final String javaPath;

    public VerseJavaVersion(int versionMajor, String javaPath) {
        super(false, String.valueOf(versionMajor), "verse-" + versionMajor);
        this.javaPath = javaPath;
    }

    @Override
    public String getJavaPath(Version version) {
        return javaPath;
    }
}