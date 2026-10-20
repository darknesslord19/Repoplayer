package com.repoplayer.app.source.cs3

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.util.Log
import com.fasterxml.jackson.databind.JsonNode
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.Plugin
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
import com.repoplayer.app.data.StreamLink
import com.repoplayer.app.data.StreamType
import com.repoplayer.app.data.Subtitle
import com.repoplayer.app.data.int
import com.repoplayer.app.data.json
import com.repoplayer.app.data.resolveUrl
import com.repoplayer.app.data.str
import com.repoplayer.app.source.ContentSource
import dalvik.system.PathClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

private const val TAG = "Cs3"

/** CloudStream repo.json / plugins.json okuyucu */
object Cs3Repo {
    /** repo.json'u (veya doğrudan plugins.json'u) okuyup eklenti listesini çıkarır */
    suspend fun read(url: String, root: JsonNode): RepoEntry {
        val name: String
        val icon: String?
        val lists: List<String>
        if (root.isArray) {
            name = url.substringAfterLast('/').substringBefore('.')
            icon = null
            lists = listOf(url)
        } else {
            name = root.str("name") ?: "CloudStream Repo"
            icon = root.str("iconUrl")
            lists = root.get("pluginLists")?.map { resolveUrl(url, it.asText()) } ?: emptyList()
        }
        val sources = coroutineScope {
            lists.map { listUrl ->
                async {
                    runCatching {
                        val arr = if (listUrl == url) root else Net.jsonTree(listUrl)
                        arr.mapNotNull { p -> pluginEntry(url, name, listUrl, p) }
                    }.onFailure { Log.e(TAG, "Liste okunamadı $listUrl", it) }.getOrDefault(emptyList())
                }
            }.awaitAll().flatten()
        }.distinctBy { it.key }.sortedBy { it.name.lowercase() }
        return RepoEntry(url = url, type = RepoType.CS3, name = name, icon = icon, sources = sources)
    }

    private fun pluginEntry(repoUrl: String, repoName: String, listUrl: String, p: JsonNode): SourceEntry? {
        val status = p.int("status") ?: 1
        if (status == 0) return null // 0 = kapalı
        val internal = p.str("internalName") ?: p.str("name") ?: return null
        val fileUrl = p.str("url") ?: return null
        return SourceEntry(
            key = "cs3:$repoUrl:$internal",
            name = p.str("name") ?: internal,
            icon = p.str("iconUrl")?.replace("%size%", "128"),
            group = repoName,
            type = RepoType.CS3,
            repoUrl = repoUrl,
            url = resolveUrl(listUrl, fileUrl),
            version = p.int("version") ?: 0,
            language = p.str("language"),
            internalName = internal,
        )
    }
}

/** Bir eklentinin yükleme durumu (Ayarlar > Eklentiler'de gösterilir) */
data class PluginStatus(
    val state: State,
    val message: String? = null,
    val apis: List<String> = emptyList(),
    val hasSettings: Boolean = false,
) {
    enum class State { LOADING, LOADED, FAILED }
}

/** .cs3 dosyalarını indirip CloudStream eklentisi olarak yükler */
object Cs3Loader {
    private const val TAG = "Cs3Loader"
    private val mutex = Mutex()
    private val loaded = HashMap<String, List<MainAPI>>()
    private val plugins = HashMap<String, BasePlugin>()
    private val statuses = java.util.concurrent.ConcurrentHashMap<String, PluginStatus>()

    fun status(key: String): PluginStatus? = statuses[key]

    fun plugin(key: String): BasePlugin? = plugins[key]

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(12)

    fun pluginsDir(context: Context) = File(context.filesDir, "plugins")

    suspend fun load(context: Context, entry: SourceEntry): List<MainAPI> = mutex.withLock {
        loaded[entry.key]?.let { return it }
        statuses[entry.key] = PluginStatus(PluginStatus.State.LOADING)
        try {
            val dir = File(pluginsDir(context), md5(entry.repoUrl))
            val safe = entry.internalName.replace(Regex("[^A-Za-z0-9_.-]"), "_")
            val file = File(dir, "$safe.cs3")
            val versionFile = File(dir, "$safe.version")
            val wanted = "${entry.version}|${entry.url}"

            withContext(Dispatchers.IO) {
                val have = runCatching { versionFile.readText() }.getOrNull()
                if (!file.exists() || file.length() == 0L || have != wanted) {
                    Logger.i(TAG, "${entry.name} indiriliyor (v${entry.version})")
                    Net.download(entry.url, file)
                    versionFile.writeText(wanted)
                }
                // Android 14+: dinamik kod dosyası salt okunur olmalı
                runCatching { file.setReadOnly() }
            }

            val (instance, apis) = withContext(Dispatchers.IO) { loadFile(context, file) }
            if (apis.isEmpty()) throw IllegalStateException("eklenti hiçbir kaynak kaydetmedi")
            loaded[entry.key] = apis
            plugins[entry.key] = instance
            statuses[entry.key] = PluginStatus(
                PluginStatus.State.LOADED,
                apis = apis.map { "${it.name} (${it.mainUrl})" },
                hasSettings = (instance as? Plugin)?.openSettings != null,
            )
            Logger.i(TAG, "${entry.name} yüklendi: ${apis.joinToString { it.name }}")
            apis
        } catch (t: Throwable) {
            statuses[entry.key] = PluginStatus(PluginStatus.State.FAILED, t.message ?: t.javaClass.simpleName)
            Logger.e(TAG, "${entry.name} yüklenemedi", t)
            throw IllegalStateException("${entry.name} yüklenemedi: ${t.message ?: t.javaClass.simpleName}", t)
        }
    }

    private fun loadFile(context: Context, file: File): Pair<BasePlugin, List<MainAPI>> {
        val path = file.absolutePath

        val loader = PathClassLoader(path, context.classLoader)
        val manifestText = loader.getResourceAsStream("manifest.json")?.use { it.reader().readText() }
            ?: throw IllegalStateException("Eklentide manifest.json yok")
        val manifest = json.readTree(manifestText)
        val className = manifest.str("pluginClassName")
            ?: throw IllegalStateException("pluginClassName bulunamadı")

        val instance = loader.loadClass(className).getDeclaredConstructor().newInstance() as BasePlugin
        instance.filename = path

        if (manifest.get("requiresResources")?.asBoolean() == true && instance is Plugin) {
            runCatching {
                val assets = AssetManager::class.java.getDeclaredConstructor().newInstance()
                AssetManager::class.java.getMethod("addAssetPath", String::class.java).invoke(assets, path)
                @Suppress("DEPRECATION")
                instance.resources = Resources(assets, context.resources.displayMetrics, context.resources.configuration)
            }
        }

        // Aynı dosya daha önce yüklendiyse eski kayıtları temizle
        APIHolder.allProviders.removeAll { it.sourcePlugin == path }
        if (instance is Plugin) instance.load(context) else instance.load()
        return instance to APIHolder.allProviders.filter { it.sourcePlugin == path }
    }

    /** İndirilen tüm eklentileri siler (bir sonraki açılışta yeniden indirilir) */
    fun clearCache(context: Context) {
        pluginsDir(context).walkBottomUp().forEach { it.setWritable(true); it.delete() }
    }
}

private fun TvType?.toKind(): Kind = when (this?.name) {
    null -> Kind.OTHER
    "Movie", "AnimeMovie", "Torrent", "Documentary" -> Kind.MOVIE
    "Live" -> Kind.LIVE
    "TvSeries", "Anime", "OVA", "Cartoon", "AsianDrama", "Podcast", "CustomMedia" -> Kind.SERIES
    else -> Kind.OTHER
}

class Cs3Source(override val entry: SourceEntry, private val apis: List<MainAPI>) : ContentSource {

    private fun SearchResponse.toItem(apiIndex: Int) = ContentItem(
        sourceKey = entry.key,
        id = url,
        title = name,
        poster = posterUrl,
        kind = type.toKind(),
        extra = apiIndex.toString(),
        // CloudStream ile aynı: yalnızca eklentinin istediği başlıklar (varsayılan Referer yok)
        posterHeaders = posterHeaders?.takeIf { it.isNotEmpty() },
    )

    private fun api(item: ContentItem): MainAPI = apis.getOrNull(item.extra?.toIntOrNull() ?: 0) ?: apis.first()

    private fun HomePageList.toRow(apiIndex: Int, moreKey: String?, order: Int) = Row(
        title = name,
        items = list.map { it.toItem(apiIndex) },
        moreKey = moreKey,
        horizontal = isHorizontalImages,
        order = order,
    )

    override fun home(): Flow<Row> = channelFlow {
        val gate = Semaphore(com.repoplayer.app.App.instance.store.parallelRequests.coerceIn(1, 12))
        val sent = java.util.concurrent.atomic.AtomicInteger(0)
        val lastError = java.util.concurrent.atomic.AtomicReference<Throwable?>(null)
        val jobs = mutableListOf<kotlinx.coroutines.Job>()
        var order = 0
        apis.forEachIndexed { apiIndex, api ->
            if (!api.hasMainPage) return@forEachIndexed
            api.mainPage.forEach { page ->
                val myOrder = (order++) * 100
                jobs += launch(Dispatchers.IO) {
                    gate.withPermit {
                        val res = try {
                            api.getMainPage(1, MainPageRequest(page.name, page.data, page.horizontalImages))
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (t: Throwable) {
                            lastError.set(t)
                            Logger.e(TAG, "${entry.name}: \"${page.name}\" satırı yüklenemedi", t)
                            null
                        }
                        res?.items?.forEachIndexed { i, list ->
                            if (list.list.isEmpty()) return@forEachIndexed
                            val key = if (res.hasNext || page.data.isNotBlank())
                                listOf(apiIndex.toString(), page.name, page.data, page.horizontalImages.toString(), list.name)
                                    .joinToString(SEP) else null
                            val row = list.toRow(apiIndex, key, myOrder + i)
                            val withPoster = row.items.count { !it.poster.isNullOrBlank() }
                            if (withPoster < row.items.size) {
                                Logger.w(TAG, "${entry.name} \"${row.title}\": ${row.items.size} içerikten ${row.items.size - withPoster} tanesinde afiş adresi yok")
                            }
                            send(row)
                            sent.incrementAndGet()
                        }
                    }
                }
            }
        }
        jobs.forEach { it.join() }
        val err = lastError.get()
        if (sent.get() == 0 && err != null) {
            throw IllegalStateException("${entry.name} ana sayfası alınamadı: ${err.message ?: err.javaClass.simpleName}", err)
        }
    }

    override suspend fun rowPage(row: Row, page: Int): List<ContentItem> = withContext(Dispatchers.IO) {
        val parts = row.moreKey?.split(SEP) ?: return@withContext emptyList()
        val apiIndex = parts[0].toIntOrNull() ?: 0
        val api = apis.getOrNull(apiIndex) ?: return@withContext emptyList()
        val res = api.getMainPage(page, MainPageRequest(parts[1], parts[2], parts[3].toBoolean()))
            ?: return@withContext emptyList()
        val list = res.items.firstOrNull { it.name == parts.getOrNull(4) } ?: res.items.firstOrNull()
        list?.list?.map { it.toItem(apiIndex) } ?: emptyList()
    }

    override suspend fun search(query: String): List<ContentItem> = withContext(Dispatchers.IO) {
        apis.mapIndexed { i, api ->
            async {
                runCatching {
                    val paged = runCatching { api.search(query, 1)?.items }.getOrNull()
                    (paged ?: api.search(query) ?: emptyList()).map { it.toItem(i) }
                }.getOrDefault(emptyList())
            }
        }.awaitAll().flatten()
    }

    override suspend fun details(item: ContentItem): Details = withContext(Dispatchers.IO) {
        val api = api(item)
        val res: LoadResponse = api.load(item.id) ?: throw IllegalStateException("İçerik yüklenemedi")
        val apiIndex = item.extra?.toIntOrNull() ?: 0

        var playId: String? = null
        val episodes = mutableListOf<Episode>()
        when (res) {
            is MovieLoadResponse -> playId = res.dataUrl
            is LiveStreamLoadResponse -> playId = res.dataUrl
            is TvSeriesLoadResponse -> res.episodes.forEachIndexed { i, e ->
                episodes += Episode(
                    id = e.data,
                    name = e.name ?: "Bölüm ${e.episode ?: (i + 1)}",
                    season = e.season,
                    episode = e.episode ?: (i + 1),
                    poster = e.posterUrl,
                    description = e.description,
                )
            }
            is AnimeLoadResponse -> {
                val multi = res.episodes.size > 1
                res.episodes.forEach { (status, list) ->
                    val tag = when (status.name) { "Dubbed" -> "[Dublaj] "; "Subbed" -> "[Altyazılı] "; else -> "" }
                    list.forEachIndexed { i, e ->
                        episodes += Episode(
                            id = e.data,
                            name = (if (multi) tag else "") + (e.name ?: "Bölüm ${e.episode ?: (i + 1)}"),
                            season = e.season,
                            episode = e.episode ?: (i + 1),
                            poster = e.posterUrl,
                            description = e.description,
                        )
                    }
                }
            }
            else -> playId = res.url
        }

        val imdb = res.syncData.values.firstNotNullOfOrNull { Regex("tt\\d{5,}").find(it)?.value }
        Details(
            item = item.copy(poster = item.poster ?: res.posterUrl, kind = res.type.toKind(), posterHeaders = res.posterHeaders?.takeIf { it.isNotEmpty() } ?: item.posterHeaders),
            title = res.name,
            poster = res.posterUrl ?: item.poster,
            backdrop = res.backgroundPosterUrl,
            plot = res.plot,
            year = res.year,
            tags = res.tags ?: emptyList(),
            episodes = episodes,
            playId = playId,
            imdbId = imdb,
            recommendations = res.recommendations?.map { it.toItem(apiIndex) } ?: emptyList(),
        )
    }

    override suspend fun links(
        details: Details,
        episode: Episode?,
        onLink: (StreamLink) -> Unit,
        onSubtitle: (Subtitle) -> Unit,
    ) {
        val data = episode?.id ?: details.playId ?: return
        val started = System.currentTimeMillis()
        var count = 0
        withContext(Dispatchers.IO) {
            val api = api(details.item)
            Logger.i(TAG, "${api.name}: bağlantılar aranıyor → $data")
            val ok = api.loadLinks(
                data,
                false,
                { sub -> onSubtitle(Subtitle(sub.lang, sub.url, sub.headers ?: emptyMap())) },
                { link ->
                    val type = when (link.type.name) {
                        "M3U8" -> StreamType.HLS
                        "DASH" -> StreamType.DASH
                        "VIDEO" -> StreamType.PROGRESSIVE
                        else -> null // torrent/magnet desteklenmiyor
                    }
                    if (type == null) Logger.w(TAG, "Desteklenmeyen bağlantı türü (${link.type.name}): ${link.name}")
                    if (type != null) {
                        count++
                        onLink(
                            StreamLink(
                                name = link.name,
                                url = link.url,
                                headers = link.getAllHeaders(),
                                type = type,
                                quality = link.quality.takeIf { it in 100..4320 }?.let { "${it}p" },
                            )
                        )
                    }
                },
            )
            val secs = (System.currentTimeMillis() - started) / 1000
            if (count == 0) Logger.w(TAG, "${api.name}: bağlantı bulunamadı (loadLinks=$ok, ${secs} sn). Site/video sunucusu erişilemiyor olabilir; Ayarlar > Genel > DNS'i değiştirmeyi deneyin.")
            else Logger.i(TAG, "${api.name}: $count bağlantı (${secs} sn)")
        }
    }

    companion object {
        private const val SEP = "\u0001"
    }
}
