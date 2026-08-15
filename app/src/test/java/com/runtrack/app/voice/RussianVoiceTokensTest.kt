package com.runtrack.app.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class RussianVoiceTokensTest {
    @Test
    fun `uses correct Russian kilometer declension`() {
        assertEquals(listOf("one", "kilometer_one"), RussianVoiceTokens.kilometerTokens(1))
        assertEquals(listOf("two", "kilometer_few"), RussianVoiceTokens.kilometerTokens(2))
        assertEquals(listOf("four", "kilometer_few"), RussianVoiceTokens.kilometerTokens(4))
        assertEquals(listOf("five", "kilometer_many"), RussianVoiceTokens.kilometerTokens(5))
        assertEquals(listOf("eleven", "kilometer_many"), RussianVoiceTokens.kilometerTokens(11))
        assertEquals(listOf("fourteen", "kilometer_many"), RussianVoiceTokens.kilometerTokens(14))
        assertEquals(
            listOf("twenty", "one", "kilometer_one"),
            RussianVoiceTokens.kilometerTokens(21),
        )
        assertEquals(
            listOf("twenty", "two", "kilometer_few"),
            RussianVoiceTokens.kilometerTokens(22),
        )
        assertEquals(
            listOf("twenty", "five", "kilometer_many"),
            RussianVoiceTokens.kilometerTokens(25),
        )
    }

    @Test
    fun `speaks thousands with feminine forms and keeps whole-number declension`() {
        assertEquals(
            listOf("one_feminine", "thousand_one", "kilometer_many"),
            RussianVoiceTokens.kilometerTokens(1_000),
        )
        assertEquals(
            listOf("one_feminine", "thousand_one", "one", "kilometer_one"),
            RussianVoiceTokens.kilometerTokens(1_001),
        )
        assertEquals(
            listOf("one_feminine", "thousand_one", "two", "kilometer_few"),
            RussianVoiceTokens.kilometerTokens(1_002),
        )
        assertEquals(
            listOf("two_feminine", "thousand_few", "kilometer_many"),
            RussianVoiceTokens.kilometerTokens(2_000),
        )
        assertEquals(
            listOf("five", "thousand_many", "kilometer_many"),
            RussianVoiceTokens.kilometerTokens(5_000),
        )
    }

    @Test
    fun `supports the full positive Int range without a fixed kilometer cap`() {
        assertEquals(
            listOf(
                "two", "billion_few",
                "one_hundred", "forty", "seven", "million_many",
                "four_hundred", "eighty", "three", "thousand_few",
                "six_hundred", "forty", "seven", "kilometer_many",
            ),
            RussianVoiceTokens.kilometerTokens(Int.MAX_VALUE),
        )
    }
}
