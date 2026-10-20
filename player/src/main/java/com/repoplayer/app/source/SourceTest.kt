package com.repoplayer.app.source

import com.repoplayer.app.data.ContentItem
import com.repoplayer.app.data.DEFAULT_UA
import com.repoplayer.app.data.Logger
import com.repoplayer.app.data.Net
import com.repoplayer.app.data.SourceEntry
import com.repoplayer.app.data.StreamLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request

/**
 * Bir kaynağı uçtan uca dener: ana sayfa → afişler → detay → bağlantılar → bağlantı açılışı.
 * Her satır günlüğe de yazılır; kullanıcı raporu kopyalayıp paylaşabilir.
 */
class SourceTest(private val manager: SourceManager, private val out: (String) -> Unit) {

    private fun line(s: String) {
        out(s)
        Logger.i("Test", s)
    }

    /** İsteği atar: durum kodu, içerik türü, ilk baytlara göre biçim ve süre */
    private suspend fun probe(url: String, headers: Map<String, String>, image: Boolean): String = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        try {
            val b = Request.Builder().url(url).header("Range", "bytes=0-4095")
            var hasUa = false
            headers.forEach { (k, v) -> if (k.equals("user-agent", true)) hasUa = true; b.header(k, v) }
            if (!hasUa) b.header("User-Agent", runCatching { com.lagradost.cloudstream3.USER_AGENT }.getOrDefault(DEFAULT_UA))
            val client = if (image) Net.imageClient else Net.client
            client.newCall(b.build()).execute().use { r ->
                val head = r.body?.byteStream()?.use { s -> val buf = ByteArray(32); val n = s.read(buf); if (n > 0) String(buf, 0, n, Charsets.ISO_8859_1) else "" } ?: ""
                val sig = when {
                    head.startsWith("#EXTM3U") -> "m3u8"
                    head.startsWith("\u0089PNG") -> "png"
                    head.startsWith("ÿØ") -> "jpg"
                    head.startsWith("RIFF") -> "webp"
                    head.contains("ftypavif") -> "avif"
                    head.contains("ftyp") -> "mp4"
                    head.trimStart().startsWith("<") -> "HTML (video/görsel değil!)"
                    else -> head.take(10).filter { it.code in 32..126 }
                }
                "${r.code} · ${r.header("Content-Type") ?: "?"} · $sig · ${System.currentTimeMillis() - t0} ms"
            }
        } catch (t: Throwable) {
            "HATA ${t.javaClass.simpleName}: ${t.message} · ${System.currentTimeMillis() - t0} ms"
        }
    }

    private fun host(u: String) = runCatching { java.net.URI(u).host }.getOrNull() ?: u.take(40)

    suspend fun run(entry: SourceEntry) {
        line("=== ${entry.name} testi ===")
        val src = try {
            manager.open(entry)
        } catch (t: Throwable) {
            line("✗ Kaynak yüklenemedi: ${t.message}"); return
        }
        line("✓ Kaynak yüklendi")

        // 1) Ana sayfa
        var items: List<ContentItem> = emptyList()
        val t0 = System.currentTimeMillis()
        try {
            val row = withTimeoutOrNull(60_000) { src.home().firstOrNull() }
            if (row == null) line("✗ Ana sayfa: 60 sn içinde satır gelmedi")
            else {
                items = row.items
                val withPoster = items.count { !it.poster.isNullOrBlank() }
                line("✓ Ana sayfa \"${row.title}\": ${items.size} içerik, $withPoster afişli · ${System.currentTimeMillis() - t0} ms")
            }
        } catch (t: Throwable) {
            line("✗ Ana sayfa hatası: ${t.message}")
        }
        if (items.isEmpty()) {
            items = runCatching { withTimeoutOrNull(60_000) { src.search("a") } ?: emptyList() }.getOrDefault(emptyList())
            line("Arama(\"a\"): ${items.size} sonuç")
        }
        if (items.isEmpty()) { line("Test edilecek içerik yok."); return }

        // 2) Afişler
        items.take(4).forEach { it ->
            val p = it.poster
            if (p.isNullOrBlank()) line("• Afiş yok: ${it.title}")
            else {
                line("• Afiş ${it.title}: ${p.take(110)}")
                line("    başlıklar=${it.posterHeaders?.keys ?: "[]"} → ${probe(p, it.posterHeaders ?: emptyMap(), true)}")
            }
        }

        // 3) Detay + bağlantılar (ilk 2 içerik)
        for (item in items.take(2)) {
            val d = try {
                withTimeoutOrNull(60_000) { src.details(item) } ?: run { line("✗ Detay zaman aşımı: ${item.title}"); null }
            } catch (t: Throwable) {
                line("✗ Detay hatası (${item.title}): ${t.message}"); null
            } ?: continue
            val ep = d.episodes.firstOrNull()
            line("✓ Detay ${d.title}: ${if (d.isSeries) "${d.episodes.size} bölüm" else "film"}")
            val links = mutableListOf<StreamLink>()
            var subs = 0
            val t1 = System.currentTimeMillis()
            try {
                withTimeoutOrNull(90_000) {
                    src.links(d, ep, { synchronized(links) { links += it } }, { subs++ })
                } ?: line("  bağlantı arama 90 sn'de bitmedi")
            } catch (t: Throwable) {
                line("✗ Bağlantı hatası: ${t.message}")
            }
            line("${if (links.isEmpty()) "✗" else "✓"} ${d.title}${ep?.let { " · ${it.name}" } ?: ""}: ${links.size} bağlantı, $subs altyazı · ${(System.currentTimeMillis() - t1) / 1000} sn")
            links.take(4).forEach { l ->
                line("• ${l.quality ?: ""} ${l.name.take(40)} · ${l.type} · ${host(l.url)}")
                line("    başlıklı: ${probe(l.url, l.headers, false)}")
                if (l.headers.isNotEmpty()) line("    başlıksız: ${probe(l.url, emptyMap(), false)}")
            }
            if (links.isNotEmpty()) break
        }
        line("=== test bitti ===")
    }
}
