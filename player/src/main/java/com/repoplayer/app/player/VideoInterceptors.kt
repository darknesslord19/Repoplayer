package com.repoplayer.app.player

import okhttp3.Interceptor
import java.util.concurrent.ConcurrentHashMap

/**
 * CloudStream'deki MainAPI.getVideoInterceptor(link) karşılığı: eklenti bazı video
 * bağlantıları için istekleri değiştiren (çerez/token ekleyen) bir yakalayıcı verir.
 * Bağlantı bulunurken kaydedilir, oynatıcı aynı adres için kullanır.
 */
object VideoInterceptors {
    private val map = ConcurrentHashMap<String, Interceptor>()

    fun register(url: String, interceptor: Interceptor) {
        if (map.size > 200) map.clear()
        map[url] = interceptor
    }

    fun get(url: String): Interceptor? = map[url]
}
