package com.forcast.asxvalue.ui.screener

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forcast.asxvalue.core.data.AsxUniverse
import com.forcast.asxvalue.core.data.Universe
import com.forcast.asxvalue.core.valuation.Verdict
import com.forcast.asxvalue.data.Prefs
import com.forcast.asxvalue.data.ScreenRow
import com.forcast.asxvalue.data.StockRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SortBy(val label: String) {
    SCORE("Value score"), UPSIDE("Upside to fair value"), PE("Lowest P/E"), YIELD("Highest dividend yield"), MARKET_CAP("Market cap")
}

data class ScreenerState(
    val universe: Universe = Universe.ASX50,
    val rows: List<ScreenRow> = emptyList(),
    val loading: Boolean = false,
    val detailed: Int = 0,
    val total: Int = 0,
    val error: String? = null,
    val sort: SortBy = SortBy.SCORE,
    val undervaluedOnly: Boolean = false,
    val sector: String? = null,
    val lastUpdated: Long? = null,
) {
    val sectors: List<String> get() = rows.mapNotNull { it.fundamentals.sector }.distinct().sorted()

    val visible: List<ScreenRow>
        get() = rows
            .filter { !undervaluedOnly || it.valuation.verdict == Verdict.UNDERVALUED || it.valuation.verdict == Verdict.SLIGHTLY_UNDERVALUED }
            .filter { sector == null || it.fundamentals.sector == sector }
            .let { list ->
                when (sort) {
                    SortBy.SCORE -> list.sortedByDescending { it.valuation.score }
                    SortBy.UPSIDE -> list.sortedByDescending { it.valuation.upside ?: Double.NEGATIVE_INFINITY }
                    SortBy.PE -> list.sortedBy { com.forcast.asxvalue.core.valuation.ValuationEngine.pe(it.fundamentals) ?: Double.MAX_VALUE }
                    SortBy.YIELD -> list.sortedByDescending { it.fundamentals.dividendYield ?: 0.0 }
                    SortBy.MARKET_CAP -> list.sortedByDescending { it.fundamentals.marketCap ?: 0.0 }
                }
            }
}

/** Runs a screen over a universe of stocks. Also used by the watchlist tab with a fixed universe. */
class ScreenerViewModel(
    private val repo: StockRepository,
    private val prefs: Prefs,
    fixedUniverse: Universe? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(ScreenerState(universe = fixedUniverse ?: prefs.settings.value.universe))
    val state: StateFlow<ScreenerState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        run(refresh = false)
        if (fixedUniverse == Universe.WATCHLIST) {
            viewModelScope.launch { prefs.watchlist.drop(1).collect { run(refresh = false) } }
        }
    }

    fun setUniverse(u: Universe) {
        if (u == _state.value.universe) return
        prefs.update { it.copy(universe = u) }
        _state.update { it.copy(universe = u, sector = null, rows = emptyList()) }
        run(refresh = false)
    }

    fun setSort(s: SortBy) = _state.update { it.copy(sort = s) }
    fun setUndervaluedOnly(on: Boolean) = _state.update { it.copy(undervaluedOnly = on) }
    fun setSector(s: String?) = _state.update { it.copy(sector = s) }

    fun run(refresh: Boolean) {
        job?.cancel()
        val stocks = AsxUniverse.forUniverse(_state.value.universe, prefs.watchlist.value)
        if (stocks.isEmpty()) {
            _state.update { it.copy(rows = emptyList(), loading = false, error = null, total = 0, detailed = 0) }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            try {
                repo.screen(stocks, prefs.settings.value.assumptions, refresh).collect { u ->
                    _state.update { it.copy(rows = u.rows, detailed = u.detailed, total = u.total, lastUpdated = System.currentTimeMillis()) }
                }
                _state.update { it.copy(loading = false) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Couldn't load market data") }
            }
        }
    }
}
