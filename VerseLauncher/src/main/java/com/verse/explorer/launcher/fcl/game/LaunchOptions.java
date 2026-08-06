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

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LaunchOptions implements Serializable {

    private File gameDir;
    private JavaVersion java;
    private String versionName;
    private String versionType;
    private String profileName;
    private final List<String> gameArguments = new ArrayList<>();
    private final List<String> javaArguments = new ArrayList<>();
    private Integer minMemory;
    private Integer maxMemory;
    private Integer width;
    private Integer height;
    private String serverIp;
    private boolean beGesture;
    private boolean vulkanDriverSystem;
    private boolean pojavBigCore;
    private String renderer;
    private String uuid;
    private boolean debugLog;

    public File getGameDir() { return gameDir; }

    public JavaVersion getJava() { return java; }

    public String getVersionName() { return versionName; }

    public String getVersionType() { return versionType; }

    public String getProfileName() { return profileName; }

    public List<String> getGameArguments() { return Collections.unmodifiableList(gameArguments); }

    public List<String> getJavaArguments() { return Collections.unmodifiableList(javaArguments); }

    public Integer getMinMemory() { return minMemory; }

    public Integer getMaxMemory() { return maxMemory; }

    public Integer getWidth() { return width; }

    public Integer getHeight() { return height; }

    public String getServerIp() { return serverIp; }

    public boolean isBeGesture() { return beGesture; }

    public boolean isVKDriverSystem() { return vulkanDriverSystem; }

    public boolean isPojavBigCore() { return pojavBigCore; }

    public String getRenderer() { return renderer; }

    public String getUuid() { return uuid; }

    public boolean isDebugLog() { return debugLog; }

    public static class Builder {
        private final LaunchOptions options = new LaunchOptions();

        public LaunchOptions create() { return options; }

        public List<String> getGameArguments() { return options.gameArguments; }

        public List<String> getJavaArguments() { return options.javaArguments; }

        public Builder setGameDir(File gameDir) { options.gameDir = gameDir; return this; }

        public Builder setJava(JavaVersion java) { options.java = java; return this; }

        public Builder setVersionName(String versionName) { options.versionName = versionName; return this; }

        public Builder setVersionType(String versionType) { options.versionType = versionType; return this; }

        public Builder setProfileName(String profileName) { options.profileName = profileName; return this; }

        public Builder setGameArguments(List<String> gameArguments) { options.gameArguments.clear(); options.gameArguments.addAll(gameArguments); return this; }

        public Builder setJavaArguments(List<String> javaArguments) { options.javaArguments.clear(); options.javaArguments.addAll(javaArguments); return this; }

        public Builder setMinMemory(Integer minMemory) { options.minMemory = minMemory; return this; }

        public Builder setMaxMemory(Integer maxMemory) { options.maxMemory = maxMemory; return this; }

        public Builder setWidth(Integer width) { options.width = width; return this; }

        public Builder setHeight(Integer height) { options.height = height; return this; }

        public Builder setServerIp(String serverIp) { options.serverIp = serverIp; return this; }

        public Builder setBEGesture(boolean beGesture) { options.beGesture = beGesture; return this; }

        public Builder setVkDriverSystem(boolean vulkanDriverSystem) { options.vulkanDriverSystem = vulkanDriverSystem; return this; }

        public Builder setPojavBigCore(boolean pojavBigCore) { options.pojavBigCore = pojavBigCore; return this; }

        public Builder setRenderer(String renderer) { options.renderer = renderer; return this; }

        public Builder setUUid(String uuid) { options.uuid = uuid; return this; }

        public Builder setDebugLog(boolean debugLog) { options.debugLog = debugLog; return this; }
    }
}
