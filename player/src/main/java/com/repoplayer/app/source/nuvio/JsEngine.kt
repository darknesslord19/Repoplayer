package com.repoplayer.app.source.nuvio

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.fasterxml.jackson.databind.JsonNode
import com.repoplayer.app.data.Net
import com.repoplayer.app.data.json
import com.repoplayer.app.data.str
import com.repoplayer.app.data.toJson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Nuvio sağlayıcılarını (JS) gizli bir WebView içinde çalıştırır.
 * WebView modern JS'i (async/await, generator...) eksiksiz destekler;
 * fetch istekleri NativeBridge ile OkHttp'ye yönlendirilir (CORS yok).
 */
@SuppressLint("SetJavaScriptEnabled", "StaticFieldLeak")
object JsEngine {
    private const val TAG = "JsEngine"
    private lateinit var appContext: Context
    private var webView: WebView? = null
    private var ready = CompletableDeferred<Unit>()
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val fetchResults = ConcurrentHashMap<String, String>()
    private val codes = ConcurrentHashMap<String, String>()
    private val runs = ConcurrentHashMap<String, CompletableDeferred<String>>()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun create() {
        val runtime = buildString {
            runCatching { append(appContext.assets.open("js/libs.js").bufferedReader().readText()) }
                .onFailure { Log.e(TAG, "libs.js bulunamadı (cheerio/crypto-js olmadan devam)", it) }
            append("\n;\n")
            append(appContext.assets.open("js/runtime.js").bufferedReader().readText())
        }
        val wv = WebView(appContext)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.blockNetworkImage = true
        wv.addJavascriptInterface(Bridge(), "NativeBridge")
        wv.webViewClient = object : WebViewClient() {
            private var injected = false
            override fun onPageFinished(view: WebView, url: String?) {
                if (injected) return
                injected = true
                view.evaluateJavascript(runtime) { ready.complete(Unit) }
            }
        }
        wv.loadDataWithBaseURL("https://repoplayer.local/", "<html><body></body></html>", "text/html", "utf-8", null)
        webView = wv
    }

    private suspend fun ensure() {
        withContext(Dispatchers.Main) {
            if (webView == null) {
                ready = CompletableDeferred()
                create()
            }
        }
        withTimeout(20_000) { ready.await() }
    }

    /**
     * Bir sağlayıcının getStreams(...) fonksiyonunu çalıştırır.
     * @param key sağlayıcı kimliği+sürümü; aynı anahtar için kod bir kez derlenir.
     * @return getStreams'in döndürdüğü dizi
     */
    /**
     * Sağlayıcının bir fonksiyonunu çağırır (getStreams, getCatalog, search, comments).
     * @param key sağlayıcı kimliği+sürümü; aynı anahtar için kod bir kez derlenir.
     * @return fonksiyonun döndürdüğü değer (yoksa NullNode)
     */
    suspend fun call(
        key: String,
        code: String,
        function: String,
        args: List<Any?>,
        timeoutMs: Long,
        scraperId: String? = null,
        tmdbKey: String? = null,
    ): JsonNode {
        ensure()
        val runId = UUID.randomUUID().toString()
        val done = CompletableDeferred<String>()
        runs[runId] = done
        codes[runId] = code
        try {
            val ctx = mapOf("scraperId" to scraperId, "tmdbKey" to tmdbKey?.takeIf { it.isNotBlank() }).toJson()
            val call = "__rp.call(${JSONObject.quote(runId)}, ${JSONObject.quote(key)}, ${JSONObject.quote(function)}, ${args.toJson()}, $ctx)"
            withContext(Dispatchers.Main) { webView?.evaluateJavascript(call, null) }
            val result = json.readTree(withTimeout(timeoutMs) { done.await() })
            if (result.get("ok")?.asBoolean() != true) {
                throw IllegalStateException(result.str("value") ?: "Bilinmeyen JS hatası")
            }
            return result.get("value") ?: json.nullNode()
        } finally {
            runs.remove(runId)
            codes.remove(runId)
        }
    }

    suspend fun getStreams(key: String, code: String, args: List<Any?>, timeoutMs: Long, scraperId: String? = null, tmdbKey: String? = null): JsonNode =
        call(key, code, "getStreams", args, timeoutMs, scraperId, tmdbKey).takeIf { it.isArray } ?: json.createArrayNode()

    private fun js(script: String) = main.post { webView?.evaluateJavascript(script, null) }

    @Suppress("unused")
    class Bridge {
        @JavascriptInterface
        fun fetch(id: String, request: String) {
            scope.launch {
                val out = try {
                    val r = json.readTree(request)
                    val headers = HashMap<String, String>()
                    r.get("headers")?.fields()?.forEach { (k, v) -> headers[k] = v.asText() }
                    val res = Net.request(
                        url = r.str("url") ?: "",
                        method = r.str("method") ?: "GET",
                        headers = headers,
                        body = r.get("body")?.takeIf { !it.isNull }?.asText(),
                        followRedirects = r.get("followRedirects")?.asBoolean() ?: true,
                    )
                    mapOf(
                        "status" to res.code,
                        "statusText" to res.message,
                        "url" to res.url,
                        "headers" to res.headers,
                        "body" to res.body,
                        "bodyBase64" to res.bodyBase64,
                        "redirected" to res.redirected,
                    ).toJson()
                } catch (t: Throwable) {
                    mapOf("error" to (t.message ?: t.javaClass.simpleName)).toJson()
                }
                fetchResults[id] = out
                js("__rp.onFetch(${JSONObject.quote(id)})")
            }
        }

        @JavascriptInterface
        fun take(id: String): String = fetchResults.remove(id) ?: "{\"error\":\"yanıt yok\"}"

        @JavascriptInterface
        fun code(runId: String): String = codes[runId] ?: ""

        @JavascriptInterface
        fun done(runId: String, payload: String) {
            runs[runId]?.complete(payload)
        }

        @JavascriptInterface
        fun log(message: String) {
            Log.d(TAG, message)
        }
    }
}
