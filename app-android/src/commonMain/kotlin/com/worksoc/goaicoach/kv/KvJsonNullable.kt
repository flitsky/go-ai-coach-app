package com.worksoc.goaicoach.kv

import com.worksoc.goaicoach.kv.json.JSONObject

// 안드로이드 `middleware/JsonNullableExtensions.kt`의 공용 사본(백로그 #294) — kv 코덱들이 함께 쓴다(코덱마다 따로 두지 않는다).
// 「필드가 없음」과 「JSON null」을 하나로 묶어 코틀린 nullable로 돌려준다. 고칠 때는 둘을 함께 고친다.

internal fun JSONObject.optNullableString(name: String): String? = if (isNull(name)) null else optString(name)

internal fun JSONObject.optNullableInt(name: String): Int? = if (isNull(name) || !has(name)) null else optInt(name)

internal fun JSONObject.optNullableLong(name: String): Long? = if (isNull(name) || !has(name)) null else optLong(name)

internal fun JSONObject.optNullableDouble(name: String): Double? = if (isNull(name) || !has(name)) null else optDouble(name)
