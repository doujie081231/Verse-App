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
package com.tungsten.fclcore.mod.curse;

import com.google.gson.JsonParseException;
import com.tungsten.fclcore.download.DefaultDependencyManager;
import com.tungsten.fclcore.download.ModpackFileCandidates;
import com.tungsten.fclcore.game.DefaultGameRepository;
import com.tungsten.fclcore.mod.ModManager;
import com.tungsten.fclcore.mod.ModpackCompletionException;
import com.tungsten.fclcore.mod.RemoteMod;
import com.tungsten.fclcore.task.DownloadException;
import com.tungsten.fclcore.task.FileDownloadTask;
import com.tungsten.fclcore.task.Task;
import com.tungsten.fclcore.util.DigestUtils;
import com.tungsten.fclcore.util.Logging;
import com.tungsten.fclcore.util.StringUtils;
import com.tungsten.fclcore.util.gson.JsonUtils;
import com.tungsten.fclcore.util.io.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Complete the CurseForge version.
 */
public final class CurseCompletionTask extends Task<Void> {

    private final DefaultDependencyManager dependency;
    private final DefaultGameRepository repository;
    private final ModManager modManager;
    private final String version;
    private CurseManifest manifest;
    private List<Task<?>> dependencies;

    private final AtomicBoolean allNameKnown = new AtomicBoolean(true);
    private final AtomicInteger finished = new AtomicInteger(0);

    /**
     * Constructor.
     *
     * @param dependencyManager the dependency manager.
     * @param version           the existent and physical version.
     */
    public CurseCompletionTask(DefaultDependencyManager dependencyManager, String version) {
        this(dependencyManager, version, null);
    }

    /**
     * Constructor.
     *
     * @param dependencyManager the dependency manager.
     * @param version           the existent and physical version.
     * @param manifest          the CurseForgeModpack manifest.
     */
    public CurseCompletionTask(DefaultDependencyManager dependencyManager, String version, CurseManifest manifest) {
        this.dependency = dependencyManager;
        this.repository = dependencyManager.getGameRepository();
        this.modManager = repository.getModManager(version);
        this.version = version;
        this.manifest = manifest;

        if (manifest == null)
            try {
                File manifestFile = new File(repository.getVersionRoot(version), "manifest.json");
                if (manifestFile.exists())
                    this.manifest = JsonUtils.GSON.fromJson(FileUtils.readText(manifestFile), CurseManifest.class);
            } catch (Exception e) {
                Logging.LOG.log(Level.WARNING, "Unable to read CurseForge modpack manifest.json", e);
            }

        setStage("fcl.modpack.download");
    }

    @Override
    public Collection<Task<?>> getDependencies() {
        return dependencies;
    }

    @Override
    public boolean isRelyingOnDependencies() {
        return false;
    }

    @Override
    public void execute() throws Exception {
        if (manifest == null)
            return;

        File root = repository.getVersionRoot(version);

        // 解析文件下载信息时同步记录 SHA-1，供整合包模组文件校验
        Map<String, FileDownloadTask.IntegrityCheck> checks = new ConcurrentHashMap<>();

        // Because in China, Curse is too difficult to visit,
        // if failed, ignore it and retry next time.
        CurseManifest newManifest = manifest.setFiles(
                manifest.getFiles().parallelStream()
                        .map(file -> {
                            updateProgress(finished.incrementAndGet(), manifest.getFiles().size());
                            if (StringUtils.isBlank(file.getFileName()) || file.getUrl() == null) {
                                try {
                                    RemoteMod.File remoteFile = CurseForgeRemoteModRepository.MODS.getModFile(Integer.toString(file.getProjectID()), Integer.toString(file.getFileID()));
                                    FileDownloadTask.IntegrityCheck check = remoteFile.getIntegrityCheck();
                                    if (check != null)
                                        checks.put(fileKey(file), check);
                                    return file.withFileName(remoteFile.getFilename()).withURL(remoteFile.getUrl());
                                } catch (FileNotFoundException fof) {
                                    // 源端已删除的文件直接跳过，不终止整个整合包安装
                                    Logging.LOG.log(Level.WARNING, "Could not query api.curseforge.com for deleted mods, skipped: " + file.getProjectID() + ", " + file.getFileID(), fof);
                                    return file;
                                } catch (IOException | JsonParseException e) {
                                    Logging.LOG.log(Level.WARNING, "Unable to fetch the file name projectID=" + file.getProjectID() + ", fileID=" + file.getFileID(), e);
                                    allNameKnown.set(false);
                                    return file;
                                }
                            } else {
                                return file;
                            }
                        })
                        .collect(Collectors.toList()));
        FileUtils.writeText(new File(root, "manifest.json"), JsonUtils.GSON.toJson(newManifest));

        File versionRoot = repository.getVersionRoot(modManager.getInstanceId());
        File resourcePacksRoot = new File(versionRoot, "resourcepacks"), shaderPacksRoot = new File(versionRoot, "shaderpacks");
        finished.set(0);
        dependencies = newManifest.getFiles()
                .stream().parallel()
                .filter(f -> f.getFileName() != null)
                .flatMap(f -> {
                    try {
                        FileDownloadTask.IntegrityCheck check = checks.get(fileKey(f));
                        File path = guessFilePath(f, resourcePacksRoot, shaderPacksRoot, check);
                        if (path == null) {
                            return Stream.empty();
                        }

                        FileDownloadTask task = new SkipMissingFileDownloadTask(ModpackFileCandidates.of(f.getUrl()), path, check);
                        task.addIntegrityCheckHandler(FileDownloadTask.ZIP_INTEGRITY_CHECK_HANDLER);
                        task.setCacheRepository(dependency.getCacheRepository());
                        task.setCaching(true);
                        return Stream.of(task.withCounter("fcl.modpack.download"));
                    } catch (IOException e) {
                        Logging.LOG.log(Level.WARNING, "Could not query api.curseforge.com for mod: " + f.getProjectID() + ", " + f.getFileID(), e);
                        return Stream.empty(); // Ignore this file.
                    } finally {
                        updateProgress(finished.incrementAndGet(), newManifest.getFiles().size());
                    }
                })
                .collect(Collectors.toList());

        if (!dependencies.isEmpty()) {
            getProperties().put("total", dependencies.size());
            notifyPropertiesChanged();
        }
    }

    /**
     * Guess where to store the file.
     * @param file The file to download.
     * @param resourcePacksRoot ./resourcepacks.
     * @param shaderPacksRoot ./shaderpacks.
     * @param check 已知 SHA-1 校验（用于复用已下载文件）
     * @return ./resourcepacks/$filename or ./shaderpacks/$filename or ./mods/$filename if the file doesn't exist. null if the file existed.
     * @throws IOException If IOException was encountered during getting data from CurseForge.
     */
    private File guessFilePath(CurseManifestFile file, File resourcePacksRoot, File shaderPacksRoot,
                               FileDownloadTask.IntegrityCheck check) throws IOException {
        RemoteMod mod = CurseForgeRemoteModRepository.MODS.getModById(Integer.toString(file.getProjectID()));
        int classID = ((CurseAddon) mod.getData()).classId();
        String fileName = file.getFileName();
        switch (classID) {
            case 12: // Resource pack
            case 6552: { // Shader pack
                File res = new File(classID == 12 ? resourcePacksRoot : shaderPacksRoot, fileName);
                return existingFileValid(res, check) ? null : res;
            }
            default: {
                if (modManager.hasSimpleMod(fileName)) {
                    return null;
                }
                File target = modManager.getSimpleModPath(fileName).toFile();
                return existingFileValid(target, check) ? null : target;
            }
        }
    }

    /**
     * 已存在文件校验：sha1 可用且匹配才复用，不匹配则删除后重新下载。
     */
    private boolean existingFileValid(File file, FileDownloadTask.IntegrityCheck check) {
        if (!file.exists())
            return false;
        if (check == null)
            return true;
        try {
            if (check.getChecksum().equalsIgnoreCase(
                    DigestUtils.digestToString(check.getAlgorithm(), file.toPath())))
                return true;
        } catch (IOException e) {
            Logging.LOG.log(Level.WARNING, "Unable to verify existing modpack file " + file, e);
        }
        if (!file.delete())
            Logging.LOG.log(Level.WARNING, "Unable to delete corrupted modpack file " + file);
        return false;
    }

    private static String fileKey(CurseManifestFile file) {
        return file.getProjectID() + ":" + file.getFileID();
    }

    @Override
    public boolean doPostExecute() {
        return true;
    }

    @Override
    public void postExecute() throws Exception {
        // Let this task fail if the curse manifest has not been completed.
        // But continue other downloads.
        if (!allNameKnown.get() || !isDependenciesSucceeded())
            throw new ModpackCompletionException();
    }

    /** 源端 404 的文件直接跳过（单个模组缺失不终止整个整合包安装） */
    private static final class SkipMissingFileDownloadTask extends FileDownloadTask {
        SkipMissingFileDownloadTask(List<URL> urls, File file, FileDownloadTask.IntegrityCheck check) {
            super(urls, file, check);
        }

        @Override
        public void execute() throws Exception {
            try {
                super.execute();
            } catch (DownloadException e) {
                if (isNotFound(e)) {
                    Logging.LOG.log(Level.WARNING, "Modpack file not found on all sources, skipped: " + getFile(), e);
                } else {
                    throw e;
                }
            }
        }

        private static boolean isNotFound(Throwable throwable) {
            Throwable current = throwable;
            while (current != null) {
                if (current instanceof FileNotFoundException)
                    return true;
                current = current.getCause();
            }
            return false;
        }
    }
}
