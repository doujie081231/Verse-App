/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.verse.explorer.launcher.fcl.game;

import com.verse.explorer.launcher.fcl.util.FCLPath;

public class JavaVersion {

    public static final int JAVA_VERSION_8 = 8;
    public static final int JAVA_VERSION_17 = 17;
    public static final int JAVA_VERSION_21 = 21;
    public static final int JAVA_VERSION_25 = 25;

    public static final JavaVersion JAVA_AUTO = new JavaVersion(true, "1.8", "Auto");

    private final boolean isAuto;
    private final String versionName;
    private final String name;

    public JavaVersion(boolean isAuto, String versionName, String name) {
        this.isAuto = isAuto;
        this.versionName = versionName;
        this.name = name;
    }

    public boolean isAuto() { return isAuto; }

    public String getVersionName() { return versionName; }

    public String getName() { return name; }

    public String getJavaPath(Version version) {
        return FCLPath.JAVA_PATH + "/" + name;
    }

    public int getVersion() {
        String[] split = versionName.split("\\.");
        if (split[0].equals("1")) {
            return Integer.parseInt(split[1]);
        } else {
            return Integer.parseInt(split[0]);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof JavaVersion)) return false;
        return name.equals(((JavaVersion) other).name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
