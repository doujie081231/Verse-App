package com.tungsten.fclcore.download;

import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 整合包模组文件的下载候选源。
 * CurseForge / Modrinth 的官方 CDN（edge.forgecdn.net / cdn.modrinth.com）在国内直连
 * 极不稳定，是整合包"下载整合包模组"阶段失败（Unable to download）的主因；
 * FileDownloadTask 对候选列表按序回退，镜像源在前、官方源兜底。
 */
public final class ModpackFileCandidates {

    /** host 前缀 → 镜像前缀（与启动器资源下载的镜像映射保持一致） */
    private static final String[][] MIRRORS = {
            {"https://cdn.modrinth.com/", "https://mod.mcimirror.top/"},
            {"https://cdn-alt.modrinth.com/", "https://mod.mcimirror.top/"},
            {"https://edge.forgecdn.net/", "https://mediafilez.forgecdn.net/"},
    };

    private ModpackFileCandidates() {}

    /** 单 URL 的候选列表：命中镜像映射时镜像在前，官方原 URL 永远兜底 */
    public static List<URL> of(URL original) {
        LinkedHashSet<URL> set = new LinkedHashSet<>();
        if (original != null) {
            String s = original.toString();
            for (String[] pair : MIRRORS) {
                if (s.startsWith(pair[0])) {
                    try {
                        set.add(new URL(pair[1] + s.substring(pair[0].length())));
                    } catch (Exception ignored) {
                    }
                }
            }
            set.add(original);
        }
        return new ArrayList<>(set);
    }

    /** 多 URL（如 Modrinth downloads 列表）的候选合并，保持顺序去重 */
    public static List<URL> of(List<URL> originals) {
        LinkedHashSet<URL> set = new LinkedHashSet<>();
        if (originals != null) {
            for (URL u : originals) {
                set.addAll(of(u));
            }
        }
        return new ArrayList<>(set);
    }
}
