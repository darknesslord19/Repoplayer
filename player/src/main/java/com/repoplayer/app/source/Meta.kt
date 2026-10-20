package com.repoplayer.app.source

import com.fasterxml.jackson.databind.JsonNode
import com.repoplayer.app.data.ContentItem
import com.repoplayer.app.data.Details
import com.repoplayer.app.data.Episode
import com.repoplayer.app.data.Kind
import com.repoplayer.app.data.Net
import com.repoplayer.app.data.Subtitle
import com.repoplayer.app.data.int
import com.repoplayer.app.data.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/** TMDB istemcisi (Nuvio kaynakları TMDB kimliğiyle çalışır) */
class Tmdb(private val key: String, private val language: String, private val quality: Int = 1) {
    private val base = "https://api.themoviedb.org/3"
    private val bearer = key.length > 40

    private suspend fun get(path: String, params: String = ""): JsonNode {
        val sep = if (path.contains('?')) "&" else "?"
        val auth = if (bearer) "" else "&api_key=$key"
        val url = "$base$path${sep}language=$language$auth$params"
        val headers = if (bearer) mapOf("Authorization" to "Bearer $key") else emptyMap()
        return com.repoplayer.app.data.json.readTree(Net.text(url, headers))
    }

    private val posterSize = when (quality) { 0 -> "w185"; 2 -> "w500"; else -> "w342" }

    fun img(path: String?, size: String? = null): String? =
        path?.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/${size ?: posterSize}$it" }

    fun toItem(sourceKey: String, n: JsonNode, forcedType: String? = null): ContentItem? {
        val type = forcedType ?: n.str("media_type") ?: return null
        if (type != "movie" && type != "tv") return null
        val id = n.int("id") ?: return null
        val date = n.str("release_date") ?: n.str("first_air_date")
        return ContentItem(
            sourceKey = sourceKey,
            id = "$type:$id",
            title = n.str("title") ?: n.str("name") ?: return null,
            poster = img(n.str("poster_path")),
            kind = if (type == "movie") Kind.MOVIE else Kind.SERIES,
            year = date?.take(4)?.toIntOrNull(),
        )
    }

    /** Ana sayfa listeleri: başlık -> (yol, tür) */
    val homeLists = listOf(
        Triple("Trend Filmler", "/trending/movie/week", "movie"),
        Triple("Trend Diziler", "/trending/tv/week", "tv"),
        Triple("Vizyondakiler", "/movie/now_playing", "movie"),
        Triple("Popüler Filmler", "/movie/popular", "movie"),
        Triple("Popüler Diziler", "/tv/popular", "tv"),
        Triple("En Beğenilen Filmler", "/movie/top_rated", "movie"),
        Triple("En Beğenilen Diziler", "/tv/top_rated", "tv"),
    )

    suspend fun list(sourceKey: String, path: String, type: String, page: Int): List<ContentItem> =
        get(path, "&page=$page").get("results")?.mapNotNull { toItem(sourceKey, it, type) } ?: emptyList()

    suspend fun search(sourceKey: String, query: String): List<ContentItem> =
        get("/search/multi", "&query=" + URLEncoder.encode(query, "UTF-8")).get("results")
            ?.mapNotNull { toItem(sourceKey, it) } ?: emptyList()

    suspend fun details(item: ContentItem): Details = coroutineScope {
        val (type, id) = item.id.split(":").let { it[0] to it[1] }
        val d = get("/$type/$id", "&append_to_response=external_ids,recommendations")
        val episodes = if (type == "tv") {
            val seasons = d.get("seasons")?.mapNotNull { it.int("season_number") }?.filter { it > 0 } ?: emptyList()
            seasons.map { s ->
                async {
                    runCatching {
                        get("/tv/$id/season/$s").get("episodes")?.map { e ->
                            val ep = e.int("episode_number") ?: 0
                            Episode(
                                id = "$s:$ep",
                                name = "S${s}B$ep · " + (e.str("name") ?: "Bölüm $ep"),
                                season = s,
                                episode = ep,
                                poster = img(e.str("still_path"), "w300"),
                                description = e.str("overview"),
                            )
                        } ?: emptyList()
                    }.getOrDefault(emptyList())
                }
            }.awaitAll().flatten()
        } else emptyList()
        val date = d.str("release_date") ?: d.str("first_air_date")
        Details(
            item = item,
            title = d.str("title") ?: d.str("name") ?: item.title,
            poster = img(d.str("poster_path")) ?: item.poster,
            backdrop = img(d.str("backdrop_path"), if (quality == 0) "w780" else "w1280"),
            plot = d.str("overview"),
            year = date?.take(4)?.toIntOrNull(),
            tags = d.get("genres")?.mapNotNull { it.str("name") } ?: emptyList(),
            episodes = episodes,
            playId = if (type == "movie") id else null,
            imdbId = d.get("external_ids").str("imdb_id") ?: d.str("imdb_id"),
            recommendations = d.get("recommendations")?.get("results")
                ?.mapNotNull { toItem(item.sourceKey, it, type) } ?: emptyList(),
        )
    }
}

/** OpenSubtitles v3 (Stremio) — anahtarsız altyazı araması */
object OpenSubs {
    suspend fun fetch(imdbId: String, season: Int?, episode: Int?, langs: List<String>): List<Subtitle> =
        withContext(Dispatchers.IO) {
            runCatching {
                val isSeries = season != null && episode != null
                val id = if (isSeries) "$imdbId:$season:$episode" else imdbId
                val url = "https://opensubtitles-v3.strem.io/subtitles/${if (isSeries) "series" else "movie"}/$id.json"
                val root = Net.jsonTree(url)
                val all = root.get("subtitles")?.mapNotNull { s ->
                    val u = s.str("url") ?: return@mapNotNull null
                    Subtitle(lang = s.str("lang") ?: "?", url = u)
                } ?: emptyList()
                val wanted = langs.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
                val filtered = if (wanted.isEmpty()) all else all.filter { it.lang.lowercase() in wanted }
                filtered.groupBy { it.lang }.flatMap { (lang, list) ->
                    list.take(6).mapIndexed { i, s -> s.copy(lang = "${langName(lang)} ${i + 1} (OpenSubtitles)") }
                }
            }.getOrDefault(emptyList())
        }

    private fun langName(code: String) = when (code.lowercase()) {
        "tur" -> "Türkçe"; "eng" -> "İngilizce"; "ger", "deu" -> "Almanca"; "fre", "fra" -> "Fransızca"
        "spa" -> "İspanyolca"; "ara" -> "Arapça"; "rus" -> "Rusça"; "ita" -> "İtalyanca"
        "por", "pob" -> "Portekizce"; "jpn" -> "Japonca"; else -> code
    }
}

/** Cinemeta (Stremio) — anahtarsız film/dizi bilgisi ve bölüm listesi */
object Cinemeta {
    private const val BASE = "https://v3-cinemeta.strem.io"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    /** Ada (ve yıla) göre IMDb kimliği bulur */
    suspend fun findImdb(name: String, series: Boolean, year: Int?): String? {
        val type = if (series) "series" else "movie"
        val metas = Net.jsonTree("$BASE/catalog/$type/top/search=${enc(name)}.json").get("metas") ?: return null
        val list = metas.mapNotNull { m ->
            val id = m.str("imdb_id") ?: m.str("id") ?: return@mapNotNull null
            Triple(id, m.str("name") ?: "", m.str("releaseInfo")?.take(4)?.toIntOrNull())
        }.filter { it.first.startsWith("tt") }
        val n = norm(name)
        return (list.firstOrNull { norm(it.second) == n && (year == null || it.third == null || kotlin.math.abs(it.third!! - year) <= 1) }
            ?: list.firstOrNull { year != null && it.third == year }
            ?: list.firstOrNull { norm(it.second) == n }
            ?: list.firstOrNull())?.first
    }

    suspend fun details(item: ContentItem, imdb: String, series: Boolean): Details {
        val type = if (series) "series" else "movie"
        val m = Net.jsonTree("$BASE/meta/$type/$imdb.json").get("meta") ?: throw IllegalStateException("Cinemeta: kayıt yok")
        val episodes = m.get("videos")?.mapNotNull { v ->
            val s = v.int("season") ?: return@mapNotNull null
            val e = v.int("episode") ?: v.int("number") ?: return@mapNotNull null
            Episode(
                id = "$s:$e",
                name = "S${s}B$e · " + (v.str("name") ?: v.str("title") ?: "Bölüm $e"),
                season = s,
                episode = e,
                poster = v.str("thumbnail"),
                description = v.str("overview") ?: v.str("description"),
            )
        }?.sortedWith(compareBy({ if (it.season == 0) 9999 else it.season ?: 1 }, { it.episode ?: 0 })) ?: emptyList()
        return Details(
            item = item,
            title = item.title.ifBlank { m.str("name") ?: "" },
            poster = m.str("poster"),
            backdrop = m.str("background"),
            plot = m.str("description"),
            year = m.str("releaseInfo")?.take(4)?.toIntOrNull() ?: item.year,
            tags = m.get("genres")?.map { it.asText() } ?: emptyList(),
            episodes = if (series) episodes else emptyList(),
            playId = if (series) null else item.id,
            imdbId = imdb,
        )
    }
}
