package com.forcast.asxvalue.core.valuation

import com.forcast.asxvalue.core.model.Fundamentals
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/** User-adjustable inputs for the valuation models. */
data class Assumptions(
    /** Australian 10-year government bond yield. */
    val riskFreeRate: Double = 0.043,
    val equityRiskPremium: Double = 0.06,
    val terminalGrowth: Double = 0.025,
    val maxGrowth: Double = 0.15,
    val minGrowth: Double = -0.05,
    /** Margin of safety needed before a stock is called undervalued. */
    val undervaluedThreshold: Double = 0.20,
)

enum class Verdict(val label: String) {
    UNDERVALUED("Undervalued"),
    SLIGHTLY_UNDERVALUED("Slightly undervalued"),
    FAIR("Around fair value"),
    OVERVALUED("Overvalued"),
    INSUFFICIENT_DATA("Not enough data"),
}

enum class Confidence { HIGH, MEDIUM, LOW }

enum class ModelKind(val title: String, val explanation: String) {
    DCF(
        "Discounted cash flow",
        "Free cash flow per share grown for 10 years (fading to terminal growth) and discounted at a CAPM cost of equity.",
    ),
    DDM(
        "Dividend discount",
        "Gordon growth model: next year's dividend divided by (cost of equity − sustainable dividend growth).",
    ),
    GRAHAM(
        "Graham number",
        "Benjamin Graham's ceiling for a defensive investor: √(22.5 × EPS × book value per share).",
    ),
    RELATIVE_PE(
        "Sector P/E",
        "Earnings per share multiplied by the median P/E of its ASX sector peers.",
    ),
    ANALYST(
        "Analyst consensus",
        "Average 12-month price target from brokers covering the stock.",
    ),
}

data class ModelValue(
    val kind: ModelKind,
    val value: Double,
    val detail: String,
    /** Excluded from the blended fair value because it is implausibly far from the price. */
    val outlier: Boolean = false,
)

data class Flag(val positive: Boolean, val text: String)

data class Valuation(
    val price: Double?,
    val fairValue: Double?,
    /** fairValue / price − 1. */
    val upside: Double?,
    val verdict: Verdict,
    val confidence: Confidence,
    /** 0–100 composite score, higher = cheaper on more measures. */
    val score: Int,
    val models: List<ModelValue>,
    val flags: List<Flag>,
    val costOfEquity: Double,
    val sectorPe: Double?,
) {
    /** (fair − price) / fair; how far below fair value the price sits. */
    val marginOfSafety: Double? get() = if (fairValue != null && price != null && fairValue > 0) (fairValue - price) / fairValue else null
}

/**
 * Blends several classic valuation methods into one fair-value estimate and a 0–100 value score.
 *
 * The models are deliberately simple and transparent; every input and intermediate number is shown
 * in the app so users can judge for themselves. Not financial advice.
 */
object ValuationEngine {
    private val FINANCIALS = setOf("Financial Services")

    fun value(
        f: Fundamentals,
        sectorPe: Double?,
        a: Assumptions = Assumptions(),
        priceStats: PriceStats? = null,
    ): Valuation {
        val price = f.price
        val beta = (f.beta ?: 1.0).coerceIn(0.6, 2.0)
        val costOfEquity = maxOf(a.riskFreeRate + beta * a.equityRiskPremium, a.terminalGrowth + 0.03)
        val growth = (f.growthEstimate ?: f.earningsGrowth ?: f.revenueGrowth ?: 0.04).coerceIn(a.minGrowth, a.maxGrowth)
        val eps = f.trailingEps ?: f.forwardEps

        val models = mutableListOf<ModelValue>()

        // 1. DCF on equity free cash flow. Not meaningful for banks and insurers.
        val fcfps = f.freeCashflowPerShare
        if (fcfps != null && fcfps > 0 && f.sector !in FINANCIALS) {
            val v = dcf(fcfps, growth, costOfEquity, a.terminalGrowth)
            models += ModelValue(
                ModelKind.DCF, v,
                "FCF/share ${money(fcfps)}, growth ${pct(growth)} → ${pct(a.terminalGrowth)}, discount ${pct(costOfEquity)}",
            )
        }

        // 2. Dividend discount model.
        val dps = f.dividendRate
        if (dps != null && dps > 0) {
            val payout = f.payoutRatio?.takeIf { it in 0.0..1.0 }
            val sustainable = if (f.returnOnEquity != null && payout != null) f.returnOnEquity * (1 - payout) else a.terminalGrowth
            val g = sustainable.coerceIn(0.0, minOf(0.045, costOfEquity - 0.02))
            val v = dps * (1 + g) / (costOfEquity - g)
            models += ModelValue(ModelKind.DDM, v, "DPS ${money(dps)}, growth ${pct(g)}, discount ${pct(costOfEquity)}")
        }

        // 3. Graham number.
        val bvps = f.bookValuePerShare
        if (eps != null && eps > 0 && bvps != null && bvps > 0) {
            models += ModelValue(ModelKind.GRAHAM, sqrt(22.5 * eps * bvps), "EPS ${money(eps)}, book value/share ${money(bvps)}")
        }

        // 4. Relative P/E against sector peers.
        if (eps != null && eps > 0 && sectorPe != null && sectorPe > 0) {
            models += ModelValue(ModelKind.RELATIVE_PE, eps * sectorPe, "EPS ${money(eps)} × sector P/E ${num1(sectorPe)}")
        }

        // 5. Analyst consensus.
        val target = f.targetMeanPrice
        val analysts = f.analystCount ?: 0
        if (target != null && target > 0 && analysts >= 2) {
            models += ModelValue(ModelKind.ANALYST, target, "Mean target of $analysts analysts")
        }

        val marked = models.map { m ->
            if (price != null && price > 0 && (m.value > price * 4 || m.value < price * 0.2)) m.copy(outlier = true) else m
        }
        val usable = marked.filterNot { it.outlier }.map { it.value }

        val fair = if (usable.size >= 2) median(usable) else null
        val upside = if (fair != null && price != null && price > 0) fair / price - 1 else null

        val confidence = when {
            usable.size >= 4 && cv(usable) < 0.35 -> Confidence.HIGH
            usable.size >= 3 && cv(usable) < 0.6 -> Confidence.MEDIUM
            else -> Confidence.LOW
        }

        val verdict = when {
            upside == null -> Verdict.INSUFFICIENT_DATA
            upside >= a.undervaluedThreshold -> Verdict.UNDERVALUED
            upside >= 0.05 -> Verdict.SLIGHTLY_UNDERVALUED
            upside >= -0.10 -> Verdict.FAIR
            else -> Verdict.OVERVALUED
        }

        val flags = flags(f, marked, sectorPe, priceStats, usable)
        val score = score(f, upside, sectorPe, confidence, priceStats)

        return Valuation(price, fair, upside, verdict, confidence, score, marked, flags, costOfEquity, sectorPe)
    }

    /** Two-stage DCF: 5 years at [g1], 5 years fading linearly to [terminal], then a Gordon terminal value. */
    fun dcf(fcf0: Double, g1: Double, r: Double, terminal: Double): Double {
        var fcf = fcf0
        var pv = 0.0
        for (year in 1..10) {
            val g = if (year <= 5) g1 else g1 + (terminal - g1) * (year - 5) / 5.0
            fcf *= 1 + g
            pv += fcf / (1 + r).pow(year)
        }
        val tv = fcf * (1 + terminal) / (r - terminal)
        return pv + tv / (1 + r).pow(10)
    }

    /** Median P/E per sector from a set of peers, ignoring losses and extreme multiples. */
    fun sectorMedianPe(peers: Collection<Pair<String, Fundamentals>>): Map<String, Double> =
        peers.groupBy({ it.first }, { it.second })
            .mapValues { (_, list) -> list.mapNotNull { pe(it) }.filter { it in 3.0..80.0 } }
            .filterValues { it.size >= 3 }
            .mapValues { median(it.value) }

    fun pe(f: Fundamentals): Double? =
        f.trailingPe?.takeIf { it > 0 }
            ?: if (f.price != null && f.trailingEps != null && f.trailingEps > 0) f.price / f.trailingEps else null

    private fun score(f: Fundamentals, upside: Double?, sectorPe: Double?, conf: Confidence, ps: PriceStats?): Int {
        val parts = mutableListOf<Pair<Double, Double>>() // score 0..100 to weight
        fun lerp(x: Double, at0: Double, at100: Double) = ((x - at0) / (at100 - at0) * 100).coerceIn(0.0, 100.0)

        upside?.let { parts += lerp(it, -0.30, 0.50) to 0.40 }
        val pe = pe(f)
        if (pe != null && sectorPe != null) parts += lerp(pe / sectorPe, 1.6, 0.5) to 0.15
        f.priceToBook?.takeIf { it > 0 }?.let { parts += lerp(it, 4.0, 0.6) to 0.10 }
        if (f.targetMeanPrice != null && f.price != null && (f.analystCount ?: 0) >= 2) {
            parts += lerp(f.targetMeanPrice / f.price - 1, -0.10, 0.30) to 0.15
        }
        val hi = f.fiftyTwoWeekHigh ?: ps?.periodHigh
        val lo = f.fiftyTwoWeekLow ?: ps?.periodLow
        if (hi != null && lo != null && f.price != null && hi > lo) {
            parts += lerp((f.price - lo) / (hi - lo), 1.0, 0.0) to 0.10
        }
        f.dividendYield?.let { parts += lerp(it, 0.0, 0.07) to 0.10 }

        if (parts.isEmpty()) return 0
        var s = parts.sumOf { it.first * it.second } / parts.sumOf { it.second }

        if ((f.trailingEps ?: 0.0) < 0) s -= 15
        if ((f.debtToEquityPct ?: 0.0) > 200 && f.sector != "Financial Services") s -= 10
        if ((f.payoutRatio ?: 0.0) > 1.0) s -= 5
        if (conf == Confidence.LOW) s -= 5
        return s.coerceIn(0.0, 100.0).toInt()
    }

    private fun flags(
        f: Fundamentals,
        models: List<ModelValue>,
        sectorPe: Double?,
        ps: PriceStats?,
        usable: List<Double>,
    ): List<Flag> {
        val out = mutableListOf<Flag>()
        val price = f.price
        models.firstOrNull { it.kind == ModelKind.GRAHAM }?.let {
            if (price != null && price < it.value) out += Flag(true, "Trades below its Graham number (${money(it.value)})")
        }
        val pe = pe(f)
        if (pe != null && sectorPe != null && pe < sectorPe * 0.8) {
            out += Flag(true, "P/E of ${num1(pe)} is well below the sector median of ${num1(sectorPe)}")
        }
        if (pe != null && sectorPe != null && pe > sectorPe * 1.5) {
            out += Flag(false, "P/E of ${num1(pe)} is a large premium to the sector median of ${num1(sectorPe)}")
        }
        if (f.targetMeanPrice != null && price != null && (f.analystCount ?: 0) >= 2) {
            val u = f.targetMeanPrice / price - 1
            if (u > 0.15) out += Flag(true, "Analysts' average target implies ${pct(u)} upside")
            if (u < -0.05) out += Flag(false, "Trades above analysts' average target (${pct(u)})")
        }
        f.netCash?.let { if (it > 0 && f.sector != "Financial Services") out += Flag(true, "Net cash balance sheet") }
        f.dividendYield?.let { if (it >= 0.05) out += Flag(true, "Dividend yield of ${pct(it)} (before franking)") }
        f.pegRatio?.let { if (it in 0.0..1.0) out += Flag(true, "PEG ratio of ${num1(it)} (growth at a reasonable price)") }

        if ((f.trailingEps ?: 0.0) < 0) out += Flag(false, "Loss-making over the last 12 months")
        if ((f.debtToEquityPct ?: 0.0) > 150 && f.sector != "Financial Services") {
            out += Flag(false, "High leverage: debt is ${num1(f.debtToEquityPct!! / 100)}× equity")
        }
        if ((f.payoutRatio ?: 0.0) > 1.0) out += Flag(false, "Paying out more than it earns in dividends")
        ps?.vsSma200?.let { if (it < -0.10) out += Flag(false, "Price is ${pct(-it)} below its 200-day average (downtrend)") }
        if (f.isDetailed && (f.analystCount ?: 0) < 3) out += Flag(false, "Little analyst coverage")
        if (usable.size >= 2 && cv(usable) > 0.6) out += Flag(false, "Valuation models disagree widely")
        if (models.any { it.outlier }) out += Flag(false, "Some model results were excluded as outliers")
        return out
    }

    fun median(xs: List<Double>): Double {
        val s = xs.sorted()
        val n = s.size
        return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2
    }

    private fun cv(xs: List<Double>): Double {
        if (xs.size < 2) return 0.0
        val mean = xs.average()
        if (mean == 0.0) return 0.0
        val sd = sqrt(xs.sumOf { (it - mean).pow(2) } / (xs.size - 1))
        return abs(sd / mean)
    }

    fun money(v: Double): String = if (abs(v) >= 100) "$%,.0f".format(v) else "$%,.2f".format(v)
    fun pct(v: Double): String = "%.1f%%".format(v * 100)
    fun num1(v: Double): String = "%.1f".format(v)
}
