package com.worksoc.goaicoach.engine.android

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * KataGo JSON analysis 엔진의 줄을 읽는 도우미(백로그 #293).
 *
 * 엔진 코드가 안드로이드 · iOS 공용이 되며 org.json(안드로이드 전용) 대신 kotlinx.serialization의 `JsonObject`를 쓴다.
 * 아래 `opt*`는 **org.json의 같은 이름 함수가 하던 뜻 그대로**다 — 키가 없거나 JSON `null`이면 기본값, 숫자는 문자열이어도
 * 읽고, 정수 자리의 소수는 버림. 파서 · 어댑터의 결과가 org.json 때와 같아야 하므로 뜻을 바꾸지 말 것.
 */
internal fun parseJsonObject(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

/** org.json `optString(key, default)` — 없거나 `null`이면 [default], 문자열이 아니면 그 값의 글자 그대로. */
internal fun JsonObject.optString(key: String, default: String = ""): String =
    when (val value = this[key]) {
        null, JsonNull -> default
        is JsonPrimitive -> value.content
        else -> value.toString()
    }

/** org.json `optBoolean(key, default)` — JSON 참/거짓, 또는 글자 "true"/"false"(대소문자 무시). 그 밖은 [default]. */
internal fun JsonObject.optBoolean(key: String, default: Boolean = false): Boolean {
    val value = this[key] as? JsonPrimitive ?: return default
    if (value is JsonNull) return default
    return when (value.content.lowercase()) {
        "true" -> true
        "false" -> false
        else -> default
    }
}

/** org.json `optInt(key, default)` — 숫자(또는 숫자 글자)면 정수로(소수는 버림), 아니면 [default]. */
internal fun JsonObject.optInt(key: String, default: Int = 0): Int = this[key].toNumberOrNull()?.toInt() ?: default

/** 키가 있고 `null`이 아니면 [optInt]의 값(숫자가 아니면 0 — org.json과 같다), 아니면 `null`. */
internal fun JsonObject.optNullableInt(key: String): Int? = if (hasNonNull(key)) optInt(key) else null

/** 키가 있고 `null`이 아니면 숫자(숫자가 아니면 NaN — org.json `optDouble`과 같다), 아니면 `null`. */
internal fun JsonObject.optNullableDouble(key: String): Double? =
    if (hasNonNull(key)) this[key].toNumberOrNull() ?: Double.NaN else null

internal fun JsonObject.optJsonObject(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.optJsonArray(key: String): JsonArray? = this[key] as? JsonArray

internal fun JsonArray.optJsonObject(index: Int): JsonObject? = getOrNull(index) as? JsonObject

/** org.json `JSONArray.optDouble(index, default)`. */
internal fun JsonArray.optDouble(index: Int, default: Double): Double = getOrNull(index).toNumberOrNull() ?: default

private fun JsonObject.hasNonNull(key: String): Boolean = this[key].let { value -> value != null && value != JsonNull }

private fun JsonElement?.toNumberOrNull(): Double? {
    val primitive = this as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    return primitive.content.toDoubleOrNull()
}
