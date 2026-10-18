package com.forcast.asxvalue.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.forcast.asxvalue.AsxValueApp
import com.forcast.asxvalue.core.data.ChartRange
import com.forcast.asxvalue.core.model.Fundamentals
import com.forcast.asxvalue.core.valuation.ModelKind
import com.forcast.asxvalue.core.valuation.Valuation
import com.forcast.asxvalue.core.valuation.ValuationEngine
import com.forcast.asxvalue.ui.Fmt
import com.forcast.asxvalue.ui.components.Disclaimer
import com.forcast.asxvalue.ui.components.MarkdownText
import com.forcast.asxvalue.ui.components.MetricGrid
import com.forcast.asxvalue.ui.components.PriceChart
import com.forcast.asxvalue.ui.components.RangeBar
import com.forcast.asxvalue.ui.components.ScoreBadge
import com.forcast.asxvalue.ui.components.SectionCard
import com.forcast.asxvalue.ui.components.verdictColor
import com.forcast.asxvalue.ui.theme.ValueColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockDetailScreen(app: AsxValueApp, symbol: String, onBack: () -> Unit, onSettings: () -> Unit) {
    val vm: StockDetailViewModel = viewModel(key = symbol) { StockDetailViewModel(symbol, app.repository, app.prefs) }
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(symbol.removeSuffix(".AX"), fontWeight = FontWeight.Bold)
                        state.fundamentals?.name?.let { Text(it, style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = vm::toggleWatch) {
                        Icon(if (state.watched) Icons.Filled.Star else Icons.Filled.StarBorder, if (state.watched) "Remove from watchlist" else "Add to watchlist")
                    }
                    IconButton(onClick = { vm.load(refresh = true) }) { Icon(Icons.Filled.Refresh, "Refresh") }
                },
            )
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            val f = state.fundamentals
            val v = state.valuation
            when {
                state.loading && f == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                f == null || v == null -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(state.error ?: "Couldn't load this stock.", color = MaterialTheme.colorScheme.error)
                    Button(onClick = { vm.load(refresh = true) }) { Text("Try again") }
                }
                else -> {
                    PrimaryTabRow(selectedTabIndex = tab) {
                        listOf("Overview", "Valuation", "Research").forEachIndexed { i, label ->
                            Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
                        }
                    }
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Header(f, v)
                        when (tab) {
                            0 -> OverviewTab(state, vm::setRange)
                            1 -> ValuationTab(f, v)
                            else -> ResearchTab(state, vm.hasApiKey, vm::generateAiNote, onSettings)
                        }
                        Disclaimer()
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(f: Fundamentals, v: Valuation) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Fmt.money(f.price), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                f.changePct?.let {
                    Text(Fmt.pct(it, signed = true), color = if (it >= 0) ValueColors.Positive else ValueColors.Negative, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            Text(v.verdict.label, color = verdictColor(v.verdict), fontWeight = FontWeight.SemiBold)
            Text(
                "Fair value ${Fmt.money(v.fairValue)}" + (v.upside?.let { " (${Fmt.pct(it, signed = true)})" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ScoreBadge(v.score, Modifier.size(56.dp))
            Text("Value score", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun OverviewTab(state: DetailState, onRange: (ChartRange) -> Unit) {
    val f = state.fundamentals!!
    SectionCard("Share price history") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChartRange.entries.forEach { r ->
                FilterChip(selected = state.range == r, onClick = { onRange(r) }, label = { Text(r.label) })
            }
        }
        Box {
            val pts = state.history?.points.orEmpty()
            val ma = when (state.range) {
                ChartRange.Y1 -> 50
                ChartRange.M6 -> 20
                ChartRange.Y5 -> 40 // 40 weeks ≈ 200 trading days
                ChartRange.MAX -> 12
                ChartRange.M1 -> null
            }
            PriceChart(pts, state.valuation?.fairValue, ma)
            if (state.chartLoading) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
        state.chartError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        state.rangeStats?.let { s ->
            MetricGrid(
                listOf(
                    "Total return (${state.range.label})" to Fmt.pct(s.totalReturn, signed = true),
                    "Annualised return" to Fmt.pct(s.cagr, signed = true),
                    "Volatility (annualised)" to Fmt.pct(s.volatility),
                    "Max drawdown" to Fmt.pct(s.maxDrawdown),
                ),
            )
        }
    }

    val lo = f.fiftyTwoWeekLow ?: state.yearStats?.periodLow
    val hi = f.fiftyTwoWeekHigh ?: state.yearStats?.periodHigh
    if (lo != null && hi != null && f.price != null) {
        SectionCard("52-week range") {
            RangeBar(lo, hi, f.price)
            state.yearStats?.vsSma200?.let {
                Text(
                    if (it >= 0) "${Fmt.pct(it)} above its 200-day average" else "${Fmt.pct(-it)} below its 200-day average",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    SectionCard("Key figures") {
        MetricGrid(
            listOf(
                "Market cap" to Fmt.big(f.marketCap),
                "Sector" to (f.sector ?: "–"),
                "P/E (trailing)" to Fmt.num(ValuationEngine.pe(f)),
                "P/E (forward)" to Fmt.num(f.forwardPe),
                "EPS (trailing)" to Fmt.money(f.trailingEps),
                "Price / book" to Fmt.num(f.priceToBook, 2),
                "Dividend / share" to Fmt.money(f.dividendRate),
                "Dividend yield" to Fmt.pct(f.dividendYield),
                "Payout ratio" to Fmt.pct(f.payoutRatio),
                "Return on equity" to Fmt.pct(f.returnOnEquity),
                "Net margin" to Fmt.pct(f.profitMargin),
                "Debt / equity" to (f.debtToEquityPct?.let { Fmt.num(it / 100, 2) + "×" } ?: "–"),
                "Revenue growth" to Fmt.pct(f.revenueGrowth, signed = true),
                "Earnings growth" to Fmt.pct(f.earningsGrowth, signed = true),
                "Free cash flow" to Fmt.big(f.freeCashflow),
                "Beta" to Fmt.num(f.beta, 2),
            ),
        )
    }

    f.businessSummary?.let {
        SectionCard("About") {
            Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 12)
        }
    }
}

@Composable
private fun ValuationTab(f: Fundamentals, v: Valuation) {
    SectionCard("Fair value estimate") {
        MetricGrid(
            listOf(
                "Price" to Fmt.money(v.price),
                "Blended fair value" to Fmt.money(v.fairValue),
                "Upside" to Fmt.pct(v.upside, signed = true),
                "Margin of safety" to Fmt.pct(v.marginOfSafety),
                "Confidence" to v.confidence.name.lowercase().replaceFirstChar { it.uppercase() },
                "Cost of equity" to Fmt.pct(v.costOfEquity),
            ),
        )
        Text(
            "The blended fair value is the median of the models below (outliers excluded). The value score (0–100) also weighs P/E and P/B versus peers, analyst targets, dividend yield and where the price sits in its 52-week range, with penalties for losses and high debt.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SectionCard("Valuation models") {
        if (v.models.isEmpty()) Text("Not enough data to run any model.")
        val maxV = (v.models.map { it.value } + listOfNotNull(v.price)).maxOrNull() ?: 1.0
        v.models.forEach { m ->
            Column(Modifier.padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.kind.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(Fmt.money(m.value), fontWeight = FontWeight.SemiBold)
                    v.price?.let { p ->
                        Spacer(Modifier.width(6.dp))
                        val u = m.value / p - 1
                        Text(Fmt.pct(u, signed = true), color = if (u >= 0) ValueColors.Positive else ValueColors.Negative, style = MaterialTheme.typography.labelMedium)
                    }
                }
                ModelBar(m.value, v.price, maxV, muted = m.outlier)
                Text(
                    (if (m.outlier) "Excluded as an outlier. " else "") + m.detail,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(m.kind.explanation, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val missing = ModelKind.entries.filter { k -> v.models.none { it.kind == k } }
        if (missing.isNotEmpty()) {
            Text(
                "Not used: " + missing.joinToString { it.title } + " (missing or negative inputs" +
                    (if (f.sector == "Financial Services") "; DCF isn't meaningful for banks and insurers" else "") + ")",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }

    if (v.flags.isNotEmpty()) {
        SectionCard("Signals") {
            v.flags.forEach { flag ->
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        if (flag.positive) Icons.Filled.CheckCircle else Icons.Filled.Warning, null,
                        tint = if (flag.positive) ValueColors.Positive else ValueColors.Negative, modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(flag.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    if (f.targetMeanPrice != null) {
        SectionCard("Analyst views") {
            MetricGrid(
                listOf(
                    "Mean target" to Fmt.money(f.targetMeanPrice),
                    "Range" to "${Fmt.money(f.targetLowPrice)} – ${Fmt.money(f.targetHighPrice)}",
                    "Analysts" to (f.analystCount?.toString() ?: "–"),
                    "Consensus" to (f.recommendationKey?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: "–"),
                ),
            )
        }
    }
}

/** Bar for one model's value, with a tick marking the current share price. */
@Composable
private fun ModelBar(value: Double, price: Double?, max: Double, muted: Boolean) {
    val color = when {
        muted -> MaterialTheme.colorScheme.outline
        price != null && value >= price -> ValueColors.Positive
        else -> ValueColors.Negative
    }
    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth((value / max).toFloat().coerceIn(0.02f, 1f)).height(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
        if (price != null) {
            Row(Modifier.fillMaxWidth()) {
                val frac = (price / max).toFloat().coerceIn(0.001f, 0.999f)
                Spacer(Modifier.weight(frac))
                Box(Modifier.width(3.dp).height(10.dp).background(MaterialTheme.colorScheme.onSurface))
                Spacer(Modifier.weight(1f - frac))
            }
        }
    }
}

@Composable
private fun ResearchTab(state: DetailState, hasKey: Boolean, onGenerate: () -> Unit, onSettings: () -> Unit) {
    val uri = LocalUriHandler.current
    SectionCard("AI equity research note") {
        val ai = state.ai
        when {
            ai.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text("Claude is reading the latest results, news and broker views… this can take a minute or two.", style = MaterialTheme.typography.bodyMedium)
            }
            ai.markdown != null -> {
                MarkdownText(ai.markdown)
                OutlinedButton(onClick = onGenerate) { Text("Regenerate") }
            }
            else -> {
                Text(
                    "Claude searches the web for the latest results, guidance, announcements and broker views, then writes a summarised research note that weighs them against this app's valuation.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                ai.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (hasKey) {
                    Button(onClick = onGenerate) {
                        Icon(Icons.Filled.AutoAwesome, null)
                        Text("  Write research note")
                    }
                } else {
                    Button(onClick = onSettings) { Text("Add Anthropic API key in Settings") }
                }
            }
        }
    }

    state.summary?.let {
        SectionCard("Quick summary (from app data)") { MarkdownText(it) }
    }

    if (state.news.isNotEmpty()) {
        SectionCard("Latest news") {
            state.news.forEach { n ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).padding(vertical = 4.dp)) {
                    androidx.compose.material3.TextButton(onClick = { runCatching { uri.openUri(n.link) } }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Text(n.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                    Text(
                        listOfNotNull(n.publisher, n.publishedEpochSec?.let(Fmt::date)).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
