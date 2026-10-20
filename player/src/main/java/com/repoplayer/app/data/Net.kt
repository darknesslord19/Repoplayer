package com.repoplayer.app.data

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

const val DEFAULT_UA =
    "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

val json = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

inline fun <reified T> String.parse(): T = json.readValue(this)
fun Any.toJson(): String = json.writeValueAsString(this)

class HttpResult(
    val code: Int,
    val message: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String,
)

/** DNS-over-HTTPS sağlayıcıları (İSS engellerini aşmak için) */
val DNS_PROVIDERS = listOf(
    Triple("Sistem", "", emptyList()),
    Triple("Cloudflare", "https://cloudflare-dns.com/dns-query", listOf("1.1.1.1", "1.0.0.1")),
    Triple("Google", "https://dns.google/dns-query", listOf("8.8.8.8", "8.8.4.4")),
    Triple("AdGuard", "https://dns.adguard.com/dns-query", listOf("94.140.14.140", "94.140.14.141")),
    Triple("Quad9", "https://dns.quad9.net/dns-query", listOf("9.9.9.9", "149.112.112.112")),
)

object Net {
    private var cacheDir: File? = null
    private var timeoutSec = 25L
    private var useCache = true
    private var dnsIndex = 1

    /** Uygulama açılışında ayarlarla yapılandırılır */
    fun init(cache: File, timeoutSeconds: Int, httpCache: Boolean, dns: Int) {
        cacheDir = cache
        timeoutSec = timeoutSeconds.toLong().coerceIn(5, 120)
        useCache = httpCache
        dnsIndex = dns
    }

    private fun OkHttpClient.Builder.applyDns(): OkHttpClient.Builder {
        val (_, url, ips) = DNS_PROVIDERS.getOrNull(dnsIndex) ?: return this
        if (url.isEmpty()) return this
        return runCatching {
            val bootstrap = OkHttpClient.Builder().build()
            dns(
                DnsOverHttps.Builder()
                    .client(bootstrap)
                    .url(url.toHttpUrl())
                    .bootstrapDnsHosts(ips.map { InetAddress.getByName(it) })
                    .build()
            )
        }.getOrElse { this }
    }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(timeoutSec.coerceAtMost(20), TimeUnit.SECONDS)
            .readTimeout(timeoutSec + 15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .applyDns()
            .apply {
                val dir = cacheDir
                if (useCache && dir != null) cache(Cache(File(dir, "http"), 50L * 1024 * 1024))
            }
            .build()
    }

    /** SSL denetimi yapmayan istemci (yalnızca eklentilerin insecureApp'i için) */
    val insecureClient: OkHttpClient by lazy {
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(trustAll), SecureRandom()) }
        client.newBuilder()
            .sslSocketFactory(ssl.socketFactory, trustAll)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    /** Afiş yükleyici: tarayıcı kimliği ekler */
    val imageClient: OkHttpClient by lazy {
        client.newBuilder()
            .addInterceptor { chain ->
                val r = chain.request()
                chain.proceed(if (r.header("User-Agent") == null) r.newBuilder().header("User-Agent", DEFAULT_UA).build() else r)
            }
            .build()
    }

    /** CloudStream eklentilerinin kullandığı ağ istemcilerini aynı DNS/önbellek ayarlarıyla kurar */
    fun configureCloudstream() {
        runCatching { com.lagradost.cloudstream3.app.baseClient = client }
            .onFailure { Logger.e("Net", "Eklenti ağ istemcisi ayarlanamadı", it) }
        runCatching { com.lagradost.cloudstream3.insecureApp.baseClient = insecureClient }
    }

    suspend fun request(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
    ): HttpResult = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url)
        var hasUa = false
        var contentType: String? = null
        headers.forEach { (k, v) ->
            if (k.equals("user-agent", true)) hasUa = true
            if (k.equals("content-type", true)) contentType = v
            builder.header(k, v)
        }
        if (!hasUa) builder.header("User-Agent", DEFAULT_UA)
        val m = method.uppercase()
        val reqBody = when {
            body != null -> body.toRequestBody((contentType ?: "text/plain; charset=utf-8").toMediaTypeOrNull())
            m == "POST" || m == "PUT" || m == "PATCH" -> ByteArray(0).toRequestBody(null)
            else -> null
        }
        builder.method(m, reqBody)
        client.newCall(builder.build()).execute().use { resp ->
            HttpResult(
                code = resp.code,
                message = resp.message,
                url = resp.request.url.toString(),
                headers = resp.headers.toMultimap().mapValues { it.value.joinToString(", ") },
                body = resp.body?.string() ?: "",
            )
        }
    }

    suspend fun text(url: String, headers: Map<String, String> = emptyMap()): String {
        val r = request(url, headers = headers)
        if (r.code !in 200..299) throw IOException("HTTP ${r.code}: $url")
        return r.body
    }

    suspend fun jsonTree(url: String): JsonNode = json.readTree(text(url))

    suspend fun download(url: String, target: File) = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", DEFAULT_UA).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}: $url")
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, target.name + ".part")
            resp.body!!.byteStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (target.exists()) {
                target.setWritable(true)
                target.delete()
            }
            if (!tmp.renameTo(target)) throw IOException("Dosya yazılamadı: $target")
        }
    }
}

/** Göreli yolu manifest adresine göre çözer */
fun resolveUrl(base: String, path: String): String {
    if (path.startsWith("http://") || path.startsWith("https://")) return path
    return try {
        java.net.URI(base).resolve(path.replace(" ", "%20")).toString()
    } catch (_: Throwable) {
        base.substringBeforeLast('/') + "/" + path.removePrefix("/")
    }
}

fun JsonNode?.str(field: String): String? =
    this?.get(field)?.takeIf { !it.isNull && !it.isMissingNode }?.asText()?.takeIf { it.isNotBlank() }

fun JsonNode?.int(field: String): Int? =
    this?.get(field)?.takeIf { it.isNumber || (it.isTextual && it.asText().toIntOrNull() != null) }?.asInt()
