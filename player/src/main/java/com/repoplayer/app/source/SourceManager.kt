package com.repoplayer.app.source

import android.content.Context
import com.fasterxml.jackson.databind.JsonNode
import com.repoplayer.app.data.Net
import com.repoplayer.app.data.RepoEntry
import com.repoplayer.app.data.RepoType
import com.repoplayer.app.data.SourceEntry
import com.repoplayer.app.data.Store
import com.repoplayer.app.data.json
import com.repoplayer.app.source.cs3.Cs3Loader
import com.repoplayer.app.source.cs3.Cs3Repo
import com.repoplayer.app.source.cs3.Cs3Source
import com.repoplayer.app.source.nuvio.NuvioCatalogSource
import com.repoplayer.app.source.nuvio.NuvioRepo
import com.repoplayer.app.source.nuvio.NuvioSource
import com.repoplayer.app.source.stremio.StremioRepo
import com.repoplayer.app.source.stremio.StremioSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SourceManager(private val context: Context, val store: Store) {
    private val cache = HashMap<String, ContentSource>()
    private val lock = Mutex()

    fun entries(): List<SourceEntry> = store.repos.flatMap { it.sources }

    fun normalize(input: String): String {
        var u = input.trim()
        u = u.replaceFirst(Regex("^(cloudstreamrepo|stremio)://", RegexOption.IGNORE_CASE), "https://")
        if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) u = "https://$u"
        return u
    }

    private suspend fun fetchJson(url: String): Pair<String, JsonNode> {
        val first = runCatching { url to json.readTree(Net.text(url)) }
        first.getOrNull()?.let { if (it.second.isObject || it.second.isArray) return it }
        if (!url.endsWith(".json")) {
            val alt = url.trimEnd('/') + "/manifest.json"
            runCatching { return alt to json.readTree(Net.text(alt)) }
        }
        throw first.exceptionOrNull() ?: IllegalArgumentException("Geçerli JSON değil")
    }

    /** repo.json (CS3), manifest.json (Nuvio scraper reposu) veya Stremio eklentisi okur */
    suspend fun resolve(input: String): RepoEntry {
        val (url, root) = fetchJson(normalize(input))
        return when {
            root.has("pluginLists") -> Cs3Repo.read(url, root)
            root.isArray && root.firstOrNull()?.has("internalName") == true -> Cs3Repo.read(url, root)
            root.has("scrapers") || root.has("providers") -> NuvioRepo.read(url, root)
            // Nuvio'nun güncel biçimi: manifest doğrudan sağlayıcı dizisi
            root.isArray && root.firstOrNull()?.has("filename") == true -> NuvioRepo.read(url, root)
            StremioRepo.isAddon(root) -> StremioRepo.read(url, root)
            else -> throw IllegalArgumentException("Tanınmayan format: repo.json veya manifest.json bekleniyordu")
        }
    }

    suspend fun addRepo(input: String): RepoEntry {
        val repo = resolve(input)
        if (repo.sources.isEmpty()) throw IllegalStateException("Repoda kullanılabilir kaynak bulunamadı")
        store.repos = store.repos.filterNot { it.url == repo.url } + repo
        return repo
    }

    suspend fun refresh(url: String): RepoEntry {
        val repo = resolve(url)
        store.repos = store.repos.map { if (it.url == url) repo else it }
        lock.withLock { cache.keys.filter { k -> repo.sources.any { it.key == k } }.forEach { cache.remove(it) } }
        return repo
    }

    fun remove(url: String) {
        store.repos = store.repos.filterNot { it.url == url }
    }

    suspend fun open(entry: SourceEntry): ContentSource = lock.withLock {
        cache[entry.key] ?: when (entry.type) {
            RepoType.CS3 -> Cs3Source(entry, Cs3Loader.load(context, entry))
            RepoType.NUVIO -> if (entry.internalName.isNotBlank()) NuvioCatalogSource(entry, store, context) else NuvioSource(entry, store, context)
            RepoType.STREMIO -> StremioSource(entry)
        }.also { cache[entry.key] = it }
    }

    fun nuvioSource(entry: SourceEntry) = NuvioSource(entry, store, context)
}
