package com.forcast.asxvalue.core.data

import com.forcast.asxvalue.core.model.Fundamentals
import com.forcast.asxvalue.core.model.NewsItem
import com.forcast.asxvalue.core.model.PriceHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Price history ranges offered in the app, mapped to Yahoo's range/interval parameters. */
enum class ChartRange(val label: String, val range: String, val interval: String) {
    M1("1M", "1mo", "1d"),
    M6("6M", "6mo", "1d"),
    Y1("1Y", "1y", "1d"),
    Y5("5Y", "5y", "1wk"),
    MAX("Max", "max", "1mo"),
}

/**
 * Client for Yahoo Finance's public (unofficial, keyless) endpoints.
 *
 * - `v8/finance/chart` for price history (no auth needed)
 * - `v7/finance/quote` for batch quotes and `v10/finance/quoteSummary` for fundamentals; both need a
 *   session cookie plus a "crumb" token, fetched once and cached.
 * - `v1/finance/search` for recent news headlines.
 */
class YahooFinance(
    private val http: OkHttpClient = defaultClient(),
) {
    private var crumb: String? = null
    private val crumbLock = Mutex()

    suspend fun history(symbol: String, range: ChartRange): PriceHistory = withContext(Dispatchers.IO) {
        val url = "https://query1.finance.yahoo.com/v8/finance/chart/$symbol".toHttpUrl().newBuilder()
            .addQueryParameter("range", range.range)
            .addQueryParameter("interval", range.interval)
            .addQueryParameter("events", "div,split")
            .addQueryParameter("includeAdjustedClose", "true")
            .build()
        YahooParsers.parseChart(get(url), symbol)
    }

    /** Quotes for many symbols at once (chunked to keep URLs short). */
    suspend fun quotes(symbols: List<String>): List<Fundamentals> = withContext(Dispatchers.IO) {
        symbols.chunked(40).flatMap { chunk ->
            withCrumb { c ->
                val url = "https://query1.finance.yahoo.com/v7/finance/quote".toHttpUrl().newBuilder()
                    .addQueryParameter("symbols", chunk.joinToString(","))
                    .addQueryParameter("crumb", c)
                    .build()
                YahooParsers.parseQuotes(get(url))
            }
        }
    }

    suspend fun fundamentals(symbol: String): Fundamentals = withContext(Dispatchers.IO) {
        withCrumb { c ->
            val url = "https://query2.finance.yahoo.com/v10/finance/quoteSummary/$symbol".toHttpUrl().newBuilder()
                .addQueryParameter(
                    "modules",
                    "price,summaryDetail,defaultKeyStatistics,financialData,assetProfile,earningsTrend",
                )
                .addQueryParameter("crumb", c)
                .build()
            YahooParsers.parseQuoteSummary(get(url), symbol)
        }
    }

    suspend fun news(symbol: String, count: Int = 10): List<NewsItem> = withContext(Dispatchers.IO) {
        val url = "https://query2.finance.yahoo.com/v1/finance/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", symbol)
            .addQueryParameter("quotesCount", "0")
            .addQueryParameter("newsCount", count.toString())
            .build()
        YahooParsers.parseNews(get(url))
    }

    // --- internals -----------------------------------------------------------------------------

    private class HttpStatusException(val code: Int) : IOException("Yahoo Finance returned HTTP $code")

    private fun get(url: HttpUrl): String {
        val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (resp.code == 404) throw YahooParsers.YahooException("Ticker not found on Yahoo Finance")
            if (!resp.isSuccessful) throw HttpStatusException(resp.code)
            return body
        }
    }

    private suspend fun <T> withCrumb(block: (String) -> T): T {
        val c = ensureCrumb()
        return try {
            block(c)
        } catch (e: HttpStatusException) {
            if (e.code != 401 && e.code != 403) throw e
            // Crumb or cookie expired: refresh once and retry.
            crumbLock.withLock { crumb = null }
            block(ensureCrumb())
        }
    }

    private suspend fun ensureCrumb(): String = crumbLock.withLock {
        crumb?.let { return it }
        // Visiting these pages sets the session cookie that the crumb is tied to.
        for (seed in listOf("https://fc.yahoo.com", "https://finance.yahoo.com/quote/BHP.AX")) {
            runCatching {
                http.newCall(Request.Builder().url(seed).header("User-Agent", USER_AGENT).build()).execute().close()
            }
            val c = runCatching { get("https://query1.finance.yahoo.com/v1/test/getcrumb".toHttpUrl()) }
                .getOrNull()?.trim()
            if (!c.isNullOrEmpty() && !c.contains('<') && !c.contains('{') && c.length < 64) {
                crumb = c
                return c
            }
        }
        throw IOException("Couldn't start a Yahoo Finance session. Check your internet connection and try again.")
    }

    private class MemoryCookieJar : CookieJar {
        private val store = mutableMapOf<String, Cookie>()

        @Synchronized
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookies.forEach { store["${it.domain}|${it.path}|${it.name}"] = it }
        }

        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            store.values.removeAll { it.expiresAt < now }
            return store.values.filter { it.matches(url) }
        }
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .cookieJar(MemoryCookieJar())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
