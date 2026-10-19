package com.riseup.alarm

import com.riseup.alarm.data.Alarm
import com.riseup.alarm.ui.formatDuration
import com.riseup.alarm.ui.formatTime
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class AlarmTest {

    // Wednesday 2026-10-21 08:00
    private val wedMorning = LocalDateTime.of(2026, 10, 21, 8, 0)

    @Test
    fun oneOffLaterTodayRingsToday() {
        val next = Alarm(1, 9, 30).nextTrigger(wedMorning)
        assertEquals(LocalDateTime.of(2026, 10, 21, 9, 30), next)
    }

    @Test
    fun oneOffEarlierTodayRingsTomorrow() {
        val next = Alarm(1, 7, 0).nextTrigger(wedMorning)
        assertEquals(LocalDateTime.of(2026, 10, 22, 7, 0), next)
    }

    @Test
    fun exactlyNowRollsToNextDay() {
        val next = Alarm(1, 8, 0).nextTrigger(wedMorning)
        assertEquals(LocalDateTime.of(2026, 10, 22, 8, 0), next)
    }

    @Test
    fun weekdayAlarmOnFridayEveningSkipsWeekend() {
        val fridayNight = LocalDateTime.of(2026, 10, 23, 22, 0)
        val next = Alarm(1, 6, 30, days = setOf(1, 2, 3, 4, 5)).nextTrigger(fridayNight)
        assertEquals(LocalDateTime.of(2026, 10, 26, 6, 30), next) // Monday
    }

    @Test
    fun repeatingAlarmLaterTodayRingsToday() {
        val next = Alarm(1, 9, 0, days = setOf(3)).nextTrigger(wedMorning)
        assertEquals(LocalDateTime.of(2026, 10, 21, 9, 0), next)
    }

    @Test
    fun formatting() {
        assertEquals("06:05" to null, formatTime(6, 5, true))
        assertEquals("12:00" to "AM", formatTime(0, 0, false))
        assertEquals("1:30" to "PM", formatTime(13, 30, false))
        assertEquals("7h 1m", formatDuration((7 * 60 + 1) * 60_000L))
        assertEquals("1m", formatDuration(5_000))
        assertEquals("2d 3h", formatDuration((51 * 60) * 60_000L))
    }
}
