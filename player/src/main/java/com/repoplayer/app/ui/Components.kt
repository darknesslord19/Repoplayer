package com.repoplayer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.repoplayer.app.data.ContentItem
import com.repoplayer.app.data.Row as ContentRow

val Bg = Color(0xFF0D0F14)
val Surface1 = Color(0xFF161A22)
val Surface2 = Color(0xFF1E2430)
val Accent = Color(0xFF2ED3B7)
val TextDim = Color(0xFF9AA3B2)

val AppColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF00201A),
    secondary = Accent,
    background = Bg,
    surface = Bg,
    surfaceVariant = Surface1,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerLow = Surface1,
    secondaryContainer = Color(0xFF15433B),
    onSecondaryContainer = Accent,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = TextDim,
    outline = Color(0xFF2A3140),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = AppColors, content = content)

/** TV kumandası ile gezinirken odaklanan öğeyi turkuaz çerçeveyle belirtir */
fun Modifier.focusFrame(shape: Shape = RoundedCornerShape(12.dp), grow: Boolean = false): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    this
        .onFocusChanged { focused = it.isFocused || it.hasFocus }
        .then(if (grow && focused) Modifier.scale(1.07f) else Modifier)
        .border(if (focused) 2.5.dp else 0.dp, if (focused) Accent else Color.Transparent, shape)
}

/** Görünüm ayarları (afiş boyutu, rozetler...) tüm ekranlara buradan ulaşır */
val LocalUi = compositionLocalOf<UiPrefs?> { null }

/** Bitirilmiş içerikler: "kaynakKey|id" */
val LocalWatched = compositionLocalOf<Set<String>> { emptySet() }

/** Yan menü (TV/tablet) etkin mi */
val LocalRail = compositionLocalOf { false }

fun posterWidth(size: Int?): Dp = when (size) { 0 -> 96.dp; 2 -> 150.dp; else -> 120.dp }

/** Afiş/arka plan görseli; eklentinin istediği başlıklarla (Referer vb.) yüklenir */
@Composable
fun RemoteImage(url: String?, headers: Map<String, String>?, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val ctx = LocalPlatformContext.current
    val model = remember(url, headers) {
        if (url.isNullOrBlank()) null
        else ImageRequest.Builder(ctx).data(url).apply {
            if (!headers.isNullOrEmpty()) {
                httpHeaders(NetworkHeaders.Builder().apply { headers.forEach { (k, v) -> set(k, v) } }.build())
            }
        }.build()
    }
    if (model != null) AsyncImage(model = model, contentDescription = contentDescription, contentScale = ContentScale.Crop, modifier = modifier)
}

@Composable
fun Poster(url: String?, title: String, modifier: Modifier = Modifier, headers: Map<String, String>? = null) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(Surface2)) {
        Text(
            title, Modifier.align(Alignment.Center).padding(8.dp),
            color = TextDim, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 4,
        )
        RemoteImage(url, headers, Modifier.fillMaxSize(), title)
    }
}

@Composable
fun PosterCard(
    item: ContentItem,
    onClick: () -> Unit,
    /** null: bulunduğu hücreyi doldurur (ızgara); varsayılan: ayardaki afiş boyutu */
    width: Dp? = posterWidth(LocalUi.current?.posterSize),
    horizontal: Boolean = false,
    progress: Float? = null,
    subtitle: String? = null,
) {
    Column(
        Modifier
            .then(if (width == null) Modifier.fillMaxWidth() else Modifier.width(if (horizontal) width * 1.6f else width))
            .focusFrame(grow = true)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        val ui = LocalUi.current
        val watched = ui?.badgeWatched != false && "${item.sourceKey}|${item.id}" in LocalWatched.current
        Box {
            Poster(item.poster, item.title, Modifier.fillMaxWidth().aspectRatio(if (horizontal) 16f / 9f else 2f / 3f), item.posterHeaders)
            if (watched) {
                Box(Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(50)).background(Accent).padding(horizontal = 6.dp, vertical = 1.dp)) {
                    Text("✓", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (ui?.badgeYear == true && item.year != null) {
                Box(Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xCC000000)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                    Text("${item.year}", color = Color.White, fontSize = 10.sp)
                }
            }
            if (progress != null && progress > 0f && ui?.badgeProgress != false) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0x66000000))) {
                    Box(Modifier.fillMaxWidth(progress).height(4.dp).background(Accent))
                }
            }
        }
        if (ui?.showTitles != false) Text(
            item.title, Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
            color = Color.White, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp,
        )
        if (subtitle != null) Text(subtitle, Modifier.padding(horizontal = 2.dp), color = TextDim, fontSize = 11.sp, maxLines = 1)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
fun RowHeader(title: String, onMore: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (onMore != null) {
            Text(
                "Tümünü göster",
                Modifier.clip(RoundedCornerShape(8.dp)).focusFrame(RoundedCornerShape(8.dp)).clickable(onClick = onMore).padding(8.dp),
                color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
fun ContentRowView(row: ContentRow, onItem: (ContentItem) -> Unit, onMore: (() -> Unit)?) {
    RowHeader(row.title, onMore)
    LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(row.items) { item ->
            PosterCard(item, { onItem(item) }, horizontal = row.horizontal)
        }
    }
}

@Composable
fun CenterMessage(text: String, loading: Boolean = false, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (loading) {
            CircularProgressIndicator(color = Accent)
            Spacer(Modifier.height(16.dp))
        }
        Text(text, color = TextDim, textAlign = TextAlign.Center, fontSize = 15.sp)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

val bottomFade = Brush.verticalGradient(listOf(Color.Transparent, Bg))

fun formatTime(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
