# RunTrack Android UI Prototype

Это **визуальный Jetpack Compose прототип** на основе утверждённого референса из 20 экранов.

## Что уже есть

- 20 экранов из исходного референса.
- Единый portrait viewport.
- Полноэкранный режим.
- Свайп влево/вправо между соседними экранами.
- Основные невидимые hotspots:
  - быстрый старт;
  - старт тренировки;
  - пауза / продолжение / завершение;
  - просмотр результата;
  - вкладки Обзор / Карта / Маршрут;
  - нижняя навигация Главная / История / Статистика / Профиль.
- Долгое нажатие на любой экран открывает список всех 20 экранов.

## Важно

Это **Phase 0 — visual prototype**. Экран пока рендерится из эталонного изображения.
GPS, Google Maps, Room, Weather API, foreground tracking и настоящие UI-компоненты здесь сознательно ещё не реализованы.

Цель этой версии — сначала утвердить внешний вид и переходы на реальном Android-устройстве.
После утверждения каждый экран заменяется на настоящие Compose-компоненты без изменения бизнес-логики.

## Stack

- Kotlin 2.2.20
- Android Gradle Plugin 8.12.2
- Gradle 8.13
- compileSdk / targetSdk 36
- Jetpack Compose BOM 2026.06.00
- activity-compose 1.13.0
- Java 17

## Build

Проект можно открыть в Android Studio и собрать `app`.

Или через Gradle 8.13:

```bash
gradle :app:assembleDebug
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## GitHub Actions

В проект включён `.github/workflows/build-apk.yml`.
После push в GitHub workflow собирает debug APK и загружает его как artifact `RunTrack-UI-Prototype-debug-apk`.
