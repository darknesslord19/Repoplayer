package android.text
interface Editable : CharSequence
open class SpannableString(private val s: CharSequence) : CharSequence by s { fun setSpan(what: Any?, start: Int, end: Int, flags: Int) {} }
