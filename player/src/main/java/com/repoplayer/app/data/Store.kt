package com.repoplayer.app.data

import android.content.Context
import android.content.SharedPreferences
import com.fasterxml.jackson.databind.JsonNode
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

    private fun str(key: String, def: String) = prefs.getString(key, def) ?: def
    private fun setStr(key: String, v: String) = prefs.edit().putString(key, v).apply()
    private fun bool(key: String, def: Boolean) = prefs.getBoolean(key, def)
    private fun setBool(key: String, v: Boolean) = prefs.edit().putBoolean(key, v).apply()
    private fun int(key: String, def: Int) = prefs.getInt(key, def)
    private fun setInt(key: String, v: Int) = prefs.edit().putInt(key, v).apply()
    private fun set(key: String) = prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet()
    private fun setSet(key: String, v: Set<String>) = prefs.edit().putStringSet(key, v).apply()

    // ---- Repolar ----
    var repos: List<RepoEntry>
        get() = read("repos", emptyList())
        set(v) = write("repos", v)

    var selectedSource: String?
        get() = prefs.getString("selected_source", null)
        set(v) = prefs.edit().putString("selected_source", v).apply()

    // ---- Profil ----
    var profileName: String
        get() = str("profile_name", "Profil")
        set(v) = setStr("profile_name", v.trim().ifEmpty { "Profil" })

    // ---- Genel ----
    var tmdbKey: String
        get() = str("tmdb_key", "")
        set(v) = setStr("tmdb_key", v.trim())

    /** İçerik dili (TMDB vb.) */
    var language: String
        get() = str("language", "tr-TR")
        set(v) = setStr("language", v)

    /** DNS-over-HTTPS: 0 sistem, 1 Cloudflare, 2 Google, 3 AdGuard, 4 Quad9 */
    var dns: Int
        get() = int("dns", 1)
        set(v) = setInt("dns", v)

    var startTab: Int
        get() = int("start_tab", 0)
        set(v) = setInt("start_tab", v)

    var updateReposOnStart: Boolean
        get() = bool("update_repos_start", false)
        set(v) = setBool("update_repos_start", v)

    // ---- Oynatıcı ----
    var player: PlayerChoice
        get() = runCatching { PlayerChoice.valueOf(str("player", "INTERNAL")) }.getOrDefault(PlayerChoice.INTERNAL)
        set(v) = setStr("player", v.name)

    var autoNextSource: Boolean
        get() = bool("auto_next", true)
        set(v) = setBool("auto_next", v)

    var autoNextEpisode: Boolean
        get() = bool("auto_next_ep", true)
        set(v) = setBool("auto_next_ep", v)

    var autoResume: Boolean
        get() = bool("auto_resume", true)
        set(v) = setBool("auto_resume", v)

    /** İleri/geri sarma adımı (sn) */
    var seekStep: Int
        get() = int("seek_step", 10)
        set(v) = setInt("seek_step", v)

    var gestures: Boolean
        get() = bool("gestures", true)
        set(v) = setBool("gestures", v)

    /** 0: sığdır, 1: yakınlaştır, 2: doldur */
    var defaultResize: Int
        get() = int("resize", 0)
        set(v) = setInt("resize", v)

    /** Varsayılan hız x100 (100 = 1.0x) */
    var defaultSpeed: Int
        get() = int("speed", 100)
        set(v) = setInt("speed", v)

    var skipIntroSec: Int
        get() = int("skip_intro", 85)
        set(v) = setInt("skip_intro", v)

    /** 0: normal, 1: yüksek, 2: çok yüksek tampon */
    var bufferLevel: Int
        get() = int("buffer", 1)
        set(v) = setInt("buffer", v)

    // ---- Altyazı ----
    var subtitleLangs: String
        get() = str("sub_langs", "tur,eng")
        set(v) = setStr("sub_langs", v)

    var openSubtitles: Boolean
        get() = bool("opensubs", true)
        set(v) = setBool("opensubs", v)

    var subtitleSize: Int
        get() = int("sub_size", 100)
        set(v) = setInt("sub_size", v)

    var subtitleBackground: Boolean
        get() = bool("sub_bg", false)
        set(v) = setBool("sub_bg", v)

    /** 0: beyaz, 1: sarı, 2: turkuaz */
    var subtitleColor: Int
        get() = int("sub_color", 0)
        set(v) = setInt("sub_color", v)

    // ---- Sağlayıcılar ----
    var disabledScrapers: Set<String>
        get() = set("disabled_scrapers")
        set(v) = setSet("disabled_scrapers", v)

    var nuvioTimeoutSec: Int
        get() = int("nuvio_timeout", 40)
        set(v) = setInt("nuvio_timeout", v)

    // ---- Plus menü ----
    /** Seçicide gizlenen kaynaklar */
    var hiddenSources: Set<String>
        get() = set("hidden_sources")
        set(v) = setSet("hidden_sources", v)

    /** Sabitlenen (en üstte gösterilen) kaynaklar */
    var pinnedSources: Set<String>
        get() = set("pinned_sources")
        set(v) = setSet("pinned_sources", v)

    /** Gizlenen ana sayfa kategorileri: "kaynakKey|başlık" */
    var hiddenRows: Set<String>
        get() = set("hidden_rows")
        set(v) = setSet("hidden_rows", v)

    var badgeProgress: Boolean
        get() = bool("badge_progress", true)
        set(v) = setBool("badge_progress", v)

    var badgeWatched: Boolean
        get() = bool("badge_watched", true)
        set(v) = setBool("badge_watched", v)

    var badgeYear: Boolean
        get() = bool("badge_year", false)
        set(v) = setBool("badge_year", v)

    // ---- Düzen ----
    /** 0: küçük, 1: orta, 2: büyük */
    var posterSize: Int
        get() = int("poster_size", 1)
        set(v) = setInt("poster_size", v)

    var showTitles: Boolean
        get() = bool("show_titles", true)
        set(v) = setBool("show_titles", v)

    /** 0: otomatik, 1: yan menü, 2: alt menü */
    var navMode: Int
        get() = int("nav_mode", 0)
        set(v) = setInt("nav_mode", v)

    // ---- Performans ----
    var parallelRequests: Int
        get() = int("parallel", 4)
        set(v) = setInt("parallel", v)

    var requestTimeoutSec: Int
        get() = int("timeout", 25)
        set(v) = setInt("timeout", v)

    var homeCache: Boolean
        get() = bool("home_cache", true)
        set(v) = setBool("home_cache", v)

    var httpCache: Boolean
        get() = bool("http_cache", true)
        set(v) = setBool("http_cache", v)

    /** 0: düşük, 1: orta, 2: yüksek görsel kalitesi */
    var imageQuality: Int
        get() = int("img_quality", 1)
        set(v) = setInt("img_quality", v)

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

    // ---- Yedekleme ----
    fun exportAll(): String {
        val out = LinkedHashMap<String, Any?>()
        prefs.all.forEach { (k, v) ->
            out[k] = when (v) {
                is Set<*> -> mapOf("set" to v.toList())
                else -> v
            }
        }
        return mapOf("app" to "RepoPlayer", "version" to 1, "prefs" to out).toJson()
    }

    /** @return geri yüklenen ayar sayısı */
    fun importAll(text: String): Int {
        val root: JsonNode = json.readTree(text)
        val p = root.get("prefs") ?: throw IllegalArgumentException("Geçerli bir RepoPlayer yedeği değil")
        val e = prefs.edit()
        var n = 0
        p.fields().forEach { (k, v) ->
            when {
                v.isObject && v.has("set") -> e.putStringSet(k, v.get("set").map { it.asText() }.toSet())
                v.isBoolean -> e.putBoolean(k, v.asBoolean())
                v.isInt -> e.putInt(k, v.asInt())
                v.isLong -> e.putLong(k, v.asLong())
                v.isNumber -> e.putFloat(k, v.floatValue())
                v.isTextual -> e.putString(k, v.asText())
                else -> return@forEach
            }
            n++
        }
        e.apply()
        return n
    }

    companion object {
        fun progressKey(item: ContentItem, episodeId: String?) =
            "${item.sourceKey}|${item.id}|${episodeId ?: ""}"
    }
}
