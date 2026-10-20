package com.repoplayer.app.source.nuvio

import android.content.Context
import com.fasterxml.jackson.databind.JsonNode
import com.repoplayer.app.data.ContentItem
import com.repoplayer.app.data.Details
import com.repoplayer.app.data.Episode
import com.repoplayer.app.data.Kind
import com.repoplayer.app.data.Logger
import com.repoplayer.app.data.Net
import com.repoplayer.app.data.RepoEntry
import com.repoplayer.app.data.RepoType
import com.repoplayer.app.data.Row
import com.repoplayer.app.data.SourceEntry
import com.repoplayer.app.data.Store
import com.repoplayer.app.data.StreamLink
import com.repoplayer.app.data.Subtitle
import com.repoplayer.app.data.guessStreamType
import com.repoplayer.app.data.int
import com.repoplayer.app.data.json
import com.repoplayer.app.data.resolveUrl
import com.repoplayer.app.data.str
import com.repoplayer.app.data.toJson
import com.repoplayer.app.source.Cinemeta
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
import java.util.concurrent.ConcurrentHashMap

const val TMDB_REQUIRED = "Bu kaynak içerikleri TMDB kimliğiyle bulur. Ayarlar > Sağlayıcılar > TMDB API anahtarı alanını doldurun (themoviedb.org'dan ücretsiz alınır)."

data class NuvioCatalog(val id: String, val name: String, val type: String, val pagination: Boolean)

data class NuvioScraper(
    val id: String,
    val name: String,
    val file: String,
    val types: List<String>,
    val logo: String?,
    val version: String,
    val enabled: Boolean,
    val catalogs: List<NuvioCatalog> = emptyList(),
    val supportsSearch: Boolean = false,
    val language: String? = null,
) {
    /** Kendi kataloğu/araması olan "katalog eklentisi" mi */
    val isCatalogPlugin get() = catalogs.isNotEmpty() || supportsSearch
}

object NuvioRepo {
    private fun cleanName(n: String) = n.trimStart('!', ' ').ifBlank { n }

    fun read(url: String, root: JsonNode): RepoEntry {
        val name = root.str("name") ?: "Nuvio Repo"
        val scrapers = scrapers(url, root)
        val sources = mutableListOf<SourceEntry>()
        // Katalog eklentileri: her biri seçicide ayrı kaynak
        scrapers.filter { it.isCatalogPlugin && it.enabled }.forEach { s ->
            sources += SourceEntry(
                key = "nuvio:$url#${s.id}",
                name = cleanName(s.name),
                icon = s.logo?.replace("%size%", "128"),
                group = name,
                type = RepoType.NUVIO,
                repoUrl = url,
                url = url,
                language = s.language,
                internalName = s.id,
            )
        }
        // Yalnızca akış veren (TMDB tabanlı) sağlayıcılar için ortak kaynak
        if (scrapers.any { !it.isCatalogPlugin } || sources.isEmpty()) {
            sources += SourceEntry(
                key = "nuvio:$url",
                name = if (sources.isEmpty()) name else "$name (TMDB · tüm sağlayıcılar)",
                icon = root.str("logo"),
                group = name,
                type = RepoType.NUVIO,
                repoUrl = url,
                url = url,
            )
        }
        return RepoEntry(url = url, type = RepoType.NUVIO, name = name, icon = root.str("logo"), sources = sources)
    }

    fun scrapers(url: String, root: JsonNode): List<NuvioScraper> {
        val arr = root.get("scrapers") ?: root.get("providers") ?: (if (root.isArray) root else null) ?: return emptyList()
        return arr.mapNotNull { s ->
            val file = s.str("filename") ?: s.str("file") ?: s.str("url") ?: return@mapNotNull null
            val lang = s.get("contentLanguage")?.let { if (it.isArray) it.firstOrNull()?.asText() else it.asText() }
            NuvioScraper(
                id = s.str("id") ?: file,
                name = s.str("name") ?: s.str("id") ?: file,
                file = resolveUrl(url, file),
                types = s.get("supportedTypes")?.map { it.asText().lowercase() } ?: listOf("movie", "tv"),
                logo = s.str("logo"),
                version = s.str("version") ?: "0",
                enabled = s.get("enabled")?.asBoolean() ?: true,
                catalogs = s.get("catalogs")?.mapNotNull { c ->
                    NuvioCatalog(
                        id = c.str("id") ?: return@mapNotNull null,
                        name = c.str("name") ?: c.str("id") ?: "",
                        type = c.str("type") ?: "movie",
                        pagination = c.get("supportsPagination")?.asBoolean() ?: false,
                    )
                } ?: emptyList(),
                supportsSearch = s.get("supportsSearch")?.asBoolean() ?: false,
                language = lang,
            )
        }
    }
}

/** Manifest ve sağlayıcı kodlarını önbellekleyen ortak yardımcı */
class NuvioCode(private val context: Context, private val manifestUrl: String) {
    private val lock = Mutex()
    private var cached: List<NuvioScraper>? = null

    suspend fun scrapers(): List<NuvioScraper> = lock.withLock {
        cached ?: NuvioRepo.scrapers(manifestUrl, Net.jsonTree(manifestUrl)).also { cached = it }
    }

    private fun md5(s: String) =
        MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(12)

    suspend fun code(s: NuvioScraper): String = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "nuvio/${md5(manifestUrl)}")
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

    fun key(s: NuvioScraper) = "$manifestUrl#${s.id}@${s.version}"
}

/** Sağlayıcının döndürdüğü akış nesnesini uygulama modeline çevirir */
internal fun emitStream(label: String, o: JsonNode, onLink: (StreamLink) -> Unit, onSubtitle: (Subtitle) -> Unit) {
    val urlNode = o.get("url")
    val url = (if (urlNode?.isObject == true) urlNode.str("url") else o.str("url"))?.takeIf { !it.contains("[object") } ?: return
    if (url.startsWith("magnet:") || o.str("infoHash") != null) return
    val headers = HashMap<String, String>()
    o.get("headers")?.fields()?.forEach { (k, v) -> if (v.isTextual) headers[k] = v.asText() }
    val text = listOfNotNull(label.takeIf { it.isNotBlank() }, o.str("title") ?: o.str("name"), o.str("size"))
        .distinct().joinToString(" · ").replace("\n", " ")
    onLink(StreamLink(name = text, url = url, headers = headers, type = guessStreamType(url), quality = o.str("quality")))
    o.get("subtitles")?.forEach { sub ->
        val su = sub.str("url") ?: return@forEach
        onSubtitle(Subtitle(sub.str("language") ?: sub.str("lang") ?: sub.str("label") ?: "?", su))
    }
}

/** Eski tip Nuvio reposu: tüm sağlayıcılar TMDB kimliğiyle akış arar; içerik TMDB'den gelir */
class NuvioSource(
    override val entry: SourceEntry,
    private val store: Store,
    context: Context,
) : ContentSource {
    private val code = NuvioCode(context, entry.url)

    suspend fun scrapers(): List<NuvioScraper> = code.scrapers()

    private fun tmdb(): Tmdb {
        val key = store.tmdbKey
        if (key.isBlank()) throw IllegalStateException(TMDB_REQUIRED)
        return Tmdb(key, store.language, store.imageQuality)
    }

    override fun home(): Flow<Row> = channelFlow {
        val t = tmdb()
        t.homeLists.forEachIndexed { i, (title, path, type) ->
            launch(Dispatchers.IO) {
                runCatching { t.list(entry.key, path, type, 1) }
                    .onSuccess { if (it.isNotEmpty()) send(Row(title, it, "$path|$type", order = i)) }
                    .onFailure { Logger.e("Nuvio", "TMDB listesi alınamadı", it) }
            }
        }
    }

    override suspend fun rowPage(row: Row, page: Int): List<ContentItem> {
        val (path, type) = row.moreKey?.split("|")?.let { it[0] to it[1] } ?: return emptyList()
        return tmdb().list(entry.key, path, type, page)
    }

    override suspend fun search(query: String): List<ContentItem> = tmdb().search(entry.key, query)

    override suspend fun details(item: ContentItem): Details = tmdb().details(item)

    override suspend fun links(
        details: Details,
        episode: Episode?,
        onLink: (StreamLink) -> Unit,
        onSubtitle: (Subtitle) -> Unit,
    ) {
        val (type, tmdbId) = details.item.id.split(":").let { it[0] to it[1] }
        val disabled = store.disabledScrapers
        val list = scrapers().filter { it.enabled && it.id !in disabled && (type in it.types || (type == "tv" && "series" in it.types)) }
        val gate = Semaphore(store.parallelRequests.coerceIn(1, 12))
        coroutineScope {
            list.forEach { s ->
                launch {
                    gate.withPermit {
                        runCatching {
                            val result = JsEngine.getStreams(
                                key = code.key(s),
                                code = code.code(s),
                                args = listOf(tmdbId, type, episode?.season, episode?.episode, null),
                                timeoutMs = store.nuvioTimeoutSec.coerceIn(10, 120) * 1000L,
                                scraperId = s.id,
                                tmdbKey = store.tmdbKey,
                            )
                            Logger.i("Nuvio", "${s.name}: ${result.size()} akış")
                            result.forEach { o -> emitStream(s.name.trimStart('!'), o, onLink, onSubtitle) }
                        }.onFailure { Logger.w("Nuvio", "${s.name} başarısız: ${it.message}") }
                    }
                }
            }
        }
    }
}

/**
 * Yeni tip Nuvio "katalog eklentisi": kendi ana sayfa katalogları (getCatalog),
 * araması (search) ve içerik adresiyle akış bulma (getStreams(..., url)).
 */
class NuvioCatalogSource(
    override val entry: SourceEntry,
    private val store: Store,
    context: Context,
) : ContentSource {
    private val code = NuvioCode(context, entry.url)
    private val scraperId = entry.internalName
    /** "Tümünü göster" sayfa belirteçleri: moreKey -> [sayfa2Token, sayfa3Token, ...] */
    private val tokens = ConcurrentHashMap<String, MutableList<String?>>()

    private suspend fun scraper(): NuvioScraper =
        code.scrapers().firstOrNull { it.id == scraperId } ?: throw IllegalStateException("Sağlayıcı manifestte yok: $scraperId")

    private val lang get() = store.language

    private suspend fun invoke(function: String, arg: Any?): JsonNode {
        val s = scraper()
        return JsEngine.call(
            key = code.key(s), code = code.code(s), function = function, args = listOf(arg),
            timeoutMs = store.nuvioTimeoutSec.coerceIn(10, 120) * 1000L,
            scraperId = s.id, tmdbKey = store.tmdbKey,
        )
    }

    /** Katalog öğesi -> ContentItem (ek bilgiler extra alanında JSON olarak saklanır) */
    private fun toItem(n: JsonNode, fallbackType: String): ContentItem? {
        val id = n.str("id") ?: return null
        val name = n.str("name") ?: n.str("title") ?: return null
        val type = (n.str("type") ?: fallbackType).let { if (it == "tv") "series" else it }
        val ext = n.get("externalIds")
        val extra = mapOf(
            "type" to type,
            "url" to n.str("url"),
            "tmdb" to (ext.str("tmdbId") ?: ext?.get("tmdbId")?.takeIf { it.isNumber }?.asText()),
            "imdb" to ext.str("imdbId"),
            "bg" to n.str("background"),
            "desc" to n.str("description"),
            "genres" to n.get("genres")?.mapNotNull { it.asText() },
        ).toJson()
        return ContentItem(
            sourceKey = entry.key,
            id = id,
            title = name,
            poster = n.str("poster"),
            kind = if (type == "series") Kind.SERIES else Kind.MOVIE,
            year = n.int("year") ?: n.str("releaseInfo")?.take(4)?.toIntOrNull(),
            extra = extra,
        )
    }

    private fun items(root: JsonNode?, type: String): List<ContentItem> {
        val arr = when {
            root == null || root.isNull -> null
            root.isArray -> root
            else -> root.get("items") ?: root.get("metas")
        } ?: return emptyList()
        return arr.mapNotNull { toItem(it, type) }
    }

    private fun apiType(type: String) = if (type == "tv") "series" else type

    override fun home(): Flow<Row> = channelFlow {
        val s = scraper()
        if (s.catalogs.isEmpty()) throw IllegalStateException("${entry.name} ana sayfa kataloğu sunmuyor. Ara sekmesini kullanın.")
        val gate = Semaphore(store.parallelRequests.coerceIn(1, 12))
        var sent = 0
        var lastError: Throwable? = null
        val jobs = s.catalogs.mapIndexed { i, c ->
            launch(Dispatchers.IO) {
                gate.withPermit {
                    try {
                        val root = invoke("getCatalog", mapOf("catalogId" to c.id, "type" to apiType(c.type), "pageToken" to null, "language" to lang))
                        val list = items(root, apiType(c.type))
                        if (list.isNotEmpty()) {
                            val key = "${c.id}|${apiType(c.type)}"
                            val next = root.str("nextPageToken")
                            if (c.pagination && next != null) tokens[key] = mutableListOf(next)
                            send(Row(c.name, list, if (c.pagination && next != null) key else null, order = i))
                            sent++
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        lastError = t
                        Logger.e("Nuvio", "${entry.name}: \"${c.name}\" kataloğu alınamadı", t)
                    }
                }
            }
        }
        jobs.forEach { it.join() }
        val err = lastError
        if (sent == 0 && err != null) throw IllegalStateException("${entry.name} katalogları alınamadı: ${err.message}", err)
    }

    override suspend fun rowPage(row: Row, page: Int): List<ContentItem> {
        val key = row.moreKey ?: return emptyList()
        val list = tokens[key] ?: return emptyList()
        val token = list.getOrNull(page - 2) ?: return emptyList()
        val (catalogId, type) = key.split("|").let { it[0] to it[1] }
        val root = invoke("getCatalog", mapOf("catalogId" to catalogId, "type" to type, "pageToken" to token, "language" to lang))
        val next = root.str("nextPageToken")
        synchronized(list) { if (list.size < page) list.add(next) else list[page - 1] = next }
        return items(root, type)
    }

    override suspend fun search(query: String): List<ContentItem> {
        val root = invoke("search", mapOf("query" to query, "language" to lang))
        return items(root, "movie")
    }

    private data class Extra(val type: String, val url: String?, val tmdb: String?, val imdb: String?, val bg: String?, val desc: String?, val genres: List<String>)

    private fun extra(item: ContentItem): Extra {
        val n = runCatching { json.readTree(item.extra ?: "{}") }.getOrNull()
        return Extra(
            type = n.str("type") ?: if (item.kind == Kind.SERIES) "series" else "movie",
            url = n.str("url"), tmdb = n.str("tmdb"), imdb = n.str("imdb"), bg = n.str("bg"), desc = n.str("desc"),
            genres = n?.get("genres")?.mapNotNull { it.asText() } ?: emptyList(),
        )
    }

    override suspend fun details(item: ContentItem): Details {
        val x = extra(item)
        val isSeries = x.type == "series"
        // 1) TMDB kimliği + anahtar
        if (x.tmdb != null && store.tmdbKey.isNotBlank()) {
            runCatching {
                val d = Tmdb(store.tmdbKey, store.language, store.imageQuality)
                    .details(item.copy(id = "${if (isSeries) "tv" else "movie"}:${x.tmdb}"))
                return d.copy(item = item, playId = if (isSeries) null else item.id, imdbId = d.imdbId ?: x.imdb)
            }.onFailure { Logger.w("Nuvio", "TMDB detay alınamadı: ${it.message}") }
        }
        // 2) IMDb kimliği ya da ada göre Cinemeta (anahtarsız)
        val imdb = x.imdb ?: runCatching { Cinemeta.findImdb(item.title, isSeries, item.year) }.getOrNull()
        if (imdb != null) {
            runCatching {
                val d = Cinemeta.details(item, imdb, isSeries)
                return d.copy(
                    plot = d.plot ?: x.desc,
                    backdrop = d.backdrop ?: x.bg,
                    poster = item.poster ?: d.poster,
                    playId = if (isSeries && d.episodes.isNotEmpty()) null else item.id,
                )
            }.onFailure { Logger.w("Nuvio", "Cinemeta detay alınamadı: ${it.message}") }
        }
        // 3) Yalnızca katalog bilgisi
        return Details(
            item = item, title = item.title, poster = item.poster, backdrop = x.bg, plot = x.desc,
            year = item.year, tags = x.genres, playId = item.id, imdbId = x.imdb,
        )
    }

    override suspend fun links(
        details: Details,
        episode: Episode?,
        onLink: (StreamLink) -> Unit,
        onSubtitle: (Subtitle) -> Unit,
    ) {
        val x = extra(details.item)
        val s = scraper()
        val streamId = x.tmdb ?: x.imdb ?: details.item.id
        val mediaType = if (x.type == "series") "tv" else "movie"
        val result = JsEngine.getStreams(
            key = code.key(s), code = code.code(s),
            args = listOf(streamId, mediaType, episode?.season, episode?.episode, x.url),
            timeoutMs = store.nuvioTimeoutSec.coerceIn(10, 120) * 1000L,
            scraperId = s.id, tmdbKey = store.tmdbKey,
        )
        Logger.i("Nuvio", "${entry.name}: ${result.size()} akış (${details.title}${episode?.let { " S${it.season}B${it.episode}" } ?: ""})")
        result.forEach { o -> emitStream("", o, onLink, onSubtitle) }
    }
}
