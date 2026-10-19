package com.riseup.alarm

import com.riseup.alarm.mission.ChallengeGenerator
import com.riseup.alarm.mission.Difficulty
import com.riseup.alarm.mission.MathChallenge
import com.riseup.alarm.mission.MissionType
import com.riseup.alarm.mission.TypingChallenge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MissionTest {

    @Test
    fun everyCombinationGeneratesASolvableChallenge() {
        val random = Random(1)
        repeat(200) {
            for (type in MissionType.entries) for (difficulty in Difficulty.entries) {
                val c = ChallengeGenerator.generate(type, difficulty, random)
                val solution = when (c) {
                    is MathChallenge -> c.answer.toString()
                    is TypingChallenge -> c.prompt
                }
                assertTrue("$type/$difficulty: ${c.prompt}", c.check(solution))
                assertFalse(c.check(""))
            }
        }
    }

    @Test
    fun mathAnswerMatchesPrompt() {
        val c = ChallengeGenerator.generate(MissionType.MATH, Difficulty.EASY, Random(7)) as MathChallenge
        val (a, b) = c.prompt.split(" + ").map { it.toInt() }
        assertEquals(a + b, c.answer)
        assertTrue(c.check(" ${c.answer} "))
        assertFalse(c.check((c.answer + 1).toString()))
    }

    @Test
    fun typingIgnoresCaseSpacingAndCurlyApostrophes() {
        val c = TypingChallenge("You've got this.", "Type")
        assertTrue(c.check("you’ve   GOT this."))
        assertFalse(c.check("You've got this"))
    }

    @Test
    fun wordCountScalesWithDifficulty() {
        val counts = Difficulty.entries.map {
            ChallengeGenerator.generate(MissionType.WORDS, it, Random(3)).prompt.split(" ").size
        }
        assertEquals(listOf(3, 5, 7), counts)
    }
}
