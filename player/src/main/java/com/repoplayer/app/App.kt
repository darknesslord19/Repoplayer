package com.repoplayer.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.EventListener
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.repoplayer.app.data.HomeCache
import com.repoplayer.app.data.Logger
import com.repoplayer.app.data.Net
import com.repoplayer.app.data.Store
import com.repoplayer.app.source.SourceManager
import com.repoplayer.app.source.nuvio.JsEngine

class App : Application(), SingletonImageLoader.Factory {
    lateinit var store: Store
        private set
    lateinit var sources: SourceManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        // CloudStream kütüphanesi bazı çıkarıcılar (WebView vb.) için bağlam ister
        com.lagradost.api.setContext(this)
        // Eklentilerin ayar/alan adı saklamak için kullandığı CloudStreamApp.context
        com.lagradost.cloudstream3.CloudStreamApp.context = this
        // CloudStream gibi: modern TLS (Conscrypt) — eski cihazlarda ve bazı CDN'lerde bağlantı sorunlarını giderir
        runCatching { java.security.Security.insertProviderAt(org.conscrypt.Conscrypt.newProvider(), 1) }
        store = Store(this)
        Net.init(cacheDir, store.requestTimeoutSec, store.httpCache, store.dns)
        Net.configureCloudstream()
        HomeCache.init(cacheDir)
        // Yeni sürümde önbellekteki eski afiş/başlık bilgileri kullanılmasın
        val prefs = getSharedPreferences("repoplayer_meta", MODE_PRIVATE)
        if (prefs.getString("last_version", null) != versionName) {
            HomeCache.clear()
            prefs.edit().putString("last_version", versionName).apply()
        }
        sources = SourceManager(this, store)
        JsEngine.init(this)
        Logger.i("App", "RepoPlayer başlatıldı · ${store.repos.size} repo, ${sources.entries().size} kaynak, DNS: ${com.repoplayer.app.data.DNS_PROVIDERS.getOrNull(store.dns)?.first}")
    }

    /** Afişler eklentilerle aynı ağ istemcisi (DNS-over-HTTPS) üzerinden yüklenir */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { Net.imageClient })) }
            .crossfade(true)
            .eventListener(object : EventListener() {
                private var logged = 0
                override fun onError(request: ImageRequest, result: ErrorResult) {
                    if (logged++ < 60) Logger.w("Görsel", "${request.data} → ${result.throwable.javaClass.simpleName}: ${result.throwable.message}")
                }
            })
            .build()

    val versionName: String
        get() = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"

    companion object {
        lateinit var instance: App
            private set
    }
}
