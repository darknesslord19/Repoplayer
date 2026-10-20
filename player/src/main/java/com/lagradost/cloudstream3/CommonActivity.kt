package com.lagradost.cloudstream3

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * Uyumluluk katmanı: CloudStream uygulamasındaki CommonActivity'nin
 * eklentilerin en sık kullandığı kısmı (aktif Activity ve toast).
 */
object CommonActivity {
    private var ref: java.lang.ref.WeakReference<Activity>? = null

    var activity: Activity?
        get() = ref?.get()
        set(value) {
            ref = value?.let { java.lang.ref.WeakReference(it) }
        }

    private val main = Handler(Looper.getMainLooper())

    fun showToast(message: String?, duration: Int? = null) = showToast(activity, message, duration)

    fun showToast(act: Activity?, message: String?, duration: Int? = null) {
        val a = act ?: activity ?: return
        if (message.isNullOrBlank()) return
        main.post { runCatching { Toast.makeText(a, message, duration ?: Toast.LENGTH_SHORT).show() } }
    }
}
