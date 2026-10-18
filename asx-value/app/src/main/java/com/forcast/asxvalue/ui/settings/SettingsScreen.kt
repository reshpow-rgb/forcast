package com.forcast.asxvalue.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forcast.asxvalue.AsxValueApp
import com.forcast.asxvalue.core.valuation.Assumptions
import com.forcast.asxvalue.ui.Fmt
import com.forcast.asxvalue.ui.components.Disclaimer
import com.forcast.asxvalue.ui.components.SectionCard

private val MODELS = listOf(
    "claude-opus-5-5" to "Claude Opus 5.5 (best research quality)",
    "claude-sonnet-5-5" to "Claude Sonnet 5.5 (faster, cheaper)",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(app: AsxValueApp) {
    val settings by app.prefs.settings.collectAsStateWithLifecycle()
    var key by remember { mutableStateOf(settings.apiKey) }
    var showKey by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard("Claude AI research (optional)") {
                Text(
                    "Research notes and value ideas are written by Claude using live web search. Add your own Anthropic API key; usage is billed to your Anthropic account. The key is stored only on this device.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it; saved = false },
                    label = { Text("Anthropic API key") },
                    placeholder = { Text("sk-ant-…") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (showKey) "Hide key" else "Show key")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { app.prefs.update { it.copy(apiKey = key) }; saved = true }) { Text("Save key") }
                    if (saved) Text("  Saved", style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = { runCatching { uri.openUri("https://console.anthropic.com/settings/keys") } }) { Text("Get a key") }
                }
                Text("Model", style = MaterialTheme.typography.labelLarge)
                MODELS.forEach { (id, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = settings.model == id, onClick = { app.prefs.update { it.copy(model = id) } })
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            SectionCard("Valuation assumptions") {
                val a = settings.assumptions
                fun set(transform: (Assumptions) -> Assumptions) = app.prefs.update { it.copy(assumptions = transform(it.assumptions)) }
                PctSlider("Risk-free rate (AU 10-year bond)", a.riskFreeRate, 0.01f..0.08f) { v -> set { it.copy(riskFreeRate = v) } }
                PctSlider("Equity risk premium", a.equityRiskPremium, 0.03f..0.09f) { v -> set { it.copy(equityRiskPremium = v) } }
                PctSlider("Terminal growth (long run)", a.terminalGrowth, 0.0f..0.04f) { v -> set { it.copy(terminalGrowth = v) } }
                PctSlider("Upside needed to call a stock undervalued", a.undervaluedThreshold, 0.05f..0.5f) { v -> set { it.copy(undervaluedThreshold = v) } }
                Text(
                    "Cost of equity = risk-free rate + beta × equity risk premium. Changes apply the next time a screen or stock is loaded.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { app.prefs.update { it.copy(assumptions = Assumptions()) } }) { Text("Reset to defaults") }
            }

            SectionCard("About the data") {
                Text(
                    "Prices, financials, analyst targets and news come from Yahoo Finance's public endpoints. They can be delayed by 20 minutes or more, and coverage of smaller ASX companies is patchy. Dividend yields exclude franking credits. The screener's stock list is a fixed set of large and mid caps; you can look up any ASX code with the search button.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Disclaimer()
        }
    }
}

@Composable
private fun PctSlider(label: String, value: Double, range: ClosedFloatingPointRange<Float>, onChange: (Double) -> Unit) {
    var local by remember(value) { mutableStateOf(value.toFloat()) }
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(Fmt.pct(local.toDouble()), style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = local,
            onValueChange = { local = (Math.round(it * 1000) / 1000f) },
            onValueChangeFinished = { onChange(local.toDouble()) },
            valueRange = range,
        )
    }
}
