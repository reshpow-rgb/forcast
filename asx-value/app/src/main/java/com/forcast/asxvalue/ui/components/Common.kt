package com.forcast.asxvalue.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forcast.asxvalue.core.valuation.Verdict
import com.forcast.asxvalue.data.ScreenRow
import com.forcast.asxvalue.ui.Fmt
import com.forcast.asxvalue.ui.theme.ValueColors

@Composable
fun verdictColor(v: Verdict): Color = when (v) {
    Verdict.UNDERVALUED, Verdict.SLIGHTLY_UNDERVALUED -> ValueColors.Positive
    Verdict.FAIR -> ValueColors.Neutral
    Verdict.OVERVALUED -> ValueColors.Negative
    Verdict.INSUFFICIENT_DATA -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun scoreColor(score: Int): Color = when {
    score >= 65 -> ValueColors.Positive
    score >= 45 -> ValueColors.Neutral
    else -> ValueColors.Negative
}

@Composable
fun ScoreBadge(score: Int, modifier: Modifier = Modifier) {
    val c = scoreColor(score)
    Box(
        modifier.size(44.dp).clip(CircleShape).background(c.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Text("$score", color = c, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
fun StockRowItem(row: ScreenRow, onClick: () -> Unit) {
    val f = row.fundamentals
    val v = row.valuation
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScoreBadge(v.score)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(f.ticker, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(6.dp))
                Text(
                    f.name ?: "", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                v.verdict.label + if (!f.isDetailed) " · quick estimate" else "",
                color = verdictColor(v.verdict), style = MaterialTheme.typography.labelMedium,
            )
            Text(
                listOfNotNull(f.sector, "P/E ${Fmt.num(com.forcast.asxvalue.core.valuation.ValuationEngine.pe(f))}", f.dividendYield?.let { "Yield ${Fmt.pct(it)}" })
                    .joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 88.dp)) {
            Text(Fmt.money(f.price), style = MaterialTheme.typography.titleSmall)
            f.changePct?.let {
                Text(Fmt.pct(it, signed = true), style = MaterialTheme.typography.labelSmall, color = if (it >= 0) ValueColors.Positive else ValueColors.Negative)
            }
            Text("FV ${Fmt.money(v.fairValue)}", style = MaterialTheme.typography.labelSmall)
            v.upside?.let { Text(Fmt.pct(it, signed = true), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = if (it >= 0) ValueColors.Positive else ValueColors.Negative) }
        }
    }
}

@Composable
fun Disclaimer(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "General information only, not financial advice. Valuations come from simple models on Yahoo Finance data, which can be delayed or wrong. Do your own research or speak to a licensed adviser before investing.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** Horizontal bar showing where [value] sits between [low] and [high]. */
@Composable
fun RangeBar(low: Double, high: Double, value: Double, modifier: Modifier = Modifier) {
    val frac = if (high > low) ((value - low) / (high - low)).toFloat().coerceIn(0f, 1f) else 0.5f
    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxWidth(frac).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primary))
        }
        Row(Modifier.fillMaxWidth()) {
            Text(Fmt.money(low), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            Text(Fmt.money(high), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

/** A two-column grid of label/value pairs. */
@Composable
fun MetricGrid(items: List<Pair<String, String>>) {
    items.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth()) {
            pair.forEach { (l, v) -> Metric(l, v, Modifier.weight(1f)) }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}
