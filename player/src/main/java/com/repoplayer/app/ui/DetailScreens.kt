package com.repoplayer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.repoplayer.app.R
import com.repoplayer.app.data.Details
import com.repoplayer.app.data.Episode
import com.repoplayer.app.data.PlayerChoice
import com.repoplayer.app.data.StreamType

@Composable
fun DetailScreen(vm: AppViewModel, state: DetailState) {
    val d = state.details
    Column(Modifier.fillMaxSize()) {
        TopBar(d?.title ?: state.item.title, { vm.back() }) {
            val fav = vm.favorites.any { it.sourceKey == state.item.sourceKey && it.id == state.item.id }
            IconButton(onClick = { vm.toggleFavorite(d?.let { it.item.copy(title = it.title, poster = it.poster ?: it.item.poster) } ?: state.item) }, modifier = Modifier.focusFrame(CircleShape)) {
                Icon(painterResource(if (fav) R.drawable.ic_bookmark else R.drawable.ic_bookmark_border), "Kaydet", tint = if (fav) Accent else Color.White)
            }
        }
        when {
            state.loading -> CenterMessage("Yükleniyor…", loading = true)
            state.error != null -> CenterMessage(state.error!!)
            d != null -> DetailContent(vm, state, d)
        }
    }
}

@Composable
private fun DetailContent(vm: AppViewModel, state: DetailState, d: Details) {
    val context = LocalContext.current
    val seasons = d.episodes.mapNotNull { it.season }.distinct().sorted()
    val shown = if (seasons.size > 1) d.episodes.filter { it.season == state.season } else d.episodes
    val last = vm.lastWatched(d.item)
    var expanded by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(230.dp)) {
                val bg = d.backdrop ?: d.poster
                if (bg != null) AsyncImage(model = bg, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x33000000), Bg))))
                Row(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalAlignment = Alignment.Bottom) {
                    Poster(d.poster, d.title, Modifier.width(100.dp).aspectRatio(2f / 3f))
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(d.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        val meta = listOfNotNull(d.year?.toString(), if (d.isSeries) "${d.episodes.size} bölüm" else null).joinToString(" · ")
                        if (meta.isNotEmpty()) Text(meta, color = TextDim, fontSize = 13.sp)
                        if (d.tags.isNotEmpty()) Text(d.tags.take(4).joinToString(" · "), color = Accent, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val resumeEp = last?.episodeId?.let { id -> d.episodes.firstOrNull { it.id == id } }
                val label: String
                val target: Episode?
                when {
                    !d.isSeries -> {
                        target = null
                        label = if (last != null && !last.finished && last.position > 0) "Devam et · ${formatTime(last.position)}" else "Oynat"
                    }
                    resumeEp != null -> {
                        // Bitti ise sonraki bölüm
                        val next = if (last?.finished == true) d.episodes.getOrNull(d.episodes.indexOf(resumeEp) + 1) ?: resumeEp else resumeEp
                        target = next
                        label = "Devam et · ${next.name.take(26)}"
                    }
                    else -> {
                        target = d.episodes.first()
                        label = "İlk bölümü oynat"
                    }
                }
                Button(
                    onClick = { vm.findLinks(d, target, context = context) },
                    modifier = Modifier.weight(1f).focusFrame(RoundedCornerShape(50)),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                ) {
                    Icon(painterResource(R.drawable.ic_play), null, tint = Color.Black)
                    Spacer(Modifier.width(6.dp))
                    Text(label, color = Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (!d.plot.isNullOrBlank()) item {
            Text(
                d.plot,
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp).clickable { expanded = !expanded },
                color = Color(0xFFCDD3DD), fontSize = 14.sp, lineHeight = 20.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis,
            )
        }
        if (seasons.size > 1) item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(seasons) { s ->
                    FilterChip(
                        selected = state.season == s,
                        onClick = { state.season = s },
                        label = { Text(if (s == 0) "Özel" else "$s. Sezon") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent, selectedLabelColor = Color.Black),
                    )
                }
            }
        }
        if (d.isSeries) item { Text("Bölümler", Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        itemsIndexed(shown) { _, ep ->
            val rec = vm.progressFor(d.item, ep.id)
            Row(
                Modifier.fillMaxWidth().focusFrame(RoundedCornerShape(10.dp)).clickable { vm.findLinks(d, ep, context = context) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(120.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Surface2)) {
                    if (ep.poster != null) AsyncImage(model = ep.poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Text("${ep.episode ?: ""}", Modifier.align(Alignment.Center), color = TextDim, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    if (rec != null) LinearProgressIndicator(progress = { rec.progress }, modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp), color = Accent, trackColor = Color(0x66000000))
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(ep.name, color = if (rec?.finished == true) TextDim else Color.White, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!ep.description.isNullOrBlank()) Text(ep.description, color = TextDim, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (d.recommendations.isNotEmpty()) item {
            RowHeader("Benzer içerikler", null)
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(d.recommendations) { PosterCard(it, { vm.openDetails(it) }) }
            }
        }
    }
}

@Composable
fun LinksSheet(vm: AppViewModel, state: LinksState) {
    val context = LocalContext.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { vm.closeLinks() }, sheetState = sheet, containerColor = Surface1) {
        Column(Modifier.fillMaxWidth().heightIn(max = 620.dp)) {
            Text(
                listOfNotNull(state.details.title, state.episode?.name).joinToString(" · "),
                Modifier.padding(horizontal = 20.dp), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.loading) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = Accent, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    "${state.links.size} kaynak · ${state.subtitles.size} altyazı" + if (state.loading) " · aranıyor…" else "",
                    color = TextDim, fontSize = 13.sp,
                )
            }
            if (state.links.isNotEmpty()) {
                Button(
                    onClick = {
                        val best = state.links.indices.maxByOrNull { state.links[it].qualityScore } ?: 0
                        vm.play(context, state, best)
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).focusFrame(RoundedCornerShape(50)),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                ) { Text("En iyi kaynağı oynat", color = Color.Black) }
            }
            if (!state.loading && state.error != null && state.links.isEmpty()) {
                Text(state.error!!, Modifier.padding(20.dp), color = TextDim)
            }
            val listState = rememberLazyListState()
            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
                itemsIndexed(state.links) { i, link ->
                    Row(
                        Modifier.fillMaxWidth().focusFrame(RoundedCornerShape(10.dp)).clickable { vm.play(context, state, i) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Surface2).padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Text(link.quality ?: when (link.type) { StreamType.HLS -> "HLS"; StreamType.DASH -> "DASH"; else -> "MP4" }, color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(link.name, Modifier.weight(1f).padding(horizontal = 12.dp), color = Color.White, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { vm.play(context, state, i, PlayerChoice.ASK) }, modifier = Modifier.focusFrame(CircleShape)) {
                            Icon(painterResource(R.drawable.ic_open), "Harici oynatıcı", tint = TextDim)
                        }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = Color(0xFF232A36))
                }
            }
        }
    }
}

@Composable
fun SourcePickerSheet(vm: AppViewModel) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var filter by remember { mutableStateOf("") }
    val list = rememberFiltered(vm.entries, filter)
    ModalBottomSheet(onDismissRequest = { vm.showPicker = false }, sheetState = sheet, containerColor = Surface1) {
        Column(Modifier.fillMaxWidth().heightIn(max = 640.dp)) {
            Text("Kaynak seç", Modifier.padding(horizontal = 20.dp), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (vm.entries.size > 8) {
                androidx.compose.material3.OutlinedTextField(
                    value = filter, onValueChange = { filter = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Kaynak ara (ad veya dil: tr)") }, singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                list.groupBy { it.group }.forEach { (group, entries) ->
                    item { Text(group, Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp), color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    items(entries) { e ->
                        val selected = e.key == vm.selected?.key
                        Row(
                            Modifier.fillMaxWidth().focusFrame(RoundedCornerShape(10.dp)).clickable { vm.select(e) }
                                .background(if (selected) Color(0xFF15433B) else Color.Transparent)
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SourceIcon(e.icon, e.name)
                            Text(e.name, Modifier.weight(1f).padding(horizontal = 12.dp), color = if (selected) Accent else Color.White, fontSize = 15.sp)
                            e.language?.let { Text(it.uppercase(), color = TextDim, fontSize = 12.sp) }
                        }
                    }
                }
                if (list.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Kaynak yok", color = TextDim)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { vm.showPicker = false; vm.tab = 3 }) { Text("Repo ekle", color = Accent) }
                    }
                }
            }
        }
    }
}
