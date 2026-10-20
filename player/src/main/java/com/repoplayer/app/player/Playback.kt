package com.repoplayer.app.player

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.repoplayer.app.data.ContentItem
import com.repoplayer.app.data.PlayerChoice
import com.repoplayer.app.data.StreamLink
import com.repoplayer.app.data.Subtitle

/** Oynatıcıya aktarılan oturum (Activity'ler arası büyük veri taşımamak için bellekte) */
data class PlayRequest(
    val item: ContentItem,
    val title: String,
    val episodeId: String?,
    val episodeLabel: String?,
    val links: List<StreamLink>,
    val startIndex: Int,
    val subtitles: List<Subtitle>,
    val progressKey: String,
    /** Dizi ise sonraki bölümü başlatır */
    val onNext: (() -> Unit)? = null,
    val nextLabel: String? = null,
)

object Playback {
    @Volatile
    var request: PlayRequest? = null

    fun start(context: Context, req: PlayRequest, choice: PlayerChoice, position: Long) {
        request = req
        when (choice) {
            PlayerChoice.VLC -> if (openVlc(context, req, position)) return
            PlayerChoice.MX -> if (openMx(context, req, position)) return
            PlayerChoice.ASK -> { openChooser(context, req.links[req.startIndex], req.title); return }
            PlayerChoice.INTERNAL -> Unit
        }
        context.startActivity(Intent(context, PlayerActivity::class.java).apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun mime(link: StreamLink) = when (link.type) {
        com.repoplayer.app.data.StreamType.HLS -> "application/x-mpegURL"
        com.repoplayer.app.data.StreamType.DASH -> "application/dash+xml"
        else -> "video/*"
    }

    fun openVlc(context: Context, req: PlayRequest, position: Long): Boolean {
        val link = req.links[req.startIndex]
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setPackage("org.videolan.vlc")
            setDataAndTypeAndNormalize(Uri.parse(link.url), mime(link))
            putExtra("title", req.title)
            if (position > 0) putExtra("position", position)
            putExtra("from_start", position <= 0)
            req.subtitles.firstOrNull()?.let { putExtra("subtitles_location", it.url) }
        }
        return launch(context, intent, "VLC yüklü değil")
    }

    fun openMx(context: Context, req: PlayRequest, position: Long): Boolean {
        val link = req.links[req.startIndex]
        for (pkg in listOf("com.mxtech.videoplayer.pro", "com.mxtech.videoplayer.ad")) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setPackage(pkg)
                setDataAndType(Uri.parse(link.url), mime(link))
                putExtra("title", req.title)
                putExtra("secure_uri", true)
                if (position > 0) putExtra("position", position.toInt())
                if (link.headers.isNotEmpty()) {
                    putExtra("headers", link.headers.flatMap { listOf(it.key, it.value) }.toTypedArray())
                }
                if (req.subtitles.isNotEmpty()) {
                    putExtra("subs", req.subtitles.map { Uri.parse(it.url) }.toTypedArray())
                    putExtra("subs.name", req.subtitles.map { it.lang }.toTypedArray())
                }
            }
            if (launch(context, intent, null)) return true
        }
        Toast.makeText(context, "MX Player yüklü değil", Toast.LENGTH_SHORT).show()
        return false
    }

    fun openChooser(context: Context, link: StreamLink, title: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(link.url), mime(link))
            putExtra("title", title)
            if (link.headers.isNotEmpty()) putExtra("headers", link.headers.flatMap { listOf(it.key, it.value) }.toTypedArray())
        }
        launch(context, Intent.createChooser(intent, "Harici oynatıcı seç"), "Uygun oynatıcı bulunamadı")
    }

    private fun launch(context: Context, intent: Intent, failMsg: String?): Boolean = try {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        if (failMsg != null) Toast.makeText(context, failMsg, Toast.LENGTH_SHORT).show()
        false
    }
}
