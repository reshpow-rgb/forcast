package com.riseup.alarm.data

import android.content.Context
import com.riseup.alarm.mission.Difficulty
import com.riseup.alarm.mission.MissionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Stores alarms as JSON in SharedPreferences - plenty for a handful of alarms, no database needed. */
class AlarmRepository private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("alarms", Context.MODE_PRIVATE)
    private val _alarms = MutableStateFlow(load())
    val alarms: StateFlow<List<Alarm>> = _alarms

    fun get(id: Int): Alarm? = _alarms.value.firstOrNull { it.id == id }

    fun newId(): Int = (_alarms.value.maxOfOrNull { it.id } ?: 0) + 1

    @Synchronized
    fun upsert(alarm: Alarm) {
        val list = _alarms.value.filterNot { it.id == alarm.id } + alarm
        save(list.sortedWith(compareBy({ it.hour }, { it.minute })))
    }

    @Synchronized
    fun delete(id: Int) = save(_alarms.value.filterNot { it.id == id })

    private fun save(list: List<Alarm>) {
        _alarms.value = list
        val json = JSONArray()
        list.forEach { json.put(it.toJson()) }
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    private fun load(): List<Alarm> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val json = JSONArray(raw)
            (0 until json.length()).map { json.getJSONObject(it).toAlarm() }
        }.getOrDefault(emptyList())
    }

    private fun Alarm.toJson() = JSONObject()
        .put("id", id)
        .put("hour", hour)
        .put("minute", minute)
        .put("days", JSONArray(days.toList()))
        .put("enabled", enabled)
        .put("label", label)
        .put("mission", mission.name)
        .put("difficulty", difficulty.name)
        .put("rounds", rounds)
        .put("sound", sound)
        .put("soundTitle", soundTitle)
        .put("animal", animal.name)

    private fun JSONObject.toAlarm(): Alarm {
        val daysJson = getJSONArray("days")
        return Alarm(
            id = getInt("id"),
            hour = getInt("hour"),
            minute = getInt("minute"),
            days = (0 until daysJson.length()).map { daysJson.getInt(it) }.toSet(),
            enabled = getBoolean("enabled"),
            label = optString("label"),
            mission = enumOrDefault(optString("mission"), MissionType.MATH),
            difficulty = enumOrDefault(optString("difficulty"), Difficulty.NORMAL),
            rounds = optInt("rounds", 1),
            sound = optString("sound", "default"),
            soundTitle = optString("soundTitle", "Phone's alarm sound"),
            animal = enumOrDefault(optString("animal"), Animal.CORGI),
        )
    }

    companion object {
        private const val KEY = "alarms_json"

        @Volatile
        private var instance: AlarmRepository? = null

        fun get(context: Context): AlarmRepository =
            instance ?: synchronized(this) {
                instance ?: AlarmRepository(context.applicationContext).also { instance = it }
            }
    }
}
