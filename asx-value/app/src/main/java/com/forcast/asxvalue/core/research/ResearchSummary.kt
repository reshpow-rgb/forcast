package com.forcast.asxvalue.core.research

import com.forcast.asxvalue.core.model.Fundamentals
import com.forcast.asxvalue.core.model.NewsItem
import com.forcast.asxvalue.core.valuation.ModelKind
import com.forcast.asxvalue.core.valuation.PriceStats
import com.forcast.asxvalue.core.valuation.Valuation
import com.forcast.asxvalue.core.valuation.ValuationEngine
import com.forcast.asxvalue.core.valuation.ValuationEngine.money
import com.forcast.asxvalue.core.valuation.ValuationEngine.num1
import com.forcast.asxvalue.core.valuation.ValuationEngine.pct
import com.forcast.asxvalue.core.valuation.Verdict
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Builds a short, equity-research-style note (Markdown) from the data the app already has.
 * Works offline and without an API key; the Claude-written note adds web research on top.
 */
object ResearchSummary {
    private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.of("Australia/Sydney"))

    fun build(f: Fundamentals, v: Valuation, stats: PriceStats?, news: List<NewsItem>): String = buildString {
        val name = f.name ?: f.ticker
        appendLine("# $name (ASX: ${f.ticker})")
        val rating = when (v.verdict) {
            Verdict.UNDERVALUED -> "Potentially undervalued"
            Verdict.SLIGHTLY_UNDERVALUED -> "Modestly undervalued"
            Verdict.FAIR -> "Fairly valued"
            Verdict.OVERVALUED -> "Looks expensive"
            Verdict.INSUFFICIENT_DATA -> "Not rated (insufficient data)"
        }
        val headline = buildList {
            add("**$rating**")
            v.price?.let { add("Price ${money(it)}") }
            v.fairValue?.let { add("Blended fair value ${money(it)}") }
            v.upside?.let { add("Upside ${signedPct(it)}") }
            add("Value score ${v.score}/100")
        }
        appendLine(headline.joinToString(" · "))
        appendLine()

        appendLine("## Summary")
        appendLine(summaryParagraph(f, v, stats))
        appendLine()

        f.businessSummary?.let {
            appendLine("## Business")
            appendLine(firstSentences(it, 3))
            appendLine()
        }

        val positives = v.flags.filter { it.positive }
        val negatives = v.flags.filterNot { it.positive }
        appendLine("## Investment thesis")
        if (positives.isEmpty()) appendLine("- No strong value signals in the available data.")
        positives.forEach { appendLine("- ${it.text}") }
        f.returnOnEquity?.let { if (it > 0.15) appendLine("- High return on equity of ${pct(it)}") }
        f.profitMargin?.let { if (it > 0.2) appendLine("- Strong net margin of ${pct(it)}") }
        appendLine()

        appendLine("## Valuation")
        if (v.models.isEmpty()) appendLine("Not enough financial data to run the valuation models.")
        v.models.forEach { m ->
            val note = if (m.outlier) " _(outlier, excluded)_" else ""
            appendLine("- **${m.kind.title}:** ${money(m.value)}$note. ${m.detail}")
        }
        appendLine(
            "- Cost of equity ${pct(v.costOfEquity)} (CAPM)" +
                (v.sectorPe?.let { ", sector P/E ${num1(it)}" } ?: "") +
                ". Confidence: ${v.confidence.name.lowercase()}.",
        )
        appendLine()

        appendLine("## Key risks")
        if (negatives.isEmpty()) appendLine("- No major red flags in the headline numbers; check the latest reports for company-specific risks.")
        negatives.forEach { appendLine("- ${it.text}") }
        sectorRisk(f.sector)?.let { appendLine("- $it") }
        appendLine()

        if (stats != null) {
            appendLine("## Share price")
            val parts = buildList {
                add("${pct(stats.totalReturn)} total return over ${num1(stats.years)} years")
                stats.cagr?.let { add("${pct(it)} a year compounded") }
                stats.volatility?.let { add("volatility ${pct(it)}") }
                add("max drawdown ${pct(stats.maxDrawdown)}")
                stats.vsSma200?.let { add(if (it >= 0) "${pct(it)} above its 200-day average" else "${pct(-it)} below its 200-day average") }
            }
            appendLine(parts.joinToString(", ").replaceFirstChar { it.uppercase() } + ".")
            appendLine()
        }

        if (news.isNotEmpty()) {
            appendLine("## Recent news")
            news.take(5).forEach { n ->
                val date = n.publishedEpochSec?.let { dateFmt.format(Instant.ofEpochSecond(it)) }
                val meta = listOfNotNull(n.publisher, date).joinToString(", ")
                appendLine("- [${n.title}](${n.link})" + if (meta.isNotEmpty()) " ($meta)" else "")
            }
            appendLine()
        }

        appendLine("_Generated from Yahoo Finance data using simple models. General information only, not financial advice._")
    }

    private fun summaryParagraph(f: Fundamentals, v: Valuation, stats: PriceStats?): String {
        val name = f.name ?: f.ticker
        val s = StringBuilder()
        if (v.fairValue != null && v.price != null && v.upside != null) {
            val usable = v.models.filterNot { it.outlier }
            val above = usable.count { it.value > v.price }
            s.append("$name trades at ${money(v.price)} against a blended fair value of ${money(v.fairValue)} ")
            s.append("(${signedPct(v.upside)}). $above of ${usable.size} valuation methods put fair value above the current price. ")
        } else {
            s.append("There isn't enough financial data to value $name with confidence. ")
        }
        val pe = ValuationEngine.pe(f)
        if (pe != null) {
            s.append("It is on a trailing P/E of ${num1(pe)}")
            v.sectorPe?.let { s.append(" versus about ${num1(it)} for its sector") }
            s.append(". ")
        }
        f.dividendYield?.let { if (it > 0) s.append("The dividend yield is ${pct(it)}. ") }
        val analyst = v.models.firstOrNull { it.kind == ModelKind.ANALYST }
        if (analyst != null && v.price != null) {
            s.append("Brokers' average target of ${money(analyst.value)} implies ${signedPct(analyst.value / v.price - 1)}. ")
        }
        stats?.vsSma200?.let {
            s.append(if (it < 0) "The shares are trading below their long-term trend. " else "The shares are trading above their long-term trend. ")
        }
        return s.toString().trim()
    }

    private fun sectorRisk(sector: String?): String? = when (sector) {
        "Basic Materials" -> "Earnings are tied to commodity prices and Chinese demand, so today's profits may not last."
        "Energy" -> "Exposed to oil, gas, coal or uranium prices and the energy-transition policy outlook."
        "Financial Services" -> "Sensitive to interest rates, the housing market and bad-debt cycles."
        "Real Estate" -> "Valuations are sensitive to interest rates and property cap rates."
        "Consumer Cyclical" -> "Earnings depend on household spending, which falls in downturns."
        "Technology" -> "High-growth valuations can fall sharply if growth slows or rates rise."
        "Healthcare" -> "Exposed to regulatory, clinical trial and reimbursement risk."
        "Utilities" -> "Regulated returns and energy policy changes can affect earnings."
        else -> null
    }

    private fun firstSentences(text: String, n: Int): String {
        val sentences = text.split(Regex("(?<=[.!?])\\s+"))
        return sentences.take(n).joinToString(" ")
    }

    private fun signedPct(v: Double) = (if (v >= 0) "+" else "") + pct(v)
}
