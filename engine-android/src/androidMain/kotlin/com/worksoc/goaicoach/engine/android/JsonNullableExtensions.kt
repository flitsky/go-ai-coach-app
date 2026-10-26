package com.worksoc.goaicoach.engine.android

import org.json.JSONObject

/**
 * `org.json.JSONObject`의 "필드 없음"과 "필드가 JSON null임"을 코틀린 nullable로 통일하는
 * 헬퍼. app-android(`middleware/JsonNullableExtensions.kt`)에도 같은 내용의 파일이 있다 —
 * 두 모듈이 서로 의존하는 방향이 아니라서 공유 인프라를 새로 만들기보다 이 정도 크기(4줄짜리
 * 함수 몇 개)는 의도적으로 중복을 허용한다.
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

internal fun JSONObject.optNullableDouble(name: String): Double? =
    if (isNull(name) || !has(name)) null else optDouble(name)
