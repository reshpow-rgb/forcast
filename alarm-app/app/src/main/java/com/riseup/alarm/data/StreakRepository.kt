package com.riseup.alarm.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/** Remembers which days an alarm rang and which days the mission was completed. */
class StreakRepository private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("streaks", Context.MODE_PRIVATE)

    var rang: Set<LocalDate> = load(KEY_RANG)
        private set
    var woke: Set<LocalDate> = load(KEY_WOKE)
        private set

    private val _stats = MutableStateFlow(compute())
    val stats: StateFlow<StreakStats> = _stats

    @Synchronized
    fun recordRing(day: LocalDate = LocalDate.now()) {
        if (day in rang) return
        rang = prune(rang + day)
        save(KEY_RANG, rang)
    }

    @Synchronized
    fun recordWake(day: LocalDate = LocalDate.now()) {
        if (day in woke) return
        woke = prune(woke + day)
        save(KEY_WOKE, woke)
    }

    /** Recompute, e.g. when the app is reopened on a new day. */
    fun refresh() {
        _stats.value = compute()
    }

    private fun compute() = Streaks.compute(rang, woke, LocalDate.now())

    private fun save(key: String, days: Set<LocalDate>) {
        prefs.edit().putStringSet(key, days.map(LocalDate::toString).toSet()).apply()
        refresh()
    }

    private fun load(key: String): Set<LocalDate> =
        prefs.getStringSet(key, emptySet()).orEmpty()
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .toSet()

    /** Keep about two years - enough for the best-streak record without growing forever. */
    private fun prune(days: Set<LocalDate>): Set<LocalDate> {
        val cutoff = LocalDate.now().minusDays(730)
        return days.filter { it.isAfter(cutoff) }.toSet()
    }

    companion object {
        private const val KEY_RANG = "rang"
        private const val KEY_WOKE = "woke"

        @Volatile
        private var instance: StreakRepository? = null

        fun get(context: Context): StreakRepository =
            instance ?: synchronized(this) {
                instance ?: StreakRepository(context.applicationContext).also { instance = it }
            }
    }
}
