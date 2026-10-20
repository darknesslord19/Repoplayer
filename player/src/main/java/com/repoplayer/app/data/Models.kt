package com.repoplayer.app.data

enum class Kind { MOVIE, SERIES, LIVE, OTHER }

enum class RepoType { CS3, NUVIO, STREMIO }

/** Kullanıcının eklediği repo.json / manifest.json */
data class RepoEntry(
    val url: String = "",
    val type: RepoType = RepoType.CS3,
    val name: String = "",
    val icon: String? = null,
    val sources: List<SourceEntry> = emptyList(),
)

/** Alt seçicide görünen tek kaynak (CS3 eklentisi, Nuvio reposu, Stremio eklentisi) */
data class SourceEntry(
    val key: String = "",
    val name: String = "",
    val icon: String? = null,
    val group: String = "",
    val type: RepoType = RepoType.CS3,
    val repoUrl: String = "",
    /** CS3: .cs3 dosya adresi, Nuvio/Stremio: manifest adresi */
    val url: String = "",
    val version: Int = 0,
    val language: String? = null,
    val internalName: String = "",
)

data class ContentItem(
    val sourceKey: String = "",
    val id: String = "",
    val title: String = "",
    val poster: String? = null,
    val kind: Kind = Kind.OTHER,
    val year: Int? = null,
    /** CS3'te hangi MainAPI'ye ait olduğu, Stremio'da tür ("movie"/"series") */
    val extra: String? = null,
)

data class Row(
    val title: String,
    val items: List<ContentItem>,
    /** "Tümünü göster" için sayfalama anahtarı (null ise sayfalama yok) */
    val moreKey: String? = null,
    val horizontal: Boolean = false,
    /** Ana sayfadaki sıra (satırlar paralel yüklendiği için) */
    val order: Int = 0,
)

data class Episode(
    val id: String,
    val name: String,
    val season: Int? = null,
    val episode: Int? = null,
    val poster: String? = null,
    val description: String? = null,
)

data class Details(
    val item: ContentItem,
    val title: String,
    val poster: String? = null,
    val backdrop: String? = null,
    val plot: String? = null,
    val year: Int? = null,
    val tags: List<String> = emptyList(),
    val episodes: List<Episode> = emptyList(),
    /** Film/canlı yayın için oynatma verisi */
    val playId: String? = null,
    val imdbId: String? = null,
    val recommendations: List<ContentItem> = emptyList(),
) {
    val isSeries get() = episodes.isNotEmpty()
}

enum class StreamType { HLS, DASH, PROGRESSIVE }

data class StreamLink(
    val name: String = "",
    val url: String = "",
    val headers: Map<String, String> = emptyMap(),
    val type: StreamType = StreamType.PROGRESSIVE,
    val quality: String? = null,
) {
    val qualityScore: Int
        get() = Regex("(\\d{3,4})p?").find(quality ?: name)?.groupValues?.get(1)?.toIntOrNull() ?: 0
}

data class Subtitle(
    val lang: String = "",
    val url: String = "",
    val headers: Map<String, String> = emptyMap(),
)

/** İzleme geçmişi / kaldığı yerden devam kaydı */
data class WatchRecord(
    val key: String = "",
    val item: ContentItem = ContentItem(),
    val title: String = "",
    val episodeId: String? = null,
    val episodeLabel: String? = null,
    val position: Long = 0,
    val duration: Long = 0,
    val updatedAt: Long = 0,
) {
    val progress: Float get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val finished: Boolean get() = duration > 0 && position > duration * 0.92
}

fun guessStreamType(url: String): StreamType {
    val u = url.substringBefore('?').lowercase()
    return when {
        u.endsWith(".m3u8") || u.contains(".m3u8") || u.contains("/hls/") -> StreamType.HLS
        u.endsWith(".mpd") -> StreamType.DASH
        else -> StreamType.PROGRESSIVE
    }
}
