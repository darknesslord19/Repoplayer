package diag

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import java.io.File
import java.net.URLClassLoader
import java.util.zip.ZipFile

/**
 * Kullanım: Main <cs3-klasörü> <jar-klasörü> <rapor.md>
 * Her .cs3 için: yükle → ana sayfa → afiş denemesi → detay → bağlantılar → bağlantı denemesi
 */
private val out = StringBuilder()
private fun p(s: String = "") { println(s); out.appendLine(s) }

private const val UA = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

private fun short(s: String?, n: Int = 110) = s?.let { if (it.length > n) it.take(n) + "…" else it } ?: "—"

private fun err(t: Throwable): String {
    val root = generateSequence(t) { it.cause }.last()
    val at = root.stackTrace.firstOrNull { !it.className.startsWith("java.") && !it.className.startsWith("kotlin.") }
    return "${root.javaClass.simpleName}: ${short(root.message, 160)}" + (at?.let { " @ ${it.className.substringAfterLast('.')}.${it.methodName}" } ?: "")
}

/** Adrese istek atıp durum kodu ve içerik türünü döndürür (en fazla 4 KB okunur) */
private fun probe(url: String, headers: Map<String, String>): String = try {
    val b = Request.Builder().url(url).header("Range", "bytes=0-4095")
    var hasUa = false
    headers.forEach { (k, v) -> if (k.equals("user-agent", true)) hasUa = true; b.header(k, v) }
    if (!hasUa) b.header("User-Agent", UA)
    app.baseClient.newCall(b.build()).execute().use { r ->
        val type = r.header("Content-Type") ?: "?"
        val head = r.body?.byteStream()?.use { s -> ByteArray(64).let { buf -> val n = s.read(buf); if (n > 0) String(buf, 0, n, Charsets.ISO_8859_1) else "" } } ?: ""
        val sig = when {
            head.startsWith("#EXTM3U") -> "m3u8"
            head.startsWith("\u0089PNG") -> "png"
            head.startsWith("ÿØ") -> "jpg"
            head.startsWith("RIFF") -> "webp"
            head.trimStart().startsWith("<") -> "HTML!"
            head.contains("ftyp") -> "mp4"
            else -> head.take(12).filter { it.code in 32..126 }
        }
        "${r.code} $type [$sig]"
    }
} catch (t: Throwable) {
    "HATA ${err(t)}"
}

private fun posterCheck(sr: SearchResponse, api: MainAPI) {
    val url = sr.posterUrl
    if (url.isNullOrBlank()) { p("  - afiş YOK · ${short(sr.name, 40)}"); return }
    val ph = sr.posterHeaders ?: emptyMap()
    val ref = mapOf("Referer" to api.mainUrl.trimEnd('/') + "/")
    p("  - ${short(sr.name, 30)} → `${short(url, 90)}`")
    p("      başlıksız: ${probe(url, emptyMap())} · Referer: ${probe(url, ref)}" + if (ph.isNotEmpty()) " · posterHeaders${ph.keys}: ${probe(url, ph)}" else "")
}

private fun linkCheck(l: ExtractorLink) {
    val h = l.getAllHeaders()
    p("  - **${short(l.name, 40)}** · ${l.type} · q=${l.quality} · `${short(l.url, 90)}`")
    p("      başlıklar=${h.keys} · başlıklı: ${probe(l.url, h)} · başlıksız: ${probe(l.url, emptyMap())}")
}

private suspend fun testApi(api: MainAPI) {
    p("### ${api.name}  (${api.mainUrl}) · ana sayfa=${api.hasMainPage}")
    var sample: List<SearchResponse> = emptyList()
    if (api.hasMainPage) {
        val page = api.mainPage.firstOrNull()
        val t0 = System.currentTimeMillis()
        val res = runCatching { withTimeoutOrNull(60_000) { api.getMainPage(1, MainPageRequest(page?.name ?: "", page?.data ?: "", page?.horizontalImages ?: false)) } }
        res.onFailure { p("- getMainPage HATA: ${err(it)}") }
        val hp = res.getOrNull()
        if (hp == null && res.isSuccess) p("- getMainPage: zaman aşımı / boş")
        hp?.let {
            p("- getMainPage(\"${page?.name}\"): ${it.items.size} liste, ${it.items.sumOf { l -> l.list.size }} öğe · ${System.currentTimeMillis() - t0} ms")
            sample = it.items.flatMap { l -> l.list }
        }
    }
    if (sample.isEmpty()) {
        val s = runCatching { withTimeoutOrNull(60_000) { api.search("a", 1)?.items ?: api.search("a") } }
        s.onFailure { p("- search HATA: ${err(it)}") }
        sample = s.getOrNull() ?: emptyList()
        p("- search(\"a\"): ${sample.size} sonuç")
    }
    if (sample.isEmpty()) return
    p("- Afiş denemesi:")
    sample.take(3).forEach { posterCheck(it, api) }

    // Detay + bağlantılar: ilk 2 içeriği dene
    for (sr in sample.take(2)) {
        p("- load(\"${short(sr.name, 40)}\") `${short(sr.url, 80)}`")
        val lr = runCatching { withTimeoutOrNull(60_000) { api.load(sr.url) } }
        lr.onFailure { p("  load HATA: ${err(it)}") }
        val r: LoadResponse = lr.getOrNull() ?: run { if (lr.isSuccess) p("  load: boş/zaman aşımı"); null } ?: continue
        val data = when (r) {
            is MovieLoadResponse -> r.dataUrl
            is TvSeriesLoadResponse -> r.episodes.firstOrNull()?.data.also { p("  dizi: ${r.episodes.size} bölüm") }
            is AnimeLoadResponse -> r.episodes.values.flatten().firstOrNull()?.data.also { p("  anime: ${r.episodes.values.sumOf { it.size }} bölüm") }
            else -> r.url
        }
        p("  tür=${r.javaClass.simpleName} · afiş=`${short(r.posterUrl, 70)}` · posterHeaders=${r.posterHeaders?.keys}")
        if (data == null) { p("  oynatma verisi yok"); continue }
        val links = mutableListOf<ExtractorLink>()
        var subs = 0
        val t0 = System.currentTimeMillis()
        val ok = runCatching {
            withTimeoutOrNull(90_000) { api.loadLinks(data, false, { subs++ }, { synchronized(links) { links += it } }) }
        }
        ok.onFailure { p("  loadLinks HATA: ${err(it)}") }
        p("  loadLinks=${ok.getOrNull()} · ${links.size} bağlantı, $subs altyazı · ${System.currentTimeMillis() - t0} ms")
        links.take(4).forEach { linkCheck(it) }
        if (links.isNotEmpty()) break
    }
}

fun main(args: Array<String>) = runBlocking {
    val cs3Dir = File(args[0])
    val jarDir = File(args[1])
    val report = File(args[2])
    app.baseClient = app.baseClient.newBuilder().followRedirects(true).build()
    p("# CS3 eklenti teşhis raporu")
    p("Çalıştırma: GitHub Actions (ABD) · ${java.time.Instant.now()}")
    p()
    val mapper = jacksonObjectMapper()
    for (cs3 in cs3Dir.listFiles { f -> f.name.endsWith(".cs3") }!!.sortedBy { it.name }) {
        val name = cs3.nameWithoutExtension
        p("## $name")
        val jar = File(jarDir, "$name.jar")
        try {
            val manifest = ZipFile(cs3).use { z -> mapper.readTree(z.getInputStream(z.getEntry("manifest.json")).readBytes()) }
            val cls = manifest.get("pluginClassName").asText()
            val loader = URLClassLoader(arrayOf(jar.toURI().toURL()), Thread.currentThread().contextClassLoader)
            val inst = loader.loadClass(cls).getDeclaredConstructor().newInstance() as BasePlugin
            inst.filename = jar.absolutePath
            if (inst is Plugin) inst.load(android.content.Context()) else inst.load()
            val apis = APIHolder.allProviders.filter { it.sourcePlugin == jar.absolutePath }
            p("- yüklendi: ${apis.joinToString { it.name }}")
            for (api in apis) {
                try { testApi(api) } catch (t: Throwable) { p("- TEST HATA: ${err(t)}") }
            }
        } catch (t: Throwable) {
            p("- YÜKLEME HATA: ${err(t)}")
        }
        p()
        report.writeText(out.toString())
    }
    report.writeText(out.toString())
    kotlin.system.exitProcess(0)
}
