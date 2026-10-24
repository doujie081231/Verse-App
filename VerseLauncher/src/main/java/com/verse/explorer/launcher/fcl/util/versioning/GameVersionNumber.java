/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2024 huangyuhui <huanghongxun2008@126.com> and contributors
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
package com.verse.explorer.launcher.fcl.util.versioning;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public abstract class GameVersionNumber implements Comparable<GameVersionNumber> {

    public static GameVersionNumber asGameVersion(String version) {
        if (version == null || version.isEmpty())
            return new Simple(version, version);

        try {
            String[] parts = version.split("\\.");
            int[] nums = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                try {
                    nums[i] = Integer.parseInt(parts[i]);
                } catch (NumberFormatException e) {
                    return new Simple(version, version);
                }
            }
            return new Release(version, nums);
        } catch (Exception e) {
            return new Simple(version, version);
        }
    }

    public static int compare(String version1, String version2) {
        return asGameVersion(version1).compareTo(asGameVersion(version2));
    }

    final String value;

    GameVersionNumber(String value) {
        this.value = value;
    }

    @Override
    public int compareTo(@NotNull GameVersionNumber other) {
        return compareToImpl(other);
    }

    abstract int compareToImpl(GameVersionNumber other);

    @Override
    public String toString() { return value; }

    static class Release extends GameVersionNumber {
        final int[] parts;

        Release(String value, int[] parts) {
            super(value);
            this.parts = parts;
        }

        @Override
        int compareToImpl(GameVersionNumber other) {
            if (other instanceof Release) {
                Release o = (Release) other;
                int len = Math.max(parts.length, o.parts.length);
                for (int i = 0; i < len; i++) {
                    int a = i < parts.length ? parts[i] : 0;
                    int b = i < o.parts.length ? o.parts[i] : 0;
                    if (a != b) return Integer.compare(a, b);
                }
                return 0;
            }
            return 1;
        }
    }

    static class Simple extends GameVersionNumber {
        Simple(String value, String normalized) {
            super(value);
        }

        @Override
        int compareToImpl(GameVersionNumber other) {
            if (other instanceof Simple)
                return this.value.compareTo(((Simple) other).value);
            if (other instanceof Release)
                return -1;
            return 0;
        }
    }
}
