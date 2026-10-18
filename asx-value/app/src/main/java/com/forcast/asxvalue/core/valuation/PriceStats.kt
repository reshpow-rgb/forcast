package com.forcast.asxvalue.core.valuation

import com.forcast.asxvalue.core.model.PriceHistory
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/** Summary statistics derived from a price history. */
data class PriceStats(
    val lastClose: Double,
    val totalReturn: Double,
    /** Compound annual growth rate over the history (null if < ~1 year of data). */
    val cagr: Double?,
    /** Annualised volatility of log returns. */
    val volatility: Double?,
    /** Largest peak-to-trough fall, as a negative fraction. */
    val maxDrawdown: Double,
    val sma50: Double?,
    val sma200: Double?,
    val periodHigh: Double,
    val periodLow: Double,
    val years: Double,
) {
    /** Price relative to its 200-period average: positive = above trend. */
    val vsSma200: Double? get() = sma200?.let { lastClose / it - 1 }

    companion object {
        private const val SECONDS_PER_YEAR = 365.25 * 24 * 3600

        fun from(history: PriceHistory): PriceStats? {
            val pts = history.points
            if (pts.size < 2) return null
            val adj = pts.map { it.adjClose }
            val closes = pts.map { it.close }
            val years = (pts.last().epochSec - pts.first().epochSec) / SECONDS_PER_YEAR
            val total = adj.last() / adj.first() - 1
            val cagr = if (years >= 0.9) (adj.last() / adj.first()).pow(1 / years) - 1 else null

            val logReturns = adj.zipWithNext { a, b -> ln(b / a) }
            val periodsPerYear = if (years > 0) (pts.size - 1) / years else 0.0
            val vol = if (logReturns.size >= 10 && periodsPerYear > 0) {
                val mean = logReturns.average()
                val variance = logReturns.sumOf { (it - mean).pow(2) } / (logReturns.size - 1)
                sqrt(variance) * sqrt(periodsPerYear)
            } else null

            var peak = adj.first()
            var maxDd = 0.0
            for (p in adj) {
                if (p > peak) peak = p
                val dd = p / peak - 1
                if (dd < maxDd) maxDd = dd
            }

            fun sma(n: Int) = if (closes.size >= n) closes.takeLast(n).average() else null

            return PriceStats(
                lastClose = closes.last(),
                totalReturn = total,
                cagr = cagr,
                volatility = vol,
                maxDrawdown = maxDd,
                sma50 = sma(50),
                sma200 = sma(200),
                periodHigh = closes.max(),
                periodLow = closes.min(),
                years = years,
            )
        }

        /** Simple moving average series aligned with [values]; null until enough data. */
        fun movingAverage(values: List<Double>, window: Int): List<Double?> {
            if (window <= 1) return values
            val out = ArrayList<Double?>(values.size)
            var sum = 0.0
            for (i in values.indices) {
                sum += values[i]
                if (i >= window) sum -= values[i - window]
                out += if (i >= window - 1) sum / window else null
            }
            return out
        }
    }
}
