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

import com.google.gson.annotations.SerializedName;

public class LibraryDownloadInfo {

    @SerializedName("path")
    private final String path;
    @SerializedName("url")
    private final String url;
    @SerializedName("sha1")
    private final String sha1;
    @SerializedName("size")
    private final int size;

    public LibraryDownloadInfo() { this(""); }

    public LibraryDownloadInfo(String path) { this(path, null); }

    public LibraryDownloadInfo(String path, String url) { this(path, url, null, 0); }

    public LibraryDownloadInfo(String path, String url, String sha1, int size) {
        this.path = path;
        this.url = url;
        this.sha1 = sha1;
        this.size = size;
    }

    public String getPath() { return path; }

    public String getUrl() { return url; }

    public String getSha1() { return sha1; }

    public int getSize() { return size; }
}
