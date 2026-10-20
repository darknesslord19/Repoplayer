package com.lagradost.cloudstream3
object CommonActivity {
    var activity: android.app.Activity? = null
    fun showToast(message: String?, duration: Int? = null) { println("    [toast] $message") }
    fun showToast(act: android.app.Activity?, message: String?, duration: Int? = null) { println("    [toast] $message") }
}
