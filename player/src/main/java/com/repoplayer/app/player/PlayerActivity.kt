package com.repoplayer.app.player

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
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
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.media3.ui.TrackSelectionDialogBuilder
import com.repoplayer.app.App
import com.repoplayer.app.R
import com.repoplayer.app.data.DEFAULT_UA
import com.repoplayer.app.data.Logger
import com.repoplayer.app.data.Net
import com.repoplayer.app.data.StreamType
import com.repoplayer.app.data.Subtitle
import com.repoplayer.app.data.WatchRecord
import kotlin.math.abs

@androidx.annotation.OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {
    private lateinit var req: PlayRequest
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var topBar: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var infoView: TextView
    private lateinit var indicator: TextView
    private lateinit var lockButton: ImageButton
    private lateinit var nextEpButton: TextView
    private var index = 0
    /** Aynı bağlantı için kaçıncı başlık denemesi (tam / UA'sız / başlıksız) */
    private var attempt = 0
    private var resizeIndex = 0
    private var locked = false
    private var ticks = 0
    private var nextDismissed = false
    private val localSubs = mutableListOf<Subtitle>()
    private val handler = Handler(Looper.getMainLooper())
    private val store get() = App.instance.store
    private val seekMs get() = store.seekStep.coerceIn(5, 60) * 1000L
    private lateinit var audio: AudioManager

    private val resizeModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT to "Sığdır",
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Yakınlaştır",
        AspectRatioFrameLayout.RESIZE_MODE_FILL to "Doldur",
    )
    private val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

    private val pickSubtitle = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Yerel altyazı"
        localSubs.add(0, Subtitle(lang = "Yerel: $name", url = uri.toString()))
        reload(keepPosition = true, selectFirstSub = true)
    }

    /** Saniyede bir: ilerleme kaydı (5 sn'de bir) ve "sonraki bölüm" düğmesi */
    private val ticker = object : Runnable {
        override fun run() {
            ticks++
            if (ticks % 5 == 0) saveProgress()
            updateNextButton()
            handler.postDelayed(this, 1000)
        }
    }

    private val hideIndicator = Runnable { indicator.visibility = View.GONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        req = Playback.request ?: run { finish(); return }
        index = req.startIndex.coerceIn(0, req.links.lastIndex)
        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        resizeIndex = store.defaultResize.coerceIn(0, resizeModes.lastIndex)
        buildUi()
        hideSystemBars()

        val (minBuf, maxBuf) = when (store.bufferLevel) {
            0 -> 15_000 to 50_000
            2 -> 60_000 to 300_000
            else -> 30_000 to 120_000
        }
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(minBuf, maxBuf, 1_500, 3_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val p = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(seekMs)
            .setSeekForwardIncrementMs(seekMs)
            .build()
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) = onError(error)
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    saveProgress()
                    if (req.onNext != null && store.autoNextEpisode) playNextEpisode()
                }
            }
        })
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setPreferredTextLanguage("tr")
            .setPreferredAudioLanguages("tr", "en")
            .build()
        p.playbackParameters = PlaybackParameters(store.defaultSpeed / 100f)
        player = p
        playerView.player = p

        val resume = if (store.autoResume) store.record(req.progressKey)?.takeIf { !it.finished }?.position ?: 0L else 0L
        load(index, resume)
        if (resume > 0) toast("Kaldığın yerden devam: ${formatMs(resume)}")
    }

    // ---------------- Arayüz ----------------

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun focusBg(radius: Int = 20, idle: Int = Color.TRANSPARENT): StateListDrawable {
        fun shape(color: Int, stroke: Boolean = false) = GradientDrawable().apply {
            cornerRadius = dp(radius).toFloat()
            setColor(color)
            if (stroke) setStroke(dp(2), 0xFF2ED3B7.toInt())
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), shape(0x552ED3B7, stroke = true))
            addState(intArrayOf(android.R.attr.state_pressed), shape(0x442ED3B7))
            addState(intArrayOf(), shape(idle))
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
        setOnLongClickListener { toast(desc); true }
        layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
    }

    /** İkon + yazı içeren hap düğme (TV'de ne yaptığı okunabilsin) */
    private fun chip(icon: Int, label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        setTextColor(Color.WHITE)
        textSize = 13f
        gravity = Gravity.CENTER_VERTICAL
        val d = getDrawable(icon)?.mutate()
        d?.setTint(Color.WHITE)
        d?.setBounds(0, 0, dp(18), dp(18))
        setCompoundDrawables(d, null, null, null)
        compoundDrawablePadding = dp(6)
        background = focusBg(18, 0x33FFFFFF)
        setPadding(dp(12), dp(8), dp(14), dp(8))
        isFocusable = true
        isClickable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        playerView = PlayerView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setShowSubtitleButton(true)
            setShowNextButton(false)
            setShowPreviousButton(false)
            setShowFastForwardButton(true)
            setShowRewindButton(true)
            controllerShowTimeoutMs = 4000
            keepScreenOn = true
            resizeMode = resizeModes[resizeIndex].first
        }
        applySubtitleStyle()

        // ---- Üst çubuk: geri + başlık, altında işlev hapları ----
        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(10))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xDD000000.toInt(), 0x00000000))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP)
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(iconButton(R.drawable.ic_back, "Geri") { finish() })
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleView = TextView(this).apply { setTextColor(Color.WHITE); textSize = 16f; maxLines = 1; typeface = Typeface.DEFAULT_BOLD }
        infoView = TextView(this).apply { setTextColor(0xFFB8C0CC.toInt()); textSize = 12f; maxLines = 1 }
        texts.addView(titleView)
        texts.addView(infoView)
        head.addView(texts)
        topBar.addView(head)

        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(4), dp(6), dp(4), 0) }
        chips.addView(chip(R.drawable.ic_library, "Kaynaklar (${req.links.size})") { showSources() })
        chips.addView(chip(R.drawable.ic_audio, "Ses") { showTracks(C.TRACK_TYPE_AUDIO, "Ses dili") })
        chips.addView(chip(R.drawable.ic_subtitles, "Altyazı") { showTracks(C.TRACK_TYPE_TEXT, "Altyazı") })
        chips.addView(chip(R.drawable.ic_add, "Altyazı dosyası") { pickSubtitle.launch(arrayOf("*/*")) })
        chips.addView(chip(R.drawable.ic_speed, "Hız") { showSpeed() })
        chips.addView(chip(R.drawable.ic_aspect, "Boyut") { cycleResize() })
        chips.addView(chip(R.drawable.ic_forward, "+${store.skipIntroSec} sn") { player?.let { it.seekTo(it.currentPosition + store.skipIntroSec * 1000L) } })
        chips.addView(chip(R.drawable.ic_lock, "Kilitle") { setLocked(true) })
        chips.addView(chip(R.drawable.ic_open, "Harici") { Playback.openChooser(this, req.links[index], req.title) })
        if (req.onNext != null) chips.addView(chip(R.drawable.ic_next, "Sonraki bölüm") { playNextEpisode() })
        topBar.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
        })

        playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            if (!locked) topBar.visibility = visibility
        })

        // ---- Orta gösterge (parlaklık / ses / sarma) ----
        indicator = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(12), dp(20), dp(12))
            background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(0xAA000000.toInt()) }
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        }

        // ---- Kilit düğmesi ----
        lockButton = iconButton(R.drawable.ic_lock_open, "Kilidi aç") { setLocked(false) }.apply {
            background = focusBg(23, 0x88000000.toInt())
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(dp(52), dp(52), Gravity.START or Gravity.CENTER_VERTICAL).apply { marginStart = dp(24) }
        }

        // ---- Sonraki bölüm düğmesi (son 90 sn) ----
        nextEpButton = chip(R.drawable.ic_next, "Sonraki: ${req.nextLabel ?: "bölüm"}") { playNextEpisode() }.apply {
            background = focusBg(22, 0xEE2ED3B7.toInt())
            setTextColor(Color.BLACK)
            compoundDrawables[0]?.setTint(Color.BLACK)
            maxLines = 1
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.BOTTOM).apply {
                marginEnd = dp(24); bottomMargin = dp(96)
            }
        }

        root.addView(playerView)
        root.addView(indicator)
        root.addView(nextEpButton)
        root.addView(topBar)
        root.addView(lockButton)
        setContentView(root)
        setupGestures()
    }

    private fun applySubtitleStyle() {
        val fg = when (store.subtitleColor) { 1 -> 0xFFFFE14D.toInt(); 2 -> 0xFF2ED3B7.toInt(); else -> Color.WHITE }
        val bg = if (store.subtitleBackground) 0x99000000.toInt() else Color.TRANSPARENT
        playerView.subtitleView?.apply {
            setStyle(CaptionStyleCompat(fg, bg, Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE, Color.BLACK, Typeface.DEFAULT_BOLD))
            setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * store.subtitleSize / 100f)
            setApplyEmbeddedStyles(false)
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private fun showIndicator(text: String) {
        indicator.text = text
        indicator.visibility = View.VISIBLE
        handler.removeCallbacks(hideIndicator)
        handler.postDelayed(hideIndicator, 800)
    }

    private fun setLocked(value: Boolean) {
        locked = value
        playerView.useController = !value
        if (value) {
            playerView.hideController()
            topBar.visibility = View.GONE
            lockButton.visibility = View.VISIBLE
            lockButton.requestFocus()
            toast("Ekran kilitlendi")
        } else {
            lockButton.visibility = View.GONE
            playerView.showController()
        }
    }

    private fun cycleResize() {
        resizeIndex = (resizeIndex + 1) % resizeModes.size
        playerView.resizeMode = resizeModes[resizeIndex].first
        showIndicator(resizeModes[resizeIndex].second)
    }

    private fun showSpeed() {
        val p = player ?: return
        val cur = p.playbackParameters.speed
        val labels = speeds.map { (if (it == cur) "▶ " else "") + "${it}x" }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Oynatma hızı")
            .setItems(labels.toTypedArray()) { _, i ->
                p.playbackParameters = PlaybackParameters(speeds[i])
                showIndicator("${speeds[i]}x")
            }
            .show()
    }

    private fun showTracks(type: Int, title: String) {
        val p = player ?: return
        val hasTracks = p.currentTracks.groups.any { it.type == type }
        if (!hasTracks) {
            toast(if (type == C.TRACK_TYPE_TEXT) "Altyazı yok — dosyadan ekleyebilirsin" else "Başka ses izi yok")
            return
        }
        TrackSelectionDialogBuilder(this, title, p, type)
            .setTheme(android.R.style.Theme_Material_Dialog_Alert)
            .setShowDisableOption(type == C.TRACK_TYPE_TEXT)
            .setAllowAdaptiveSelections(false)
            .build()
            .show()
    }

    private fun showSources() {
        val names = req.links.mapIndexed { i, l -> (if (i == index) "▶ " else "") + listOfNotNull(l.quality, l.name).joinToString(" · ") }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Kaynaklar (${req.links.size})")
            .setItems(names.toTypedArray()) { _, i -> index = i; attempt = 0; reload(keepPosition = true) }
            .show()
    }

    // ---------------- Jestler ----------------

    private enum class Mode { NONE, SEEK, BRIGHTNESS, VOLUME }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestures() {
        var mode = Mode.NONE
        var startX = 0f
        var startY = 0f
        var startPos = 0L
        var seekTarget = 0L
        var startBrightness = 0.5f
        var startVolume = 0
        val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val edge = dp(48)

        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                mode = Mode.NONE
                startX = e.x
                startY = e.y
                startPos = player?.currentPosition ?: 0L
                startBrightness = window.attributes.screenBrightness.takeIf { it >= 0 } ?: 0.5f
                startVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (playerView.isControllerFullyVisible) playerView.hideController() else playerView.showController()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val p = player ?: return false
                val w = playerView.width
                when {
                    e.x < w / 3f -> { p.seekBack(); showIndicator("⏪ ${store.seekStep} sn") }
                    e.x > w * 2f / 3f -> { p.seekForward(); showIndicator("${store.seekStep} sn ⏩") }
                    else -> { if (p.isPlaying) p.pause() else p.play(); showIndicator(if (p.isPlaying) "▶" else "❚❚") }
                }
                return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                if (startY < edge || startY > playerView.height - edge) return false
                val totalX = e2.x - startX
                val totalY = e2.y - startY
                if (mode == Mode.NONE) {
                    if (abs(totalX) < dp(16) && abs(totalY) < dp(16)) return false
                    mode = when {
                        abs(totalX) > abs(totalY) -> Mode.SEEK
                        startX < playerView.width / 2f -> Mode.BRIGHTNESS
                        else -> Mode.VOLUME
                    }
                }
                when (mode) {
                    Mode.SEEK -> {
                        val dur = player?.duration?.takeIf { it > 0 } ?: return true
                        val delta = (totalX / playerView.width * 180_000).toLong() // ekran genişliği = 3 dk
                        seekTarget = (startPos + delta).coerceIn(0, dur)
                        val sign = if (delta >= 0) "+" else "-"
                        showIndicator("${formatMs(seekTarget)}  ($sign${formatMs(abs(delta))})")
                    }
                    Mode.BRIGHTNESS -> {
                        val v = (startBrightness - totalY / playerView.height).coerceIn(0.01f, 1f)
                        window.attributes = window.attributes.apply { screenBrightness = v }
                        showIndicator("☀ ${(v * 100).toInt()}%")
                    }
                    Mode.VOLUME -> {
                        val v = (startVolume - totalY / playerView.height * maxVolume * 1.5f).toInt().coerceIn(0, maxVolume)
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
                        showIndicator("🔊 ${v * 100 / maxVolume.coerceAtLeast(1)}%")
                    }
                    Mode.NONE -> Unit
                }
                return true
            }
        })

        playerView.setOnTouchListener { _, event ->
            if (locked) {
                if (event.action == MotionEvent.ACTION_UP) {
                    lockButton.visibility = View.VISIBLE
                }
                return@setOnTouchListener true
            }
            if (!store.gestures) return@setOnTouchListener false
            detector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                if (mode == Mode.SEEK) player?.seekTo(seekTarget)
                mode = Mode.NONE
            }
            true
        }
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

    /** Bir bağlantı için tek bir açma denemesi: hangi ağ motoru, hangi başlıklar */
    private data class Attempt(val cronet: Boolean, val headers: Map<String, String>, val label: String)

    /**
     * CloudStream oynatıcısıyla aynı sıra: önce Cronet (Chrome ağ motoru, HTTP/3, Chrome TLS
     * parmak izi; bazı CDN'ler yalnızca bunu kabul eder), olmazsa OkHttp (DNS-over-HTTPS +
     * WebView çerezleri), sonra başlık varyasyonları (UA'sız / başlıksız).
     * Eklentinin video yakalayıcısı varsa CloudStream gibi doğrudan OkHttp + yakalayıcı.
     */
    private fun attempts(link: com.repoplayer.app.data.StreamLink): List<Attempt> {
        val h = link.headers
        val list = mutableListOf<Attempt>()
        if (cronetEngine(this) != null && VideoInterceptors.get(link.url) == null) list += Attempt(true, h, "Cronet")
        list += Attempt(false, h, "OkHttp")
        val noUa = h.filterKeys { !it.equals("user-agent", true) }
        if (noUa.size != h.size) list += Attempt(false, noUa, "OkHttp, UA'sız")
        if (h.isNotEmpty() && noUa.isNotEmpty()) list += Attempt(false, emptyMap(), "OkHttp, başlıksız")
        return list
    }

    private fun headerVariants(h: Map<String, String>) = attempts(req.links[index].copy(headers = h))

    private fun dataSourceFactory(link: com.repoplayer.app.data.StreamLink, a: Attempt): androidx.media3.datasource.HttpDataSource.Factory {
        val ua = a.headers.entries.firstOrNull { it.key.equals("user-agent", true) }?.value
            ?: runCatching { com.lagradost.cloudstream3.USER_AGENT }.getOrDefault(DEFAULT_UA)
        val headers = a.headers.filterKeys { !it.equals("user-agent", true) }
        val engine = if (a.cronet) cronetEngine(this) else null
        if (engine != null) {
            return androidx.media3.datasource.cronet.CronetDataSource.Factory(engine, cronetExecutor)
                .setUserAgent(ua)
                .setConnectionTimeoutMs(20_000)
                .setReadTimeoutMs(30_000)
                .setResetTimeoutOnRedirects(true)
                .setHandleSetCookieRequests(true)
                .setDefaultRequestProperties(headers)
        }
        val interceptor = VideoInterceptors.get(link.url)
        val client = if (interceptor != null) Net.client.newBuilder().addInterceptor(interceptor).build() else Net.client
        return OkHttpDataSource.Factory(client).setUserAgent(ua).setDefaultRequestProperties(headers)
    }

    private fun load(i: Int, position: Long, selectFirstSub: Boolean = false) {
        val p = player ?: return
        val original = req.links[i]
        val tries = attempts(original)
        val a = tries[attempt.coerceIn(0, tries.lastIndex)]
        val link = original.copy(headers = a.headers)
        titleView.text = req.title
        infoView.text = listOfNotNull(link.quality, link.name, "${i + 1}/${req.links.size}").joinToString(" · ")
        nextDismissed = false

        val factory = DefaultMediaSourceFactory(DefaultDataSource.Factory(this, dataSourceFactory(original, a)))

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
        Logger.e("Player", "Kaynak ${index + 1} (${req.links[index].name}) deneme ${attempt + 1} açılamadı: $msg · ${req.links[index].url.take(120)}", error)
        val pos = player?.currentPosition ?: 0L
        // Önce aynı bağlantıyı farklı başlıklarla dene
        if (attempt < headerVariants(req.links[index].headers).lastIndex) {
            attempt++
            Logger.i("Player", "Aynı kaynak yeniden deneniyor: ${attempts(req.links[index]).getOrNull(attempt)?.label}")
            load(index, pos)
            return
        }
        if (store.autoNextSource && index < req.links.lastIndex) {
            toast("Kaynak açılamadı ($msg), sonrakine geçiliyor…")
            index++
            attempt = 0
            load(index, pos)
        } else {
            Toast.makeText(this, "Oynatılamadı: $msg. Kaynaklar'dan başka birini deneyin.", Toast.LENGTH_LONG).show()
            playerView.showController()
        }
    }

    private fun updateNextButton() {
        val p = player ?: return
        val show = req.onNext != null && !nextDismissed && !locked && p.duration > 0 &&
            p.duration - p.currentPosition in 1..90_000
        val vis = if (show) View.VISIBLE else View.GONE
        if (nextEpButton.visibility != vis) nextEpButton.visibility = vis
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

    // ---------------- TV kumandası ----------------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val p = player
        if (event.action == KeyEvent.ACTION_DOWN && p != null) {
            if (locked) {
                lockButton.visibility = View.VISIBLE
                lockButton.requestFocus()
                return if (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER) super.dispatchKeyEvent(event) else true
            }
            when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> { if (p.isPlaying) p.pause() else p.play(); playerView.showController(); return true }
                KeyEvent.KEYCODE_MEDIA_PLAY -> { p.play(); return true }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> { p.pause(); playerView.showController(); return true }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { p.seekForward(); showIndicator("${store.seekStep} sn ⏩"); return true }
                KeyEvent.KEYCODE_MEDIA_REWIND -> { p.seekBack(); showIndicator("⏪ ${store.seekStep} sn"); return true }
                KeyEvent.KEYCODE_MEDIA_NEXT -> { if (req.onNext != null) playNextEpisode(); return true }
                KeyEvent.KEYCODE_MENU -> { showSources(); return true }
                KeyEvent.KEYCODE_CAPTIONS -> { showTracks(C.TRACK_TYPE_TEXT, "Altyazı"); return true }
            }
            // Kontrol çubuğu kapalıyken: sol/sağ sarar, orta tuş durdurur/oynatır, yukarı/aşağı menüyü açar
            if (!playerView.isControllerFullyVisible && topBar.visibility != View.VISIBLE) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { p.seekBack(); showIndicator("⏪ ${store.seekStep} sn"); return true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { p.seekForward(); showIndicator("${store.seekStep} sn ⏩"); return true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        if (p.isPlaying) p.pause() else p.play()
                        playerView.showController()
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> { playerView.showController(); return true }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // ---------------- Yaşam döngüsü ----------------

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
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

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        playerView.useController = !isInPictureInPictureMode && !locked
        if (isInPictureInPictureMode) {
            topBar.visibility = View.GONE
            nextEpButton.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        saveProgress()
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        private val cronetExecutor = java.util.concurrent.Executors.newFixedThreadPool(4)
        @Volatile private var engine: org.chromium.net.CronetEngine? = null
        @Volatile private var engineTried = false

        /** Google Play Hizmetleri'nin Cronet'i; yoksa (bazı TV'ler) null döner ve OkHttp kullanılır */
        fun cronetEngine(context: Context): org.chromium.net.CronetEngine? {
            engine?.let { return it }
            if (engineTried) return null
            synchronized(this) {
                if (engineTried) return engine
                engineTried = true
                engine = runCatching {
                    org.chromium.net.CronetEngine.Builder(context.applicationContext)
                        .enableHttp2(true)
                        .enableQuic(true)
                        .enableBrotli(true)
                        .build()
                }.onFailure { Logger.w("Player", "Cronet yok, OkHttp kullanılacak: ${it.message}") }.getOrNull()
                return engine
            }
        }
    }
}
