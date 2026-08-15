package com.runtrack.app.domain

import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

object GreetingResolver {
    fun greetingFor(time: LocalTime): String = when (time.hour) {
        in 5..11 -> "Доброе утро!"
        in 12..17 -> "Добрый день!"
        in 18..22 -> "Добрый вечер!"
        else -> "Доброй ночи!"
    }

    fun millisUntilNextBoundary(now: ZonedDateTime): Long {
        val date = now.toLocalDate()
        val next = when (now.hour) {
            in 0..4 -> date.atTime(5, 0).atZone(now.zone)
            in 5..11 -> date.atTime(12, 0).atZone(now.zone)
            in 12..17 -> date.atTime(18, 0).atZone(now.zone)
            in 18..22 -> date.atTime(23, 0).atZone(now.zone)
            else -> date.plusDays(1).atTime(5, 0).atZone(now.zone)
        }
        return Duration.between(now, next).toMillis().coerceAtLeast(1_000L)
    }
}
