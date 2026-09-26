package com.brainquest.game.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.brainquest.game.AppViewModel
import com.brainquest.game.data.question.Subjects
import com.brainquest.game.data.subjectFromKey
import com.brainquest.game.ui.meta.AchievementsScreen
import com.brainquest.game.ui.meta.DailyScreen
import com.brainquest.game.ui.meta.ProfileScreen
import com.brainquest.game.ui.meta.SettingsScreen
import com.brainquest.game.ui.meta.ShopScreen
import com.brainquest.game.ui.meta.WrongBookScreen
import com.brainquest.game.game.klotski.KlotskiScreen
import com.brainquest.game.game.gomoku.GomokuScreen
import com.brainquest.game.game.snake.SnakeScreen
import com.brainquest.game.game.quizbattle.BattleScreen
import com.brainquest.game.ui.LevelListScreen
import com.brainquest.game.ui.SubjectsScreen
import com.brainquest.game.ui.HomeScreen

object Routes {
    const val HOME = "home"
    const val SUBJECTS = "subjects"
    const val PROFILE = "profile"
    const val LEVELS = "levels/{subject}"
    const val BATTLE = "battle/{subject}/{level}"
    const val KLOTSKI = "klotski"
    const val GOMOKU = "gomoku"
    const val SNAKE = "snake"
    const val DAILY = "daily"
    const val WRONGBOOK = "wrongbook"
    const val SHOP = "shop"
    const val ACHIEVEMENTS = "achievements"
    const val SETTINGS = "settings"
    const val PAPERS = "papers"
    const val PK = "pk"
    const val CLOUD_SAVE = "cloud_save"

    fun levels(subjectKey: String) = "levels/$subjectKey"
    fun battle(subjectKey: String, level: Int) = "battle/$subjectKey/$level"
}

private data class TabDef(val route: String, val label: String, val icon: ImageVector)

@Composable
fun AppRoot(vm: AppViewModel) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val player by vm.player.collectAsState()

    LaunchedEffect(Unit) {
        vm.events.collect { snackbar.showSnackbar(it, withDismissAction = true) }
    }

    val tabs = listOf(
        TabDef(Routes.HOME, "大厅", Icons.Filled.Home),
        TabDef(Routes.SUBJECTS, "闯关", Icons.Filled.Map),
        TabDef(Routes.PROFILE, "我的", Icons.Filled.Person),
    )
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute in tabs.map { it.route }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(inner),
        ) {
            composable(Routes.HOME) { HomeScreen(vm, nav) }
            composable(Routes.SUBJECTS) { SubjectsScreen(vm, nav) }
            composable(Routes.PROFILE) { ProfileScreen(vm, nav) }
            composable(Routes.LEVELS) { entry ->
                val key = entry.arguments?.getString("subject") ?: "math"
                LevelListScreen(vm, nav, subjectFromKey(key))
            }
            composable(Routes.BATTLE) { entry ->
                val key = entry.arguments?.getString("subject") ?: "math"
                val level = entry.arguments?.getString("level")?.toIntOrNull() ?: 1
                BattleScreen(vm, nav, subjectFromKey(key), level)
            }
            composable(Routes.KLOTSKI) { KlotskiScreen(vm, nav) }
            composable(Routes.GOMOKU) { GomokuScreen(vm, nav) }
            composable(Routes.SNAKE) { SnakeScreen(vm, nav) }
            composable(Routes.DAILY) { DailyScreen(vm, nav) }
            composable(Routes.WRONGBOOK) { WrongBookScreen(vm, nav) }
            composable(Routes.SHOP) { ShopScreen(vm, nav) }
            composable(Routes.ACHIEVEMENTS) { AchievementsScreen(vm, nav) }
            composable(Routes.SETTINGS) { SettingsScreen(vm, nav) }
            composable(Routes.CLOUD_SAVE) { com.brainquest.game.ui.meta.CloudSaveScreen(vm, nav) }
            composable(Routes.PAPERS) { com.brainquest.game.ui.meta.PapersScreen(vm, nav) }
            composable(Routes.PK) { com.brainquest.game.game.pk.PkBattleScreen(vm, nav) }
        }
    }
}

/** 统一返回：先回退，否则回主页 */
fun NavHostController.backOrHome() {
    if (!popBackStack()) navigate(Routes.HOME)
}
