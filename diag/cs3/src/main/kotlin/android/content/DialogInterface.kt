package android.content
interface DialogInterface {
    fun dismiss()
    fun interface OnClickListener { fun onClick(d: DialogInterface?, which: Int) }
    fun interface OnShowListener { fun onShow(d: DialogInterface?) }
}
