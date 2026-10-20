package com.repoplayer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.repoplayer.app.R
import com.repoplayer.app.data.ContentItem
import com.repoplayer.app.data.RepoType

/** Alttaki "HDFilmCehennemi ⌄" gibi kaynak seçici hap */
@Composable
fun SourcePill(vm: AppViewModel, modifier: Modifier = Modifier) {
    val sel = vm.selected ?: return
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xF0101A1A))
            .border(1.dp, Accent.copy(alpha = 0.7f), RoundedCornerShape(50))
            .focusFrame(RoundedCornerShape(50))
            .clickable { vm.showPicker = true }
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Accent))
        Spacer(Modifier.width(10.dp))
        Text(sel.name, color = Accent, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 6.dp))
        Icon(painterResource(R.drawable.ic_expand), null, tint = Accent, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun NoSources(vm: AppViewModel) = CenterMessage(
    "Henüz kaynak eklenmedi.\n\nCloudStream repo.json, Nuvio manifest.json\nveya Stremio eklenti adresi ekleyin.",
) {
    Button(onClick = { vm.tab = 3 }, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
        Text("Repo ekle", color = Color.Black)
    }
}

@Composable
fun HomeScreen(vm: AppViewModel) {
    if (vm.selected == null) { NoSources(vm); return }
    val sel = vm.selected!!
    val continueList = vm.history.filter { it.item.sourceKey == sel.key && !it.finished }.distinctBy { it.item.id }.take(20)

    Box(Modifier.fillMaxSize()) {
        when {
            vm.rows.isEmpty() && vm.homeLoading -> CenterMessage("${sel.name} yükleniyor…", loading = true)
            vm.rows.isEmpty() && vm.homeError != null -> CenterMessage(vm.homeError!!) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.loadHome() }, colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text("Tekrar dene", color = Color.Black) }
                    if (sel.type == RepoType.NUVIO) TextButton(onClick = { vm.tab = 3 }) { Text("Ayarlar", color = Accent) }
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                item {
                    if (vm.homeLoading) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Accent, trackColor = Color.Transparent)
                }
                if (continueList.isNotEmpty()) item {
                    RowHeader("İzlemeye devam et", null)
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(continueList) { rec ->
                            PosterCard(rec.item, { vm.openDetails(rec.item) }, progress = rec.progress, subtitle = rec.episodeLabel)
                        }
                    }
                }
                items(vm.rows) { row ->
                    ContentRowView(row, { vm.openDetails(it) }, if (row.moreKey != null) ({ vm.openMore(row) }) else null)
                }
            }
        }
        SourcePill(vm, Modifier.align(Alignment.BottomEnd).padding(16.dp))
    }
}

@Composable
fun ItemGrid(items: List<ContentItem>, vm: AppViewModel, header: (@Composable () -> Unit)? = null, footer: (@Composable () -> Unit)? = null, onEnd: (() -> Unit)? = null) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(112.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (header != null) item(span = { GridItemSpan(maxLineSpan) }) { header() }
        items(items) { item -> PosterCard(item, { vm.openDetails(item) }, width = null) }
        if (footer != null) item(span = { GridItemSpan(maxLineSpan) }) { footer() }
        if (onEnd != null) item(span = { GridItemSpan(maxLineSpan) }) { LaunchedEffect(items.size) { onEnd() } }
    }
}

@Composable
fun TopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.focusFrame(CircleShape)) {
            Icon(painterResource(R.drawable.ic_back), "Geri", tint = Color.White)
        }
        Text(title, Modifier.weight(1f), color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

@Composable
fun MoreScreen(vm: AppViewModel, state: MoreState) {
    Column(Modifier.fillMaxSize()) {
        TopBar(state.row.title, { vm.back() })
        ItemGrid(
            state.items, vm,
            footer = { if (state.loading) Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) } },
            onEnd = if (state.end) null else ({ vm.loadMore(state) }),
        )
    }
}

@Composable
fun SearchScreen(vm: AppViewModel) {
    if (vm.selected == null) { NoSources(vm); return }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = vm.query,
                onValueChange = { vm.query = it },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("${vm.selected?.name} içinde ara") },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search() }),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, cursorColor = Accent),
            )
            when {
                vm.searching -> CenterMessage("Aranıyor…", loading = true)
                vm.searchError != null -> CenterMessage(vm.searchError!!)
                vm.searchResults.isEmpty() -> CenterMessage("Aramak istediğiniz film veya diziyi yazın")
                else -> ItemGrid(vm.searchResults, vm)
            }
        }
        SourcePill(vm, Modifier.align(Alignment.BottomEnd).padding(16.dp))
    }
}

@Composable
fun LibraryScreen(vm: AppViewModel) {
    val continueList = vm.history.filter { !it.finished }.distinctBy { it.item.sourceKey + it.item.id }
    if (continueList.isEmpty() && vm.favorites.isEmpty() && vm.history.isEmpty()) {
        CenterMessage("Kütüphane boş.\nİzlediğin ve kaydettiğin içerikler burada görünür.")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        if (continueList.isNotEmpty()) item {
            RowHeader("İzlemeye devam et", null)
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(continueList) { rec ->
                    PosterCard(rec.item, { vm.openDetails(rec.item) }, progress = rec.progress, subtitle = rec.episodeLabel ?: formatTime(rec.position))
                }
            }
        }
        if (vm.favorites.isNotEmpty()) item {
            RowHeader("Kaydedilenler", null)
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(vm.favorites) { PosterCard(it, { vm.openDetails(it) }) }
            }
        }
        if (vm.history.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { RowHeader("Geçmiş", null) }
                    TextButton(onClick = { vm.clearHistory() }) { Text("Temizle", color = Accent) }
                }
            }
            items(vm.history.take(100)) { rec ->
                Row(
                    Modifier.fillMaxWidth().focusFrame(RoundedCornerShape(10.dp)).clickable { vm.openDetails(rec.item) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Poster(rec.item.poster, rec.item.title, Modifier.width(48.dp).height(72.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(rec.item.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(rec.episodeLabel, "${formatTime(rec.position)} / ${formatTime(rec.duration)}").joinToString(" · "),
                            color = TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(progress = { rec.progress }, modifier = Modifier.fillMaxWidth().height(3.dp), color = Accent, trackColor = Surface2)
                    }
                    IconButton(onClick = { vm.removeHistory(rec) }) { Icon(painterResource(R.drawable.ic_delete), "Sil", tint = TextDim) }
                }
            }
        }
    }
}

/** Kaynak ikonu (yoksa baş harf) */
@Composable
fun SourceIcon(url: String?, name: String, size: Int = 36) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(8.dp)).background(Surface2), contentAlignment = Alignment.Center) {
        if (url.isNullOrBlank()) Text(name.take(1).uppercase(), color = Accent, fontWeight = FontWeight.Bold)
        else AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun rememberFiltered(all: List<com.repoplayer.app.data.SourceEntry>, filter: String) = remember(all, filter) {
    derivedStateOf {
        val f = filter.trim().lowercase()
        if (f.isEmpty()) all else all.filter { it.name.lowercase().contains(f) || it.group.lowercase().contains(f) || (it.language ?: "").lowercase() == f }
    }
}.value
