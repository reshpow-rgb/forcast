package com.forcast.asxvalue.core.model

/** One daily (or weekly/monthly) closing price. */
data class PricePoint(
    val epochSec: Long,
    val close: Double,
    /** Close adjusted for dividends and splits; used for return statistics. */
    val adjClose: Double = close,
)

data class Dividend(val epochSec: Long, val amount: Double)

data class PriceHistory(
    val symbol: String,
    val currency: String?,
    val points: List<PricePoint>,
    val dividends: List<Dividend> = emptyList(),
)

data class NewsItem(
    val title: String,
    val publisher: String?,
    val link: String,
    val publishedEpochSec: Long?,
)

/**
 * Everything we know about a company's market data and financials.
 * Fields are nullable because Yahoo Finance coverage varies a lot across ASX stocks.
 * Ratios are fractions (0.05 = 5%) unless noted.
 */
data class Fundamentals(
    /** Yahoo symbol, e.g. "BHP.AX". */
    val symbol: String,
    val name: String? = null,
    val sector: String? = null,
    val industry: String? = null,
    val businessSummary: String? = null,
    val website: String? = null,
    val currency: String? = null,

    val price: Double? = null,
    val previousClose: Double? = null,
    val marketCap: Double? = null,
    val sharesOutstanding: Double? = null,

    val trailingEps: Double? = null,
    val forwardEps: Double? = null,
    val trailingPe: Double? = null,
    val forwardPe: Double? = null,
    val pegRatio: Double? = null,
    val priceToBook: Double? = null,
    val bookValuePerShare: Double? = null,

    /** Annual dividend per share. */
    val dividendRate: Double? = null,
    val payoutRatio: Double? = null,

    /** Levered (equity) free cash flow, trailing twelve months. */
    val freeCashflow: Double? = null,
    val operatingCashflow: Double? = null,
    val totalDebt: Double? = null,
    val totalCash: Double? = null,
    val totalRevenue: Double? = null,
    val ebitda: Double? = null,

    val revenueGrowth: Double? = null,
    val earningsGrowth: Double? = null,
    /** Analyst long-term (or next-year) EPS growth estimate. */
    val growthEstimate: Double? = null,
    val returnOnEquity: Double? = null,
    /** Debt to equity in percent, as Yahoo reports it (e.g. 45.0 = 0.45x). */
    val debtToEquityPct: Double? = null,
    val profitMargin: Double? = null,
    val beta: Double? = null,

    val fiftyTwoWeekHigh: Double? = null,
    val fiftyTwoWeekLow: Double? = null,

    val targetMeanPrice: Double? = null,
    val targetHighPrice: Double? = null,
    val targetLowPrice: Double? = null,
    val analystCount: Int? = null,
    val recommendationKey: String? = null,
    /** 1 = strong buy … 5 = sell. */
    val recommendationMean: Double? = null,

    /** True once the detailed quoteSummary call has been merged in. */
    val isDetailed: Boolean = false,
) {
    val ticker: String get() = symbol.removeSuffix(".AX")

    val changePct: Double?
        get() = if (price != null && previousClose != null && previousClose > 0) price / previousClose - 1 else null

    val dividendYield: Double?
        get() = if (dividendRate != null && price != null && price > 0) dividendRate / price else null

    val freeCashflowPerShare: Double?
        get() = if (freeCashflow != null && sharesOutstanding != null && sharesOutstanding > 0) freeCashflow / sharesOutstanding else null

    val netCash: Double?
        get() = if (totalCash != null && totalDebt != null) totalCash - totalDebt else null

    /** Fill gaps in this record with values from [other] (this record wins). */
    fun mergedWith(other: Fundamentals): Fundamentals = Fundamentals(
        symbol = symbol,
        name = name ?: other.name,
        sector = sector ?: other.sector,
        industry = industry ?: other.industry,
        businessSummary = businessSummary ?: other.businessSummary,
        website = website ?: other.website,
        currency = currency ?: other.currency,
        price = price ?: other.price,
        previousClose = previousClose ?: other.previousClose,
        marketCap = marketCap ?: other.marketCap,
        sharesOutstanding = sharesOutstanding ?: other.sharesOutstanding,
        trailingEps = trailingEps ?: other.trailingEps,
        forwardEps = forwardEps ?: other.forwardEps,
        trailingPe = trailingPe ?: other.trailingPe,
        forwardPe = forwardPe ?: other.forwardPe,
        pegRatio = pegRatio ?: other.pegRatio,
        priceToBook = priceToBook ?: other.priceToBook,
        bookValuePerShare = bookValuePerShare ?: other.bookValuePerShare,
        dividendRate = dividendRate ?: other.dividendRate,
        payoutRatio = payoutRatio ?: other.payoutRatio,
        freeCashflow = freeCashflow ?: other.freeCashflow,
        operatingCashflow = operatingCashflow ?: other.operatingCashflow,
        totalDebt = totalDebt ?: other.totalDebt,
        totalCash = totalCash ?: other.totalCash,
        totalRevenue = totalRevenue ?: other.totalRevenue,
        ebitda = ebitda ?: other.ebitda,
        revenueGrowth = revenueGrowth ?: other.revenueGrowth,
        earningsGrowth = earningsGrowth ?: other.earningsGrowth,
        growthEstimate = growthEstimate ?: other.growthEstimate,
        returnOnEquity = returnOnEquity ?: other.returnOnEquity,
        debtToEquityPct = debtToEquityPct ?: other.debtToEquityPct,
        profitMargin = profitMargin ?: other.profitMargin,
        beta = beta ?: other.beta,
        fiftyTwoWeekHigh = fiftyTwoWeekHigh ?: other.fiftyTwoWeekHigh,
        fiftyTwoWeekLow = fiftyTwoWeekLow ?: other.fiftyTwoWeekLow,
        targetMeanPrice = targetMeanPrice ?: other.targetMeanPrice,
        targetHighPrice = targetHighPrice ?: other.targetHighPrice,
        targetLowPrice = targetLowPrice ?: other.targetLowPrice,
        analystCount = analystCount ?: other.analystCount,
        recommendationKey = recommendationKey ?: other.recommendationKey,
        recommendationMean = recommendationMean ?: other.recommendationMean,
        isDetailed = isDetailed || other.isDetailed,
    )
}

/** Normalises user input like "bhp", "BHP.AX" or " cba " to a Yahoo ASX symbol. */
fun toAsxSymbol(input: String): String {
    val t = input.trim().uppercase()
    return if (t.endsWith(".AX")) t else "$t.AX"
}
