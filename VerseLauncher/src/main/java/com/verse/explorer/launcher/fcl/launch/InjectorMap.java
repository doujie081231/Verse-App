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
package com.verse.explorer.launcher.fcl.launch;

import com.verse.explorer.launcher.fcl.game.Version;

import java.util.ArrayList;

public class InjectorMap {

    private final ArrayList<MapInfo> maps;

    public InjectorMap(ArrayList<MapInfo> maps) { this.maps = maps; }

    public ArrayList<MapInfo> getMaps() { return maps; }

    public static class MapInfo {
        private final String id;
        private final Argument argument;

        public MapInfo(String id, Argument argument) {
            this.id = id;
            this.argument = argument;
        }

        public String getId() { return id; }

        public Argument getArgument() { return argument; }
    }

    public static class Argument {
        private final String vanilla;
        private final String forge;
        private final String neoforge;
        private final String fabric;

        public Argument(String vanilla, String forge, String neoforge, String fabric) {
            this.vanilla = vanilla;
            this.forge = forge;
            this.neoforge = neoforge;
            this.fabric = fabric;
        }

        public String getVanilla() { return vanilla; }

        public String getForge() { return forge; }

        public String getNeoforge() { return neoforge; }

        public String getFabric() { return fabric; }

        public String getArgument(Version version, String gameVersion) {
            return getVanilla();
        }
    }
}
