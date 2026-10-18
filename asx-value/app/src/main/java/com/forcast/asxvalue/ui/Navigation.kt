package com.forcast.asxvalue.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.forcast.asxvalue.AsxValueApp
import com.forcast.asxvalue.ui.detail.StockDetailScreen
import com.forcast.asxvalue.ui.screener.IdeasScreen
import com.forcast.asxvalue.ui.screener.ScreenerScreen
import com.forcast.asxvalue.ui.settings.SettingsScreen
import com.forcast.asxvalue.ui.watchlist.WatchlistScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("screener", "Screener", Icons.Filled.Insights),
    Tab("watchlist", "Watchlist", Icons.Filled.Star),
    Tab("settings", "Settings", Icons.Filled.Settings),
)

fun NavHostController.openStock(symbol: String) = navigate("stock/$symbol")

@Composable
fun AsxValueNav(app: AsxValueApp) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    Scaffold(
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { inner ->
        NavHost(nav, startDestination = "screener", modifier = Modifier.padding(bottom = inner.calculateBottomPadding())) {
            composable("screener") {
                ScreenerScreen(app, onOpen = nav::openStock, onIdeas = { nav.navigate("ideas") })
            }
            composable("watchlist") { WatchlistScreen(app, onOpen = nav::openStock) }
            composable("settings") { SettingsScreen(app) }
            composable("ideas") { IdeasScreen(app, onBack = { nav.popBackStack() }, onSettings = { nav.navigate("settings") }) }
            composable("stock/{symbol}", arguments = listOf(navArgument("symbol") { type = NavType.StringType })) { e ->
                StockDetailScreen(
                    app,
                    symbol = e.arguments?.getString("symbol").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onSettings = { nav.navigate("settings") },
                )
            }
        }
    }
}
