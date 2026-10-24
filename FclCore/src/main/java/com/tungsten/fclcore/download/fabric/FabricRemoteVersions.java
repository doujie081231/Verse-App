package com.tungsten.fclcore.download.fabric;

import java.util.List;

/**
 * FabricRemoteVersion 构造器为包私有（FCL 由 FabricVersionList 在包内创建）。
 * 宿主（VerseApp 已从 BMCL 列表取到 loader 版本串）需要直接构造远程版本，
 * 因此提供包内公开工厂。
 */
public final class FabricRemoteVersions {
    private FabricRemoteVersions() {}

    public static FabricRemoteVersion of(String gameVersion, String selfVersion, List<String> urls) {
        return new FabricRemoteVersion(gameVersion, selfVersion, urls);
    }
}