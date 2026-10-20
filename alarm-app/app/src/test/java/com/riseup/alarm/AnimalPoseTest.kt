package com.riseup.alarm

import com.riseup.alarm.ui.animals.Pose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimalPoseTest {

    @Test
    fun asleepHasEyesShutAndZs() {
        val p = Pose.of(0f, t = 1f)
        assertEquals(0f, p.eyes, 0.001f)
        assertEquals(1f, p.zzz, 0.001f)
        assertEquals(0f, p.joy, 0.001f)
    }

    @Test
    fun awakeIsHappyWithEyesOpenAndNoZs() {
        val p = Pose.of(1f, t = 1f)
        assertEquals(1f, p.eyes, 0.001f)
        assertEquals(0f, p.zzz, 0.001f)
        assertEquals(1f, p.joy, 0.001f)
        assertEquals(1f, p.perk, 0.001f)
    }

    @Test
    fun halfAwakeYawnsSometimes() {
        val yawns = (0 until 110).map { Pose.of(0.6f, t = it / 10f).yawn }
        assertTrue(yawns.any { it > 0.5f })
        assertTrue(yawns.any { it == 0f })
    }

    @Test
    fun wakingIsMonotonic() {
        val perks = (0..10).map { Pose.of(it / 10f, t = 0f).perk }
        assertEquals(perks.sorted(), perks)
    }
}
