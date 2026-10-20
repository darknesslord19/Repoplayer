package android.content

import android.content.res.Resources

/** JVM taklidi: eklentilerin kullandığı kadarı */
open class Context {
    private val prefs = HashMap<String, SharedPreferences>()
    open fun getApplicationContext(): Context = this
    open fun getResources(): Resources = Resources()
    open fun getSharedPreferences(name: String, mode: Int): SharedPreferences = prefs.getOrPut(name) { MemPrefs() }
}

interface SharedPreferences {
    fun getString(key: String, def: String?): String?
    fun getStringSet(key: String, def: Set<String>?): Set<String>?
    fun getBoolean(key: String, def: Boolean): Boolean
    fun getInt(key: String, def: Int): Int
    fun edit(): Editor
    interface Editor {
        fun putString(key: String, v: String?): Editor
        fun putStringSet(key: String, v: Set<String>?): Editor
        fun putBoolean(key: String, v: Boolean): Editor
        fun putInt(key: String, v: Int): Editor
        fun remove(key: String): Editor
        fun apply()
        fun commit(): Boolean
    }
}

class MemPrefs : SharedPreferences {
    val map = HashMap<String, Any?>()
    @Suppress("UNCHECKED_CAST")
    override fun getString(key: String, def: String?) = map[key] as? String ?: def
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, def: Set<String>?) = map[key] as? Set<String> ?: def
    override fun getBoolean(key: String, def: Boolean) = map[key] as? Boolean ?: def
    override fun getInt(key: String, def: Int) = map[key] as? Int ?: def
    override fun edit() = object : SharedPreferences.Editor {
        override fun putString(key: String, v: String?) = apply { map[key] = v }
        override fun putStringSet(key: String, v: Set<String>?) = apply { map[key] = v }
        override fun putBoolean(key: String, v: Boolean) = apply { map[key] = v }
        override fun putInt(key: String, v: Int) = apply { map[key] = v }
        override fun remove(key: String) = apply { map.remove(key) }
        override fun apply() {}
        override fun commit() = true
    }
}
