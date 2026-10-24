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

import android.util.Log;

import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonReader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class GameVersion {
    private GameVersion() {}

    private static Optional<String> getVersionFromJson(InputStream versionJson) {
        try {
            JsonReader reader = new JsonReader(new InputStreamReader(versionJson, StandardCharsets.UTF_8));
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if ("id".equals(name)) {
                    String id = reader.nextString();
                    if (id != null && id.contains(" / "))
                        id = id.substring(0, id.indexOf(" / "));
                    return Optional.ofNullable(id);
                } else {
                    reader.skipValue();
                }
            }
            return Optional.empty();
        } catch (IOException | JsonParseException | ClassCastException e) {
            Log.w("GameVersion", "Failed to parse version.json", e);
            return Optional.empty();
        }
    }

    public static Optional<String> minecraftVersion(File file) {
        if (file == null || !file.exists() || !file.isFile() || !file.canRead())
            return Optional.empty();

        try (ZipFile gameJar = new ZipFile(file)) {
            ZipEntry versionJson = gameJar.getEntry("version.json");
            if (versionJson != null) {
                Optional<String> result = getVersionFromJson(gameJar.getInputStream(versionJson));
                if (result.isPresent())
                    return result;
            }
            return Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
