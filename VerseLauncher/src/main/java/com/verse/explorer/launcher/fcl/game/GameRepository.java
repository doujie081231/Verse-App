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
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

public interface GameRepository extends VersionProvider {

    @Override
    boolean hasVersion(String id);

    @Override
    Version getVersion(String id) throws VersionNotFoundException;

    default Version getResolvedVersion(String id) throws VersionNotFoundException {
        return getVersion(id).resolve(this);
    }

    default Version getResolvedPreservingPatchesVersion(String id) throws VersionNotFoundException {
        return getVersion(id).resolvePreservingPatches(this);
    }

    int getVersionCount();

    Collection<Version> getVersions();

    void refreshVersions();

    File getVersionRoot(String id);

    File getRunDirectory(String id);

    File getLibrariesDirectory(Version version);

    File getLibraryFile(Version version, Library lib);

    File getVersionJar(Version version);

    Optional<String> getGameVersion(Version version);

    default Optional<String> getGameVersion(String versionId) throws VersionNotFoundException {
        return getGameVersion(getVersion(versionId));
    }

    Path getModsDirectory(String id);

    default File getVersionJar(String version) throws VersionNotFoundException {
        return getVersionJar(getVersion(version).resolve(this));
    }

    boolean renameVersion(String from, String to);

    Path getActualAssetDirectory(String version, String assetId);

    Path getAssetDirectory(String version, String assetId);

    Optional<Path> getAssetObject(String version, String assetId, String name) throws IOException;

    Path getAssetObject(String version, String assetId, AssetObject obj);

    AssetIndex getAssetIndex(String version, String assetId) throws IOException;

    Path getIndexFile(String version, String assetId);

    Path getLoggingObject(String version, String assetId, LoggingInfo loggingInfo);

    default Set<String> getClasspath(Version version) {
        Set<String> classpath = new LinkedHashSet<>();
        for (Library library : version.getLibraries())
            if (library.appliesToCurrentEnvironment() && !library.isNative()) {
                File f = getLibraryFile(version, library);
                if (f.exists() && f.isFile())
                    classpath.add(f.getAbsolutePath());
            }
        return classpath;
    }
}
