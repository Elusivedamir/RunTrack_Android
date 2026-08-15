package com.runtrack.app.voice

internal object RussianVoiceTokens {
    const val START = "start"

    internal val allTokenIds: Set<String> = setOf(
        "start",
        "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
        "seventeen", "eighteen", "nineteen",
        "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety",
        "one_hundred", "two_hundred", "three_hundred", "four_hundred", "five_hundred",
        "six_hundred", "seven_hundred", "eight_hundred", "nine_hundred",
        "one_feminine", "two_feminine",
        "thousand_one", "thousand_few", "thousand_many",
        "million_one", "million_few", "million_many",
        "billion_one", "billion_few", "billion_many",
        "kilometer_one", "kilometer_few", "kilometer_many",
    )

    fun kilometerTokens(kilometers: Int): List<String> {
        require(kilometers > 0) { "kilometers must be positive" }
        return buildList {
            appendNumber(this, kilometers)
            add(
                pluralToken(
                    kilometers,
                    one = "kilometer_one",
                    few = "kilometer_few",
                    many = "kilometer_many",
                )
            )
        }
    }

    private fun appendNumber(out: MutableList<String>, value: Int) {
        require(value > 0)

        val billions = value / 1_000_000_000
        val millions = (value / 1_000_000) % 1_000
        val thousands = (value / 1_000) % 1_000
        val units = value % 1_000

        if (billions > 0) {
            appendBelowThousand(out, billions, feminine = false)
            out += pluralToken(
                billions,
                one = "billion_one",
                few = "billion_few",
                many = "billion_many",
            )
        }
        if (millions > 0) {
            appendBelowThousand(out, millions, feminine = false)
            out += pluralToken(
                millions,
                one = "million_one",
                few = "million_few",
                many = "million_many",
            )
        }
        if (thousands > 0) {
            appendBelowThousand(out, thousands, feminine = true)
            out += pluralToken(
                thousands,
                one = "thousand_one",
                few = "thousand_few",
                many = "thousand_many",
            )
        }
        if (units > 0) {
            appendBelowThousand(out, units, feminine = false)
        }
    }

    private fun appendBelowThousand(
        out: MutableList<String>,
        value: Int,
        feminine: Boolean,
    ) {
        require(value in 1..999)

        val hundreds = value / 100
        if (hundreds > 0) {
            out += when (hundreds) {
                1 -> "one_hundred"
                2 -> "two_hundred"
                3 -> "three_hundred"
                4 -> "four_hundred"
                5 -> "five_hundred"
                6 -> "six_hundred"
                7 -> "seven_hundred"
                8 -> "eight_hundred"
                9 -> "nine_hundred"
                else -> error("unreachable")
            }
        }

        val remainder = value % 100
        if (remainder == 0) return

        if (remainder < 20) {
            out += smallNumberToken(remainder, feminine)
            return
        }

        out += when (remainder / 10) {
            2 -> "twenty"
            3 -> "thirty"
            4 -> "forty"
            5 -> "fifty"
            6 -> "sixty"
            7 -> "seventy"
            8 -> "eighty"
            9 -> "ninety"
            else -> error("unreachable")
        }

        val ones = remainder % 10
        if (ones > 0) out += smallNumberToken(ones, feminine)
    }

    private fun smallNumberToken(value: Int, feminine: Boolean): String = when (value) {
        1 -> if (feminine) "one_feminine" else "one"
        2 -> if (feminine) "two_feminine" else "two"
        3 -> "three"
        4 -> "four"
        5 -> "five"
        6 -> "six"
        7 -> "seven"
        8 -> "eight"
        9 -> "nine"
        10 -> "ten"
        11 -> "eleven"
        12 -> "twelve"
        13 -> "thirteen"
        14 -> "fourteen"
        15 -> "fifteen"
        16 -> "sixteen"
        17 -> "seventeen"
        18 -> "eighteen"
        19 -> "nineteen"
        else -> error("number token out of range: $value")
    }

    private fun pluralToken(
        value: Int,
        one: String,
        few: String,
        many: String,
    ): String {
        val lastTwo = value % 100
        if (lastTwo in 11..14) return many
        return when (value % 10) {
            1 -> one
            2, 3, 4 -> few
            else -> many
        }
    }
}
