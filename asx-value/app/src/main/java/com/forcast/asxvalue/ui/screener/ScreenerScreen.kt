package com.forcast.asxvalue.ui.screener

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.forcast.asxvalue.AsxValueApp
import com.forcast.asxvalue.core.data.Universe
import com.forcast.asxvalue.core.model.toAsxSymbol
import com.forcast.asxvalue.ui.components.Disclaimer
import com.forcast.asxvalue.ui.components.StockRowItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenerScreen(app: AsxValueApp, onOpen: (String) -> Unit, onIdeas: () -> Unit) {
    val vm: ScreenerViewModel = viewModel { ScreenerViewModel(app.repository, app.prefs) }
    val state by vm.state.collectAsStateWithLifecycle()
    var searchOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ASX Value Finder") },
                actions = {
                    IconButton(onClick = { searchOpen = true }) { Icon(Icons.Filled.Search, "Look up a ticker") }
                    Box {
                        IconButton(onClick = { sortOpen = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort") }
                        DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                            SortBy.entries.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s.label, fontWeight = if (s == state.sort) FontWeight.Bold else null) },
                                    onClick = { vm.setSort(s); sortOpen = false },
                                )
                            }
                        }
                    }
                    IconButton(onClick = { vm.run(refresh = true) }, enabled = !state.loading) { Icon(Icons.Filled.Refresh, "Refresh") }
                },
            )
        },
    ) { inner ->
        LazyColumn(Modifier.fillMaxSize().padding(inner), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val options = listOf(Universe.ASX50 to "Top 50", Universe.ASX100 to "Top ~100", Universe.WATCHLIST to "Watchlist")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        options.forEachIndexed { i, (u, label) ->
                            SegmentedButton(
                                selected = state.universe == u,
                                onClick = { vm.setUniverse(u) },
                                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                            ) { Text(label) }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(
                            selected = state.undervaluedOnly,
                            onClick = { vm.setUndervaluedOnly(!state.undervaluedOnly) },
                            label = { Text("Undervalued only") },
                        )
                        Spacer(Modifier.weight(1f))
                        Text("Sorted by ${state.sort.label.lowercase()}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (state.sectors.size > 1) {
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FilterChip(selected = state.sector == null, onClick = { vm.setSector(null) }, label = { Text("All sectors") }) }
                        items(state.sectors) { s ->
                            FilterChip(selected = state.sector == s, onClick = { vm.setSector(if (state.sector == s) null else s) }, label = { Text(s) })
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    if (state.loading) {
                        LinearProgressIndicator(
                            progress = { if (state.total == 0) 0f else state.detailed.toFloat() / state.total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            if (state.total == 0) "Fetching quotes…" else "Analysing financials ${state.detailed}/${state.total}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            item {
                Card(
                    onClick = onIdeas,
                    enabled = state.rows.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Where might value be hiding?", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(
                                "Claude researches the top-ranked stocks and suggests ideas and value traps",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
            if (!state.loading && state.rows.isEmpty() && state.error == null) {
                item {
                    Text(
                        if (state.universe == Universe.WATCHLIST) "Your watchlist is empty. Add stocks from their detail page." else "No results.",
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            val visible = state.visible
            items(visible, key = { it.fundamentals.symbol }) { row ->
                StockRowItem(row) { onOpen(row.fundamentals.symbol) }
                HorizontalDivider(Modifier.padding(start = 72.dp))
            }
            item { Disclaimer(Modifier.padding(16.dp)) }
        }
    }

    if (searchOpen) {
        TickerSearchDialog(onDismiss = { searchOpen = false }, onGo = { searchOpen = false; onOpen(toAsxSymbol(it)) })
    }
}

@Composable
fun TickerSearchDialog(onDismiss: () -> Unit, onGo: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val valid = text.trim().matches(Regex("(?i)[a-z0-9]{2,6}(\\.ax)?"))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Look up an ASX stock") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("ASX code, e.g. BHP") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (valid) onGo(text) }),
            )
        },
        confirmButton = { TextButton(onClick = { onGo(text) }, enabled = valid) { Text("Open") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
