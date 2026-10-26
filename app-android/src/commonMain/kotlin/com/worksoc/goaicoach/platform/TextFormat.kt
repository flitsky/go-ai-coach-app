package com.worksoc.goaicoach.platform

/**
 * `String.format`의 공용 자리(백로그 #294 — S6). 공용 Kotlin에는 `String.format`이 없다.
 *
 * - [formatDeviceLocale]: 기기 언어의 숫자 표기(독일어면 소수점이 쉼표) — 안드로이드는 `String.format(format, *args)` 그대로.
 * - [formatRootLocale]: 언어와 무관한 표기(시계 `1:05:09`처럼) — 안드로이드는 `String.format(Locale.ROOT, …)` 그대로.
 *
 * iOS는 이 앱이 쓰는 지시자만 아는 작은 서식기다(`%d` · `%0Nd` · `%.Nf` · `%s` · `%n$s` · `%%`) — 숫자 표기는 늘 ASCII다.
 */
expect fun formatDeviceLocale(format: String, vararg args: Any?): String

expect fun formatRootLocale(format: String, vararg args: Any?): String
