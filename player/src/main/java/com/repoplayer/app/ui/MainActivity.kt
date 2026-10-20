package com.repoplayer.app.ui

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lagradost.cloudstream3.CommonActivity
import com.repoplayer.app.R

class MainActivity : ComponentActivity() {
    private var vmRef: AppViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CommonActivity.activity = this
        setContent {
            val vm: AppViewModel = viewModel()
            vmRef = vm
            LaunchedEffect(Unit) {
                handleIntent(intent)
                vm.checkUpdate(silent = true)
            }
            AppTheme { AppRoot(vm, isTv()) }
        }
    }

    private fun isTv(): Boolean {
        val ui = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        return ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            packageManager.hasSystemFeature("android.software.leanback")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Eklenti ayar ekranları ve toast'ları için aktif ekran
        CommonActivity.activity = this
        vmRef?.refreshLocal()
    }

    /** Kumandadaki menü tuşu kaynak seçiciyi açar */
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            vmRef?.let { it.showPicker = !it.showPicker }
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    /** cloudstreamrepo://… veya stremio://…/manifest.json bağlantıları doğrudan eklenir */
    private fun handleIntent(intent: Intent?) {
        val data = intent?.data?.toString() ?: return
        intent.data = null
        vmRef?.let {
            it.tab = 3
            it.settingsStack.clear()
            it.openSettings("extensions")
            it.addRepo(data)
        }
    }
}

private data class Tab(val label: String, val icon: Int)

private val tabs = listOf(
    Tab("Ana sayfa", R.drawable.ic_home),
    Tab("Ara", R.drawable.ic_search),
    Tab("Kütüphane", R.drawable.ic_library),
    Tab("Ayarlar", R.drawable.ic_settings),
)

@Composable
fun AppRoot(vm: AppViewModel, isTv: Boolean) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }
    BackHandler(enabled = vm.showPicker || vm.links != null || vm.stack.isNotEmpty() || vm.tab != 0 || vm.settingsStack.isNotEmpty()) { vm.back() }

    val wide = LocalConfiguration.current.screenWidthDp >= 840
    val rail = when (vm.ui.navMode) {
        1 -> true
        2 -> false
        else -> isTv || wide
    }
    val watched = remember(vm.history) {
        vm.history.filter { it.finished && it.episodeId == null }.map { "${it.item.sourceKey}|${it.item.id}" }.toSet()
    }

    CompositionLocalProvider(LocalUi provides vm.ui, LocalWatched provides watched, LocalRail provides rail) {
        Scaffold(
            containerColor = Bg,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (!rail && vm.stack.isEmpty()) {
                    NavigationBar(containerColor = Surface1) {
                        tabs.forEachIndexed { i, t ->
                            NavigationBarItem(
                                selected = vm.tab == i,
                                onClick = { if (vm.tab == i && i == 3) vm.settingsStack.clear(); vm.tab = i },
                                icon = { Icon(painterResource(t.icon), t.label) },
                                label = { Text(t.label) },
                                modifier = Modifier.focusFrame(RoundedCornerShape(16.dp)),
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Accent,
                                    selectedTextColor = Accent,
                                    indicatorColor = Color(0xFF15433B),
                                    unselectedIconColor = TextDim,
                                    unselectedTextColor = TextDim,
                                ),
                            )
                        }
                    }
                }
            },
        ) { pad ->
            Row(Modifier.fillMaxSize().background(Bg).padding(pad)) {
                if (rail) SideRail(vm)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    when (val page = vm.stack.lastOrNull()) {
                        is Page.Detail -> DetailScreen(vm, page.state)
                        is Page.More -> MoreScreen(vm, page.state)
                        null -> when (vm.tab) {
                            0 -> HomeScreen(vm)
                            1 -> SearchScreen(vm)
                            2 -> LibraryScreen(vm)
                            else -> SettingsScreen(vm)
                        }
                    }
                }
            }
        }

        if (vm.showPicker) SourcePickerSheet(vm)
        vm.links?.let { LinksSheet(vm, it) }
    }
}

/** TV / tablet: sol yan menü (kumandayla sola basınca ulaşılır) */
@Composable
private fun SideRail(vm: AppViewModel) {
    NavigationRail(containerColor = Surface1, modifier = Modifier.fillMaxHeight()) {
        Spacer(Modifier.height(12.dp))
        tabs.forEachIndexed { i, t ->
            NavigationRailItem(
                selected = vm.tab == i && vm.stack.isEmpty(),
                onClick = { vm.stack.clear(); if (vm.tab == i && i == 3) vm.settingsStack.clear(); vm.tab = i },
                icon = { Icon(painterResource(t.icon), t.label) },
                label = { Text(t.label, fontSize = 12.sp) },
                modifier = Modifier.focusFrame(RoundedCornerShape(16.dp)),
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = Accent,
                    selectedTextColor = Accent,
                    indicatorColor = Color(0xFF15433B),
                    unselectedIconColor = TextDim,
                    unselectedTextColor = TextDim,
                ),
            )
        }
        Spacer(Modifier.weight(1f))
        // Kaynak seçici
        vm.selected?.let { sel ->
            NavigationRailItem(
                selected = false,
                onClick = { vm.showPicker = true },
                icon = { SourceIcon(sel.icon, sel.name, 28) },
                label = { Text(sel.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Accent) },
                modifier = Modifier.focusFrame(RoundedCornerShape(16.dp)),
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Suppress("unused")
@Composable
private fun RailSpacer() = Column(Modifier.size(1.dp)) {}
