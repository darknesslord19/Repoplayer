package com.lagradost.cloudstream3.plugins

import android.content.Context
import android.content.res.Resources

/**
 * .cs3 eklentilerinin miras aldığı sınıf. Asıl CloudStream uygulamasındaki
 * `Plugin` sınıfıyla aynı paket/ad/imzaya sahiptir; böylece derlenmiş eklentiler
 * bu uygulamada da yüklenebilir. registerMainAPI / registerExtractorAPI
 * BasePlugin'den (CloudStream kütüphanesi) gelir.
 */
abstract class Plugin : BasePlugin() {
    @Throws(Throwable::class)
    open fun load(context: Context) {
        load()
    }

    /** requiresResources=true olan eklentiler için kaynaklar */
    var resources: Resources? = null

    /** Eklenti ayar ekranı (bu uygulamada isteğe bağlı olarak açılır) */
    var openSettings: ((context: Context) -> Unit)? = null
}
