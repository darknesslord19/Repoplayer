package com.repoplayer.app.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repoplayer.app.App
import com.repoplayer.app.data.ContentItem
import com.repoplayer.app.data.Details
import com.repoplayer.app.data.Episode
import com.repoplayer.app.data.HomeCache
import com.repoplayer.app.data.Logger
import com.repoplayer.app.data.Net
import com.repoplayer.app.data.PlayerChoice
import com.repoplayer.app.data.RepoEntry
import com.repoplayer.app.data.RepoType
import com.repoplayer.app.data.Row
import com.repoplayer.app.data.SourceEntry
import com.repoplayer.app.data.Store
import com.repoplayer.app.data.StreamLink
import com.repoplayer.app.data.Subtitle
import com.repoplayer.app.data.WatchRecord
import com.repoplayer.app.data.str
import com.repoplayer.app.player.PlayRequest
import com.repoplayer.app.player.Playback
import com.repoplayer.app.source.ContentSource
import com.repoplayer.app.source.OpenSubs
import com.repoplayer.app.source.nuvio.NuvioScraper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

class DetailState(val item: ContentItem) {
    var details by mutableStateOf<Details?>(null)
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var season by mutableStateOf<Int?>(null)
}

class MoreState(val row: Row, val sourceKey: String) {
    val items = mutableStateListOf<ContentItem>().apply { addAll(row.items) }
    var page by mutableIntStateOf(1)
    var loading by mutableStateOf(false)
    var end by mutableStateOf(row.moreKey == null)
}

class LinksState(val details: Details, val episode: Episode?) {
    val links = mutableStateListOf<StreamLink>()
    val subtitles = mutableStateListOf<Subtitle>()
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
}

sealed class Page {
    class Detail(val state: DetailState) : Page()
    class More(val state: MoreState) : Page()
}

/** Ayarlar alt sayfası */
data class SettingsPage(val id: String, val arg: String? = null)

/** Görünümü etkileyen ayarların anlık kopyası (değişince ekran yeniden çizilir) */
data class UiPrefs(
    val posterSize: Int,
    val showTitles: Boolean,
    val badgeProgress: Boolean,
    val badgeWatched: Boolean,
    val badgeYear: Boolean,
    val navMode: Int,
    val hiddenRows: Set<String>,
    val hiddenSources: Set<String>,
    val pinnedSources: Set<String>,
    val profileName: String,
) {
    companion object {
        fun from(s: Store) = UiPrefs(
            s.posterSize, s.showTitles, s.badgeProgress, s.badgeWatched, s.badgeYear, s.navMode,
            s.hiddenRows, s.hiddenSources, s.pinnedSources, s.profileName,
        )
    }
}

/** Tüm kaynaklarda arama sonucu */
data class SourceResults(val entry: SourceEntry, val items: List<ContentItem>, val error: String? = null)

data class UpdateInfo(val version: String, val url: String, val notes: String?)

class AppViewModel : ViewModel() {
    private val app = App.instance
    val store: Store = app.store
    private val sources = app.sources
    private val main = Handler(Looper.getMainLooper())

    var ui by mutableStateOf(UiPrefs.from(store))
        private set

    var entries by mutableStateOf(sources.entries())
        private set
    var repos by mutableStateOf(store.repos)
        private set
    var selected by mutableStateOf(entries.firstOrNull { it.key == store.selectedSource } ?: entries.firstOrNull())
        private set
    var tab by mutableIntStateOf(store.startTab.coerceIn(0, 3))
    var showPicker by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var history by mutableStateOf(store.history)
        private set
    var favorites by mutableStateOf(store.favorites)
        private set

    val stack = mutableStateListOf<Page>()
    val settingsStack = mutableStateListOf<SettingsPage>()

    /** Seçicide görünen kaynaklar: gizlenenler hariç, sabitlenenler önde */
    val visibleEntries: List<SourceEntry>
        get() = entries.filter { it.key !in ui.hiddenSources || it.key == selected?.key }
            .sortedByDescending { it.key in ui.pinnedSources }

    // ---------- Ana sayfa ----------
    var rows by mutableStateOf<List<Row>>(emptyList())
        private set
    var homeLoading by mutableStateOf(false)
        private set
    var homeError by mutableStateOf<String?>(null)
        private set
    var homeFromCache by mutableStateOf(false)
        private set
    private var homeJob: Job? = null

    init {
        loadHome()
        if (store.updateReposOnStart && repos.isNotEmpty()) refreshAllRepos(silent = true)
        else migrateNuvioRepos()
    }

    /** Eski sürümde eklenmiş Nuvio repolarını yeni katalog yapısına yükseltir */
    private fun migrateNuvioRepos() {
        val old = store.repos.filter { r -> r.type == RepoType.NUVIO && r.sources.none { it.internalName.isNotBlank() } }
        if (old.isEmpty()) return
        viewModelScope.launch {
            old.forEach { r -> runCatching { sources.refresh(r.url) }.onFailure { Logger.e("Repo", "Nuvio repo yükseltilemedi", it) } }
            syncRepos()
        }
    }

    fun toast(text: String) {
        message = text
    }

    fun updatePrefs(block: Store.() -> Unit) {
        store.block()
        ui = UiPrefs.from(store)
    }

    fun refreshLocal() {
        history = store.history
        favorites = store.favorites
    }

    private fun entryFor(key: String) = entries.firstOrNull { it.key == key }

    private suspend fun source(key: String): ContentSource =
        sources.open(entryFor(key) ?: throw IllegalStateException("Bu içeriğin kaynağı artık ekli değil"))

    fun select(entry: SourceEntry) {
        showPicker = false
        stack.clear()
        searchResults = emptyList()
        globalResults = emptyList()
        searchError = null
        if (selected?.key == entry.key && rows.isNotEmpty()) return
        selected = entry
        store.selectedSource = entry.key
        loadHome()
    }

    fun loadHome(force: Boolean = false) {
        homeJob?.cancel()
        homeError = null
        val entry = selected ?: run { rows = emptyList(); homeLoading = false; return }
        val cached = if (store.homeCache && !force) HomeCache.get(entry.key) else null
        rows = cached ?: emptyList()
        homeFromCache = cached != null
        homeLoading = true
        homeJob = viewModelScope.launch {
            val fresh = ArrayList<Row>()
            try {
                val src = sources.open(entry)
                src.home().collect { r ->
                    fresh += r
                    fresh.sortBy { it.order }
                    rows = fresh.toList()
                    homeFromCache = false
                }
                if (fresh.isNotEmpty()) {
                    HomeCache.put(entry.key, fresh)
                    preloadSiblings(entry)
                } else if (rows.isEmpty()) homeError = "Bu kaynağın ana sayfası yok veya boş döndü.\nAra sekmesinden arama yapabilirsiniz."
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (generateSequence(t) { it.cause }.any { it is CancellationException }) return@launch
                Logger.e("Home", "${entry.name} ana sayfası", t)
                if (rows.isEmpty()) homeError = t.message ?: t.javaClass.simpleName
                else toast("Yenilenemedi, önbellek gösteriliyor: ${t.message}")
            } finally {
                homeLoading = false
            }
        }
    }

    private var preloaded = false

    /**
     * Hız: seçili kaynakla aynı repodaki diğer CS3 eklentilerini arka planda sırayla
     * indirip yükler; kaynak değiştirince ana sayfa beklemeden açılır.
     */
    private fun preloadSiblings(current: SourceEntry) {
        if (preloaded) return
        preloaded = true
        viewModelScope.launch(Dispatchers.IO) {
            delay(3000)
            visibleEntries.filter { it.type == RepoType.CS3 && it.repoUrl == current.repoUrl && it.key != current.key }
                .take(20)
                .forEach { e ->
                    runCatching { sources.open(e) }
                    delay(400)
                }
        }
    }

    fun visibleRows(): List<Row> {
        val key = selected?.key ?: return rows
        return rows.filter { "$key|${it.title}" !in ui.hiddenRows }
    }

    fun toggleRowHidden(title: String) {
        val key = selected?.key ?: return
        val id = "$key|$title"
        updatePrefs { hiddenRows = if (id in hiddenRows) hiddenRows - id else hiddenRows + id }
    }

    // ---------- Arama ----------
    var query by mutableStateOf("")
    var searchAll by mutableStateOf(false)
    var searchResults by mutableStateOf<List<ContentItem>>(emptyList())
        private set
    var globalResults by mutableStateOf<List<SourceResults>>(emptyList())
        private set
    var searching by mutableStateOf(false)
        private set
    var searchError by mutableStateOf<String?>(null)
        private set
    private var searchJob: Job? = null

    fun search() {
        val q = query.trim()
        if (q.length < 2) return
        searchJob?.cancel()
        searching = true
        searchError = null
        searchResults = emptyList()
        globalResults = emptyList()
        searchJob = viewModelScope.launch {
            try {
                if (searchAll) {
                    val gate = Semaphore(store.parallelRequests.coerceIn(1, 12))
                    val targets = visibleEntries
                    val results = targets.map { e ->
                        async {
                            gate.withPermit {
                                try {
                                    val items = withTimeoutOrNull(store.requestTimeoutSec * 2000L) { sources.open(e).search(q) }
                                    SourceResults(e, items ?: emptyList(), if (items == null) "zaman aşımı" else null)
                                } catch (c: CancellationException) {
                                    throw c
                                } catch (t: Throwable) {
                                    SourceResults(e, emptyList(), t.message)
                                }
                            }.also { r -> if (r.items.isNotEmpty()) globalResults = (globalResults + r).sortedBy { targets.indexOf(it.entry) } }
                        }
                    }.awaitAll()
                    if (results.none { it.items.isNotEmpty() }) searchError = "\"$q\" için hiçbir kaynakta sonuç yok"
                } else {
                    val entry = selected ?: return@launch
                    searchResults = sources.open(entry).search(q)
                    if (searchResults.isEmpty()) searchError = "\"$q\" için sonuç yok"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Logger.e("Search", "Arama", t)
                searchError = t.message ?: "Arama başarısız"
            } finally {
                searching = false
            }
        }
    }

    // ---------- Detay ----------
    fun openDetails(item: ContentItem) {
        val state = DetailState(item)
        stack.add(Page.Detail(state))
        viewModelScope.launch {
            try {
                val d = source(item.sourceKey).details(item)
                state.details = d
                val lastEp = history.firstOrNull { it.item.sourceKey == item.sourceKey && it.item.id == item.id }?.episodeId
                state.season = d.episodes.firstOrNull { it.id == lastEp }?.season ?: d.episodes.firstOrNull()?.season
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Logger.e("Details", item.title, t)
                state.error = t.message ?: "İçerik yüklenemedi"
            } finally {
                state.loading = false
            }
        }
    }

    fun openMore(row: Row) {
        val key = selected?.key ?: return
        val state = MoreState(row, key)
        stack.add(Page.More(state))
        if (state.items.size < 12) loadMore(state)
    }

    fun loadMore(state: MoreState) {
        if (state.loading || state.end) return
        state.loading = true
        viewModelScope.launch {
            try {
                val next = state.page + 1
                val list = source(state.sourceKey).rowPage(state.row, next)
                val known = state.items.map { it.id }.toHashSet()
                val fresh = list.filter { it.id !in known }
                if (fresh.isEmpty()) state.end = true else state.items.addAll(fresh)
                state.page = next
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                state.end = true
            } finally {
                state.loading = false
            }
        }
    }

    fun back(): Boolean {
        if (showPicker) { showPicker = false; return true }
        if (links != null) { closeLinks(); return true }
        if (stack.isNotEmpty()) { stack.removeAt(stack.lastIndex); return true }
        if (tab == 3 && settingsStack.isNotEmpty()) { settingsStack.removeAt(settingsStack.lastIndex); return true }
        if (tab != 0) { tab = 0; return true }
        return false
    }

    fun openSettings(id: String, arg: String? = null) {
        settingsStack.add(SettingsPage(id, arg))
    }

    fun toggleFavorite(item: ContentItem) {
        store.toggleFavorite(item)
        favorites = store.favorites
    }

    // ---------- Bağlantılar ----------
    var links by mutableStateOf<LinksState?>(null)
        private set
    private var linksJob: Job? = null

    fun closeLinks() {
        linksJob?.cancel()
        links = null
    }

    fun findLinks(details: Details, episode: Episode?, autoPlay: Boolean = false, context: Context? = null) {
        linksJob?.cancel()
        val state = LinksState(details, episode)
        links = state
        val ctx = context ?: app
        linksJob = viewModelScope.launch {
            var played = false
            val auto = if (autoPlay) launch {
                // İlk bağlantıdan sonra kısa süre bekleyip en iyisini oynat
                while (state.links.isEmpty()) delay(300)
                delay(4000)
                if (!played) { played = true; playBest(ctx, state) }
            } else null
            try {
                launch {
                    val imdb = details.imdbId
                    if (imdb != null && store.openSubtitles) {
                        val subs = OpenSubs.fetch(imdb, episode?.season, episode?.episode, store.subtitleLangs.split(","))
                        main.post { state.subtitles.addAll(subs) }
                    }
                }
                source(details.item.sourceKey).links(
                    details, episode,
                    onLink = { l -> main.post { if (state.links.none { it.url == l.url }) state.links.add(l) } },
                    onSubtitle = { s -> main.post { if (state.subtitles.none { it.url == s.url }) state.subtitles.add(s) } },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Logger.e("Links", details.title, t)
                state.error = t.message ?: "Bağlantılar alınamadı"
            } finally {
                delay(150) // bekleyen main.post'lar işlensin
                state.loading = false
                if (state.links.isEmpty() && state.error == null) state.error = "Bu içerik için oynatılabilir bağlantı bulunamadı"
                if (autoPlay && !played && state.links.isNotEmpty()) { played = true; auto?.cancel(); playBest(ctx, state) }
                else auto?.cancel()
            }
        }
    }

    private fun playBest(context: Context, state: LinksState) {
        val best = state.links.indices.maxByOrNull { state.links[it].qualityScore } ?: return
        play(context, state, best)
    }

    fun play(context: Context, state: LinksState, index: Int, choice: PlayerChoice = store.player) {
        val d = state.details
        val ep = state.episode
        val nextEp = ep?.let { cur -> d.episodes.getOrNull(d.episodes.indexOfFirst { it.id == cur.id } + 1) }
        val title = if (ep != null) "${d.title} · ${ep.name}" else d.title
        val key = Store.progressKey(d.item, ep?.id)
        val req = PlayRequest(
            item = d.item.copy(title = d.title, poster = d.poster ?: d.item.poster),
            title = title,
            episodeId = ep?.id,
            episodeLabel = ep?.name,
            links = state.links.toList(),
            startIndex = index,
            subtitles = state.subtitles.toList(),
            progressKey = key,
            onNext = nextEp?.let { n -> { findLinks(d, n, autoPlay = true) } },
            nextLabel = nextEp?.name,
        )
        val resume = if (store.autoResume) store.record(key)?.takeIf { !it.finished }?.position ?: 0L else 0L
        Playback.start(context, req, choice, resume)
        links = null
    }

    fun progressFor(item: ContentItem, episodeId: String?): WatchRecord? =
        history.firstOrNull { it.key == Store.progressKey(item, episodeId) }

    fun lastWatched(item: ContentItem): WatchRecord? =
        history.firstOrNull { it.item.sourceKey == item.sourceKey && it.item.id == item.id }

    fun removeHistory(rec: WatchRecord) {
        store.removeHistory(rec.key)
        history = store.history
    }

    fun clearHistory() {
        store.clearHistory()
        history = store.history
    }

    // ---------- Repolar ----------
    var repoBusy by mutableStateOf(false)
        private set

    private fun syncRepos() {
        repos = store.repos
        entries = sources.entries()
        if (selected == null || entries.none { it.key == selected?.key }) {
            selected = visibleEntries.firstOrNull()
            store.selectedSource = selected?.key
            loadHome()
        }
    }

    fun addRepo(url: String, onDone: (Boolean) -> Unit = {}) {
        if (url.isBlank()) return
        repoBusy = true
        viewModelScope.launch {
            try {
                val repo: RepoEntry = sources.addRepo(url)
                syncRepos()
                Logger.i("Repo", "${repo.name} eklendi (${repo.sources.size} kaynak)")
                toast("${repo.name} eklendi · ${repo.sources.size} kaynak")
                if (selected == null || repo.sources.size == 1) repo.sources.firstOrNull()?.let { select(it) }
                onDone(true)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Logger.e("Repo", "Eklenemedi: $url", t)
                toast("Eklenemedi: ${t.message}")
                onDone(false)
            } finally {
                repoBusy = false
            }
        }
    }

    fun refreshRepo(url: String) {
        repoBusy = true
        viewModelScope.launch {
            try {
                val r = sources.refresh(url)
                syncRepos()
                toast("${r.name} güncellendi · ${r.sources.size} kaynak")
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Logger.e("Repo", "Güncellenemedi: $url", t)
                toast("Güncellenemedi: ${t.message}")
            } finally {
                repoBusy = false
            }
        }
    }

    fun refreshAllRepos(silent: Boolean = false) {
        repoBusy = true
        viewModelScope.launch {
            var ok = 0
            store.repos.forEach { r ->
                try {
                    sources.refresh(r.url); ok++
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Logger.e("Repo", "Güncellenemedi: ${r.url}", t)
                }
            }
            syncRepos()
            repoBusy = false
            if (!silent) toast("$ok/${store.repos.size} repo güncellendi")
        }
    }

    fun removeRepo(url: String) {
        sources.remove(url)
        syncRepos()
    }

    suspend fun nuvioScrapers(entry: SourceEntry): List<NuvioScraper> = sources.nuvioSource(entry).scrapers()

    /** Eklentiyi indirip yükler (Ayarlar > Eklentiler > Test) */
    fun testSource(entry: SourceEntry, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                sources.open(entry)
                toast("${entry.name} yüklendi")
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                toast(t.message ?: "Yüklenemedi")
            } finally {
                onDone()
            }
        }
    }

    // ---------- Kaynak testi ----------
    val testOutput = mutableStateListOf<String>()
    var testRunning by mutableStateOf(false)
        private set
    var testTarget by mutableStateOf<SourceEntry?>(null)
    private var testJob: Job? = null

    fun runSourceTest(entry: SourceEntry) {
        testJob?.cancel()
        testTarget = entry
        testOutput.clear()
        testRunning = true
        testJob = viewModelScope.launch {
            try {
                com.repoplayer.app.source.SourceTest(sources) { line -> main.post { testOutput.add(line) } }.run(entry)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                main.post { testOutput.add("✗ Test hatası: ${t.message}") }
            } finally {
                delay(200)
                testRunning = false
            }
        }
    }

    fun toggleSourceHidden(key: String) = updatePrefs {
        hiddenSources = if (key in hiddenSources) hiddenSources - key else hiddenSources + key
    }

    fun toggleSourcePinned(key: String) = updatePrefs {
        pinnedSources = if (key in pinnedSources) pinnedSources - key else pinnedSources + key
    }

    fun settingsChanged() {
        if (selected?.type == RepoType.NUVIO && rows.isEmpty()) loadHome(force = true)
    }

    // ---------- Yedekleme ----------
    fun exportBackup(): String = store.exportAll()

    fun importBackup(text: String) {
        try {
            val n = store.importAll(text)
            ui = UiPrefs.from(store)
            repos = store.repos
            entries = sources.entries()
            history = store.history
            favorites = store.favorites
            selected = entries.firstOrNull { it.key == store.selectedSource } ?: entries.firstOrNull()
            loadHome(force = true)
            toast("Yedek geri yüklendi ($n ayar)")
        } catch (t: Throwable) {
            toast("Yedek okunamadı: ${t.message}")
        }
    }

    // ---------- Güncelleme ----------
    var update by mutableStateOf<UpdateInfo?>(null)
        private set
    var checkingUpdate by mutableStateOf(false)
        private set

    fun checkUpdate(silent: Boolean = false) {
        checkingUpdate = true
        viewModelScope.launch {
            try {
                val rel = Net.jsonTree("https://api.github.com/repos/$GITHUB_REPO/releases/latest")
                val tag = rel.str("tag_name")?.removePrefix("v") ?: throw IllegalStateException("Sürüm bulunamadı")
                val apk = rel.get("assets")?.firstOrNull { it.str("name")?.endsWith(".apk") == true }?.str("browser_download_url")
                    ?: rel.str("html_url") ?: ""
                if (isNewer(tag, app.versionName)) {
                    update = UpdateInfo(tag, apk, rel.str("body"))
                    if (!silent) toast("Yeni sürüm: v$tag")
                } else {
                    update = null
                    if (!silent) toast("Uygulama güncel (v${app.versionName})")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (!silent) toast("Güncelleme denetlenemedi: ${t.message}")
            } finally {
                checkingUpdate = false
            }
        }
    }

    private fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split('.', '-').mapNotNull { it.toIntOrNull() }
        val l = local.split('.', '-').mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    fun clearCaches(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            HomeCache.clear()
            context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
            main.post { toast("Önbellek temizlendi") }
        }
    }

    companion object {
        const val GITHUB_REPO = "darknesslord19/Repoplayer"
    }
}
