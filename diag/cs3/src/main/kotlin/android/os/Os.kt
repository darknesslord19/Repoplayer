package android.os
class Looper { companion object { @JvmStatic fun getMainLooper() = Looper() } }
open class Handler(l: Looper) { fun post(r: Runnable): Boolean { r.run(); return true } }
