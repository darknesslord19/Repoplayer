package com.repoplayer.app.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repoplayer.app.App
import com.repoplayer.app.R
import com.repoplayer.app.data.Logger
import com.repoplayer.app.data.PlayerChoice
import com.repoplayer.app.data.RepoType
import com.repoplayer.app.data.SourceEntry
import com.repoplayer.app.source.cs3.Cs3Loader
import com.repoplayer.app.source.cs3.PluginStatus
import com.repoplayer.app.source.nuvio.NuvioScraper

private val typeLabel = mapOf(RepoType.CS3 to "CS3", RepoType.NUVIO to "Nuvio", RepoType.STREMIO to "Addon")

@Composable
fun SettingsPageView(vm: AppViewModel, page: SettingsPage) {
    when (page.id) {
        "plus" -> PlusPage(vm)
        "sources" -> SourcesLayoutPage(vm)
        "categories" -> CategoriesPage(vm)
        "badges" -> BadgesPage(vm)
        "dev" -> DevPage(vm)
        "log" -> LogPage(vm)
        "general" -> GeneralPage(vm)
        "player" -> PlayerPage(vm)
        "providers" -> ProvidersPage(vm)
        "layout" -> LayoutPage(vm)
        "performance" -> PerformancePage(vm)
        "backup" -> BackupPage(vm)
        "extensions" -> ExtensionsPage(vm)
        "repo" -> RepoPage(vm, page.arg ?: "")
        else -> SubPage(vm, "Ayarlar") {}
    }
}

// ===================== Plus Menü =====================

@Composable
private fun PlusPage(vm: AppViewModel) = SubPage(vm, "Plus Menü") {
    item {
        SettingsCard {
            NavRow(R.drawable.ic_home, "Kategoriler", "Ana sayfadaki satırları göster / gizle (${vm.selected?.name ?: "kaynak yok"})", { vm.openSettings("categories") })
            CardDivider()
            NavRow(R.drawable.ic_library, "Kaynak düzeni", "Kaynakları sabitle, seçiciden gizle", { vm.openSettings("sources") })
            CardDivider()
            NavRow(R.drawable.ic_star, "Rozetler", "Afiş üzerindeki işaretler", { vm.openSettings("badges") })
            CardDivider()
            NavRow(R.drawable.ic_extension, "Depo listesi", "${vm.repos.size} repo · ${vm.entries.size} kaynak", { vm.openSettings("extensions") })
        }
    }
}

@Composable
private fun SourcesLayoutPage(vm: AppViewModel) = SubPage(vm, "Kaynak düzeni") {
    item {
        Text(
            "📌 sabitlenen kaynaklar seçicide en üstte görünür. 👁 ile kaynağı seçiciden gizleyebilirsin.",
            Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = TextDim, fontSize = 13.sp,
        )
    }
    vm.repos.forEach { repo ->
        item { SectionLabel(repo.name) }
        item {
            SettingsCard {
                repo.sources.forEachIndexed { i, e ->
                    if (i > 0) CardDivider()
                    val hidden = e.key in vm.ui.hiddenSources
                    val pinned = e.key in vm.ui.pinnedSources
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        SourceIcon(e.icon, e.name, 40)
                        Text(
                            e.name, Modifier.weight(1f).padding(horizontal = 14.dp),
                            color = if (hidden) TextDim else Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(onClick = { vm.toggleSourcePinned(e.key) }, modifier = Modifier.focusFrame(CircleShape)) {
                            Icon(painterResource(R.drawable.ic_pin), "Sabitle", tint = if (pinned) Accent else Color(0xFF4A5363))
                        }
                        IconButton(onClick = { vm.toggleSourceHidden(e.key) }, modifier = Modifier.focusFrame(CircleShape)) {
                            Icon(painterResource(if (hidden) R.drawable.ic_hide else R.drawable.ic_show), "Gizle", tint = if (hidden) TextDim else Accent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoriesPage(vm: AppViewModel) = SubPage(vm, "Kategoriler") {
    val sel = vm.selected
    if (sel == null || vm.rows.isEmpty()) {
        item { Text("Önce ana sayfada bir kaynak açın; satırları burada yönetebilirsiniz.", Modifier.padding(24.dp), color = TextDim) }
        return@SubPage
    }
    item { Text("${sel.name} ana sayfası", Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = TextDim, fontSize = 13.sp) }
    item {
        SettingsCard {
            vm.rows.forEachIndexed { i, row ->
                if (i > 0) CardDivider()
                val visible = "${sel.key}|${row.title}" !in vm.ui.hiddenRows
                SwitchRow(row.title, "${row.items.size} içerik", visible) { vm.toggleRowHidden(row.title) }
            }
        }
    }
}

@Composable
private fun BadgesPage(vm: AppViewModel) = SubPage(vm, "Rozetler") {
    item {
        SettingsCard {
            SwitchRow("İlerleme çubuğu", "Yarım kalan içeriklerde afişin altında", vm.ui.badgeProgress) { v -> vm.updatePrefs { badgeProgress = v } }
            CardDivider()
            SwitchRow("İzlendi işareti", "Bitirilen içeriklerde ✓ rozeti", vm.ui.badgeWatched) { v -> vm.updatePrefs { badgeWatched = v } }
            CardDivider()
            SwitchRow("Yıl rozeti", "Afişin köşesinde yapım yılı", vm.ui.badgeYear) { v -> vm.updatePrefs { badgeYear = v } }
        }
    }
}

// ===================== Geliştirici =====================

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

@Composable
private fun DevPage(vm: AppViewModel) {
    val context = LocalContext.current
    SubPage(vm, "Geliştirici") {
        item {
            SettingsCard {
                Column(Modifier.padding(20.dp)) {
                    Text("RepoPlayer", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Sürüm ${App.instance.versionName} · ${context.packageName}", color = TextDim, fontSize = 13.sp)
                    Gap(10)
                    Text(
                        "CloudStream (.cs3) eklentilerini, Nuvio sağlayıcılarını ve Stremio eklentilerini tek, sade bir oynatıcıda çalıştırır. " +
                            "Eklentiler CloudStream kütüphanesi ile, Nuvio sağlayıcıları gizli bir JS motorunda çalışır.",
                        color = Color(0xFFCDD3DD), fontSize = 14.sp, lineHeight = 20.sp,
                    )
                }
            }
        }
        item {
            SettingsCard {
                NavRow(R.drawable.ic_code, "Günlük kayıtları", "Eklenti yükleme ve oynatma hataları", { vm.openSettings("log") })
                CardDivider()
                NavRow(R.drawable.ic_globe, "GitHub sayfası", AppViewModel.GITHUB_REPO, { openUrl(context, "https://github.com/${AppViewModel.GITHUB_REPO}") })
                CardDivider()
                NavRow(R.drawable.ic_open, "Hata bildir / iletişim", "GitHub Issues üzerinden", { openUrl(context, "https://github.com/${AppViewModel.GITHUB_REPO}/issues") })
            }
        }
        item {
            SettingsCard {
                NavRow(R.drawable.ic_delete, "Eklenti dosyalarını temizle", "İndirilen .cs3 dosyaları silinir, gerekince yeniden indirilir", {
                    Cs3Loader.clearCache(context)
                    vm.toast("Eklenti dosyaları silindi. Uygulamayı yeniden başlatın.")
                }, trailing = {})
                CardDivider()
                NavRow(R.drawable.ic_refresh, "Önbelleği temizle", "Ana sayfa, HTTP ve görsel önbelleği", { vm.clearCaches(context) }, trailing = {})
            }
        }
    }
}

@Composable
private fun LogPage(vm: AppViewModel) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val lines = remember(tick) { Logger.all() }
    SubPage(vm, "Günlük kayıtları") {
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { tick++ }, modifier = Modifier.focusFrame(RoundedCornerShape(50))) { Text("Yenile", color = Accent) }
                OutlinedButton(onClick = {
                    val text = lines.joinToString("\n") { Logger.format(it) }
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("RepoPlayer günlük", text))
                    vm.toast("Günlük panoya kopyalandı")
                }, modifier = Modifier.focusFrame(RoundedCornerShape(50))) { Text("Kopyala", color = Accent) }
                OutlinedButton(onClick = { Logger.clear(); tick++ }, modifier = Modifier.focusFrame(RoundedCornerShape(50))) { Text("Temizle", color = Accent) }
            }
        }
        if (lines.isEmpty()) item { Text("Kayıt yok", Modifier.padding(24.dp), color = TextDim) }
        items(lines) { l ->
            val c = when (l.level) { 'E' -> Color(0xFFFF7A7A); 'W' -> Color(0xFFFFC857); else -> Color(0xFFB8C0CC) }
            Text(
                Logger.format(l), Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp).focusFrame(RoundedCornerShape(6.dp)).clickable { },
                color = c, fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 16.sp,
            )
        }
    }
}

// ===================== Genel =====================

@Composable
private fun GeneralPage(vm: AppViewModel) = SubPage(vm, "Genel") {
    val s = vm.store
    item {
        SettingsCard {
            val langs = listOf("tr-TR" to "Türkçe", "en-US" to "English", "de-DE" to "Deutsch")
            var lang by remember { mutableStateOf(langs.indexOfFirst { it.first == s.language }.coerceAtLeast(0)) }
            ChoiceRow("İçerik dili", "TMDB başlık ve açıklamaları (Nuvio)", langs.map { it.second }, lang) { lang = it; s.language = langs[it].first }
            CardDivider()
            var start by remember { mutableStateOf(s.startTab) }
            ChoiceRow("Açılış ekranı", null, listOf("Ana sayfa", "Ara", "Kütüphane"), start.coerceIn(0, 2)) { start = it; s.startTab = it }
            CardDivider()
            var upd by remember { mutableStateOf(s.updateReposOnStart) }
            SwitchRow("Açılışta repoları güncelle", "Yeni eklenti sürümlerini otomatik getirir", upd) { upd = it; s.updateReposOnStart = it }
        }
    }
    item { SectionLabel("İSS kısıtlamaları") }
    item {
        val context = LocalContext.current
        val initial = remember { s.dns }
        var dns by remember { mutableStateOf(s.dns) }
        SettingsCard {
            ChoiceRow(
                "DNS (DNS-over-HTTPS)",
                "Site veya video açılmıyor, afişler gelmiyorsa operatör engelini aşmak için DNS seç. Eklentiler, oynatıcı ve afişler bu DNS'i kullanır.",
                com.repoplayer.app.data.DNS_PROVIDERS.map { it.first }, dns,
            ) { dns = it; s.dns = it }
            if (dns != initial) {
                CardDivider()
                NavRow(R.drawable.ic_refresh, "Uygulamayı yeniden başlat", "DNS değişikliği yeniden başlatınca etkinleşir", { restartApp(context) })
            }
        }
    }
}

fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}

// ===================== Oynatıcı =====================

@Composable
private fun PlayerPage(vm: AppViewModel) = SubPage(vm, "Oynatıcı") {
    val s = vm.store
    item { SectionLabel("Oynatıcı") }
    item {
        SettingsCard {
            val players = listOf(PlayerChoice.INTERNAL to "Dahili", PlayerChoice.VLC to "VLC", PlayerChoice.MX to "MX Player", PlayerChoice.ASK to "Her seferinde sor")
            var p by remember { mutableStateOf(s.player) }
            ChoiceRow("Varsayılan oynatıcı", null, players.map { it.second }, players.indexOfFirst { it.first == p }) { p = players[it].first; s.player = p }
            CardDivider()
            var resume by remember { mutableStateOf(s.autoResume) }
            SwitchRow("Kaldığı yerden devam et", "Yarım kalan içerik otomatik olarak devam eder", resume) { resume = it; s.autoResume = it }
            CardDivider()
            var nextSrc by remember { mutableStateOf(s.autoNextSource) }
            SwitchRow("Hatalı kaynakta otomatik geç", "Bir bağlantı açılmazsa listedeki sonrakini dener", nextSrc) { nextSrc = it; s.autoNextSource = it }
            CardDivider()
            var nextEp by remember { mutableStateOf(s.autoNextEpisode) }
            SwitchRow("Sonraki bölümü otomatik oynat", "Bölüm bitince sıradakini başlatır", nextEp) { nextEp = it; s.autoNextEpisode = it }
        }
    }
    item { SectionLabel("Kontroller") }
    item {
        SettingsCard {
            var gestures by remember { mutableStateOf(s.gestures) }
            SwitchRow("Dokunma jestleri", "Çift dokunma: sar · Sol kaydırma: parlaklık · Sağ: ses · Yatay: sar", gestures) { gestures = it; s.gestures = it }
            CardDivider()
            val steps = listOf(5, 10, 15, 30)
            var step by remember { mutableStateOf(s.seekStep) }
            ChoiceRow("İleri / geri sarma adımı", "Çift dokunma ve kumanda ok tuşları", steps.map { "$it sn" }, steps.indexOf(step).coerceAtLeast(0)) { step = steps[it]; s.seekStep = step }
            CardDivider()
            var intro by remember { mutableStateOf(s.skipIntroSec) }
            SliderRow("İntro atlama süresi", intro, 30..180, 5, { "$it sn" }) { intro = it; s.skipIntroSec = it }
            CardDivider()
            var resize by remember { mutableStateOf(s.defaultResize) }
            ChoiceRow("Varsayılan görüntü boyutu", null, listOf("Sığdır", "Yakınlaştır", "Doldur"), resize) { resize = it; s.defaultResize = it }
            CardDivider()
            val speeds = listOf(75, 100, 125, 150, 200)
            var speed by remember { mutableStateOf(s.defaultSpeed) }
            ChoiceRow("Varsayılan hız", null, speeds.map { "${it / 100.0}x" }, speeds.indexOf(speed).coerceAtLeast(1)) { speed = speeds[it]; s.defaultSpeed = speed }
            CardDivider()
            var buf by remember { mutableStateOf(s.bufferLevel) }
            ChoiceRow("Tampon (ön yükleme)", "Yüksek: daha az takılma, daha çok bellek", listOf("Normal", "Yüksek", "Çok yüksek"), buf) { buf = it; s.bufferLevel = it }
        }
    }
    item { SectionLabel("Altyazılar") }
    item {
        SettingsCard {
            var os by remember { mutableStateOf(s.openSubtitles) }
            SwitchRow("OpenSubtitles altyazıları", "IMDb kimliği olan içeriklerde otomatik altyazı arar", os) { os = it; s.openSubtitles = it }
            CardDivider()
            var langs by remember { mutableStateOf(s.subtitleLangs) }
            SettingsField(langs, "Altyazı dilleri (tur,eng,ger…)", { langs = it; s.subtitleLangs = it })
            CardDivider()
            var size by remember { mutableStateOf(s.subtitleSize) }
            SliderRow("Altyazı boyutu", size, 50..200, 10, { "%$it" }) { size = it; s.subtitleSize = it }
            CardDivider()
            var color by remember { mutableStateOf(s.subtitleColor) }
            ChoiceRow("Altyazı rengi", null, listOf("Beyaz", "Sarı", "Turkuaz"), color) { color = it; s.subtitleColor = it }
            CardDivider()
            var bg by remember { mutableStateOf(s.subtitleBackground) }
            SwitchRow("Altyazı arka planı", "Okunabilirlik için yarı saydam kutu", bg) { bg = it; s.subtitleBackground = it }
        }
    }
}

// ===================== Sağlayıcılar =====================

@Composable
private fun ProvidersPage(vm: AppViewModel) = SubPage(vm, "Sağlayıcılar") {
    val s = vm.store
    item { SectionLabel("TMDB") }
    item {
        SettingsCard {
            var key by remember { mutableStateOf(s.tmdbKey) }
            SettingsField(key, "TMDB API anahtarı (v3 key veya v4 token)", { key = it; s.tmdbKey = it }) { vm.settingsChanged() }
            Text(
                "Nuvio sağlayıcıları içerikleri TMDB kimliğiyle bulur. themoviedb.org → Ayarlar → API bölümünden ücretsiz anahtar alabilirsin.",
                Modifier.padding(horizontal = 20.dp, vertical = 6.dp), color = TextDim, fontSize = 12.sp,
            )
        }
    }
    item { SectionLabel("Nuvio") }
    item {
        SettingsCard {
            var t by remember { mutableStateOf(s.nuvioTimeoutSec) }
            SliderRow("Sağlayıcı zaman aşımı", t, 10..120, 5, { "$it sn" }) { t = it; s.nuvioTimeoutSec = it }
            val nuvio = vm.repos.filter { it.type == RepoType.NUVIO }
            if (nuvio.isEmpty()) {
                Text("Ekli Nuvio reposu yok.", Modifier.padding(20.dp), color = TextDim, fontSize = 13.sp)
            }
            nuvio.forEach { r ->
                CardDivider()
                NavRow(R.drawable.ic_providers, r.name, "Sağlayıcıları aç / kapat", { vm.openSettings("repo", r.url) })
            }
        }
    }
}

// ===================== Düzen =====================

@Composable
private fun LayoutPage(vm: AppViewModel) = SubPage(vm, "Düzen") {
    item {
        SettingsCard {
            ChoiceRow("Afiş boyutu", null, listOf("Küçük", "Orta", "Büyük"), vm.ui.posterSize) { v -> vm.updatePrefs { posterSize = v } }
            CardDivider()
            SwitchRow("Afiş altında başlık", null, vm.ui.showTitles) { v -> vm.updatePrefs { showTitles = v } }
            CardDivider()
            ChoiceRow("Menü yerleşimi", "Otomatik: TV ve tablette yan menü, telefonda alt menü", listOf("Otomatik", "Yan menü", "Alt menü"), vm.ui.navMode) { v -> vm.updatePrefs { navMode = v } }
        }
    }
}

// ===================== Hız ve optimizasyon =====================

@Composable
private fun PerformancePage(vm: AppViewModel) {
    val context = LocalContext.current
    SubPage(vm, "Hız ve Optimizasyon") {
        val s = vm.store
        item {
            SettingsCard {
                var hc by remember { mutableStateOf(s.homeCache) }
                SwitchRow("Ana sayfa önbelleği", "Kaynak açılırken son içerikler anında gösterilir, arkada yenilenir", hc) { hc = it; s.homeCache = it }
                CardDivider()
                var http by remember { mutableStateOf(s.httpCache) }
                SwitchRow("HTTP önbelleği (50 MB)", "Tekrarlanan istekleri hızlandırır · yeniden başlatınca etkin", http) { http = it; s.httpCache = it }
                CardDivider()
                NavRow(R.drawable.ic_globe, "DNS ve İSS kısıtlamaları", "Şu an: ${com.repoplayer.app.data.DNS_PROVIDERS.getOrNull(s.dns)?.first}", { vm.openSettings("general") })
                CardDivider()
                var par by remember { mutableStateOf(s.parallelRequests) }
                SliderRow("Paralel istek sayısı", par, 1..12, 1, { "$it" }) { par = it; s.parallelRequests = it }
                CardDivider()
                var to by remember { mutableStateOf(s.requestTimeoutSec) }
                SliderRow("İstek zaman aşımı", to, 10..60, 5, { "$it sn" }) { to = it; s.requestTimeoutSec = it }
                CardDivider()
                var q by remember { mutableStateOf(s.imageQuality) }
                ChoiceRow("Görsel kalitesi", "Düşük: daha hızlı yükleme, daha az veri", listOf("Düşük", "Orta", "Yüksek"), q) { q = it; s.imageQuality = it }
            }
        }
        item {
            SettingsCard {
                NavRow(R.drawable.ic_refresh, "Önbelleği temizle", "Ana sayfa, HTTP ve görsel önbelleği", { vm.clearCaches(context) }, trailing = {})
            }
        }
        item {
            Text(
                "İpucu: Yavaş internette paralel istek sayısını 2–3'e düşür. Hızlı bağlantıda 6–8 ana sayfayı daha hızlı doldurur.",
                Modifier.padding(24.dp), color = TextDim, fontSize = 12.sp,
            )
        }
    }
}

// ===================== Güncellemeler ve yedekleme =====================

@Composable
private fun BackupPage(vm: AppViewModel) {
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(vm.exportBackup().toByteArray()) }
            vm.toast("Yedek kaydedildi")
        }.onFailure { vm.toast("Yedek kaydedilemedi: ${it.message}") }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.reader().readText() }
        }.onSuccess { text -> if (text != null) vm.importBackup(text) }
            .onFailure { vm.toast("Yedek okunamadı: ${it.message}") }
    }
    SubPage(vm, "Güncellemeler ve Yedekleme") {
        item { SectionLabel("Uygulama") }
        item {
            SettingsCard {
                NavRow(
                    R.drawable.ic_refresh, "Güncellemeleri denetle",
                    vm.update?.let { "Yeni sürüm: v${it.version} — indirmek için aşağıya dokun" } ?: "Yüklü sürüm: v${App.instance.versionName}",
                    { vm.checkUpdate() },
                    trailing = { if (vm.checkingUpdate) CircularProgressIndicator(Modifier.size(20.dp), color = Accent, strokeWidth = 2.dp) },
                )
                vm.update?.let { up ->
                    CardDivider()
                    NavRow(R.drawable.ic_backup, "v${up.version} sürümünü indir", "APK indirilir, açınca güncellenir", { openUrl(context, up.url) })
                }
            }
        }
        item { SectionLabel("Repolar") }
        item {
            SettingsCard {
                NavRow(R.drawable.ic_extension, "Tüm repoları şimdi güncelle", "${vm.repos.size} repo", { vm.refreshAllRepos() },
                    trailing = { if (vm.repoBusy) CircularProgressIndicator(Modifier.size(20.dp), color = Accent, strokeWidth = 2.dp) })
            }
        }
        item { SectionLabel("Yedekleme") }
        item {
            SettingsCard {
                NavRow(R.drawable.ic_backup, "Yedek al", "Repolar, ayarlar, geçmiş ve kaydedilenler (.json)", { exportLauncher.launch("repoplayer-yedek.json") })
                CardDivider()
                NavRow(R.drawable.ic_refresh, "Yedeği geri yükle", "Daha önce alınmış .json yedeği", { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) })
                CardDivider()
                NavRow(R.drawable.ic_delete, "İzleme geçmişini temizle", "${vm.history.size} kayıt", { vm.clearHistory(); vm.toast("Geçmiş temizlendi") }, trailing = {})
            }
        }
    }
}

// ===================== Eklentiler =====================

@Composable
private fun ExtensionsPage(vm: AppViewModel) {
    var repoUrl by remember { mutableStateOf("") }
    SubPage(vm, "Eklentiler") {
        item {
            SettingsCard {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text("Depo ekle", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = repoUrl, onValueChange = { repoUrl = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        placeholder = { Text("https://…/repo.json veya …/manifest.json") },
                        singleLine = true, shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, cursorColor = Accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { vm.addRepo(repoUrl) { if (it) repoUrl = "" } }),
                    )
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { vm.addRepo(repoUrl) { if (it) repoUrl = "" } },
                            enabled = !vm.repoBusy && repoUrl.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = Accent),
                            modifier = Modifier.focusFrame(RoundedCornerShape(50)),
                        ) { Text("Ekle", color = Color.Black) }
                        if (vm.repoBusy) CircularProgressIndicator(Modifier.padding(start = 12.dp).size(20.dp), color = Accent, strokeWidth = 2.dp)
                    }
                    Text(
                        "CloudStream repo.json / plugins.json (.cs3), Nuvio manifest.json ve Stremio eklenti manifest.json desteklenir.",
                        Modifier.padding(top = 8.dp), color = TextDim, fontSize = 12.sp,
                    )
                }
            }
        }
        if (vm.repos.isNotEmpty()) item { SectionLabel("Ekli depolar") }
        items(vm.repos) { repo ->
            SettingsCard {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp).clip(RoundedCornerShape(18.dp))
                        .focusFrame(RoundedCornerShape(18.dp)).clickable { vm.openSettings("repo", repo.url) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SourceIcon(repo.icon, repo.name, 52)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(repo.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SmallChip(typeLabel[repo.type] ?: "")
                            Text("  ${repo.sources.size} kaynak · içeriği gör", color = TextDim, fontSize = 13.sp)
                        }
                    }
                    Icon(painterResource(R.drawable.ic_chevron), null, tint = TextDim)
                }
            }
        }
    }
}

@Composable
private fun RepoPage(vm: AppViewModel, url: String) {
    val repo = vm.repos.firstOrNull { it.url == url }
    if (repo == null) {
        SubPage(vm, "Depo") { item { Text("Depo bulunamadı", Modifier.padding(24.dp), color = TextDim) } }
        return
    }
    val context = LocalContext.current
    var refreshTick by remember { mutableIntStateOf(0) }
    var testing by remember { mutableStateOf<String?>(null) }
    var scrapers by remember { mutableStateOf<List<NuvioScraper>?>(null) }
    var scraperError by remember { mutableStateOf<String?>(null) }
    var disabled by remember { mutableStateOf(vm.store.disabledScrapers) }
    if (repo.type == RepoType.NUVIO) {
        LaunchedEffect(url) {
            runCatching { vm.nuvioScrapers(repo.sources.first()) }.onSuccess { scrapers = it }.onFailure { scraperError = it.message }
        }
    }

    SubPage(vm, repo.name) {
        item {
            SettingsCard {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    SourceIcon(repo.icon, repo.name, 60)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(repo.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("${typeLabel[repo.type]} · ${repo.sources.size} kaynak", color = TextDim, fontSize = 13.sp)
                        Text(repo.url, color = TextDim, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.refreshRepo(url) }, modifier = Modifier.focusFrame(RoundedCornerShape(50))) {
                        Icon(painterResource(R.drawable.ic_refresh), null, tint = Accent, modifier = Modifier.size(18.dp))
                        Text("  Güncelle", color = Accent)
                    }
                    OutlinedButton(onClick = { vm.removeRepo(url); vm.back() }, modifier = Modifier.focusFrame(RoundedCornerShape(50))) {
                        Icon(painterResource(R.drawable.ic_delete), null, tint = Color(0xFFFF7A7A), modifier = Modifier.size(18.dp))
                        Text("  Kaldır", color = Color(0xFFFF7A7A))
                    }
                }
            }
        }

        if (repo.type == RepoType.NUVIO) {
            item { SectionLabel("Sağlayıcılar") }
            item {
                SettingsCard {
                    val list = scrapers
                    when {
                        scraperError != null -> Text(scraperError!!, Modifier.padding(20.dp), color = TextDim)
                        list == null -> Row(Modifier.padding(20.dp)) { CircularProgressIndicator(Modifier.size(22.dp), color = Accent, strokeWidth = 2.dp) }
                        else -> list.forEachIndexed { i, sc ->
                            if (i > 0) CardDivider()
                            val on = sc.id !in disabled
                            SwitchRow(sc.name, sc.types.joinToString(", ") + (if (!sc.enabled) " · repoda kapalı" else "") + " · v${sc.version}", on) {
                                disabled = if (on) disabled + sc.id else disabled - sc.id
                                vm.store.disabledScrapers = disabled
                            }
                        }
                    }
                }
            }
        }

        item { SectionLabel(if (repo.type == RepoType.CS3) "Eklentiler" else "Kaynak") }
        items(repo.sources) { e ->
            val status = remember(refreshTick, testing) { if (e.type == RepoType.CS3) Cs3Loader.status(e.key) else null }
            SourceRow(
                vm, e, status,
                testing = testing == e.key,
                onOpen = { vm.select(e); vm.settingsStack.clear(); vm.tab = 0 },
                onTest = {
                    testing = e.key
                    vm.testSource(e) { testing = null; refreshTick++ }
                },
                onSettings = {
                    val act = context as? Activity
                    val plugin = Cs3Loader.plugin(e.key) as? com.lagradost.cloudstream3.plugins.Plugin
                    if (act != null && plugin?.openSettings != null) runCatching { plugin.openSettings?.invoke(act) }
                        .onFailure { vm.toast("Eklenti ayarları açılamadı: ${it.message}") }
                },
            )
        }
    }
}

@Composable
private fun SourceRow(
    vm: AppViewModel,
    e: SourceEntry,
    status: PluginStatus?,
    testing: Boolean,
    onOpen: () -> Unit,
    onTest: () -> Unit,
    onSettings: () -> Unit,
) {
    val hidden = e.key in vm.ui.hiddenSources
    SettingsCard {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp).clip(RoundedCornerShape(18.dp))
                .focusFrame(RoundedCornerShape(18.dp)).clickable(onClick = onOpen)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SourceIcon(e.icon, e.name, 46)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(e.name, color = if (hidden) TextDim else Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (e.version > 0) SmallChip("v${e.version}", TextDim)
                    e.language?.let { SmallChip(it.uppercase(), TextDim) }
                    when (status?.state) {
                        PluginStatus.State.LOADED -> SmallChip("Yüklü", Accent)
                        PluginStatus.State.FAILED -> SmallChip("Hata", Color(0xFFFF7A7A))
                        PluginStatus.State.LOADING -> SmallChip("Yükleniyor", Color(0xFFFFC857))
                        null -> Unit
                    }
                    if (hidden) SmallChip("Gizli", TextDim)
                }
                if (status?.state == PluginStatus.State.FAILED && status.message != null) {
                    Text(status.message, color = Color(0xFFFF9A9A), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (status?.state == PluginStatus.State.LOADED && status.apis.isNotEmpty()) {
                    Text(status.apis.joinToString(), color = TextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text("Aç", color = Accent, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.padding(start = 16.dp, end = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (e.type == RepoType.CS3) {
                SmallAction(if (testing) "Yükleniyor…" else "Yükle / test et", onTest)
                if (status?.hasSettings == true) SmallAction("Eklenti ayarları", onSettings)
            }
            SmallAction(if (hidden) "Göster" else "Gizle") { vm.toggleSourceHidden(e.key) }
            SmallAction(if (e.key in vm.ui.pinnedSources) "Sabitlemeyi kaldır" else "Sabitle") { vm.toggleSourcePinned(e.key) }
        }
    }
}

@Composable
private fun SmallAction(text: String, onClick: () -> Unit) {
    Text(
        text,
        Modifier.clip(RoundedCornerShape(10.dp)).focusFrame(RoundedCornerShape(10.dp)).clickable(onClick = onClick)
            .background(Color(0xFF1E2430)).padding(horizontal = 10.dp, vertical = 6.dp),
        color = Color(0xFFCDD3DD), fontSize = 12.sp,
    )
}

@Suppress("unused")
@Composable
private fun SwitchIcon(on: Boolean) = Switch(checked = on, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = Accent))
