package com.repoplayer.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
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
        store = Store(this)
        Net.init(cacheDir, store.requestTimeoutSec, store.httpCache, store.dns)
        Net.configureCloudstream()
        HomeCache.init(cacheDir)
        sources = SourceManager(this, store)
        JsEngine.init(this)
        Logger.i("App", "RepoPlayer başlatıldı · ${store.repos.size} repo, ${sources.entries().size} kaynak, DNS: ${com.repoplayer.app.data.DNS_PROVIDERS.getOrNull(store.dns)?.first}")
    }

    /** Afişler eklentilerle aynı ağ istemcisi (DNS-over-HTTPS) üzerinden yüklenir */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { Net.imageClient })) }
            .crossfade(true)
            .build()

    val versionName: String
        get() = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"

    companion object {
        lateinit var instance: App
            private set
    }
}
