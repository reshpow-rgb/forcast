package com.forcast.asxvalue.core.research

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.UserLocation
import com.anthropic.models.messages.WebSearchTool20260209
import com.forcast.asxvalue.core.model.Fundamentals
import com.forcast.asxvalue.core.model.NewsItem
import com.forcast.asxvalue.core.valuation.PriceStats
import com.forcast.asxvalue.core.valuation.Valuation
import com.forcast.asxvalue.core.valuation.ValuationEngine.money
import com.forcast.asxvalue.core.valuation.ValuationEngine.num1
import com.forcast.asxvalue.core.valuation.ValuationEngine.pct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

class ResearchException(message: String) : Exception(message)

/**
 * Uses Claude with web search to write summarised equity research notes and value ideas.
 * Requires the user's own Anthropic API key (entered in Settings).
 */
class ClaudeResearch(apiKey: String, private val model: String = DEFAULT_MODEL) {
    private val client: AnthropicClient = AnthropicOkHttpClient.builder().apiKey(apiKey).build()

    /** A summarised research note on one company, informed by our numbers plus live web research. */
    suspend fun researchNote(
        f: Fundamentals,
        v: Valuation,
        stats: PriceStats?,
        news: List<NewsItem>,
    ): String = withContext(Dispatchers.IO) {
        val prompt = buildString {
            appendLine("Write a summarised equity research note on ${f.name ?: f.ticker} (ASX: ${f.ticker}).")
            appendLine("Today is ${today()}.")
            appendLine()
            appendLine("Use web search to find the latest results, guidance, announcements, broker views and news before writing.")
            appendLine("Below is the app's own data and model output. Use it, and say where your research disagrees with it.")
            appendLine()
            appendLine("<app_data>")
            append(factSheet(f, v, stats))
            if (news.isNotEmpty()) {
                appendLine("Recent headlines:")
                news.take(8).forEach { appendLine("- ${it.title} (${it.publisher ?: "unknown"})") }
            }
            appendLine("</app_data>")
            appendLine()
            appendLine("Structure the note in Markdown with these sections:")
            appendLine("# <Company> (ASX: <ticker>) – <one-line view>")
            appendLine("A bold line: view (Undervalued / Fairly valued / Overvalued), price, your fair-value range.")
            appendLine("## Summary (3–4 sentences)")
            appendLine("## Recent performance (latest results and guidance, with dates)")
            appendLine("## Investment thesis (3–5 bullets)")
            appendLine("## Valuation (compare the app's models with broker targets and peers; give a fair-value range)")
            appendLine("## Key risks (3–5 bullets)")
            appendLine("## Catalysts to watch (upcoming events with dates where known)")
            appendLine("## Bottom line (2 sentences on whether, and why, the shares look undervalued)")
        }
        run(SYSTEM_NOTE, prompt, maxSearches = 6)
    }

    /** Ideas on where value may lie across the screened ASX universe. */
    suspend fun valueIdeas(candidates: List<Pair<Fundamentals, Valuation>>, sectorPe: Map<String, Double>): String =
        withContext(Dispatchers.IO) {
            val prompt = buildString {
                appendLine("Today is ${today()}. The app has screened ASX large and mid caps with simple valuation models.")
                appendLine("Here are the stocks it ranks as cheapest, best first:")
                appendLine()
                appendLine("<screen_results>")
                candidates.forEach { (f, v) ->
                    append("- ${f.ticker} ${f.name ?: ""} [${f.sector ?: "?"}]: price ${f.price?.let(::money) ?: "?"}")
                    v.fairValue?.let { append(", fair value ${money(it)}") }
                    v.upside?.let { append(", upside ${pct(it)}") }
                    append(", score ${v.score}/100")
                    com.forcast.asxvalue.core.valuation.ValuationEngine.pe(f)?.let { append(", P/E ${num1(it)}") }
                    f.dividendYield?.let { append(", yield ${pct(it)}") }
                    val warnings = v.flags.filterNot { it.positive }.joinToString("; ") { it.text }
                    if (warnings.isNotEmpty()) append(". Warnings: $warnings")
                    appendLine()
                }
                appendLine("</screen_results>")
                appendLine()
                appendLine("Sector median P/Es: " + sectorPe.entries.joinToString { "${it.key} ${num1(it.value)}" })
                appendLine()
                appendLine("Use web search to check current market conditions, then write in Markdown:")
                appendLine("# Where ASX value may be hiding – ${today()}")
                appendLine("## Market backdrop (3–4 sentences: rates, AUD, commodities, sentiment)")
                appendLine("## Sectors that look cheap and why (2–4 bullets)")
                appendLine("## Top ideas (4–6 stocks from the list worth researching further: one short paragraph each covering why it screens cheap, what the market is worried about, and what could change the view)")
                appendLine("## Likely value traps (stocks from the list that look cheap for good reason)")
                appendLine("## What to check before buying (short checklist)")
            }
            run(SYSTEM_IDEAS, prompt, maxSearches = 5)
        }

    private fun run(system: String, prompt: String, maxSearches: Long): String {
        var params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(16000L)
            .system(system)
            .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.MEDIUM).build())
            .addTool(
                WebSearchTool20260209.builder()
                    .maxUses(maxSearches)
                    .userLocation(UserLocation.builder().country("AU").timezone("Australia/Sydney").build())
                    .build(),
            )
            // If a safety classifier declines, let the API retry on its recommended fallback model.
            .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            .addUserMessage(prompt)
            .build()

        val text = StringBuilder()
        val sources = LinkedHashMap<String, String>()
        try {
            // Server-side web search can pause a long turn; resend to let Claude continue.
            repeat(MAX_CONTINUATIONS) {
                val msg = client.messages().create(params)
                for (block in msg.content()) {
                    block.text().ifPresent { t ->
                        text.append(t.text())
                        t.citations().ifPresent { cs ->
                            cs.forEach { c ->
                                c.webSearchResultLocation().ifPresent { w -> sources.putIfAbsent(w.url(), w.title().orElse(w.url())) }
                            }
                        }
                    }
                }
                when (msg.stopReason().orElse(null)) {
                    StopReason.PAUSE_TURN -> params = params.toBuilder().addMessage(msg).build()
                    StopReason.REFUSAL -> throw ResearchException("Claude declined to write this note. Try again later.")
                    else -> return withSources(text.toString(), sources)
                }
            }
        } catch (e: UnauthorizedException) {
            throw ResearchException("Your Anthropic API key was rejected. Check it in Settings.")
        } catch (e: RateLimitException) {
            throw ResearchException("Rate limited by the Anthropic API. Wait a minute and try again.")
        } catch (e: AnthropicServiceException) {
            throw ResearchException("Anthropic API error (${e.statusCode()}): ${e.message}")
        } catch (e: AnthropicIoException) {
            throw ResearchException("Couldn't reach the Anthropic API. Check your connection.")
        }
        return withSources(text.toString(), sources)
    }

    private fun withSources(body: String, sources: Map<String, String>): String {
        if (body.isBlank()) throw ResearchException("Claude returned an empty response. Try again.")
        if (sources.isEmpty()) return body.trim()
        return body.trim() + "\n\n## Sources\n" + sources.entries.take(12).joinToString("\n") { (url, title) -> "- [$title]($url)" }
    }

    private fun factSheet(f: Fundamentals, v: Valuation, stats: PriceStats?): String = buildString {
        fun line(label: String, value: String?) { if (value != null) appendLine("$label: $value") }
        line("Sector", listOfNotNull(f.sector, f.industry).joinToString(" / ").ifEmpty { null })
        line("Price", f.price?.let(::money))
        line("Market cap", f.marketCap?.let { "A$%.2fbn".format(it / 1e9) })
        line("Trailing EPS", f.trailingEps?.let(::money))
        line("Forward EPS", f.forwardEps?.let(::money))
        line("Trailing P/E", f.trailingPe?.let(::num1))
        line("Forward P/E", f.forwardPe?.let(::num1))
        line("Price/book", f.priceToBook?.let(::num1))
        line("Dividend per share", f.dividendRate?.let(::money))
        line("Dividend yield", f.dividendYield?.let(::pct))
        line("Payout ratio", f.payoutRatio?.let(::pct))
        line("Return on equity", f.returnOnEquity?.let(::pct))
        line("Debt/equity", f.debtToEquityPct?.let { "${num1(it / 100)}x" })
        line("Revenue growth (yoy)", f.revenueGrowth?.let(::pct))
        line("Earnings growth (yoy)", f.earningsGrowth?.let(::pct))
        line("Analyst growth estimate", f.growthEstimate?.let(::pct))
        line("Analyst mean target", f.targetMeanPrice?.let { "${money(it)} (${f.analystCount ?: "?"} analysts, ${f.recommendationKey ?: "n/a"})" })
        line("52-week range", if (f.fiftyTwoWeekLow != null && f.fiftyTwoWeekHigh != null) "${money(f.fiftyTwoWeekLow)} – ${money(f.fiftyTwoWeekHigh)}" else null)
        stats?.let { s ->
            line("Price history", "${pct(s.totalReturn)} over ${num1(s.years)} years" + (s.cagr?.let { ", CAGR ${pct(it)}" } ?: ""))
            line("Max drawdown", pct(s.maxDrawdown))
            line("vs 200-day average", s.vsSma200?.let(::pct))
        }
        appendLine("App valuation models:")
        v.models.forEach { appendLine("- ${it.kind.title}: ${money(it.value)}${if (it.outlier) " (outlier, excluded)" else ""} – ${it.detail}") }
        line("Blended fair value", v.fairValue?.let { "${money(it)} (upside ${v.upside?.let(::pct)}), confidence ${v.confidence.name.lowercase()}" })
        line("App verdict", "${v.verdict.label}, value score ${v.score}/100")
        val flags = v.flags.joinToString("; ") { (if (it.positive) "+ " else "- ") + it.text }
        line("Signals", flags.ifEmpty { null })
    }

    private fun today(): String = LocalDate.now(ZoneId.of("Australia/Sydney")).toString()

    companion object {
        const val DEFAULT_MODEL = "claude-opus-5-5"
        private const val MAX_CONTINUATIONS = 4

        private const val SYSTEM_NOTE =
            "You are an equity research analyst covering ASX-listed companies for Australian retail investors. " +
                "Write concise, balanced research notes in plain English, the way a broker's summary note reads. " +
                "Ground claims in your web research and the provided data, give dates for results and events, " +
                "and use A$ figures. Be clear about uncertainty and never overstate certainty about future prices. " +
                "Mention franking where relevant. Output only the note in Markdown. " +
                "End with one italic line saying this is general information, not personal financial advice."

        private const val SYSTEM_IDEAS =
            "You are an ASX equity strategist helping Australian retail investors find shares that may be undervalued. " +
                "Be specific and balanced: explain why each idea screens cheap and what the market may be worried about. " +
                "Only discuss stocks from the provided list as ideas. Use A$ figures and give dates for recent events. " +
                "Output only Markdown. End with one italic line saying this is general information, not personal financial advice."
    }
}
