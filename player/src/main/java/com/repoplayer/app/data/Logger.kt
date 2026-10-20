package com.repoplayer.app.data

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Uygulama içi günlük (Ayarlar > Geliştirici > Günlük) */
object Logger {
    data class Line(val time: Long, val level: Char, val tag: String, val message: String)

    private val lines = ArrayDeque<Line>()
    private const val MAX = 400
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Synchronized
    private fun add(level: Char, tag: String, msg: String) {
        lines.addLast(Line(System.currentTimeMillis(), level, tag, msg))
        while (lines.size > MAX) lines.removeFirst()
    }

    fun i(tag: String, msg: String) {
        Log.d(tag, msg)
        add('I', tag, msg)
    }

    fun w(tag: String, msg: String) {
        Log.w(tag, msg)
        add('W', tag, msg)
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        Log.e(tag, msg, t)
        val detail = t?.let { e ->
            val cause = generateSequence(e) { it.cause }.last()
            val where = cause.stackTrace.firstOrNull()?.let { " @ ${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" } ?: ""
            ": ${cause.javaClass.simpleName}: ${cause.message ?: ""}$where"
        } ?: ""
        add('E', tag, msg + detail)
    }

    @Synchronized
    fun all(): List<Line> = lines.toList().asReversed()

    @Synchronized
    fun clear() = lines.clear()

    fun format(l: Line) = "${fmt.format(Date(l.time))} ${l.level}/${l.tag}: ${l.message}"
}
