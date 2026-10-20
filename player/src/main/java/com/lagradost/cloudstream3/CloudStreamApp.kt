package com.lagradost.cloudstream3

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.getKeys
import com.lagradost.cloudstream3.utils.DataStore.removeKey
import com.lagradost.cloudstream3.utils.DataStore.removeKeys
import com.lagradost.cloudstream3.utils.DataStore.setKey
import java.lang.ref.WeakReference

/**
 * CloudStream uygulamasının CloudStreamApp sınıfının eklentilerin kullandığı kısmı:
 * bağlam ve anahtar-değer saklama (eklenti ayarları, alan adı geçmişi vb.).
 */
class CloudStreamApp {
    companion object {
        /** Use to get Activity from Context. */
        tailrec fun Context.getActivity(): Activity? {
            return when (this) {
                is Activity -> this
                is ContextWrapper -> baseContext.getActivity()
                else -> null
            }
        }

        private var _context: WeakReference<Context>? = null
        var context
            get() = _context?.get()
            internal set(value) {
                _context = WeakReference(value)
            }

        fun <T : Any> getKeyClass(path: String, valueType: Class<T>): T? {
            return context?.getKey(path, valueType)
        }

        fun <T : Any> setKeyClass(path: String, value: T) {
            context?.setKey(path, value)
        }

        fun removeKeys(folder: String): Int? {
            return context?.removeKeys(folder)
        }

        fun <T> setKey(path: String, value: T) {
            context?.setKey(path, value)
        }

        fun <T> setKey(folder: String, path: String, value: T) {
            context?.setKey(folder, path, value)
        }

        inline fun <reified T : Any> getKey(path: String, defVal: T?): T? {
            return context?.getKey(path, defVal)
        }

        inline fun <reified T : Any> getKey(path: String): T? {
            return context?.getKey(path)
        }

        inline fun <reified T : Any> getKey(folder: String, path: String): T? {
            return context?.getKey(folder, path)
        }

        inline fun <reified T : Any> getKey(folder: String, path: String, defVal: T?): T? {
            return context?.getKey(folder, path, defVal)
        }

        fun getKeys(folder: String): List<String>? {
            return context?.getKeys(folder)
        }

        fun removeKey(folder: String, path: String) {
            context?.removeKey(folder, path)
        }

        fun removeKey(path: String) {
            context?.removeKey(path)
        }

        fun openBrowser(url: String, activity: Activity?) {
            val ctx = activity ?: context ?: return
            runCatching {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    if (ctx !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }
    }
}

/** Eski eklentiler için takma ad (CloudStream'deki AcraApplication) */
class AcraApplication {
    companion object {
        val context get() = CloudStreamApp.context
        fun removeKeys(folder: String): Int? = CloudStreamApp.removeKeys(folder)
        fun <T> setKey(path: String, value: T) = CloudStreamApp.setKey(path, value)
        fun <T> setKey(folder: String, path: String, value: T) = CloudStreamApp.setKey(folder, path, value)
        inline fun <reified T : Any> getKey(path: String, defVal: T?): T? = CloudStreamApp.getKey(path, defVal)
        inline fun <reified T : Any> getKey(path: String): T? = CloudStreamApp.getKey(path)
        inline fun <reified T : Any> getKey(folder: String, path: String): T? = CloudStreamApp.getKey(folder, path)
        inline fun <reified T : Any> getKey(folder: String, path: String, defVal: T?): T? = CloudStreamApp.getKey(folder, path, defVal)
    }
}
