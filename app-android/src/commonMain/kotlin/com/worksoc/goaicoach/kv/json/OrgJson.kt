package com.worksoc.goaicoach.kv.json

/*
 * 안드로이드 `org.json`의 공용 사본(백로그 #294 — 이식 계획 P2). 안드로이드 저장소의 코덱을 **import 줄만 바꿔** 공용으로 옮겨
 * iOS에서 쓰려고 둔다 — 안드로이드 저장소 자신은 계속 플랫폼의 `org.json`을 쓴다(`KvStoreParityTest`가 같은 입력에서 둘의 출력이
 * 같은지 본다).
 *
 * 따라 한 동작(Android libcore `org.json`):
 *  - 키 순서는 넣은 순서(읽은 문서의 순서). `put(name, null)`은 지우기. 유한하지 않은 Double은 거절.
 *  - 읽은 수: 소수점 · 지수가 없으면 정수(Int에 들면 Int, 아니면 Long), 있으면 Double.
 *  - `getInt`/`optInt` 등은 수와 「수로 읽히는 문자열」을 다 받는다. `optString`은 문자열이 아니면 그 값의 글자를, 없으면 대체값을.
 *  - 쓰기: 수는 정수로 떨어지면 정수 표기(3.0 → `3`), `/`는 `\/`로, 제어 문자는 `\uXXXX`로.
 */

class JSONException(message: String) : Exception(message)

class JSONObject() {
    private val values = LinkedHashMap<String, Any>()

    constructor(json: String) : this() {
        val parsed = JsonReader(json).readValueAtTop()
        if (parsed !is JSONObject) throw JSONException("Value $json cannot be converted to JSONObject")
        values.putAll(parsed.values)
    }

    fun length(): Int = values.size

    fun put(name: String, value: Boolean): JSONObject = apply { values[name] = value }

    fun put(name: String, value: Int): JSONObject = apply { values[name] = value }

    fun put(name: String, value: Long): JSONObject = apply { values[name] = value }

    fun put(name: String, value: Double): JSONObject = apply { values[name] = checkDouble(value) }

    fun put(name: String, value: Any?): JSONObject = apply {
        when (value) {
            null -> values.remove(name)
            is Double -> values[name] = checkDouble(value)
            is Float -> values[name] = checkDouble(value.toDouble())
            else -> values[name] = value
        }
    }

    fun remove(name: String): Any? = values.remove(name)

    fun has(name: String): Boolean = values.containsKey(name)

    fun isNull(name: String): Boolean = values[name].let { it == null || it === NULL }

    fun opt(name: String): Any? = values[name]

    fun get(name: String): Any = values[name] ?: throw JSONException("No value for $name")

    fun keys(): Iterator<String> = values.keys.toList().iterator()

    fun names(): JSONArray? = if (values.isEmpty()) null else JSONArray().also { array -> values.keys.forEach { array.put(it) } }

    fun getBoolean(name: String): Boolean = toBoolean(get(name)) ?: throw mismatch(name, "boolean")

    fun optBoolean(name: String, fallback: Boolean = false): Boolean = toBoolean(opt(name)) ?: fallback

    fun getDouble(name: String): Double = toDouble(get(name)) ?: throw mismatch(name, "double")

    fun optDouble(name: String, fallback: Double = Double.NaN): Double = toDouble(opt(name)) ?: fallback

    fun getInt(name: String): Int = toInt(get(name)) ?: throw mismatch(name, "int")

    fun optInt(name: String, fallback: Int = 0): Int = toInt(opt(name)) ?: fallback

    fun getLong(name: String): Long = toLong(get(name)) ?: throw mismatch(name, "long")

    fun optLong(name: String, fallback: Long = 0L): Long = toLong(opt(name)) ?: fallback

    fun getString(name: String): String = valueToString(get(name)) ?: throw mismatch(name, "String")

    fun optString(name: String, fallback: String = ""): String = valueToString(opt(name)) ?: fallback

    fun getJSONArray(name: String): JSONArray = get(name) as? JSONArray ?: throw mismatch(name, "JSONArray")

    fun optJSONArray(name: String): JSONArray? = opt(name) as? JSONArray

    fun getJSONObject(name: String): JSONObject = get(name) as? JSONObject ?: throw mismatch(name, "JSONObject")

    fun optJSONObject(name: String): JSONObject? = opt(name) as? JSONObject

    override fun toString(): String = StringBuilder().also { writeObject(it, this, indent = 0, depth = 0) }.toString()

    fun toString(indentSpaces: Int): String = StringBuilder().also { writeObject(it, this, indentSpaces, depth = 0) }.toString()

    internal fun entries(): Set<Map.Entry<String, Any>> = values.entries

    private fun mismatch(name: String, type: String) = JSONException("Value at $name of type ${values[name]?.let { it::class.simpleName }} cannot be converted to $type")

    companion object {
        /** JSON의 `null` — 키가 있되 값이 없음. [isNull]이 참이고 [optString]은 `"null"`을 돌려준다(Android와 같다). */
        val NULL: Any = object {
            override fun equals(other: Any?): Boolean = other == null || other === this

            override fun hashCode(): Int = 0

            override fun toString(): String = "null"
        }
    }
}

class JSONArray() {
    private val values = ArrayList<Any?>()

    constructor(json: String) : this() {
        val parsed = JsonReader(json).readValueAtTop()
        if (parsed !is JSONArray) throw JSONException("Value $json cannot be converted to JSONArray")
        values.addAll(parsed.values)
    }

    constructor(collection: Collection<*>) : this() {
        collection.forEach { put(it) }
    }

    fun length(): Int = values.size

    fun put(value: Boolean): JSONArray = apply { values.add(value) }

    fun put(value: Int): JSONArray = apply { values.add(value) }

    fun put(value: Long): JSONArray = apply { values.add(value) }

    fun put(value: Double): JSONArray = apply { values.add(checkDouble(value)) }

    fun put(value: Any?): JSONArray = apply {
        values.add(if (value is Double) checkDouble(value) else if (value is Float) checkDouble(value.toDouble()) else value)
    }

    fun opt(index: Int): Any? = values.getOrNull(index)

    fun get(index: Int): Any = values.getOrNull(index) ?: throw JSONException("Value at $index is null or out of range [0..${values.size})")

    fun isNull(index: Int): Boolean = opt(index).let { it == null || it === JSONObject.NULL }

    fun getBoolean(index: Int): Boolean = toBoolean(get(index)) ?: throw JSONException("Value at $index cannot be converted to boolean")

    fun optBoolean(index: Int, fallback: Boolean = false): Boolean = toBoolean(opt(index)) ?: fallback

    fun getDouble(index: Int): Double = toDouble(get(index)) ?: throw JSONException("Value at $index cannot be converted to double")

    fun optDouble(index: Int, fallback: Double = Double.NaN): Double = toDouble(opt(index)) ?: fallback

    fun getInt(index: Int): Int = toInt(get(index)) ?: throw JSONException("Value at $index cannot be converted to int")

    fun optInt(index: Int, fallback: Int = 0): Int = toInt(opt(index)) ?: fallback

    fun getLong(index: Int): Long = toLong(get(index)) ?: throw JSONException("Value at $index cannot be converted to long")

    fun optLong(index: Int, fallback: Long = 0L): Long = toLong(opt(index)) ?: fallback

    fun getString(index: Int): String = valueToString(get(index)) ?: throw JSONException("Value at $index cannot be converted to String")

    fun optString(index: Int, fallback: String = ""): String = valueToString(opt(index)) ?: fallback

    fun getJSONArray(index: Int): JSONArray = get(index) as? JSONArray ?: throw JSONException("Value at $index cannot be converted to JSONArray")

    fun optJSONArray(index: Int): JSONArray? = opt(index) as? JSONArray

    fun getJSONObject(index: Int): JSONObject = get(index) as? JSONObject ?: throw JSONException("Value at $index cannot be converted to JSONObject")

    fun optJSONObject(index: Int): JSONObject? = opt(index) as? JSONObject

    override fun toString(): String = StringBuilder().also { writeArray(it, this, indent = 0, depth = 0) }.toString()

    fun toString(indentSpaces: Int): String = StringBuilder().also { writeArray(it, this, indentSpaces, depth = 0) }.toString()

    internal fun items(): List<Any?> = values
}

private fun checkDouble(value: Double): Double {
    if (value.isNaN() || value.isInfinite()) throw JSONException("Forbidden numeric value: $value")
    return value
}

private fun toBoolean(value: Any?): Boolean? = when (value) {
    is Boolean -> value
    is String -> when {
        value.equals("true", ignoreCase = true) -> true
        value.equals("false", ignoreCase = true) -> false
        else -> null
    }
    else -> null
}

private fun toDouble(value: Any?): Double? = when (value) {
    is Double -> value
    is Number -> value.toDouble()
    is String -> value.trim().toDoubleOrNull()
    else -> null
}

private fun toInt(value: Any?): Int? = when (value) {
    is Int -> value
    is Number -> value.toInt()
    is String -> value.trim().toDoubleOrNull()?.toInt()
    else -> null
}

private fun toLong(value: Any?): Long? = when (value) {
    is Long -> value
    is Number -> value.toLong()
    is String -> value.trim().toDoubleOrNull()?.toLong()
    else -> null
}

private fun valueToString(value: Any?): String? = when (value) {
    null -> null
    is String -> value
    is Number -> numberToString(value)
    else -> value.toString()
}

internal fun numberToString(number: Number): String {
    val doubleValue = number.toDouble()
    checkDouble(doubleValue)
    if (number is Double && number == 0.0 && 1.0 / number < 0) return "-0"
    if (number is Int || number is Long || number is Short || number is Byte) return number.toString()
    val longValue = number.toLong()
    if (doubleValue == longValue.toDouble()) return longValue.toString()
    return number.toString()
}

private fun writeValue(out: StringBuilder, value: Any?, indent: Int, depth: Int) {
    when (value) {
        null -> out.append("null")
        is JSONObject -> writeObject(out, value, indent, depth)
        is JSONArray -> writeArray(out, value, indent, depth)
        is String -> writeString(out, value)
        is Boolean -> out.append(value.toString())
        is Number -> out.append(numberToString(value))
        else -> if (value === JSONObject.NULL) out.append("null") else writeString(out, value.toString())
    }
}

private fun newline(out: StringBuilder, indent: Int, depth: Int) {
    if (indent == 0) return
    out.append('\n')
    repeat(indent * depth) { out.append(' ') }
}

private fun writeObject(out: StringBuilder, value: JSONObject, indent: Int, depth: Int) {
    val entries = value.entries()
    if (entries.isEmpty()) {
        out.append("{}")
        return
    }
    out.append('{')
    var first = true
    for ((key, item) in entries) {
        if (!first) out.append(',')
        first = false
        newline(out, indent, depth + 1)
        writeString(out, key)
        out.append(if (indent == 0) ":" else ": ")
        writeValue(out, item, indent, depth + 1)
    }
    newline(out, indent, depth)
    out.append('}')
}

private fun writeArray(out: StringBuilder, value: JSONArray, indent: Int, depth: Int) {
    val items = value.items()
    if (items.isEmpty()) {
        out.append("[]")
        return
    }
    out.append('[')
    items.forEachIndexed { index, item ->
        if (index > 0) out.append(',')
        newline(out, indent, depth + 1)
        writeValue(out, item, indent, depth + 1)
    }
    newline(out, indent, depth)
    out.append(']')
}

private fun writeString(out: StringBuilder, value: String) {
    out.append('"')
    for (c in value) {
        when (c) {
            '"', '\\', '/' -> out.append('\\').append(c)
            '\t' -> out.append("\\t")
            '\b' -> out.append("\\b")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            '\u000C' -> out.append("\\f")
            else -> if (c.code <= 0x1F) {
                out.append("\\u").append(c.code.toString(16).padStart(4, '0'))
            } else {
                out.append(c)
            }
        }
    }
    out.append('"')
}

/** 엄격한 JSON 읽기 — 수의 종류는 Android `JSONTokener`처럼 가른다(정수면 Int/Long, 아니면 Double). */
private class JsonReader(private val text: String) {
    private var pos = 0

    fun readValueAtTop(): Any? {
        val value = readValue()
        skipWhitespace()
        if (pos != text.length) throw syntax("Unexpected trailing characters")
        return value
    }

    private fun syntax(message: String) = JSONException("$message at character $pos of $text")

    private fun skipWhitespace() {
        while (pos < text.length && text[pos].isWhitespace()) pos++
    }

    private fun readValue(): Any? {
        skipWhitespace()
        if (pos >= text.length) throw syntax("End of input")
        return when (val c = text[pos]) {
            '{' -> readObject()
            '[' -> readArray()
            '"' -> readString()
            't', 'f', 'n' -> readKeyword()
            else -> if (c == '-' || c.isDigit()) readNumber() else throw syntax("Unexpected character '$c'")
        }
    }

    private fun readKeyword(): Any? = when {
        text.startsWith("true", pos) -> true.also { pos += 4 }
        text.startsWith("false", pos) -> false.also { pos += 5 }
        text.startsWith("null", pos) -> JSONObject.NULL.also { pos += 4 }
        else -> throw syntax("Unexpected literal")
    }

    private fun readNumber(): Any {
        val start = pos
        if (text[pos] == '-') pos++
        while (pos < text.length && (text[pos].isDigit() || text[pos] in ".eE+-")) pos++
        val literal = text.substring(start, pos)
        if (literal.none { it in ".eE" }) {
            literal.toLongOrNull()?.let { long -> return if (long in Int.MIN_VALUE..Int.MAX_VALUE) long.toInt() else long }
        }
        return literal.toDoubleOrNull() ?: throw syntax("Invalid number $literal")
    }

    private fun readString(): String {
        pos++ // opening quote
        val out = StringBuilder()
        while (true) {
            if (pos >= text.length) throw syntax("Unterminated string")
            val c = text[pos++]
            when (c) {
                '"' -> return out.toString()
                '\\' -> {
                    if (pos >= text.length) throw syntax("Unterminated escape")
                    when (val e = text[pos++]) {
                        '"', '\\', '/' -> out.append(e)
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'u' -> {
                            if (pos + 4 > text.length) throw syntax("Unterminated escape")
                            out.append(text.substring(pos, pos + 4).toInt(16).toChar())
                            pos += 4
                        }
                        else -> throw syntax("Invalid escape \\$e")
                    }
                }
                else -> out.append(c)
            }
        }
    }

    private fun readObject(): JSONObject {
        pos++ // {
        val result = JSONObject()
        skipWhitespace()
        if (text.getOrNull(pos) == '}') {
            pos++
            return result
        }
        while (true) {
            skipWhitespace()
            if (text.getOrNull(pos) != '"') throw syntax("Expected a name")
            val key = readString()
            skipWhitespace()
            if (text.getOrNull(pos) != ':') throw syntax("Expected ':' after $key")
            pos++
            result.put(key, readValue())
            skipWhitespace()
            when (text.getOrNull(pos)) {
                ',' -> pos++
                '}' -> {
                    pos++
                    return result
                }
                else -> throw syntax("Unterminated object")
            }
        }
    }

    private fun readArray(): JSONArray {
        pos++ // [
        val result = JSONArray()
        skipWhitespace()
        if (text.getOrNull(pos) == ']') {
            pos++
            return result
        }
        while (true) {
            result.put(readValue())
            skipWhitespace()
            when (text.getOrNull(pos)) {
                ',' -> pos++
                ']' -> {
                    pos++
                    return result
                }
                else -> throw syntax("Unterminated array")
            }
        }
    }
}
