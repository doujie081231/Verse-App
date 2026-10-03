/*
 * Verse Explorer X
 * Default GameRepository implementation for the Verse game directory layout.
 *
 * The game directory is the run directory (ROOT_FOLDER layout):
 *   <gameDir>/
 *     versions/<id>/<id>.json   <id>.jar
 *     libraries/
 *     assets/
 */
package com.verse.explorer.launcher.fcl.game;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.verse.explorer.launcher.fcl.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;

/**
 * A minimal, self-contained implementation of {@link GameRepository} for the
 * Verse game directory layout. It scans {@code <baseDir>/versions} and parses
 * each version json into a {@link Version}.
 */
public class DefaultGameRepository implements GameRepository {

    private static final String TAG = "DefaultGameRepository";

    private File baseDirectory;
    private Map<String, Version> versions = Collections.emptyMap();
    private final ConcurrentHashMap<File, Optional<String>> gameVersions = new ConcurrentHashMap<>();

    private final Gson gson = new GsonBuilder()
            .setLenient()
            .registerTypeAdapter(Instant.class, InstantTypeAdapter.INSTANCE)
            .registerTypeAdapterFactory(LowerCaseEnumTypeAdapterFactory.INSTANCE)
            .create();

    public DefaultGameRepository(File baseDirectory) {
        this.baseDirectory = baseDirectory;
    }

    public File getBaseDirectory() {
        return baseDirectory;
    }

    public void setBaseDirectory(File baseDirectory) {
        this.baseDirectory = baseDirectory;
    }

    @Override
    public boolean hasVersion(String id) {
        return id != null && versions.containsKey(id);
    }

    @Override
    public Version getVersion(String id) throws VersionNotFoundException {
        if (!hasVersion(id)) {
            throw new VersionNotFoundException("Version '" + id + "' does not exist in "
                    + versions.keySet() + ".");
        }
        return versions.get(id);
    }

    @Override
    public int getVersionCount() {
        return versions.size();
    }

    @Override
    public Collection<Version> getVersions() {
        return versions.values();
    }

    /** 版本隔离：true（默认）= 运行目录为 versions/<id>；false = 游戏根目录 */
    private volatile boolean runDirectoryIsolated = true;

    /** 版本级设置注入：关闭该版本的运行目录隔离 */
    public void setRunDirectoryIsolated(boolean isolated) {
        this.runDirectoryIsolated = isolated;
    }

    @Override
    public File getVersionRoot(String id) {
        return new File(getBaseDirectory(), "versions/" + id);
    }

    @Override
    public File getRunDirectory(String id) {
        // 版本隔离（对应 FCL VersionSetting.isIsolateGameDir → FCLGameRepository.getRunDirectory
        // 的两种目录类型）：开启时运行目录为 versions/<id>，mods/saves/resourcepacks/shaderpacks
        // 等随版本存放；关闭时回到游戏根目录。该值同时作为 FCLConfig.workingDir
        // （bridge.chdir 的工作目录）与 ${game_directory} 参数。
        if (!runDirectoryIsolated) {
            return getBaseDirectory();
        }
        return getVersionRoot(id);
    }

    @Override
    public File getLibrariesDirectory(Version version) {
        return new File(getBaseDirectory(), "libraries");
    }

    @Override
    public File getLibraryFile(Version version, Library lib) {
        if ("local".equals(lib.getHint()) && lib.getFileName() != null) {
            return new File(getVersionRoot(version.getId()), "libraries/" + lib.getFileName());
        } else {
            return new File(getLibrariesDirectory(version), lib.getPath());
        }
    }

    @Override
    public File getVersionJar(Version version) {
        Version v = version.resolve(this);
        String id = Optional.ofNullable(v.getJar()).orElse(v.getId());
        return new File(getVersionRoot(id), id + ".jar");
    }

    @Override
    public Optional<String> getGameVersion(Version version) {
        return gameVersions.computeIfAbsent(getVersionJar(version), versionJar -> {
            Optional<String> gameVersion = GameVersion.minecraftVersion(versionJar);
            if (!gameVersion.isPresent()) {
                gameVersion = Optional.ofNullable(version.getVersion());
            }
            if (!gameVersion.isPresent()) {
                Log.w(TAG, "Cannot find out game version of " + version.getId()
                        + ", primary jar: " + versionJar);
            }
            return gameVersion;
        });
    }

    @Override
    public Path getModsDirectory(String id) {
        return getRunDirectory(id).toPath().resolve("mods");
    }

    @Override
    public boolean renameVersion(String from, String to) {
        try {
            Version fromVersion = getVersion(from);
            Path fromDir = getVersionRoot(from).toPath();
            Path toDir = getVersionRoot(to).toPath();
            Files.move(fromDir, toDir);

            Path fromJson = toDir.resolve(from + ".json");
            Path fromJar = toDir.resolve(from + ".jar");
            Path toJson = toDir.resolve(to + ".json");
            Path toJar = toDir.resolve(to + ".jar");

            boolean hasJar = Files.exists(fromJar);
            Files.move(fromJson, toJson);
            if (hasJar) Files.move(fromJar, toJar);
            return true;
        } catch (IOException | VersionNotFoundException e) {
            Log.w(TAG, "Unable to rename version " + from + " to " + to, e);
            return false;
        }
    }

    @Override
    public void refreshVersions() {
        Map<String, Version> result = new TreeMap<>();
        File versionsDir = new File(getBaseDirectory(), "versions");
        Log.d(TAG, "refreshVersions: baseDirectory=" + getBaseDirectory()
                + ", versionsDirExists=" + versionsDir.exists()
                + ", isDir=" + versionsDir.isDirectory());
        File[] files = versionsDir.listFiles();
        if (files == null) {
            Log.e(TAG, "refreshVersions: versionsDir.listFiles() returned null");
        } else {
            Log.d(TAG, "refreshVersions: found " + files.length + " entries");
            for (File f : files) {
                Log.d(TAG, "refreshVersions: entry=" + f.getAbsolutePath()
                        + ", isDir=" + f.isDirectory());
            }
        }
        if (files != null) {
            // FCL-style two-phase refresh: collect ALL versions first, then resolve.
            // This allows child versions (e.g. "26.2-forge-65.1.3" inheritsFrom "26.2")
            // to resolve their parents even when the parent is parsed later.
            SimpleVersionProvider provider = new SimpleVersionProvider();
            for (File dir : files) {
                if (!dir.isDirectory()) continue;
                String id = dir.getName();
                File json = new File(dir, id + ".json");
                if (!json.exists()) {
                    Log.w(TAG, "refreshVersions: " + dir + " has no " + id + ".json");
                    continue;
                }
                try {
                    Version version = readVersionJson(json);
                    if (!id.equals(version.getId())) {
                        version._setId(id);
                    }
                    provider.addVersion(version);
                } catch (Exception e) {
                    Log.e(TAG, "refreshVersions: failed to read version " + id + " from " + json, e);
                }
            }

            for (Version version : provider.getVersionMap().values()) {
                try {
                    Version resolved = version.resolve(provider);
                    boolean ok = resolved.appliesToCurrentEnvironment();
                    Log.d(TAG, "refreshVersions: " + version.getId() + " parsed, applies=" + ok
                            + ", mainClass=" + resolved.getMainClass()
                            + ", jvmArgs=" + resolved.getArguments()
                                    .map(Arguments::getJvm)
                                    .map(List::size)
                                    .orElse(0));
                    if (ok) {
                        // Store the RESOLVED version so that child versions (e.g.
                        // "26.2-forge-65.1.3" inheritsFrom "26.2") keep their merged
                        // parent arguments (including "-cp ${classpath}"). Storing the
                        // raw version would drop inherited JVM/game arguments and the
                        // launcher would launch with an empty classpath.
                        result.put(version.getId(), dedupeLibraries(resolved));
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Ignoring version " + version.getId()
                            + " because it inherits from a nonexistent version.");
                }
            }
        }
        gameVersions.clear();
        this.versions = result;
        Log.d(TAG, "refreshVersions: final versions=" + result.keySet());
    }

    public Version readVersionJson(File file) throws IOException, JsonParseException {
        String jsonText = FileUtils.readText(file);
        try {
            return gson.fromJson(jsonText, Version.class);
        } catch (JsonParseException e) {
            throw new JsonParseException("Version json incorrect: " + file, e);
        }
    }

    /**
     * 合并版本库去重：resolve 会把父版本（原版）库追加到补丁库之后，但同
     * group:artifact 的多版本并全会触发模块导出冲突（如 log4j-slf4j2-impl 2.19.0
     * 与 2.22.1 模块导出同一包 org.apache.logging.slf4j）。按去掉 version 段
     * （保留 classifier）的 key 保留先出现的补丁库，剔除父版本的旧库。
     * 注意 key 必须保留 classifier：forge-universal 与 forge-client 是
     * group:artifact:version:classifier 四段，minecraft 客户端类只在 client jar。
     */
    private Version dedupeLibraries(Version version) {
        List<Library> libs = version.getLibraries();
        if (libs == null || libs.isEmpty()) return version;
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        List<Library> deduped = new java.util.ArrayList<>();
        for (Library lib : libs) {
            String name = lib.getName();
            String[] parts = name.split(":");
            String key;
            if (parts.length >= 4) {
                key = parts[0] + ":" + parts[1] + ":" + parts[3];
            } else if (parts.length >= 2) {
                key = parts[0] + ":" + parts[1];
            } else {
                key = name;
            }
            if (seen.add(key)) deduped.add(lib);
        }
        return deduped.size() == libs.size() ? version : version.setLibraries(deduped);
    }

    @Override
    public AssetIndex getAssetIndex(String version, String assetId) throws IOException {
        File indexFile = getIndexFile(version, assetId).toFile();
        if (!indexFile.exists()) {
            throw new IOException("Asset index file not found: " + indexFile);
        }
        try {
            AssetIndex index = gson.fromJson(FileUtils.readText(indexFile), AssetIndex.class);
            if (index == null) {
                throw new IOException("Asset index file malformed: " + indexFile);
            }
            return index;
        } catch (JsonParseException e) {
            throw new IOException("Asset index file malformed: " + indexFile, e);
        }
    }

    @Override
    public Path getActualAssetDirectory(String version, String assetId) {
        try {
            return reconstructAssets(version, assetId);
        } catch (IOException | JsonParseException e) {
            Log.w(TAG, "Unable to reconstruct asset directory", e);
            return getAssetDirectory(version, assetId);
        }
    }

    @Override
    public Path getAssetDirectory(String version, String assetId) {
        return getBaseDirectory().toPath().resolve("assets");
    }

    @Override
    public Optional<Path> getAssetObject(String version, String assetId, String name) throws IOException {
        AssetObject assetObject = getAssetIndex(version, assetId).getObjects().get(name);
        if (assetObject == null) return Optional.empty();
        return Optional.of(getAssetObject(version, assetId, assetObject));
    }

    @Override
    public Path getAssetObject(String version, String assetId, AssetObject obj) {
        return getAssetObject(version, getAssetDirectory(version, assetId), obj);
    }

    public Path getAssetObject(String version, Path assetDir, AssetObject obj) {
        return assetDir.resolve("objects").resolve(obj.getLocation());
    }

    @Override
    public Path getIndexFile(String version, String assetId) {
        return getAssetDirectory(version, assetId).resolve("indexes").resolve(assetId + ".json");
    }

    @Override
    public Path getLoggingObject(String version, String assetId, LoggingInfo loggingInfo) {
        return getAssetDirectory(version, assetId).resolve("log_configs")
                .resolve(loggingInfo.getFile().getId());
    }

    protected Path reconstructAssets(String version, String assetId) throws IOException, JsonParseException {
        Path assetsDir = getAssetDirectory(version, assetId);
        Path indexFile = getIndexFile(version, assetId);
        Path virtualRoot = assetsDir.resolve("virtual").resolve(assetId);

        if (!Files.isRegularFile(indexFile)) {
            return assetsDir;
        }

        AssetIndex index = getAssetIndex(version, assetId);
        if (index == null) {
            return assetsDir;
        }

        if (index.isVirtual()) {
            Path resourcesDir = getRunDirectory(version).toPath().resolve("resources");
            int tot = index.getObjects().size();
            int cnt = 0;
            for (Map.Entry<String, AssetObject> entry : index.getObjects().entrySet()) {
                Path target = virtualRoot.resolve(entry.getKey());
                Path original = getAssetObject(version, assetsDir, entry.getValue());
                if (Files.exists(original)) {
                    cnt++;
                    if (!Files.isRegularFile(target)) {
                        copyFile(original, target);
                    }
                    if (index.needMapToResources()) {
                        target = resourcesDir.resolve(entry.getKey());
                        if (!Files.isRegularFile(target)) {
                            copyFile(original, target);
                        }
                    }
                }
            }
            if (cnt * 10 < tot) {
                return assetsDir;
            } else {
                return virtualRoot;
            }
        }
        return assetsDir;
    }

    private static void copyFile(Path from, Path to) throws IOException {
        try {
            Files.createDirectories(to.getParent());
            Files.copy(from, to);
        } catch (IOException e) {
            throw e;
        }
    }

    public boolean isLoaded() {
        return versions != null;
    }

    @Override
    public String toString() {
        return "DefaultGameRepository(baseDirectory=" + baseDirectory
                + ", versions=" + (versions == null ? null : versions.keySet()) + ")";
    }
}