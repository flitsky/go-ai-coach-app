package com.worksoc.goaicoach.persistence

import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap

/**
 * 스레드 안전한 인메모리 [SharedPreferences] — `android.jar`의 실제 구현은 JVM 단위 테스트에서
 * 쓸 수 없어서(`Method ... not mocked`) 스토어를 `internal constructor(prefs)`로 받아 이것을 넣는다.
 *
 * 실제 구현과 같은 성질 둘만 흉내 낸다: ⓐ 한 번의 `getString`/`apply`는 각각 원자적이다,
 * ⓑ `apply`는 메모리에 곧바로 반영돼 다른 스레드의 다음 읽기가 그 값을 본다.
 * **읽기와 쓰기 사이를 묶어 주지는 않는다** — 그게 스토어의 몫이고, 이 페이크가 겨누는 대상이다.
 *
 * [afterRead]는 읽기가 **끝난 직후** 불린다(값은 이미 손에 쥔 채로). 경합 테스트가 "읽은 뒤
 * 쓰기 전"에 스레드를 붙잡아 두는 자리다. [afterWrite]는 쓰기가 반영된 직후 불린다.
 */
internal class InMemorySharedPreferences(
    initial: Map<String, Any> = emptyMap(),
) : SharedPreferences {
    private val values = ConcurrentHashMap<String, Any>(initial)

    @Volatile
    var afterRead: ((key: String) -> Unit)? = null

    @Volatile
    var afterWrite: (() -> Unit)? = null

    fun rawString(key: String): String? = values[key] as? String

    override fun getAll(): MutableMap<String, *> = HashMap(values)

    override fun getString(key: String?, defValue: String?): String? {
        val value = values[key] as? String ?: defValue
        afterRead?.invoke(key.orEmpty())
        return value
    }

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (values[key] as? Set<String>)?.toMutableSet() ?: defValues

    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = key != null && values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val pending = HashMap<String, Any?>()
        private var clearRequested = false

        private fun put(key: String?, value: Any?): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = put(key, value)

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor =
            put(key, values?.toSet())

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = put(key, value)

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = put(key, value)

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = put(key, value)

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = put(key, value)

        override fun remove(key: String?): SharedPreferences.Editor = put(key, null)

        override fun clear(): SharedPreferences.Editor {
            clearRequested = true
            return this
        }

        override fun commit(): Boolean {
            writeToMemory()
            return true
        }

        override fun apply() {
            writeToMemory()
        }

        private fun writeToMemory() {
            synchronized(this@InMemorySharedPreferences) {
                if (clearRequested) values.clear()
                pending.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
            }
            afterWrite?.invoke()
        }
    }
}
