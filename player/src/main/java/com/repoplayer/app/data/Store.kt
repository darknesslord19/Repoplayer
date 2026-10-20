package com.repoplayer.app.data

import android.content.Context
import android.content.SharedPreferences
import com.fasterxml.jackson.module.kotlin.readValue

enum class PlayerChoice { INTERNAL, VLC, MX, ASK }

/** Basit kalıcı depolama: repolar, geçmiş, favoriler, ayarlar */
class Store(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("repoplayer", Context.MODE_PRIVATE)

    private inline fun <reified T> read(key: String, default: T): T =
        prefs.getString(key, null)?.let { runCatching { json.readValue<T>(it) }.getOrNull() } ?: default

    private fun write(key: String, value: Any) {
        prefs.edit().putString(key, value.toJson()).apply()
    }

    // ---- Repolar ----
    var repos: List<RepoEntry>
        get() = read("repos", emptyList())
        set(v) = write("repos", v)

    var selectedSource: String?
        get() = prefs.getString("selected_source", null)
        set(v) = prefs.edit().putString("selected_source", v).apply()

    // ---- Ayarlar ----
    var tmdbKey: String
        get() = prefs.getString("tmdb_key", "") ?: ""
        set(v) = prefs.edit().putString("tmdb_key", v.trim()).apply()

    var language: String
        get() = prefs.getString("language", "tr-TR") ?: "tr-TR"
        set(v) = prefs.edit().putString("language", v).apply()

    /** OpenSubtitles dil kodları (ISO 639-2), virgülle */
    var subtitleLangs: String
        get() = prefs.getString("sub_langs", "tur,eng") ?: "tur,eng"
        set(v) = prefs.edit().putString("sub_langs", v).apply()

    var openSubtitles: Boolean
        get() = prefs.getBoolean("opensubs", true)
        set(v) = prefs.edit().putBoolean("opensubs", v).apply()

    var player: PlayerChoice
        get() = runCatching { PlayerChoice.valueOf(prefs.getString("player", "INTERNAL")!!) }.getOrDefault(PlayerChoice.INTERNAL)
        set(v) = prefs.edit().putString("player", v.name).apply()

    var autoNextSource: Boolean
        get() = prefs.getBoolean("auto_next", true)
        set(v) = prefs.edit().putBoolean("auto_next", v).apply()

    var subtitleSize: Int
        get() = prefs.getInt("sub_size", 100)
        set(v) = prefs.edit().putInt("sub_size", v).apply()

    /** Nuvio: kapatılan scraper id'leri */
    var disabledScrapers: Set<String>
        get() = prefs.getStringSet("disabled_scrapers", emptySet()) ?: emptySet()
        set(v) = prefs.edit().putStringSet("disabled_scrapers", v).apply()

    // ---- Geçmiş / devam ----
    var history: List<WatchRecord>
        get() = read("history", emptyList())
        private set(v) = write("history", v)

    fun record(key: String): WatchRecord? = history.firstOrNull { it.key == key }

    fun saveProgress(rec: WatchRecord) {
        val list = history.filterNot { it.key == rec.key }.toMutableList()
        list.add(0, rec.copy(updatedAt = System.currentTimeMillis()))
        history = list.take(300)
    }

    fun removeHistory(key: String) {
        history = history.filterNot { it.key == key }
    }

    fun clearHistory() {
        history = emptyList()
    }

    // ---- Favoriler ----
    var favorites: List<ContentItem>
        get() = read("favorites", emptyList())
        private set(v) = write("favorites", v)

    fun isFavorite(item: ContentItem) = favorites.any { it.sourceKey == item.sourceKey && it.id == item.id }

    fun toggleFavorite(item: ContentItem) {
        favorites = if (isFavorite(item)) favorites.filterNot { it.sourceKey == item.sourceKey && it.id == item.id }
        else listOf(item) + favorites
    }

    companion object {
        fun progressKey(item: ContentItem, episodeId: String?) =
            "${item.sourceKey}|${item.id}|${episodeId ?: ""}"
    }
}
