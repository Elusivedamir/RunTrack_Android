package com.runtrack.app

import androidx.annotation.DrawableRes

data class PrototypeScreen(
    val title: String,
    @DrawableRes val drawableRes: Int,
)

val prototypeScreens = listOf(
    PrototypeScreen("Главный экран", R.drawable.screen_01_main),
    PrototypeScreen("Настройка тренировки", R.drawable.screen_02_workout_setup),
    PrototypeScreen("Активная тренировка", R.drawable.screen_03_active_workout),
    PrototypeScreen("Пауза", R.drawable.screen_04_pause),
    PrototypeScreen("Завершение тренировки", R.drawable.screen_05_finish),
    PrototypeScreen("Результат — обзор", R.drawable.screen_06_result_overview),
    PrototypeScreen("Результат — карта", R.drawable.screen_07_result_map),
    PrototypeScreen("Результат — маршрут", R.drawable.screen_08_result_route_art),
    PrototypeScreen("Детали тренировки", R.drawable.screen_09_workout_details),
    PrototypeScreen("История", R.drawable.screen_10_history),
    PrototypeScreen("Статистика — обзор", R.drawable.screen_11_stats_overview),
    PrototypeScreen("Статистика — графики", R.drawable.screen_12_stats_charts),
    PrototypeScreen("Рекорды", R.drawable.screen_13_records),
    PrototypeScreen("Сравнение", R.drawable.screen_14_comparison),
    PrototypeScreen("Профиль", R.drawable.screen_15_profile),
    PrototypeScreen("Календарь", R.drawable.screen_16_calendar),
    PrototypeScreen("Карта всех маршрутов", R.drawable.screen_17_all_routes_map),
    PrototypeScreen("Настройки", R.drawable.screen_18_settings),
    PrototypeScreen("Подключения", R.drawable.screen_19_connections),
    PrototypeScreen("Экспорт тренировки", R.drawable.screen_20_export),
)
