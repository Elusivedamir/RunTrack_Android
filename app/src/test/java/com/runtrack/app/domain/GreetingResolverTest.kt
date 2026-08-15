package com.runtrack.app.domain

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class GreetingResolverTest {
    @Test fun greetingBoundariesAreExact() {
        assertEquals("Доброй ночи!", GreetingResolver.greetingFor(LocalTime.of(4, 59)))
        assertEquals("Доброе утро!", GreetingResolver.greetingFor(LocalTime.of(5, 0)))
        assertEquals("Доброе утро!", GreetingResolver.greetingFor(LocalTime.of(11, 59)))
        assertEquals("Добрый день!", GreetingResolver.greetingFor(LocalTime.of(12, 0)))
        assertEquals("Добрый день!", GreetingResolver.greetingFor(LocalTime.of(17, 59)))
        assertEquals("Добрый вечер!", GreetingResolver.greetingFor(LocalTime.of(18, 0)))
        assertEquals("Добрый вечер!", GreetingResolver.greetingFor(LocalTime.of(22, 59)))
        assertEquals("Доброй ночи!", GreetingResolver.greetingFor(LocalTime.of(23, 0)))
    }
}
