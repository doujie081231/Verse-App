package com.verse.explorer.app.data.version

import com.google.gson.GsonBuilder
import com.verse.explorer.app.domain.model.version.FabricLoaderMeta
import com.verse.explorer.app.domain.model.version.ForgeRaw
import com.verse.explorer.app.domain.model.version.LoaderVersion
import com.verse.explorer.app.domain.model.version.NeoForgeEntry
import com.verse.explorer.app.domain.model.version.OptiFineEntry
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object LoaderRepository {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val gson = GsonBuilder().setLenient().create()

    fun fetchFabricVersions(mcVersion: String): List<LoaderVersion> {
        val urls = listOf(
            "https://bmclapi2.bangbang93.com/fabric-meta/v2/versions/loader/$mcVersion",
            "https://meta.fabricmc.net/v2/versions/loader/$mcVersion",
            "https://bmclapi.nofated.win/fabric-meta/v2/versions/loader/$mcVersion"
        )
        for (url in urls) {
            try {
                val request = Request.Builder().url(url).header("User-Agent", "VerseExplorerX/1.0").build()
                val response = httpClient.newCall(request).execute()
                response.use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (body != null) {
                            val list = gson.fromJson(body, Array<FabricLoaderMeta>::class.java)
                            if (list.isNotEmpty()) return list.map { LoaderVersion(it.loader.version, "release") }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return emptyList()
    }

    fun fetchForgeVersions(mcVersion: String): List<LoaderVersion> {
        val urls = listOf(
            "https://bmclapi2.bangbang93.com/forge/minecraft/$mcVersion",
            "https://bmclapi.nofated.win/forge/minecraft/$mcVersion"
        )
        for (url in urls) {
            try {
                val request = Request.Builder().url(url).header("User-Agent", "VerseExplorerX/1.0").build()
                val response = httpClient.newCall(request).execute()
                response.use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (body != null) {
                            val list = gson.fromJson(body, Array<ForgeRaw>::class.java)
                            if (list.isNotEmpty()) return list.map { LoaderVersion(it.version, if (it.type == "latest") "release" else "beta") }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return emptyList()
    }

    fun fetchNeoForgeVersions(mcVersion: String): List<LoaderVersion> {
        val urls = listOf(
            "https://bmclapi2.bangbang93.com/neoforge/list?mcversion=$mcVersion",
            "https://bmclapi.nofated.win/neoforge/list?mcversion=$mcVersion"
        )
        for (url in urls) {
            try {
                val request = Request.Builder().url(url).header("User-Agent", "VerseExplorerX/1.0").build()
                val response = httpClient.newCall(request).execute()
                response.use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (body != null) {
                            val list = gson.fromJson(body, Array<NeoForgeEntry>::class.java)
                            if (list.isNotEmpty()) return list.map { LoaderVersion(it.version, "release") }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return emptyList()
    }

    fun fetchQuiltVersions(mcVersion: String): List<LoaderVersion> {
        val urls = listOf(
            "https://bmclapi2.bangbang93.com/quilt-meta/v3/versions/loader/$mcVersion",
            "https://meta.quiltmc.org/v3/versions/loader/$mcVersion",
            "https://bmclapi.nofated.win/quilt-meta/v3/versions/loader/$mcVersion"
        )
        for (url in urls) {
            try {
                val request = Request.Builder().url(url).header("User-Agent", "VerseExplorerX/1.0").build()
                val response = httpClient.newCall(request).execute()
                response.use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (body != null) {
                            val list = gson.fromJson(body, Array<FabricLoaderMeta>::class.java)
                            if (list.isNotEmpty()) return list.map { LoaderVersion(it.loader.version, "release") }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return emptyList()
    }

    fun fetchOptiFineVersions(mcVersion: String): List<LoaderVersion> {
        val urls = listOf(
            "https://bmclapi2.bangbang93.com/optifine/$mcVersion",
            "https://bmclapi.nofated.win/optifine/$mcVersion"
        )
        for (url in urls) {
            try {
                val request = Request.Builder().url(url).header("User-Agent", "VerseExplorerX/1.0").build()
                val response = httpClient.newCall(request).execute()
                response.use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (body != null) {
                            val list = gson.fromJson(body, Array<OptiFineEntry>::class.java)
                            if (list.isNotEmpty()) return list.map { LoaderVersion(it.fileName.removePrefix("OptiFine_").removeSuffix(".jar"), "release") }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return emptyList()
    }
}
