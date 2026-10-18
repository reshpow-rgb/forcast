package com.forcast.asxvalue.ui.screener

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.forcast.asxvalue.AsxValueApp
import com.forcast.asxvalue.core.research.ClaudeResearch
import com.forcast.asxvalue.data.Prefs
import com.forcast.asxvalue.data.StockRepository
import com.forcast.asxvalue.ui.components.Disclaimer
import com.forcast.asxvalue.ui.components.MarkdownText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class IdeasState(val loading: Boolean = false, val markdown: String? = null, val error: String? = null)

class IdeasViewModel(private val repo: StockRepository, private val prefs: Prefs) : ViewModel() {
    private val _state = MutableStateFlow(IdeasState())
    val state: StateFlow<IdeasState> = _state.asStateFlow()

    val hasKey get() = prefs.settings.value.apiKey.isNotBlank()

    fun generate() {
        val screen = repo.lastScreen
        if (screen == null || screen.rows.isEmpty()) {
            _state.value = IdeasState(error = "Run the screener first.")
            return
        }
        val settings = prefs.settings.value
        _state.value = IdeasState(loading = true)
        viewModelScope.launch {
            _state.value = try {
                val top = screen.rows.filter { it.valuation.fairValue != null }
                    .sortedByDescending { it.valuation.score }
                    .take(15)
                    .map { it.fundamentals to it.valuation }
                IdeasState(markdown = ClaudeResearch(settings.apiKey, settings.model).valueIdeas(top, screen.sectorPe))
            } catch (e: Exception) {
                IdeasState(error = e.message ?: "Something went wrong")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeasScreen(app: AsxValueApp, onBack: () -> Unit, onSettings: () -> Unit) {
    val vm: IdeasViewModel = viewModel { IdeasViewModel(app.repository, app.prefs) }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI value ideas") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                state.loading -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text("Claude is researching the market. This can take a minute or two.", style = MaterialTheme.typography.bodyMedium)
                }
                state.markdown != null -> {
                    MarkdownText(state.markdown!!)
                    OutlinedButton(onClick = vm::generate) { Text("Regenerate") }
                }
                else -> {
                    Text(
                        "Claude takes the 15 highest-scoring stocks from your latest screen, searches the web for current market " +
                            "conditions and news, and suggests which look worth researching and which may be value traps.",
                    )
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (vm.hasKey) {
                        Button(onClick = vm::generate) {
                            Icon(Icons.Filled.AutoAwesome, null)
                            Text("  Generate ideas")
                        }
                    } else {
                        Text("This needs your Anthropic API key.", style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = onSettings) { Text("Add API key in Settings") }
                    }
                }
            }
            Disclaimer()
        }
    }
}
