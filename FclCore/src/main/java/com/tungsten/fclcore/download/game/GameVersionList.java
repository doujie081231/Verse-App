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
package com.tungsten.fclcore.download.game;

import static com.tungsten.fclcore.util.Logging.LOG;

import com.tungsten.fclcore.download.DownloadProvider;
import com.tungsten.fclcore.download.VersionList;
import com.tungsten.fclcore.task.Schedulers;
import com.tungsten.fclcore.util.gson.JsonUtils;
import com.tungsten.fclcore.util.io.HttpRequest;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class GameVersionList extends VersionList<GameRemoteVersion> {
    private final DownloadProvider downloadProvider;

    private static final String FALLBACK_MANIFEST_URL =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    public GameVersionList(DownloadProvider downloadProvider) {
        this.downloadProvider = downloadProvider;
    }

    @Override
    public boolean hasType() {
        return true;
    }

    @Override
    protected Collection<GameRemoteVersion> getVersionsImpl(String gameVersion) {
        return versions.values();
    }

    /** 版本清单双源获取（宿主适配）：主源为当前下载源的清单 URL，失败/被截断时回退官方 v2 清单 */
    private CompletableFuture<GameRemoteVersions> fetchManifest() {
        List<String> urls = new ArrayList<>();
        urls.add(downloadProvider.getVersionListURL());
        urls.add(FALLBACK_MANIFEST_URL);
        return CompletableFuture.supplyAsync(() -> {
            Exception last = null;
            for (String url : urls) {
                try {
                    // getStringWithRetry 内部自带重试；解析失败（如清单被截断）会抛异常并回退下一源
                    return HttpRequest.GET(url).getJson(GameRemoteVersions.class);
                } catch (Exception e) {
                    last = e;
                    LOG.log(Level.WARNING, "Failed to refresh game version list from " + url, e);
                }
            }
            throw new RuntimeException(last);
        }, Schedulers.defaultScheduler());
    }

    @Override
    public CompletableFuture<?> refreshAsync() {
        return fetchManifest()
                .thenAcceptAsync(root -> {
                    GameRemoteVersions unlistedVersions = null;
                    try (Reader input = new InputStreamReader(
                            GameVersionList.class.getResourceAsStream("/assets/game/unlisted-versions.json"))) {
                        unlistedVersions = JsonUtils.GSON.fromJson(input, GameRemoteVersions.class);
                    } catch (Throwable e) {
                        LOG.log(Level.WARNING, "Failed to load unlisted versions", e);
                    }

                    lock.writeLock().lock();
                    try {
                        versions.clear();

                        if (unlistedVersions != null) {
                            for (GameRemoteVersionInfo unlistedVersion : unlistedVersions.getVersions()) {
                                versions.put(unlistedVersion.getGameVersion(), new GameRemoteVersion(
                                        unlistedVersion.getGameVersion(),
                                        unlistedVersion.getGameVersion(),
                                        Collections.singletonList(unlistedVersion.getUrl()),
                                        unlistedVersion.getType(), unlistedVersion.getReleaseTime()));
                            }
                        }

                        for (GameRemoteVersionInfo remoteVersion : root.getVersions()) {
                            versions.put(remoteVersion.getGameVersion(), new GameRemoteVersion(
                                    remoteVersion.getGameVersion(),
                                    remoteVersion.getGameVersion(),
                                    Collections.singletonList(remoteVersion.getUrl()),
                                    remoteVersion.getType(), remoteVersion.getReleaseTime()));
                        }
                    } finally {
                        lock.writeLock().unlock();
                    }
                });
    }
}
