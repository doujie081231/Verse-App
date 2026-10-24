/*
 * Verse Explorer X
 * Simple in-memory VersionProvider, aligned with FCL's SimpleVersionProvider.
 * Used by DefaultGameRepository.refreshVersions() so that child versions
 * (e.g. "26.2-forge-65.1.3" inheriting "26.2") can resolve their parents
 * even when the parent is parsed later in the directory scan.
 */
package com.verse.explorer.launcher.fcl.game;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

public class SimpleVersionProvider implements VersionProvider {

    private final Map<String, Version> versions = new TreeMap<>();

    public void addVersion(Version version) {
        versions.put(version.getId(), version);
    }

    public void addVersion(Collection<Version> versionCollection) {
        for (Version version : versionCollection) addVersion(version);
    }

    public Map<String, Version> getVersionMap() {
        return versions;
    }

    @Override
    public boolean hasVersion(String id) {
        return versions.containsKey(id);
    }

    @Override
    public Version getVersion(String id) throws VersionNotFoundException {
        if (!hasVersion(id)) throw new VersionNotFoundException(id);
        return versions.get(id);
    }
}