package com.forcast.asxvalue.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forcast.asxvalue.core.data.ChartRange
import com.forcast.asxvalue.core.model.Fundamentals
import com.forcast.asxvalue.core.model.NewsItem
import com.forcast.asxvalue.core.model.PriceHistory
import com.forcast.asxvalue.core.research.ClaudeResearch
import com.forcast.asxvalue.core.research.ResearchSummary
import com.forcast.asxvalue.core.valuation.PriceStats
import com.forcast.asxvalue.core.valuation.Valuation
import com.forcast.asxvalue.core.valuation.ValuationEngine
import com.forcast.asxvalue.data.Prefs
import com.forcast.asxvalue.data.StockRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiNoteState(val loading: Boolean = false, val markdown: String? = null, val error: String? = null)

data class DetailState(
    val loading: Boolean = true,
    val error: String? = null,
    val fundamentals: Fundamentals? = null,
    val valuation: Valuation? = null,
    /** Statistics from one year of daily prices (drives trend signals). */
    val yearStats: PriceStats? = null,
    val range: ChartRange = ChartRange.Y1,
    val history: PriceHistory? = null,
    val chartLoading: Boolean = false,
    val chartError: String? = null,
    val news: List<NewsItem> = emptyList(),
    val summary: String? = null,
    val ai: AiNoteState = AiNoteState(),
    val watched: Boolean = false,
) {
    val rangeStats: PriceStats? get() = history?.let(PriceStats::from)
}

class StockDetailViewModel(
    private val symbol: String,
    private val repo: StockRepository,
    private val prefs: Prefs,
) : ViewModel() {
    private val _state = MutableStateFlow(DetailState(watched = prefs.isWatched(symbol)))
    val state: StateFlow<DetailState> = _state.asStateFlow()
    private var chartJob: Job? = null

    val hasApiKey: Boolean get() = prefs.settings.value.apiKey.isNotBlank()

    init {
        load(refresh = false)
    }

    fun load(refresh: Boolean) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val fundamentals = async { runCatching { repo.fundamentals(symbol, refresh) } }
            val year = async { runCatching { repo.history(symbol, ChartRange.Y1) } }
            val news = async { runCatching { repo.news(symbol) }.getOrDefault(emptyList()) }

            val f = fundamentals.await().getOrElse { e ->
                _state.update { it.copy(loading = false, error = e.message ?: "Couldn't load $symbol") }
                return@launch
            }
            val history = year.await().getOrNull()
            val stats = history?.let(PriceStats::from)
            val v = ValuationEngine.value(f, repo.sectorPe(f.sector), prefs.settings.value.assumptions, stats)
            val items = news.await()
            _state.update {
                it.copy(
                    loading = false,
                    fundamentals = f,
                    valuation = v,
                    yearStats = stats,
                    history = if (it.range == ChartRange.Y1) history else it.history,
                    chartError = if (history == null && it.range == ChartRange.Y1) "Couldn't load price history" else it.chartError,
                    news = items,
                    summary = ResearchSummary.build(f, v, stats, items),
                )
            }
        }
    }

    fun setRange(r: ChartRange) {
        if (r == _state.value.range && _state.value.history != null) return
        _state.update { it.copy(range = r, chartLoading = true, chartError = null) }
        chartJob?.cancel()
        chartJob = viewModelScope.launch {
            val h = runCatching { repo.history(symbol, r) }
            _state.update {
                it.copy(chartLoading = false, history = h.getOrNull() ?: it.history, chartError = h.exceptionOrNull()?.let { "Couldn't load price history" })
            }
        }
    }

    fun toggleWatch() {
        prefs.toggleWatch(symbol)
        _state.update { it.copy(watched = prefs.isWatched(symbol)) }
    }

    fun generateAiNote() {
        val s = _state.value
        val f = s.fundamentals ?: return
        val v = s.valuation ?: return
        val settings = prefs.settings.value
        _state.update { it.copy(ai = AiNoteState(loading = true)) }
        viewModelScope.launch {
            val ai = try {
                AiNoteState(markdown = ClaudeResearch(settings.apiKey, settings.model).researchNote(f, v, s.yearStats, s.news))
            } catch (e: Exception) {
                AiNoteState(error = e.message ?: "Something went wrong")
            }
            _state.update { it.copy(ai = ai) }
        }
    }
}
