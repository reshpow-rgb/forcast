package com.forcast.asxvalue.data

import com.forcast.asxvalue.core.data.AsxUniverse
import com.forcast.asxvalue.core.data.ChartRange
import com.forcast.asxvalue.core.data.UniverseStock
import com.forcast.asxvalue.core.data.YahooFinance
import com.forcast.asxvalue.core.model.Fundamentals
import com.forcast.asxvalue.core.model.NewsItem
import com.forcast.asxvalue.core.model.PriceHistory
import com.forcast.asxvalue.core.valuation.Assumptions
import com.forcast.asxvalue.core.valuation.PriceStats
import com.forcast.asxvalue.core.valuation.Valuation
import com.forcast.asxvalue.core.valuation.ValuationEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

data class ScreenRow(val fundamentals: Fundamentals, val valuation: Valuation)

data class ScreenUpdate(
    val rows: List<ScreenRow>,
    val sectorPe: Map<String, Double>,
    /** Stocks whose detailed fundamentals have been loaded so far. */
    val detailed: Int,
    val total: Int,
    val failed: List<String> = emptyList(),
)

/** Fetches market data, caches it in memory and runs the valuation models. */
class StockRepository(private val yahoo: YahooFinance) {
    private val fundamentals = ConcurrentHashMap<String, Fundamentals>()
    private val oneYear = ConcurrentHashMap<String, PriceHistory>()

    /** Live sector P/E medians from the most recent broad screen. */
    @Volatile var liveSectorPe: Map<String, Double> = emptyMap()
        private set

    /** The latest screen results, used by the AI value-ideas screen. */
    @Volatile var lastScreen: ScreenUpdate? = null
        private set

    fun sectorPe(sector: String?): Double =
        sector?.let { liveSectorPe[it] ?: AsxUniverse.fallbackSectorPe[it] } ?: AsxUniverse.MARKET_PE

    /**
     * Screens [stocks]: one batch quote call gives quick valuations straight away, then detailed
     * fundamentals are fetched (most promising first) and each row is re-valued as they arrive.
     */
    fun screen(stocks: List<UniverseStock>, a: Assumptions, refresh: Boolean): Flow<ScreenUpdate> = channelFlow {
        val quotes = yahoo.quotes(stocks.map { it.symbol }).associateBy { it.symbol }
        val current = LinkedHashMap<String, Fundamentals>()
        for (s in stocks) {
            val q = quotes[s.symbol] ?: continue
            val base = q.copy(name = q.name ?: s.name, sector = s.sector.takeIf { it != "Other" })
            val cached = fundamentals[s.symbol]?.takeIf { it.isDetailed && !refresh }
            current[s.symbol] = cached?.copy(price = base.price, previousClose = base.previousClose)?.mergedWith(base) ?: base
        }

        val peers = current.values.mapNotNull { f -> f.sector?.let { it to f } }
        val computed = ValuationEngine.sectorMedianPe(peers)
        val sectorPe = if (stocks.size >= 30) computed.also { liveSectorPe = it } else liveSectorPe.ifEmpty { computed }
        fun pe(sector: String?) = sector?.let { sectorPe[it] ?: AsxUniverse.fallbackSectorPe[it] } ?: AsxUniverse.MARKET_PE

        val failed = mutableListOf<String>()
        fun snapshot(): ScreenUpdate {
            val rows = current.values.map { f -> ScreenRow(f, ValuationEngine.value(f, pe(f.sector), a, oneYear[f.symbol]?.let(PriceStats::from))) }
            return ScreenUpdate(rows, sectorPe, current.values.count { it.isDetailed }, current.size, failed.toList())
                .also { lastScreen = it }
        }

        val first = snapshot()
        send(first)

        val todo = first.rows.sortedByDescending { it.valuation.score }
            .map { it.fundamentals }
            .filterNot { it.isDetailed }
        val permits = Semaphore(4)
        val lock = Any()
        todo.map { f ->
            launch {
                permits.withPermit {
                    val detailed = runCatching { yahoo.fundamentals(f.symbol) }.getOrNull()
                    val update = synchronized(lock) {
                        if (detailed == null) {
                            failed += f.ticker
                        } else {
                            val merged = detailed.copy(price = f.price ?: detailed.price).mergedWith(f)
                            fundamentals[f.symbol] = merged
                            current[f.symbol] = merged
                        }
                        snapshot()
                    }
                    send(update)
                }
            }
        }
    }

    suspend fun fundamentals(symbol: String, refresh: Boolean = false): Fundamentals {
        fundamentals[symbol]?.takeIf { it.isDetailed && !refresh }?.let { return it }
        val f = yahoo.fundamentals(symbol)
        val universe = AsxUniverse.find(symbol)
        val merged = f.copy(sector = f.sector ?: universe?.sector, name = f.name ?: universe?.name)
        fundamentals[symbol] = merged
        return merged
    }

    suspend fun history(symbol: String, range: ChartRange): PriceHistory {
        if (range == ChartRange.Y1) oneYear[symbol]?.let { return it }
        return yahoo.history(symbol, range).also { if (range == ChartRange.Y1) oneYear[symbol] = it }
    }

    suspend fun news(symbol: String): List<NewsItem> = yahoo.news(symbol)
}
