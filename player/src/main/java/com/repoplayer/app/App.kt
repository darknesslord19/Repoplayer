package com.repoplayer.app

import android.app.Application
import com.repoplayer.app.data.HomeCache
import com.repoplayer.app.data.Logger
import com.repoplayer.app.data.Net
import com.repoplayer.app.data.Store
import com.repoplayer.app.source.SourceManager
import com.repoplayer.app.source.nuvio.JsEngine

class App : Application() {
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
        Net.init(cacheDir, store.requestTimeoutSec, store.httpCache)
        HomeCache.init(cacheDir)
        sources = SourceManager(this, store)
        JsEngine.init(this)
        Logger.i("App", "RepoPlayer başlatıldı · ${store.repos.size} repo, ${sources.entries().size} kaynak")
    }

    val versionName: String
        get() = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"

    companion object {
        lateinit var instance: App
            private set
    }
}
