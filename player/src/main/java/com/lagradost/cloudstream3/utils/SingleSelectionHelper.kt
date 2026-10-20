package com.lagradost.cloudstream3.utils

import android.app.Activity
import android.app.AlertDialog

/** Uyumluluk katmanı: eklenti ayar ekranlarının kullandığı seçim diyaloğu */
object SingleSelectionHelper {
    fun Activity?.showDialog(
        items: List<String>,
        selectedIndex: Int,
        name: String,
        showApply: Boolean,
        dismissCallback: () -> Unit,
        callback: (Int) -> Unit,
    ) {
        val act = this ?: return
        act.runOnUiThread {
            var chosen = selectedIndex
            val b = AlertDialog.Builder(act, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(name)
                .setSingleChoiceItems(items.toTypedArray(), selectedIndex) { d, which ->
                    chosen = which
                    if (!showApply) {
                        callback(which)
                        d.dismiss()
                    }
                }
                .setOnDismissListener { dismissCallback() }
            if (showApply) b.setPositiveButton("Uygula") { _, _ -> callback(chosen) }
            b.show()
        }
    }
}
