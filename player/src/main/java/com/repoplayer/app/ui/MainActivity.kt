package com.repoplayer.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.repoplayer.app.R

class MainActivity : ComponentActivity() {
    private var vmRef: AppViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: AppViewModel = viewModel()
            vmRef = vm
            LaunchedEffect(Unit) { handleIntent(intent) }
            AppTheme { AppRoot(vm) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        vmRef?.refreshLocal()
    }

    /** cloudstreamrepo://… veya stremio://…/manifest.json bağlantıları doğrudan eklenir */
    private fun handleIntent(intent: Intent?) {
        val data = intent?.data?.toString() ?: return
        intent.data = null
        vmRef?.let {
            it.tab = 3
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
fun AppRoot(vm: AppViewModel) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }
    BackHandler(enabled = vm.links != null || vm.stack.isNotEmpty() || vm.tab != 0) { vm.back() }

    Scaffold(
        containerColor = Bg,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (vm.stack.isEmpty()) {
                NavigationBar(containerColor = Surface1) {
                    tabs.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = vm.tab == i,
                            onClick = { vm.tab = i },
                            icon = { Icon(painterResource(t.icon), t.label) },
                            label = { Text(t.label) },
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
        Box(Modifier.fillMaxSize().background(Bg).padding(pad)) {
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

    if (vm.showPicker) SourcePickerSheet(vm)
    vm.links?.let { LinksSheet(vm, it) }
}
