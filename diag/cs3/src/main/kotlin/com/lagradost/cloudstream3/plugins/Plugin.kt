package com.lagradost.cloudstream3.plugins
abstract class Plugin : BasePlugin() {
    open fun load(context: android.content.Context) { load() }
    var resources: Any? = null
    var openSettings: ((context: android.content.Context) -> Unit)? = null
}
