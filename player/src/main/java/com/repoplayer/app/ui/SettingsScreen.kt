package com.repoplayer.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repoplayer.app.App
import com.repoplayer.app.R

// ===================== Ortak ayar bileşenleri =====================

val CardBg = Color(0xFF151922)
val IconBg = Color(0xFF232A36)

@Composable
fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp)
            .clip(RoundedCornerShape(24.dp)).background(CardBg).padding(vertical = 6.dp),
        content = content,
    )
}

@Composable
fun CardDivider() = HorizontalDivider(Modifier.padding(start = 88.dp, end = 20.dp), color = Color(0xFF242B37))

@Composable
fun NavRow(icon: Int, title: String, subtitle: String?, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp).clip(RoundedCornerShape(18.dp))
            .focusFrame(RoundedCornerShape(18.dp)).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(IconBg), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), null, tint = Accent, modifier = Modifier.size(26.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, color = TextDim, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) trailing() else Icon(painterResource(R.drawable.ic_chevron), null, tint = TextDim)
    }
}

@Composable
fun SwitchRow(title: String, desc: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp).clip(RoundedCornerShape(16.dp))
            .focusFrame(RoundedCornerShape(16.dp)).clickable { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Color.White, fontSize = 15.sp)
            if (desc != null) Text(desc, color = TextDim, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Switch(checked = checked, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Color(0xFF00201A)))
    }
}

@Composable
fun ChoiceRow(title: String, desc: String?, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(title, color = Color.White, fontSize = 15.sp)
        if (desc != null) Text(desc, color = TextDim, fontSize = 12.sp)
        Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { i, label ->
                FilterChip(
                    selected = i == selected,
                    onClick = { onSelect(i) },
                    label = { Text(label) },
                    modifier = Modifier.focusFrame(RoundedCornerShape(8.dp)),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent, selectedLabelColor = Color.Black),
                )
            }
        }
    }
}

@Composable
fun SliderRow(title: String, value: Int, range: IntRange, step: Int, format: (Int) -> String, onChange: (Int) -> Unit) {
    var v by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row {
            Text(title, Modifier.weight(1f), color = Color.White, fontSize = 15.sp)
            Text(format(v.toInt()), color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = v,
            onValueChange = { v = (Math.round(it / step) * step).toFloat() },
            onValueChangeFinished = { onChange(v.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = ((range.last - range.first) / step - 1).coerceAtLeast(0),
            modifier = Modifier.focusFrame(RoundedCornerShape(12.dp)),
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
        )
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, Modifier.padding(start = 28.dp, top = 16.dp, bottom = 2.dp), color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun SettingsField(value: String, label: String, onChange: (String) -> Unit, onDone: (() -> Unit)? = null) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        label = { Text(label) }, singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, cursorColor = Accent),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone?.invoke() }),
    )
}

@Composable
fun SubPage(vm: AppViewModel, title: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopBar(title, { vm.back() })
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp), content = content)
    }
}

// ===================== Ana ayar ekranı =====================

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val page = vm.settingsStack.lastOrNull()
    if (page != null) {
        SettingsPageView(vm, page)
        return
    }
    val context = LocalContext.current
    var editName by remember { mutableStateOf(false) }
    val sourceCount = vm.entries.size

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 10.dp, bottom = 40.dp)) {
        // ---- Profil ----
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp).clip(RoundedCornerShape(26.dp)).background(CardBg)
                    .focusFrame(RoundedCornerShape(26.dp)).clickable { editName = true }.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(76.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Accent, Color(0xFF1B6F62))))
                        .padding(3.dp).clip(CircleShape).background(Color(0xFF1A1F29)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(vm.ui.profileName.take(1).uppercase(), color = Accent, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f).padding(horizontal = 18.dp)) {
                    Text(vm.ui.profileName, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Aktif profil · $sourceCount kaynak", color = TextDim, fontSize = 14.sp)
                }
                Icon(painterResource(R.drawable.ic_chevron), null, tint = TextDim)
            }
        }

        // ---- Güncelleme bildirimi ----
        vm.update?.let { up ->
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp).clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF15433B)).focusFrame(RoundedCornerShape(20.dp))
                        .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(up.url))) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_backup), null, tint = Accent)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("Yeni sürüm hazır: v${up.version}", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("İndirmek için dokun", color = Accent, fontSize = 13.sp)
                    }
                }
            }
        }

        // ---- Plus + Geliştirici ----
        item {
            SettingsCard {
                NavRow(R.drawable.ic_star, "Plus Menü", "Kategoriler, Kaynak düzeni, Rozetler", { vm.openSettings("plus") })
                CardDivider()
                NavRow(R.drawable.ic_code, "Geliştirici", "Hakkında, günlük ve iletişim", { vm.openSettings("dev") })
            }
        }
        // ---- Ana ayarlar ----
        item {
            SettingsCard {
                NavRow(R.drawable.ic_tune, "Genel", "Dil, başlangıç ekranı, repo güncelleme", { vm.openSettings("general") })
                CardDivider()
                NavRow(R.drawable.ic_play, "Oynatıcı", "Altyazılar, oynatıcı özellikleri, jestler, tampon", { vm.openSettings("player") })
                CardDivider()
                NavRow(R.drawable.ic_providers, "Sağlayıcılar", "TMDB, Nuvio sağlayıcıları, zaman aşımı", { vm.openSettings("providers") })
                CardDivider()
                NavRow(R.drawable.ic_layout, "Düzen", "Görünüm, afiş boyutu, menü yerleşimi", { vm.openSettings("layout") })
                CardDivider()
                NavRow(R.drawable.ic_bolt, "Hız ve Optimizasyon", "Önbellek, paralel istek, görsel kalitesi", { vm.openSettings("performance") })
            }
        }
        item {
            SettingsCard {
                NavRow(R.drawable.ic_backup, "Güncellemeler ve Yedekleme", "Uygulama güncellemesi, yedek al / geri yükle", { vm.openSettings("backup") })
            }
        }
        item {
            SettingsCard {
                NavRow(
                    R.drawable.ic_extension, "Eklentiler",
                    if (vm.repos.isEmpty()) "Depo ekle" else "Depo ekle · ${vm.repos.size} repo, $sourceCount kaynak",
                    { vm.openSettings("extensions") },
                )
            }
        }
        item {
            Text(
                "RepoPlayer v${App.instance.versionName} · CS3 + Nuvio + Stremio",
                Modifier.fillMaxWidth().padding(20.dp), color = TextDim, fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }

    if (editName) {
        var name by remember { mutableStateOf(vm.ui.profileName) }
        AlertDialog(
            onDismissRequest = { editName = false },
            containerColor = Surface1,
            title = { Text("Profil adı") },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, cursorColor = Accent),
                )
            },
            confirmButton = {
                Button(onClick = { vm.updatePrefs { profileName = name }; editName = false }, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                    Text("Kaydet", color = Color.Black)
                }
            },
            dismissButton = { TextButton(onClick = { editName = false }) { Text("Vazgeç", color = TextDim) } },
        )
    }
}

@Composable
fun SmallChip(text: String, color: Color = Accent) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp)) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Gap(h: Int = 8) = Spacer(Modifier.height(h.dp).width(1.dp))
