package com.kingbrezz.slime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kingbrezz.slime.data.SlimeRepository
import com.kingbrezz.slime.nav.Routes
import com.kingbrezz.slime.ui.SlimeViewModel
import com.kingbrezz.slime.ui.SlimeViewModelFactory
import com.kingbrezz.slime.ui.components.BottomNav
import com.kingbrezz.slime.ui.configs.ConfigsScreen
import com.kingbrezz.slime.ui.home.HomeScreen
import com.kingbrezz.slime.ui.library.LibraryScreen
import com.kingbrezz.slime.ui.modmenu.ModMenuLayer
import com.kingbrezz.slime.ui.modules.ModulesScreen
import com.kingbrezz.slime.ui.session.SessionScreen
import com.kingbrezz.slime.ui.settings.SettingsScreen
import com.kingbrezz.slime.ui.theme.Bg
import com.kingbrezz.slime.ui.theme.SlimeTheme
import com.kingbrezz.slime.ui.versions.VersionsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = SlimeRepository(applicationContext)
        setContent {
            SlimeTheme {
                val vm: SlimeViewModel = viewModel(factory = SlimeViewModelFactory(repo))
                val nav = rememberNavController()
                val snack = remember { SnackbarHostState() }
                val toast by vm.toast.collectAsState()
                val backStack by nav.currentBackStackEntryAsState()
                val route = backStack?.destination?.route
                val showBottom = route != null && route != Routes.SESSION

                LaunchedEffect(toast) {
                    val msg = toast ?: return@LaunchedEffect
                    snack.showSnackbar(msg)
                    vm.clearToast()
                }

                Box(Modifier.fillMaxSize()) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize().background(Bg),
                        containerColor = Bg,
                        bottomBar = {
                            if (showBottom) {
                                BottomNav(
                                    current = route ?: Routes.HOME,
                                    onNavigate = { dest ->
                                        if (dest != route) {
                                            nav.navigate(dest) {
                                                popUpTo(Routes.HOME) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                )
                            }
                        },
                    ) { padding ->
                        Box(Modifier.padding(padding)) {
                            NavHost(navController = nav, startDestination = Routes.HOME) {
                                composable(Routes.HOME) {
                                    HomeScreen(
                                        vm = vm,
                                        onPlay = {
                                            val v = vm.activeVersion()
                                            if (v == null || !v.installed) {
                                                vm.flash("Pasang versi dulu")
                                                nav.navigate(Routes.VERSIONS)
                                                return@HomeScreen
                                            }
                                            vm.log("Mainkan ${v.version}")
                                            val inject = vm.runInjectPipeline()
                                            vm.log(inject.summary)
                                            nav.navigate(Routes.SESSION)
                                        },
                                        onOpenVersions = { nav.navigate(Routes.VERSIONS) },
                                        onOpenConfigs = { nav.navigate(Routes.CONFIGS) },
                                    )
                                }
                                composable(Routes.VERSIONS) { VersionsScreen(vm) }
                                composable(Routes.MODULES) { ModulesScreen(vm) }
                                composable(Routes.CONFIGS) { ConfigsScreen(vm) }
                                composable(Routes.LIBRARY) { LibraryScreen(vm) }
                                composable(Routes.SETTINGS) { SettingsScreen(vm) }
                                composable(Routes.SESSION) {
                                    SessionScreen(
                                        vm = vm,
                                        onExit = { nav.popBackStack() },
                                    )
                                }
                            }
                        }
                    }

                    ModMenuLayer(vm)
                    SnackbarHost(
                        hostState = snack,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.systemBars)
                            .padding(bottom = 76.dp),
                    )
                }
            }
        }
    }
}
