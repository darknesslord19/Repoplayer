package android.widget
import android.content.Context
open class TextView(c: Context?) : android.view.View(c) {
    fun setTextColor(c: Int) {}
    fun setHintTextColor(c: Int) {}
    open fun setText(t: CharSequence?) {}
}
open class Button(c: Context?) : TextView(c)
open class EditText(c: Context?) : TextView(c) {
    fun getText(): android.text.Editable? = null
    fun setError(e: CharSequence?) {}
    fun setInputType(t: Int) {}
    fun setSelection(i: Int) {}
    fun setSingleLine(b: Boolean) {}
}
open class FrameLayout(c: Context?) : android.view.ViewGroup(c) { open class LayoutParams(w: Int, h: Int) : android.view.ViewGroup.LayoutParams(w, h) }
class Toast { fun show() {}; companion object { @JvmStatic fun makeText(c: Context?, t: CharSequence?, d: Int): Toast { println("    [toast] $t"); return Toast() } } }
