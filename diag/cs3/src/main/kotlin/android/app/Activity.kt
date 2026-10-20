package android.app
open class Activity : android.content.Context() { fun isDestroyed() = false; fun isFinishing() = false }
open class AlertDialog : android.content.DialogInterface {
    override fun dismiss() {}
    fun show() {}
    fun getButton(i: Int): android.widget.Button? = null
    fun getWindow(): android.view.Window? = null
    fun setOnShowListener(l: android.content.DialogInterface.OnShowListener?) {}
    open class Builder(c: android.content.Context?) {
        fun setTitle(t: CharSequence?) = this
        fun setMessage(t: CharSequence?) = this
        fun setView(v: android.view.View?) = this
        fun setPositiveButton(t: CharSequence?, l: android.content.DialogInterface.OnClickListener?) = this
        fun setNegativeButton(t: CharSequence?, l: android.content.DialogInterface.OnClickListener?) = this
        fun setNeutralButton(t: CharSequence?, l: android.content.DialogInterface.OnClickListener?) = this
        fun create() = AlertDialog()
    }
}
