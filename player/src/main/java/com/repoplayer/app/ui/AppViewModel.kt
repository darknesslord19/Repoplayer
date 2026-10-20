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
import com.repoplayer.app.data.PlayerChoice
import com.repoplayer.app.data.RepoEntry
import com.repoplayer.app.data.Row
import com.repoplayer.app.data.SourceEntry
import com.repoplayer.app.data.Store
import com.repoplayer.app.data.StreamLink
import com.repoplayer.app.data.Subtitle
import com.repoplayer.app.data.WatchRecord
import com.repoplayer.app.player.PlayRequest
import com.repoplayer.app.player.Playback
import com.repoplayer.app.source.ContentSource
import com.repoplayer.app.source.OpenSubs
import com.repoplayer.app.source.nuvio.NuvioScraper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

class AppViewModel : ViewModel() {
    private val app = App.instance
    val store: Store = app.store
    private val sources = app.sources
    private val main = Handler(Looper.getMainLooper())

    var entries by mutableStateOf(sources.entries())
        private set
    var repos by mutableStateOf(store.repos)
        private set
    var selected by mutableStateOf(entries.firstOrNull { it.key == store.selectedSource } ?: entries.firstOrNull())
        private set
    var tab by mutableIntStateOf(0)
    var showPicker by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var history by mutableStateOf(store.history)
        private set
    var favorites by mutableStateOf(store.favorites)
        private set

    val stack = mutableStateListOf<Page>()

    // ---------- Ana sayfa ----------
    var rows by mutableStateOf<List<Row>>(emptyList())
        private set
    var homeLoading by mutableStateOf(false)
        private set
    var homeError by mutableStateOf<String?>(null)
        private set
    private var homeJob: Job? = null

    init {
        loadHome()
    }

    fun toast(text: String) {
        message = text
    }

    fun refreshLocal() {
        history = store.history
        favorites = store.favorites
    }

    private fun entryFor(key: String) = entries.firstOrNull { it.key == key }

    private suspend fun source(key: String): ContentSource =
        sources.open(entryFor(key) ?: throw IllegalStateException("Bu içeriğin kaynağı artık ekli değil"))

    fun select(entry: SourceEntry) {
        selected = entry
        store.selectedSource = entry.key
        showPicker = false
        stack.clear()
        searchResults = emptyList()
        searchError = null
        loadHome()
    }

    fun loadHome() {
        homeJob?.cancel()
        rows = emptyList()
        homeError = null
        val entry = selected ?: run { homeLoading = false; return }
        homeLoading = true
        homeJob = viewModelScope.launch {
            try {
                val src = sources.open(entry)
                src.home().collect { r -> rows = (rows + r).sortedBy { it.order } }
                if (rows.isEmpty()) homeError = "Bu kaynağın ana sayfası yok veya boş döndü.\nAra sekmesinden arama yapabilirsiniz."
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                homeError = t.message ?: t.javaClass.simpleName
            } finally {
                homeLoading = false
            }
        }
    }

    // ---------- Arama ----------
    var query by mutableStateOf("")
    var searchResults by mutableStateOf<List<ContentItem>>(emptyList())
        private set
    var searching by mutableStateOf(false)
        private set
    var searchError by mutableStateOf<String?>(null)
        private set
    private var searchJob: Job? = null

    fun search() {
        val q = query.trim()
        val entry = selected ?: return
        if (q.length < 2) return
        searchJob?.cancel()
        searching = true
        searchError = null
        searchJob = viewModelScope.launch {
            try {
                searchResults = sources.open(entry).search(q)
                if (searchResults.isEmpty()) searchError = "\"$q\" için sonuç yok"
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
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
        if (links != null) { closeLinks(); return true }
        if (stack.isNotEmpty()) { stack.removeAt(stack.lastIndex); return true }
        if (tab != 0) { tab = 0; return true }
        return false
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
        )
        val resume = store.record(key)?.takeIf { !it.finished }?.position ?: 0L
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
            selected = entries.firstOrNull()
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
                toast("${repo.name} eklendi · ${repo.sources.size} kaynak")
                if (selected == null || repo.sources.size == 1) repo.sources.firstOrNull()?.let { select(it) }
                onDone(true)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
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
                toast("${r.name} güncellendi")
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                toast("Güncellenemedi: ${t.message}")
            } finally {
                repoBusy = false
            }
        }
    }

    fun removeRepo(url: String) {
        sources.remove(url)
        syncRepos()
    }

    suspend fun nuvioScrapers(entry: SourceEntry): List<NuvioScraper> = sources.nuvioSource(entry).scrapers()

    fun settingsChanged() {
        // TMDB anahtarı vb. değişince Nuvio ana sayfasını yenile
        if (selected?.type == com.repoplayer.app.data.RepoType.NUVIO && rows.isEmpty()) loadHome()
    }
}
