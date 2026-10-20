package com.repoplayer.app

import android.app.Application
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
        sources = SourceManager(this, store)
        JsEngine.init(this)
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
