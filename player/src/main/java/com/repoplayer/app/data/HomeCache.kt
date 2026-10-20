package com.repoplayer.app.data

import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import java.security.MessageDigest

/** Ana sayfa satırlarını diske yazar; kaynak açılırken anında gösterilir, arkada yenilenir */
object HomeCache {
    private var dir: File? = null
    private val memory = HashMap<String, List<Row>>()

    fun init(cacheDir: File) {
        dir = File(cacheDir, "home").apply { mkdirs() }
    }

    private fun file(key: String): File? {
        val name = MessageDigest.getInstance("MD5").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return dir?.let { File(it, "$name.json") }
    }

    @Synchronized
    fun get(key: String): List<Row>? = memory[key] ?: runCatching {
        file(key)?.takeIf { it.exists() }?.readText()?.let { json.readValue<List<Row>>(it) }
    }.getOrNull()?.also { memory[key] = it }

    @Synchronized
    fun put(key: String, rows: List<Row>) {
        if (rows.isEmpty()) return
        memory[key] = rows
        runCatching { file(key)?.let { it.parentFile?.mkdirs(); it.writeText(rows.toJson()) } }
    }

    @Synchronized
    fun clear() {
        memory.clear()
        dir?.listFiles()?.forEach { it.delete() }
    }
}
