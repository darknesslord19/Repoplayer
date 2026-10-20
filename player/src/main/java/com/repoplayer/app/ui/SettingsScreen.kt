package com.repoplayer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.repoplayer.app.R
import com.repoplayer.app.data.PlayerChoice
import com.repoplayer.app.data.RepoEntry
import com.repoplayer.app.data.RepoType
import com.repoplayer.app.source.nuvio.NuvioScraper

@Composable
private fun Section(title: String) {
    Text(title, Modifier.padding(start = 16.dp, top = 22.dp, bottom = 8.dp), color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun SwitchRow(title: String, desc: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().focusFrame(RoundedCornerShape(10.dp)).clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp)
            if (desc != null) Text(desc, color = TextDim, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Accent))
    }
}

private val typeLabel = mapOf(RepoType.CS3 to "CS3", RepoType.NUVIO to "Nuvio", RepoType.STREMIO to "Addon")

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val store = vm.store
    var repoUrl by remember { mutableStateOf("") }
    var tmdb by remember { mutableStateOf(store.tmdbKey) }
    var langs by remember { mutableStateOf(store.subtitleLangs) }
    var openSubs by remember { mutableStateOf(store.openSubtitles) }
    var autoNext by remember { mutableStateOf(store.autoNextSource) }
    var player by remember { mutableStateOf(store.player) }
    var subSize by remember { mutableFloatStateOf(store.subtitleSize.toFloat()) }
    var scraperRepo by remember { mutableStateOf<RepoEntry?>(null) }
    val fieldColors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, cursorColor = Accent)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item { Section("Repo / manifest ekle") }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                OutlinedTextField(
                    value = repoUrl, onValueChange = { repoUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://…/repo.json veya …/manifest.json") },
                    singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { vm.addRepo(repoUrl) { if (it) repoUrl = "" } }),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { vm.addRepo(repoUrl) { if (it) repoUrl = "" } },
                        enabled = !vm.repoBusy && repoUrl.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                        modifier = Modifier.focusFrame(RoundedCornerShape(50)),
                    ) { Text("Ekle", color = Color.Black) }
                    if (vm.repoBusy) CircularProgressIndicator(Modifier.padding(start = 12.dp).size(20.dp), color = Accent, strokeWidth = 2.dp)
                }
                Text(
                    "Desteklenenler: CloudStream repo.json / plugins.json (.cs3), Nuvio manifest.json (JS sağlayıcılar), Stremio eklenti manifest.json. cloudstreamrepo:// bağlantıları da çalışır.",
                    Modifier.padding(top = 8.dp), color = TextDim, fontSize = 12.sp,
                )
            }
        }
        if (vm.repos.isNotEmpty()) item { Section("Ekli repolar") }
        items(vm.repos) { repo ->
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                SourceIcon(repo.icon, repo.name, 40)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(repo.name, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${typeLabel[repo.type]} · ${repo.sources.size} kaynak", color = TextDim, fontSize = 12.sp)
                    Text(repo.url, color = TextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (repo.type == RepoType.NUVIO) TextButton(onClick = { scraperRepo = repo }) { Text("Sağlayıcılar", color = Accent, fontSize = 13.sp) }
                IconButton(onClick = { vm.refreshRepo(repo.url) }, modifier = Modifier.focusFrame(CircleShape)) {
                    Icon(painterResource(R.drawable.ic_refresh), "Yenile", tint = TextDim)
                }
                IconButton(onClick = { vm.removeRepo(repo.url) }, modifier = Modifier.focusFrame(CircleShape)) {
                    Icon(painterResource(R.drawable.ic_delete), "Sil", tint = TextDim)
                }
            }
        }

        item { Section("Oynatıcı") }
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(PlayerChoice.INTERNAL to "Dahili", PlayerChoice.VLC to "VLC", PlayerChoice.MX to "MX Player", PlayerChoice.ASK to "Sor").forEach { (c, l) ->
                    FilterChip(
                        selected = player == c,
                        onClick = { player = c; store.player = c },
                        label = { Text(l) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent, selectedLabelColor = Color.Black),
                    )
                }
            }
        }
        item {
            SwitchRow("Hatalı kaynakta otomatik geç", "Bir bağlantı açılmazsa listedeki sonrakini dener", autoNext) {
                autoNext = it; store.autoNextSource = it
            }
        }

        item { Section("Altyazı") }
        item {
            SwitchRow("OpenSubtitles altyazıları", "IMDb kimliği olan içeriklerde otomatik altyazı arar", openSubs) {
                openSubs = it; store.openSubtitles = it
            }
        }
        item {
            OutlinedTextField(
                value = langs, onValueChange = { langs = it; store.subtitleLangs = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                label = { Text("Altyazı dilleri (tur,eng,ger…)") }, singleLine = true,
                shape = RoundedCornerShape(12.dp), colors = fieldColors,
            )
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Altyazı boyutu: %${subSize.toInt()}", color = Color.White, fontSize = 14.sp)
                Slider(
                    value = subSize, onValueChange = { subSize = it }, valueRange = 50f..200f, steps = 14,
                    onValueChangeFinished = { store.subtitleSize = subSize.toInt() },
                    colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
                )
            }
        }

        item { Section("Nuvio / TMDB") }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                OutlinedTextField(
                    value = tmdb, onValueChange = { tmdb = it; store.tmdbKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("TMDB API anahtarı (v3 key veya v4 token)") }, singleLine = true,
                    shape = RoundedCornerShape(12.dp), colors = fieldColors,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { vm.settingsChanged() }),
                )
                Text(
                    "Nuvio sağlayıcıları içerikleri TMDB kimliğiyle bulur. themoviedb.org > Ayarlar > API bölümünden ücretsiz anahtar alabilirsiniz.",
                    Modifier.padding(top = 6.dp), color = TextDim, fontSize = 12.sp,
                )
            }
        }

        item { Section("Veri") }
        item {
            Row(Modifier.padding(horizontal = 8.dp)) {
                TextButton(onClick = { vm.clearHistory(); vm.toast("Geçmiş temizlendi") }) { Text("İzleme geçmişini temizle", color = Accent) }
            }
        }
        item {
            Text("RepoPlayer 1.0 · CS3 + Nuvio + Stremio", Modifier.padding(16.dp), color = TextDim, fontSize = 12.sp)
        }
    }

    scraperRepo?.let { repo -> ScraperDialog(vm, repo) { scraperRepo = null } }
}

@Composable
private fun ScraperDialog(vm: AppViewModel, repo: RepoEntry, onClose: () -> Unit) {
    var list by remember { mutableStateOf<List<NuvioScraper>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var disabled by remember { mutableStateOf(vm.store.disabledScrapers) }
    LaunchedEffect(repo.url) {
        runCatching { vm.nuvioScrapers(repo.sources.first()) }
            .onSuccess { list = it }.onFailure { error = it.message }
    }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Surface1,
        title = { Text("${repo.name} sağlayıcıları") },
        text = {
            when {
                error != null -> Text(error!!, color = TextDim)
                list == null -> CircularProgressIndicator(color = Accent)
                else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(list!!) { s ->
                        val on = s.id !in disabled
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).focusFrame(RoundedCornerShape(8.dp)).clickable {
                                disabled = if (on) disabled + s.id else disabled - s.id
                                vm.store.disabledScrapers = disabled
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = on, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = Accent))
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(s.name, color = if (s.enabled) Color.White else TextDim)
                                Text(s.types.joinToString(", ") + if (!s.enabled) " · repoda kapalı" else "", color = TextDim, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Tamam", color = Accent) } },
    )
}

@Suppress("unused")
private fun Modifier.card() = this.clip(RoundedCornerShape(12.dp)).background(Surface2)
