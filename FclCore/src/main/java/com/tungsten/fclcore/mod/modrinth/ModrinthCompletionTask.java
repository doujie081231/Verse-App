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
package com.tungsten.fclcore.mod.modrinth;

import com.tungsten.fclcore.download.DefaultDependencyManager;
import com.tungsten.fclcore.download.ModpackFileCandidates;
import com.tungsten.fclcore.game.DefaultGameRepository;
import com.tungsten.fclcore.mod.ModManager;
import com.tungsten.fclcore.mod.ModpackCompletionException;
import com.tungsten.fclcore.task.DownloadException;
import com.tungsten.fclcore.task.FileDownloadTask;
import com.tungsten.fclcore.task.Task;
import com.tungsten.fclcore.util.DigestUtils;
import com.tungsten.fclcore.util.Logging;
import com.tungsten.fclcore.util.gson.JsonUtils;
import com.tungsten.fclcore.util.io.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public class ModrinthCompletionTask extends Task<Void> {

    private final DefaultDependencyManager dependency;
    private final DefaultGameRepository repository;
    private final ModManager modManager;
    private final String version;
    private ModrinthManifest manifest;
    private final List<Task<?>> dependencies = new ArrayList<>();

    private final AtomicBoolean allNameKnown = new AtomicBoolean(true);
    private final AtomicInteger finished = new AtomicInteger(0);
    private final AtomicBoolean notFound = new AtomicBoolean(false);

    /**
     * Constructor.
     *
     * @param dependencyManager the dependency manager.
     * @param version           the existent and physical version.
     */
    public ModrinthCompletionTask(DefaultDependencyManager dependencyManager, String version) {
        this(dependencyManager, version, null);
    }

    /**
     * Constructor.
     *
     * @param dependencyManager the dependency manager.
     * @param version           the existent and physical version.
     * @param manifest          the CurseForgeModpack manifest.
     */
    public ModrinthCompletionTask(DefaultDependencyManager dependencyManager, String version, ModrinthManifest manifest) {
        this.dependency = dependencyManager;
        this.repository = dependencyManager.getGameRepository();
        this.modManager = repository.getModManager(version);
        this.version = version;
        this.manifest = manifest;

        if (manifest == null)
            try {
                File manifestFile = new File(repository.getVersionRoot(version), "modrinth.index.json");
                if (manifestFile.exists())
                    this.manifest = JsonUtils.GSON.fromJson(FileUtils.readText(manifestFile), ModrinthManifest.class);
            } catch (Exception e) {
                Logging.LOG.log(Level.WARNING, "Unable to read Modrinth modpack manifest.json", e);
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

        Path runDirectory = repository.getRunDirectory(version).toPath().toAbsolutePath().normalize();
        Path modsDirectory = runDirectory.resolve("mods");

        for (ModrinthManifest.File file : manifest.getFiles()) {
            if (file.getEnv() != null && file.getEnv().getOrDefault("client", "required").equals("unsupported"))
                continue;
            if (file.getDownloads().isEmpty())
                continue;
            Path filePath = runDirectory.resolve(file.getPath()).toAbsolutePath().normalize();
            if (!filePath.startsWith(runDirectory))
                throw new ModpackCompletionException("Unsecure path: " + file.getPath());
            String sha1 = file.getHashes() == null ? null : file.getHashes().get("sha1");
            if (Files.isRegularFile(filePath)) {
                // 已存在文件校验：sha1 可用且匹配才复用，
                // 不匹配（中断/镜像错误留下的坏文件）删除后重新下载
                if (sha1 == null || sha1.isEmpty())
                    continue;
                String actual = DigestUtils.digestToString("SHA-1", filePath);
                if (sha1.equalsIgnoreCase(actual))
                    continue;
                Files.deleteIfExists(filePath);
            }
            if (modsDirectory.equals(filePath.getParent()) && this.modManager.hasSimpleMod(FileUtils.getName(filePath)))
                continue;
            FileDownloadTask.IntegrityCheck check = sha1 == null || sha1.isEmpty()
                    ? null
                    : new FileDownloadTask.IntegrityCheck("SHA-1", sha1);
            FileDownloadTask task = new SkipMissingFileDownloadTask(ModpackFileCandidates.of(file.getDownloads()), filePath.toFile(), check);
            task.addIntegrityCheckHandler(FileDownloadTask.ZIP_INTEGRITY_CHECK_HANDLER);
            task.setCacheRepository(dependency.getCacheRepository());
            task.setCaching(true);
            dependencies.add(task.withCounter("fcl.modpack.download"));
        }

        if (!dependencies.isEmpty()) {
            getProperties().put("total", dependencies.size());
            notifyPropertiesChanged();
        }
    }

    @Override
    public boolean doPostExecute() {
        return true;
    }

    @Override
    public void postExecute() throws Exception {
        // Let this task fail if the curse manifest has not been completed.
        // But continue other downloads.
        if (notFound.get())
            throw new ModpackCompletionException(new FileNotFoundException());
        if (!allNameKnown.get())
            throw new ModpackCompletionException();
        if (!isDependenciesSucceeded()) {
            // 不再抛空消息：把失败的文件名列出来，否则用户只看到 "ModpackCompletionException: null"，
            // 既无法判断是哪个文件，也无从重试
            List<String> failed = new ArrayList<>();
            for (Task<?> task : dependencies) {
                if (task.getException() == null) continue;
                if (task instanceof FileDownloadTask) {
                    failed.add(((FileDownloadTask) task).getFile().getName());
                }
                if (failed.size() >= 5) break;
            }
            throw new ModpackCompletionException(failed.isEmpty()
                    ? "整合包文件下载失败"
                    : "整合包部分文件下载失败: " + String.join("、", failed));
        }
    }

    /**
     * 源端已下架（404）的整合包文件直接跳过：
     * 单个 mod 在镜像与官方都返回 404 时不应让整个整合包安装失败，
     * 其余文件正常安装，缺失的文件在启动日志中体现。
     */
    private static final class SkipMissingFileDownloadTask extends FileDownloadTask {
        SkipMissingFileDownloadTask(List<URL> urls, File file, FileDownloadTask.IntegrityCheck check) {
            super(urls, file, check);
        }

        @Override
        public void execute() throws Exception {
            try {
                super.execute();
            } catch (Exception e) {
                // 下载任务抛出的是 IOException / ChecksumMismatchException，404 表现为 FileNotFoundException，
                // 这里沿因果链判断；命中 404 才跳过，其他失败照常让整合包安装失败
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
