package com.forcast.asxvalue.ui.watchlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.forcast.asxvalue.AsxValueApp
import com.forcast.asxvalue.core.data.Universe
import com.forcast.asxvalue.ui.components.Disclaimer
import com.forcast.asxvalue.ui.components.StockRowItem
import com.forcast.asxvalue.ui.screener.ScreenerViewModel
import com.forcast.asxvalue.ui.screener.TickerSearchDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(app: AsxValueApp, onOpen: (String) -> Unit) {
    val vm: ScreenerViewModel = viewModel(key = "watchlist") { ScreenerViewModel(app.repository, app.prefs, Universe.WATCHLIST) }
    val state by vm.state.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Watchlist") },
                actions = { IconButton(onClick = { vm.run(refresh = true) }, enabled = !state.loading) { Icon(Icons.Filled.Refresh, "Refresh") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, "Add stock") }
        },
    ) { inner ->
        LazyColumn(Modifier.fillMaxSize().padding(inner), contentPadding = PaddingValues(bottom = 88.dp)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (!state.loading && state.rows.isEmpty() && state.error == null) {
                        Text("No stocks yet. Tap + to add one, or use the star on any stock's page.", Modifier.padding(vertical = 16.dp))
                    }
                }
            }
            items(state.visible, key = { it.fundamentals.symbol }) { row ->
                StockRowItem(row) { onOpen(row.fundamentals.symbol) }
                HorizontalDivider(Modifier.padding(start = 72.dp))
            }
            item { Disclaimer(Modifier.padding(16.dp)) }
        }
    }

    if (adding) {
        TickerSearchDialog(onDismiss = { adding = false }, onGo = { t ->
            adding = false
            if (!app.prefs.isWatched(t)) app.prefs.toggleWatch(t)
        })
    }
}
