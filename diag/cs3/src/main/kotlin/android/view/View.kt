package android.view
open class View(c: android.content.Context?) {
    fun interface OnClickListener { fun onClick(v: View?) }
    fun interface OnFocusChangeListener { fun onFocusChange(v: View?, hasFocus: Boolean) }
    fun setOnClickListener(l: OnClickListener?) {}
    fun setOnFocusChangeListener(l: OnFocusChangeListener?) {}
    fun requestFocus(): Boolean = false
}
open class ViewGroup(c: android.content.Context?) : View(c) {
    open class LayoutParams(w: Int, h: Int)
    fun getChildAt(i: Int): View? = null
    fun getChildCount(): Int = 0
    fun addView(v: View?) {}
    fun addView(v: View?, p: LayoutParams?) {}
}
class Window { fun getDecorView(): View? = null }
