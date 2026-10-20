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
class Tmdb(private val key: String, private val language: String) {
    private val base = "https://api.themoviedb.org/3"
    private val bearer = key.length > 40

    private suspend fun get(path: String, params: String = ""): JsonNode {
        val sep = if (path.contains('?')) "&" else "?"
        val auth = if (bearer) "" else "&api_key=$key"
        val url = "$base$path${sep}language=$language$auth$params"
        val headers = if (bearer) mapOf("Authorization" to "Bearer $key") else emptyMap()
        return com.repoplayer.app.data.json.readTree(Net.text(url, headers))
    }

    fun img(path: String?, size: String = "w342"): String? =
        path?.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/$size$it" }

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
            backdrop = img(d.str("backdrop_path"), "w780"),
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
