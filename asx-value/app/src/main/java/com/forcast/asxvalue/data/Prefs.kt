package com.forcast.asxvalue.data

import android.content.Context
import com.forcast.asxvalue.core.data.Universe
import com.forcast.asxvalue.core.research.ClaudeResearch
import com.forcast.asxvalue.core.valuation.Assumptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    val apiKey: String = "",
    val model: String = ClaudeResearch.DEFAULT_MODEL,
    val assumptions: Assumptions = Assumptions(),
    val universe: Universe = Universe.ASX50,
)

/** Settings and watchlist, stored in the app's private SharedPreferences. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("asx_value", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _watchlist = MutableStateFlow(sp.getStringSet(KEY_WATCHLIST, DEFAULT_WATCHLIST)!!.toSortedSet().toList())
    val watchlist: StateFlow<List<String>> = _watchlist.asStateFlow()

    private fun load(): Settings {
        val d = Assumptions()
        return Settings(
            apiKey = sp.getString("api_key", "").orEmpty(),
            model = sp.getString("model", ClaudeResearch.DEFAULT_MODEL) ?: ClaudeResearch.DEFAULT_MODEL,
            assumptions = Assumptions(
                riskFreeRate = sp.getFloat("rf", d.riskFreeRate.toFloat()).toDouble(),
                equityRiskPremium = sp.getFloat("erp", d.equityRiskPremium.toFloat()).toDouble(),
                terminalGrowth = sp.getFloat("tg", d.terminalGrowth.toFloat()).toDouble(),
                undervaluedThreshold = sp.getFloat("uv", d.undervaluedThreshold.toFloat()).toDouble(),
            ),
            universe = runCatching { Universe.valueOf(sp.getString("universe", null) ?: "") }.getOrDefault(Universe.ASX50),
        )
    }

    fun update(transform: (Settings) -> Settings) {
        val s = transform(_settings.value)
        sp.edit()
            .putString("api_key", s.apiKey.trim())
            .putString("model", s.model)
            .putFloat("rf", s.assumptions.riskFreeRate.toFloat())
            .putFloat("erp", s.assumptions.equityRiskPremium.toFloat())
            .putFloat("tg", s.assumptions.terminalGrowth.toFloat())
            .putFloat("uv", s.assumptions.undervaluedThreshold.toFloat())
            .putString("universe", s.universe.name)
            .apply()
        _settings.value = s.copy(apiKey = s.apiKey.trim())
    }

    fun isWatched(ticker: String) = ticker.trim().uppercase().removeSuffix(".AX") in _watchlist.value

    fun toggleWatch(ticker: String) {
        val t = ticker.trim().uppercase().removeSuffix(".AX")
        val next = _watchlist.value.toMutableSet().apply { if (!add(t)) remove(t) }.toSortedSet().toList()
        sp.edit().putStringSet(KEY_WATCHLIST, next.toSet()).apply()
        _watchlist.value = next
    }

    private companion object {
        const val KEY_WATCHLIST = "watchlist"
        val DEFAULT_WATCHLIST = setOf("BHP", "CBA", "CSL", "WES")
    }
}
