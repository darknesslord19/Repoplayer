package com.repoplayer.app.source.nuvio

import android.content.Context
import android.util.Log
import com.fasterxml.jackson.databind.JsonNode
import com.repoplayer.app.data.ContentItem
import com.repoplayer.app.data.Details
import com.repoplayer.app.data.Episode
import com.repoplayer.app.data.Net
import com.repoplayer.app.data.RepoEntry
import com.repoplayer.app.data.RepoType
import com.repoplayer.app.data.Row
import com.repoplayer.app.data.SourceEntry
import com.repoplayer.app.data.Store
import com.repoplayer.app.data.StreamLink
import com.repoplayer.app.data.Subtitle
import com.repoplayer.app.data.guessStreamType
import com.repoplayer.app.data.resolveUrl
import com.repoplayer.app.data.str
import com.repoplayer.app.source.ContentSource
import com.repoplayer.app.source.Tmdb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

const val TMDB_REQUIRED = "Nuvio kaynakları TMDB kimliğiyle çalışır. Ayarlar > TMDB API anahtarı alanını doldurun (themoviedb.org'dan ücretsiz alınır)."

data class NuvioScraper(
    val id: String,
    val name: String,
    val file: String,
    val types: List<String>,
    val logo: String?,
    val version: String,
    val enabled: Boolean,
)

object NuvioRepo {
    fun read(url: String, root: JsonNode): RepoEntry {
        val name = root.str("name") ?: "Nuvio Repo"
        return RepoEntry(
            url = url,
            type = RepoType.NUVIO,
            name = name,
            sources = listOf(
                SourceEntry(
                    key = "nuvio:$url",
                    name = name,
                    group = "Nuvio",
                    type = RepoType.NUVIO,
                    repoUrl = url,
                    url = url,
                )
            ),
        )
    }

    fun scrapers(url: String, root: JsonNode): List<NuvioScraper> {
        val arr = root.get("scrapers") ?: root.get("providers") ?: (if (root.isArray) root else null) ?: return emptyList()
        return arr.mapNotNull { s ->
            val file = s.str("filename") ?: s.str("file") ?: s.str("url") ?: return@mapNotNull null
            NuvioScraper(
                id = s.str("id") ?: file,
                name = s.str("name") ?: s.str("id") ?: file,
                file = resolveUrl(url, file),
                types = s.get("supportedTypes")?.map { it.asText().lowercase() } ?: listOf("movie", "tv"),
                logo = s.str("logo"),
                version = s.str("version") ?: "0",
                enabled = s.get("enabled")?.asBoolean() ?: true,
            )
        }
    }
}

class NuvioSource(
    override val entry: SourceEntry,
    private val store: Store,
    private val context: Context,
) : ContentSource {
    private val lock = Mutex()
    private var cached: List<NuvioScraper>? = null

    suspend fun scrapers(): List<NuvioScraper> = lock.withLock {
        cached ?: NuvioRepo.scrapers(entry.url, Net.jsonTree(entry.url)).also { cached = it }
    }

    private fun tmdb(): Tmdb {
        val key = store.tmdbKey
        if (key.isBlank()) throw IllegalStateException(TMDB_REQUIRED)
        return Tmdb(key, store.language)
    }

    override fun home(): Flow<Row> = channelFlow {
        val t = tmdb()
        t.homeLists.forEachIndexed { i, (title, path, type) ->
            launch(Dispatchers.IO) {
                runCatching { t.list(entry.key, path, type, 1) }
                    .onSuccess { if (it.isNotEmpty()) send(Row(title, it, "$path|$type", order = i)) }
                    .onFailure { Log.e("Nuvio", "TMDB listesi alınamadı", it) }
            }
        }
    }

    override suspend fun rowPage(row: Row, page: Int): List<ContentItem> {
        val (path, type) = row.moreKey?.split("|")?.let { it[0] to it[1] } ?: return emptyList()
        return tmdb().list(entry.key, path, type, page)
    }

    override suspend fun search(query: String): List<ContentItem> = tmdb().search(entry.key, query)

    override suspend fun details(item: ContentItem): Details = tmdb().details(item)

    private fun md5(s: String) =
        MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(12)

    private suspend fun code(s: NuvioScraper): String = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "nuvio/${md5(entry.url)}")
        val f = File(dir, md5(s.id) + ".js")
        val v = File(dir, md5(s.id) + ".ver")
        val want = "${s.version}|${s.file}"
        if (f.exists() && runCatching { v.readText() }.getOrNull() == want) return@withContext f.readText()
        val text = Net.text(s.file)
        dir.mkdirs()
        f.writeText(text)
        v.writeText(want)
        text
    }

    override suspend fun links(
        details: Details,
        episode: Episode?,
        onLink: (StreamLink) -> Unit,
        onSubtitle: (Subtitle) -> Unit,
    ) {
        val (type, tmdbId) = details.item.id.split(":").let { it[0] to it[1] }
        val disabled = store.disabledScrapers
        val list = scrapers().filter { it.enabled && it.id !in disabled && (type in it.types || (type == "tv" && "series" in it.types)) }
        val gate = Semaphore(5)
        coroutineScope {
            list.forEach { s ->
                launch {
                    gate.withPermit {
                        runCatching {
                            val result = JsEngine.getStreams(
                                key = "${entry.url}#${s.id}@${s.version}",
                                code = code(s),
                                args = listOf(tmdbId, type, episode?.season, episode?.episode),
                                timeoutMs = 45_000,
                            )
                            result.forEach { o -> emit(s, o, onLink, onSubtitle) }
                        }.onFailure { Log.w("Nuvio", "${s.name} başarısız: ${it.message}") }
                    }
                }
            }
        }
    }

    private fun emit(s: NuvioScraper, o: JsonNode, onLink: (StreamLink) -> Unit, onSubtitle: (Subtitle) -> Unit) {
        val url = o.str("url") ?: return
        if (url.startsWith("magnet:")) return
        val headers = HashMap<String, String>()
        o.get("headers")?.fields()?.forEach { (k, v) -> headers[k] = v.asText() }
        val label = listOfNotNull(s.name, o.str("title") ?: o.str("name"), o.str("size")).distinct().joinToString(" · ")
        onLink(StreamLink(name = label, url = url, headers = headers, type = guessStreamType(url), quality = o.str("quality")))
        o.get("subtitles")?.forEach { sub ->
            val su = sub.str("url") ?: return@forEach
            onSubtitle(Subtitle(sub.str("lang") ?: sub.str("label") ?: "?", su))
        }
    }
}
