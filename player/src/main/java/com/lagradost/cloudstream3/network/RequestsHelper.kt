package com.lagradost.cloudstream3.network

import android.content.Context
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.nicehttp.Requests
import okhttp3.Headers
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient

/*
 * CloudStream uygulamasındaki RequestsHelper.kt'nin karşılığı (eklentiler bu fonksiyonları çağırır).
 * İstemci kurulumu RepoPlayer'ın ortak ağ istemcisine (DNS-over-HTTPS, önbellek, WebView çerezleri) bağlanır.
 */

fun Requests.initClient(context: Context) {
    this.baseClient = buildDefaultClient(context)
}

fun Requests.initClient(context: Context, ignoreSSL: Boolean = false) {
    this.baseClient = buildDefaultClient(context, ignoreSSL)
}

fun buildDefaultClient(context: Context): OkHttpClient = buildDefaultClient(context, false)

fun buildDefaultClient(context: Context, ignoreSSL: Boolean = false): OkHttpClient =
    if (ignoreSSL) com.repoplayer.app.data.Net.insecureClient else com.repoplayer.app.data.Net.client

private val DEFAULT_HEADERS = mapOf("user-agent" to USER_AGENT)

/** Set headers > Set cookies > Default headers > Default Cookies */
fun getHeaders(
    headers: Map<String, String>,
    cookie: Map<String, String>
): Headers {
    val cookieMap =
        if (cookie.isNotEmpty()) mapOf(
            "Cookie" to cookie.entries.joinToString(" ") {
                "${it.key}=${it.value};"
            }) else mapOf()
    val tempHeaders = (DEFAULT_HEADERS + headers + cookieMap)
    return tempHeaders.toHeaders()
}
