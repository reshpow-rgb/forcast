package com.riseup.alarm.mission

import kotlin.random.Random

enum class MissionType(val title: String, val blurb: String) {
    MATH("Maths", "Solve a sum"),
    QUOTE("Quote", "Re-type a motivational quote"),
    WORDS("Words", "Re-type a list of words"),
}

enum class Difficulty(val title: String, val premium: Boolean) {
    EASY("Easy", false),
    NORMAL("Normal", false),
    HARD("Hard", true),
}

/** One task the user has to complete before the alarm stops. */
sealed interface Challenge {
    /** What is shown to the user. */
    val prompt: String

    /** Instruction shown above the prompt. */
    val instruction: String

    /** True when the user's input solves the challenge. */
    fun check(input: String): Boolean
}

data class MathChallenge(override val prompt: String, val answer: Int) : Challenge {
    override val instruction get() = "Solve"
    override fun check(input: String) = input.trim().toIntOrNull() == answer
}

data class TypingChallenge(override val prompt: String, override val instruction: String) : Challenge {
    override fun check(input: String) = normalise(input) == normalise(prompt)

    /** Case, curly quotes and extra spaces don't count - a sleepy thumb shouldn't fail on those. */
    private fun normalise(s: String) = s
        .replace('’', '\'')
        .replace('‘', '\'')
        .replace('“', '"')
        .replace('”', '"')
        .trim()
        .replace(Regex("\\s+"), " ")
        .lowercase()
}

object ChallengeGenerator {

    fun generate(type: MissionType, difficulty: Difficulty, random: Random = Random.Default): Challenge =
        when (type) {
            MissionType.MATH -> math(difficulty, random)
            MissionType.QUOTE -> quote(difficulty, random)
            MissionType.WORDS -> words(difficulty, random)
        }

    private fun math(difficulty: Difficulty, random: Random): MathChallenge = when (difficulty) {
        Difficulty.EASY -> {
            val a = random.nextInt(10, 50)
            val b = random.nextInt(10, 50)
            MathChallenge("$a + $b", a + b)
        }
        Difficulty.NORMAL -> {
            val a = random.nextInt(3, 10)
            val b = random.nextInt(11, 30)
            val c = random.nextInt(10, 100)
            MathChallenge("$a × $b + $c", a * b + c)
        }
        Difficulty.HARD -> {
            val a = random.nextInt(11, 30)
            val b = random.nextInt(11, 30)
            val c = random.nextInt(100, 500)
            val d = random.nextInt(10, 100)
            MathChallenge("$a × $b + $c − $d", a * b + c - d)
        }
    }

    private fun quote(difficulty: Difficulty, random: Random): TypingChallenge {
        val pool = when (difficulty) {
            Difficulty.EASY -> Content.quotes.filter { it.length <= 32 }
            Difficulty.NORMAL -> Content.quotes.filter { it.length in 25..55 }
            Difficulty.HARD -> Content.quotes.filter { it.length > 45 }
        }
        return TypingChallenge(pool.random(random), "Type this quote")
    }

    private fun words(difficulty: Difficulty, random: Random): TypingChallenge {
        val (count, pool) = when (difficulty) {
            Difficulty.EASY -> 3 to Content.words.filter { it.length <= 6 }
            Difficulty.NORMAL -> 5 to Content.words
            Difficulty.HARD -> 7 to Content.words.filter { it.length >= 6 }
        }
        val picked = pool.shuffled(random).take(count)
        return TypingChallenge(picked.joinToString(" "), "Type these words")
    }
}
