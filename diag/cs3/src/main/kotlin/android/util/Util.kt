package android.util

class DisplayMetrics { @JvmField var density = 2f; @JvmField var widthPixels = 1080; @JvmField var heightPixels = 1920 }

object Log {
    @JvmStatic fun d(t: String?, m: String?): Int { println("    [log/$t] $m"); return 0 }
    @JvmStatic fun i(t: String?, m: String?): Int { println("    [log/$t] $m"); return 0 }
    @JvmStatic fun w(t: String?, m: String?): Int { println("    [log/$t] $m"); return 0 }
    @JvmStatic fun e(t: String?, m: String?): Int { println("    [log/$t] $m"); return 0 }
    @JvmStatic fun e(t: String?, m: String?, e: Throwable?): Int { println("    [log/$t] $m $e"); return 0 }
}

object Base64 {
    const val DEFAULT = 0
    const val NO_PADDING = 1
    const val NO_WRAP = 2
    const val URL_SAFE = 8
    private fun dec(flags: Int) = if (flags and URL_SAFE != 0) java.util.Base64.getUrlDecoder() else java.util.Base64.getMimeDecoder()
    @JvmStatic fun decode(s: String, flags: Int): ByteArray = dec(flags).decode(s.trim())
    @JvmStatic fun decode(b: ByteArray, flags: Int): ByteArray = dec(flags).decode(b)
    @JvmStatic fun encodeToString(b: ByteArray, flags: Int): String {
        var enc = if (flags and URL_SAFE != 0) java.util.Base64.getUrlEncoder() else java.util.Base64.getEncoder()
        if (flags and NO_PADDING != 0) enc = enc.withoutPadding()
        return enc.encodeToString(b)
    }
    @JvmStatic fun encode(b: ByteArray, flags: Int): ByteArray = encodeToString(b, flags).toByteArray()
}
