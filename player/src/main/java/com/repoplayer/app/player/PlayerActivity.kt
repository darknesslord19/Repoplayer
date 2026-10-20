package com.repoplayer.app.player

import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.repoplayer.app.App
import com.repoplayer.app.R
import com.repoplayer.app.data.DEFAULT_UA
import com.repoplayer.app.data.StreamType
import com.repoplayer.app.data.Subtitle
import com.repoplayer.app.data.WatchRecord

@androidx.annotation.OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {
    private lateinit var req: PlayRequest
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var topBar: LinearLayout
    private lateinit var titleView: TextView
    private var index = 0
    private var resizeIndex = 0
    private val localSubs = mutableListOf<Subtitle>()
    private val handler = Handler(Looper.getMainLooper())
    private val store get() = App.instance.store

    private val resizeModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT to "Sığdır",
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Yakınlaştır",
        AspectRatioFrameLayout.RESIZE_MODE_FILL to "Doldur",
    )

    private val pickSubtitle = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        runCatching { contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Yerel altyazı"
        localSubs.add(0, Subtitle(lang = "Yerel: $name", url = uri.toString()))
        reload(keepPosition = true, selectFirstSub = true)
    }

    private val saver = object : Runnable {
        override fun run() {
            saveProgress()
            handler.postDelayed(this, 5000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        req = Playback.request ?: run { finish(); return }
        index = req.startIndex.coerceIn(0, req.links.lastIndex)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buildUi()
        hideSystemBars()

        val p = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) = onError(error)
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    saveProgress()
                    if (req.onNext != null) playNextEpisode()
                }
            }
        })
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setPreferredTextLanguage("tr")
            .setPreferredAudioLanguages("tr", "en")
            .build()
        player = p
        playerView.player = p

        val resume = store.record(req.progressKey)?.takeIf { !it.finished }?.position ?: 0L
        load(index, resume)
        if (resume > 0) Toast.makeText(this, "Kaldığın yerden devam: ${formatMs(resume)}", Toast.LENGTH_SHORT).show()
    }

    // ---------------- Arayüz ----------------

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun focusBg(): StateListDrawable {
        fun shape(color: Int) = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(color) }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), shape(0x662ED3B7))
            addState(intArrayOf(android.R.attr.state_pressed), shape(0x442ED3B7))
            addState(intArrayOf(), shape(Color.TRANSPARENT))
        }
    }

    private fun iconButton(icon: Int, desc: String, onClick: () -> Unit) = ImageButton(this).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(Color.WHITE)
        contentDescription = desc
        background = focusBg()
        setPadding(dp(10), dp(10), dp(10), dp(10))
        isFocusable = true
        setOnClickListener { onClick() }
        setOnLongClickListener { Toast.makeText(context, desc, Toast.LENGTH_SHORT).show(); true }
        layoutParams = LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginStart = dp(4) }
    }

    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        playerView = PlayerView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setShowSubtitleButton(true)
            setShowNextButton(false)
            setShowPreviousButton(false)
            controllerShowTimeoutMs = 4000
            keepScreenOn = true
            resizeMode = resizeModes[0].first
        }
        playerView.subtitleView?.apply {
            setStyle(CaptionStyleCompat(Color.WHITE, Color.TRANSPARENT, Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE, Color.BLACK, null))
            setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * store.subtitleSize / 100f)
        }

        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(12), dp(8))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xCC000000.toInt(), 0x00000000))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP)
        }
        topBar.addView(iconButton(R.drawable.ic_back, "Geri") { finish() })
        titleView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            maxLines = 2
            setPadding(dp(8), 0, dp(8), 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        topBar.addView(titleView)
        topBar.addView(iconButton(R.drawable.ic_subtitles, "Altyazı dosyası ekle") {
            pickSubtitle.launch(arrayOf("*/*"))
        })
        topBar.addView(iconButton(R.drawable.ic_library, "Kaynak değiştir") { showSources() })
        topBar.addView(iconButton(R.drawable.ic_aspect, "Görüntü boyutu") { cycleResize() })
        topBar.addView(iconButton(R.drawable.ic_open, "Harici oynatıcıda aç") {
            Playback.openChooser(this, req.links[index], req.title)
        })
        if (req.onNext != null) topBar.addView(iconButton(R.drawable.ic_next, "Sonraki bölüm") { playNextEpisode() })

        playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            topBar.visibility = visibility
        })

        root.addView(playerView)
        root.addView(topBar)
        setContentView(root)
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun cycleResize() {
        resizeIndex = (resizeIndex + 1) % resizeModes.size
        playerView.resizeMode = resizeModes[resizeIndex].first
        Toast.makeText(this, resizeModes[resizeIndex].second, Toast.LENGTH_SHORT).show()
    }

    private fun showSources() {
        val names = req.links.mapIndexed { i, l -> (if (i == index) "▶ " else "") + listOfNotNull(l.quality, l.name).joinToString(" · ") }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Kaynaklar (${req.links.size})")
            .setItems(names.toTypedArray()) { _, i -> index = i; reload(keepPosition = true) }
            .show()
    }

    // ---------------- Oynatma ----------------

    private fun subMime(url: String): String {
        val u = url.substringBefore('?').lowercase()
        return when {
            u.endsWith(".vtt") || u.contains(".vtt") -> MimeTypes.TEXT_VTT
            u.endsWith(".ass") || u.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            u.endsWith(".ttml") || u.endsWith(".dfxp") || u.endsWith(".xml") -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP
        }
    }

    private fun langCode(label: String): String? {
        val l = label.lowercase()
        return when {
            l.startsWith("tr") || l.contains("türk") || l.contains("turk") -> "tr"
            l.startsWith("en") || l.contains("ingiliz") || l.contains("english") -> "en"
            else -> null
        }
    }

    private fun load(i: Int, position: Long, selectFirstSub: Boolean = false) {
        val p = player ?: return
        val link = req.links[i]
        titleView.text = "${req.title}\n${listOfNotNull(link.quality, link.name).joinToString(" · ")}"

        val ua = link.headers.entries.firstOrNull { it.key.equals("user-agent", true) }?.value ?: DEFAULT_UA
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(ua)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(30_000)
            .setDefaultRequestProperties(link.headers.filterKeys { !it.equals("user-agent", true) })
        val factory = DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http))

        val subs = (localSubs + req.subtitles).distinctBy { it.url }
        val firstTr = subs.indexOfFirst { langCode(it.lang) == "tr" }
        val configs = subs.mapIndexed { n, s ->
            val isDefault = (selectFirstSub && n == 0) || (!selectFirstSub && n == firstTr)
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(s.url))
                .setMimeType(subMime(s.url))
                .setLanguage(langCode(s.lang) ?: "und")
                .setLabel(s.lang)
                .setId("sub$n")
                .setSelectionFlags(if (isDefault) C.SELECTION_FLAG_DEFAULT else 0)
                .build()
        }

        val item = MediaItem.Builder()
            .setUri(link.url)
            .setMimeType(
                when (link.type) {
                    StreamType.HLS -> MimeTypes.APPLICATION_M3U8
                    StreamType.DASH -> MimeTypes.APPLICATION_MPD
                    StreamType.PROGRESSIVE -> null
                }
            )
            .setSubtitleConfigurations(configs)
            .build()

        p.setMediaSource(factory.createMediaSource(item))
        p.prepare()
        if (position > 0) p.seekTo(position)
        p.playWhenReady = true
    }

    private fun reload(keepPosition: Boolean, selectFirstSub: Boolean = false) {
        val pos = if (keepPosition) player?.currentPosition ?: 0L else 0L
        load(index, pos, selectFirstSub)
    }

    private fun onError(error: PlaybackException) {
        val msg = error.errorCodeName.removePrefix("ERROR_CODE_")
        if (store.autoNextSource && index < req.links.lastIndex) {
            Toast.makeText(this, "Kaynak açılamadı ($msg), sonrakine geçiliyor…", Toast.LENGTH_SHORT).show()
            val pos = player?.currentPosition ?: 0L
            index++
            load(index, pos)
        } else {
            Toast.makeText(this, "Oynatılamadı: $msg. Başka bir kaynak deneyin.", Toast.LENGTH_LONG).show()
            playerView.showController()
        }
    }

    private fun playNextEpisode() {
        val next = req.onNext ?: return
        saveProgress()
        finish()
        next()
    }

    private fun saveProgress() {
        val p = player ?: return
        val duration = p.duration
        if (duration <= 0 || duration == C.TIME_UNSET) return
        store.saveProgress(
            WatchRecord(
                key = req.progressKey,
                item = req.item,
                title = req.title,
                episodeId = req.episodeId,
                episodeLabel = req.episodeLabel,
                position = p.currentPosition,
                duration = duration,
            )
        )
    }

    private fun formatMs(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }

    // ---------------- Yaşam döngüsü ----------------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // TV kumandası: kontrol çubuğu kapalıyken ok tuşları onu açsın
        if (event.action == KeyEvent.ACTION_DOWN && !playerView.isControllerFullyVisible) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                    playerView.showController(); return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> { player?.seekBack(); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { player?.seekForward(); return true }
            }
        }
        return playerView.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        handler.post(saver)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(saver)
        saveProgress()
        val inPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode
        if (!inPip) player?.pause()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && player?.isPlaying == true &&
            packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)
        ) {
            runCatching { enterPictureInPictureMode(PictureInPictureParams.Builder().build()) }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        playerView.useController = !isInPictureInPictureMode
        topBar.visibility = if (isInPictureInPictureMode) View.GONE else topBar.visibility
    }

    override fun onDestroy() {
        handler.removeCallbacks(saver)
        saveProgress()
        player?.release()
        player = null
        super.onDestroy()
    }
}
