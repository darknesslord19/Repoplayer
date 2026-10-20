package com.repoplayer.app.source.stremio

import com.fasterxml.jackson.databind.JsonNode
import com.repoplayer.app.data.ContentItem
import com.repoplayer.app.data.Details
import com.repoplayer.app.data.Episode
import com.repoplayer.app.data.Kind
import com.repoplayer.app.data.Net
import com.repoplayer.app.data.RepoEntry
import com.repoplayer.app.data.RepoType
import com.repoplayer.app.data.Row
import com.repoplayer.app.data.SourceEntry
import com.repoplayer.app.data.StreamLink
import com.repoplayer.app.data.Subtitle
import com.repoplayer.app.data.guessStreamType
import com.repoplayer.app.data.int
import com.repoplayer.app.data.str
import com.repoplayer.app.source.ContentSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/** Stremio / Nuvio "addon" manifest.json desteği (catalog, meta, stream, subtitles) */
object StremioRepo {
    fun isAddon(root: JsonNode) = root.isObject && (root.has("resources") || root.has("catalogs")) && !root.has("scrapers")

    fun read(url: String, root: JsonNode): RepoEntry {
        val name = root.str("name") ?: "Stremio Eklentisi"
        return RepoEntry(
            url = url,
            type = RepoType.STREMIO,
            name = name,
            icon = root.str("logo"),
            sources = listOf(
                SourceEntry(
                    key = "stremio:$url",
                    name = name,
                    icon = root.str("logo"),
                    group = "Eklentiler",
                    type = RepoType.STREMIO,
                    repoUrl = url,
                    url = url,
                )
            ),
        )
    }
}

class StremioSource(override val entry: SourceEntry) : ContentSource {
    private val base = entry.url.removeSuffix("/manifest.json").removeSuffix("/")
    private var manifest: JsonNode? = null

    private suspend fun manifest(): JsonNode = manifest ?: Net.jsonTree(entry.url).also { manifest = it }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** Kaynak bu türü/kimliği destekliyor mu */
    private suspend fun supports(resource: String, type: String, id: String): Boolean {
        val m = manifest()
        val res = m.get("resources") ?: return false
        val globalTypes = m.get("types")?.map { it.asText() } ?: emptyList()
        val globalPrefixes = m.get("idPrefixes")?.map { it.asText() }
        return res.any { r ->
            if (r.isTextual) {
                r.asText() == resource && (globalTypes.isEmpty() || type in globalTypes) &&
                    (globalPrefixes == null || globalPrefixes.any { id.startsWith(it) })
            } else {
                r.str("name") == resource &&
                    (r.get("types")?.map { it.asText() }?.contains(type) ?: (globalTypes.isEmpty() || type in globalTypes)) &&
                    ((r.get("idPrefixes") ?: m.get("idPrefixes"))?.map { it.asText() }?.any { id.startsWith(it) } ?: true)
            }
        }
    }

    private fun JsonNode.toItem(): ContentItem? {
        val id = str("id") ?: return null
        val type = str("type") ?: "movie"
        return ContentItem(
            sourceKey = entry.key,
            id = id,
            title = str("name") ?: return null,
            poster = str("poster"),
            kind = when (type) { "movie" -> Kind.MOVIE; "series", "anime" -> Kind.SERIES; "tv", "channel" -> Kind.LIVE; else -> Kind.OTHER },
            year = str("releaseInfo")?.take(4)?.toIntOrNull(),
            extra = type,
        )
    }

    private suspend fun catalog(type: String, id: String, extra: String?): List<ContentItem> {
        val url = "$base/catalog/$type/$id" + (if (extra != null) "/$extra" else "") + ".json"
        return Net.jsonTree(url).get("metas")?.mapNotNull { it.toItem() } ?: emptyList()
    }

    private fun JsonNode.requiresExtra(): Boolean {
        if ((get("extraRequired")?.size() ?: 0) > 0) return true
        return get("extra")?.any { it.get("isRequired")?.asBoolean() == true } == true
    }

    override fun home(): Flow<Row> = channelFlow {
        manifest().get("catalogs")?.forEachIndexed { i, c ->
            if (c.requiresExtra()) return@forEachIndexed
            // Yalnızca arama için tanımlanmış kataloglar (ör. "sw-search-movie") ana sayfada boş satır üretir
            val isSearchCatalog = c.get("extra")?.any { it.str("name") == "search" } == true && (c.str("id") ?: "").contains("search", true)
            if (isSearchCatalog) return@forEachIndexed
            val type = c.str("type") ?: return@forEachIndexed
            val id = c.str("id") ?: return@forEachIndexed
            val title = c.str("name")?.let { "$it" + if (type == "series") " · Dizi" else if (type == "movie") " · Film" else "" } ?: id
            launch(Dispatchers.IO) {
                runCatching { catalog(type, id, null) }.onSuccess {
                    if (it.isNotEmpty()) send(Row(title, it, "$type|$id|${it.size}", order = i))
                }
            }
        }
    }

    override suspend fun rowPage(row: Row, page: Int): List<ContentItem> {
        val p = row.moreKey?.split("|") ?: return emptyList()
        val pageSize = p.getOrNull(2)?.toIntOrNull()?.coerceAtLeast(1) ?: 100
        return catalog(p[0], p[1], if (page <= 1) null else "skip=${(page - 1) * pageSize}")
    }

    override suspend fun search(query: String): List<ContentItem> = coroutineScope {
        val cats = manifest().get("catalogs")?.filter { c -> c.get("extra")?.any { it.str("name") == "search" } == true || c.get("extraSupported")?.any { it.asText() == "search" } == true } ?: emptyList()
        cats.map { c ->
            async(Dispatchers.IO) {
                runCatching { catalog(c.str("type") ?: "movie", c.str("id") ?: "", "search=${enc(query)}") }.getOrDefault(emptyList())
            }
        }.awaitAll().flatten().distinctBy { it.id }
    }

    override suspend fun details(item: ContentItem): Details = withContext(Dispatchers.IO) {
        val type = item.extra ?: "movie"
        val metaUrl = if (supports("meta", type, item.id)) "$base/meta/$type/${enc(item.id)}.json"
        else if (item.id.startsWith("tt")) "https://v3-cinemeta.strem.io/meta/$type/${item.id}.json"
        else null
        val m = metaUrl?.let { runCatching { Net.jsonTree(it).get("meta") }.getOrNull() }
        val videos = m?.get("videos")?.mapNotNull { v ->
            val vid = v.str("id") ?: return@mapNotNull null
            val s = v.int("season")
            val e = v.int("episode") ?: v.int("number")
            Episode(
                id = vid,
                name = (if (s != null && e != null) "S${s}B$e · " else "") + (v.str("name") ?: v.str("title") ?: "Bölüm ${e ?: ""}"),
                season = s,
                episode = e,
                poster = v.str("thumbnail"),
                description = v.str("overview") ?: v.str("description"),
            )
        }?.sortedWith(compareBy({ if ((it.season ?: 1) == 0) 9999 else it.season ?: 1 }, { it.episode ?: 0 })) ?: emptyList()
        val isSeries = type == "series" || type == "anime"
        Details(
            item = item,
            title = m?.str("name") ?: item.title,
            poster = m?.str("poster") ?: item.poster,
            backdrop = m?.str("background"),
            plot = m?.str("description"),
            year = m?.str("releaseInfo")?.take(4)?.toIntOrNull() ?: item.year,
            tags = m?.get("genres")?.map { it.asText() } ?: emptyList(),
            episodes = if (isSeries || videos.size > 1) videos else emptyList(),
            playId = if (isSeries && videos.isNotEmpty()) null else (videos.singleOrNull()?.id ?: item.id),
            imdbId = (m?.str("imdb_id") ?: item.id).takeIf { it.startsWith("tt") }?.substringBefore(':'),
        )
    }

    override suspend fun links(
        details: Details,
        episode: Episode?,
        onLink: (StreamLink) -> Unit,
        onSubtitle: (Subtitle) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val type = details.item.extra ?: "movie"
        val id = episode?.id ?: details.playId ?: return@withContext
        coroutineScope {
            launch {
                if (!supports("subtitles", type, id)) return@launch
                runCatching {
                    Net.jsonTree("$base/subtitles/$type/${enc(id)}.json").get("subtitles")?.forEach { s ->
                        val u = s.str("url") ?: return@forEach
                        onSubtitle(Subtitle(s.str("lang") ?: "?", u))
                    }
                }
            }
            runCatching {
                Net.jsonTree("$base/stream/$type/${enc(id)}.json").get("streams")?.forEach { s ->
                    val url = s.str("url") ?: return@forEach // torrent (infoHash) ve harici linkler atlanır
                    val headers = HashMap<String, String>()
                    s.get("behaviorHints")?.get("proxyHeaders")?.get("request")?.fields()?.forEach { (k, v) -> headers[k] = v.asText() }
                    val label = listOfNotNull(s.str("name"), s.str("title") ?: s.str("description"))
                        .joinToString(" · ").replace("\n", " ")
                    onLink(StreamLink(label.ifBlank { details.title }, url, headers, guessStreamType(url), null))
                    s.get("subtitles")?.forEach { sub ->
                        val su = sub.str("url") ?: return@forEach
                        onSubtitle(Subtitle(sub.str("lang") ?: "?", su))
                    }
                }
            }.getOrElse { throw it }
        }
        Unit
    }
}
