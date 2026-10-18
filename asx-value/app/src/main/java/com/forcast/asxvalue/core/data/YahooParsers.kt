package com.forcast.asxvalue.core.data

import com.forcast.asxvalue.core.model.Dividend
import com.forcast.asxvalue.core.model.Fundamentals
import com.forcast.asxvalue.core.model.NewsItem
import com.forcast.asxvalue.core.model.PriceHistory
import com.forcast.asxvalue.core.model.PricePoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * Pure JSON parsing for Yahoo Finance's unofficial endpoints. Kept free of networking
 * and Android so it can be unit tested on the JVM.
 */
object YahooParsers {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    class YahooException(message: String) : Exception(message)

    // --- JSON helpers -------------------------------------------------------------------------

    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonElement?.arr(): JsonArray? = this as? JsonArray
    private fun JsonObject?.o(key: String): JsonObject? = this?.get(key).obj()

    /** Reads a number that Yahoo may send as `1.2` or as `{"raw": 1.2, "fmt": "1.20"}`. */
    private fun JsonObject?.num(key: String): Double? {
        val el = this?.get(key) ?: return null
        val v = when (el) {
            is JsonObject -> (el["raw"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.doubleOrNull
            is JsonPrimitive -> el.takeIf { it !is JsonNull }?.doubleOrNull
            else -> null
        }
        return v?.takeIf { it.isFinite() }
    }

    private fun JsonObject?.str(key: String): String? {
        val el = this?.get(key) ?: return null
        val p = when (el) {
            is JsonObject -> el["fmt"] as? JsonPrimitive
            is JsonPrimitive -> el
            else -> null
        }
        return p?.takeIf { it !is JsonNull && it.isString }?.content?.takeIf { it.isNotBlank() }
    }

    private fun parse(body: String): JsonObject =
        runCatching { json.parseToJsonElement(body).jsonObject }
            .getOrElse { throw YahooException("Unexpected response from Yahoo Finance") }

    private fun throwIfError(container: JsonObject?) {
        val err = container.o("error") ?: return
        val desc = err.str("description") ?: err.str("code") ?: "Unknown error"
        throw YahooException(desc)
    }

    // --- Chart (price history) ----------------------------------------------------------------

    fun parseChart(body: String, symbol: String): PriceHistory {
        val chart = parse(body).o("chart")
        throwIfError(chart)
        val result = chart?.get("result").arr()?.firstOrNull().obj()
            ?: throw YahooException("No price history for $symbol")
        val meta = result.o("meta")
        val timestamps = result["timestamp"].arr() ?: JsonArray(emptyList())
        val indicators = result.o("indicators")
        val closes = indicators?.get("quote").arr()?.firstOrNull().obj()?.get("close").arr()
        val adjCloses = indicators?.get("adjclose").arr()?.firstOrNull().obj()?.get("adjclose").arr()

        val points = ArrayList<PricePoint>(timestamps.size)
        for (i in timestamps.indices) {
            val ts = (timestamps[i] as? JsonPrimitive)?.longOrNull ?: continue
            val close = (closes?.getOrNull(i) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.doubleOrNull ?: continue
            if (!close.isFinite() || close <= 0) continue
            val adj = (adjCloses?.getOrNull(i) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.doubleOrNull
            points += PricePoint(ts, close, adj?.takeIf { it.isFinite() && it > 0 } ?: close)
        }

        val dividends = result.o("events").o("dividends")?.values?.mapNotNull { d ->
            val o = d.obj()
            val amount = o.num("amount")
            val date = o.num("date")?.toLong()
            if (amount != null && date != null) Dividend(date, amount) else null
        }?.sortedBy { it.epochSec } ?: emptyList()

        return PriceHistory(symbol, meta.str("currency"), points, dividends)
    }

    // --- Batch quote (v7) ---------------------------------------------------------------------

    fun parseQuotes(body: String): List<Fundamentals> {
        val qr = parse(body).o("quoteResponse")
        throwIfError(qr)
        return qr?.get("result").arr()?.mapNotNull { el ->
            val q = el.obj() ?: return@mapNotNull null
            val symbol = q.str("symbol") ?: return@mapNotNull null
            val price = q.num("regularMarketPrice")
            val dps = q.num("trailingAnnualDividendRate")?.takeIf { it > 0 } ?: q.num("dividendRate")
            Fundamentals(
                symbol = symbol,
                name = q.str("longName") ?: q.str("shortName"),
                currency = q.str("currency"),
                price = price,
                previousClose = q.num("regularMarketPreviousClose"),
                marketCap = q.num("marketCap"),
                sharesOutstanding = q.num("sharesOutstanding"),
                trailingEps = q.num("epsTrailingTwelveMonths"),
                forwardEps = q.num("epsForward"),
                trailingPe = q.num("trailingPE"),
                forwardPe = q.num("forwardPE"),
                priceToBook = q.num("priceToBook"),
                bookValuePerShare = q.num("bookValue"),
                dividendRate = dps,
                fiftyTwoWeekHigh = q.num("fiftyTwoWeekHigh"),
                fiftyTwoWeekLow = q.num("fiftyTwoWeekLow"),
                recommendationMean = q.str("averageAnalystRating")?.substringBefore(" ")?.toDoubleOrNull(),
            )
        } ?: emptyList()
    }

    // --- quoteSummary (detailed fundamentals) -------------------------------------------------

    fun parseQuoteSummary(body: String, symbol: String): Fundamentals {
        val qs = parse(body).o("quoteSummary")
        throwIfError(qs)
        val r = qs?.get("result").arr()?.firstOrNull().obj()
            ?: throw YahooException("No fundamentals for $symbol")

        val price = r.o("price")
        val sd = r.o("summaryDetail")
        val ks = r.o("defaultKeyStatistics")
        val fd = r.o("financialData")
        val ap = r.o("assetProfile")

        val trend = r.o("earningsTrend")?.get("trend").arr()?.mapNotNull { it.obj() } ?: emptyList()
        fun trendGrowth(period: String) = trend.firstOrNull { it.str("period") == period }.num("growth")
        val growthEstimate = trendGrowth("+5y") ?: trendGrowth("+1y")

        val dps = sd.num("dividendRate")?.takeIf { it > 0 } ?: sd.num("trailingAnnualDividendRate")

        return Fundamentals(
            symbol = symbol,
            name = price.str("longName") ?: price.str("shortName"),
            sector = ap.str("sector"),
            industry = ap.str("industry"),
            businessSummary = ap.str("longBusinessSummary"),
            website = ap.str("website"),
            currency = price.str("currency") ?: fd.str("financialCurrency"),
            price = price.num("regularMarketPrice") ?: fd.num("currentPrice"),
            previousClose = price.num("regularMarketPreviousClose") ?: sd.num("previousClose"),
            marketCap = price.num("marketCap") ?: sd.num("marketCap"),
            sharesOutstanding = ks.num("sharesOutstanding") ?: ks.num("impliedSharesOutstanding"),
            trailingEps = ks.num("trailingEps"),
            forwardEps = ks.num("forwardEps"),
            trailingPe = sd.num("trailingPE"),
            forwardPe = sd.num("forwardPE") ?: ks.num("forwardPE"),
            pegRatio = ks.num("pegRatio"),
            priceToBook = ks.num("priceToBook"),
            bookValuePerShare = ks.num("bookValue"),
            dividendRate = dps,
            payoutRatio = sd.num("payoutRatio"),
            freeCashflow = fd.num("freeCashflow"),
            operatingCashflow = fd.num("operatingCashflow"),
            totalDebt = fd.num("totalDebt"),
            totalCash = fd.num("totalCash"),
            totalRevenue = fd.num("totalRevenue"),
            ebitda = fd.num("ebitda"),
            revenueGrowth = fd.num("revenueGrowth"),
            earningsGrowth = fd.num("earningsGrowth"),
            growthEstimate = growthEstimate,
            returnOnEquity = fd.num("returnOnEquity"),
            debtToEquityPct = fd.num("debtToEquity"),
            profitMargin = fd.num("profitMargins"),
            beta = sd.num("beta") ?: ks.num("beta"),
            fiftyTwoWeekHigh = sd.num("fiftyTwoWeekHigh"),
            fiftyTwoWeekLow = sd.num("fiftyTwoWeekLow"),
            targetMeanPrice = fd.num("targetMeanPrice"),
            targetHighPrice = fd.num("targetHighPrice"),
            targetLowPrice = fd.num("targetLowPrice"),
            analystCount = fd.num("numberOfAnalystOpinions")?.toInt(),
            recommendationKey = fd?.get("recommendationKey")?.let { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content },
            recommendationMean = fd.num("recommendationMean"),
            isDetailed = true,
        )
    }

    // --- News search ---------------------------------------------------------------------------

    fun parseNews(body: String): List<NewsItem> =
        parse(body)["news"].arr()?.mapNotNull { el ->
            val n = el.obj() ?: return@mapNotNull null
            val title = n.str("title") ?: return@mapNotNull null
            val link = n.str("link") ?: return@mapNotNull null
            NewsItem(title, n.str("publisher"), link, n.num("providerPublishTime")?.toLong())
        } ?: emptyList()
}
