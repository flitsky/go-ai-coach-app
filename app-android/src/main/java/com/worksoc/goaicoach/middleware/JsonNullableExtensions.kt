package com.worksoc.goaicoach.middleware

import org.json.JSONObject

/**
 * `org.json.JSONObject`는 "필드가 없음"과 "필드가 JSON null임"을 서로 다른 방식으로
 * 노출한다(`has`/`isNull`/`opt*`가 각각 별개). 이 파일의 확장 함수들은 그 두 경우를 하나로
 * 묶어 코틀린 nullable 타입으로 돌려준다 — 2계층(`middleware`)의 원격 엔진/포지션 분석 코덱과
 * 4계층 어댑터(`persistence`)의 디스크 캐시 코덱이 동일한 null 처리 로직을 각자 재정의하고
 * 있었기에 이 파일로 통합했다.
 */
internal fun JSONObject.putNullable(
    name: String,
    value: Any?,
): JSONObject =
    put(name, value ?: JSONObject.NULL)

internal fun JSONObject.optNullableString(name: String): String? =
    if (isNull(name)) null else optString(name)

internal fun JSONObject.optNullableInt(name: String): Int? =
    if (isNull(name) || !has(name)) null else optInt(name)

internal fun JSONObject.optNullableLong(name: String): Long? =
    if (isNull(name) || !has(name)) null else optLong(name)

internal fun JSONObject.optNullableDouble(name: String): Double? =
    if (isNull(name) || !has(name)) null else optDouble(name)
