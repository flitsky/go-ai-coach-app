package com.worksoc.goaicoach.kv

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * 이름 붙은 키-값 묶음 하나(백로그 #294 — 이식 계획 S32). 안드로이드 `SharedPreferences` 파일 하나에 해당한다 — iOS는
 * `NSUserDefaults(suiteName:)` 하나다. 안드로이드 저장소는 이것을 쓰지 않는다(오늘의 SharedPreferences 그대로). 이것은
 * iOS 저장소(`kv` 패키지)와 그 저장소를 JVM에서 시험하는 메모리 구현의 바닥이다.
 *
 * 값의 뜻은 `SharedPreferences`와 같다: 없는 키는 기본값, `putString(key, null)`은 지우기.
 */
internal interface KeyValueStore {
    fun getString(key: String): String?

    fun putString(key: String, value: String?)

    fun getInt(key: String, default: Int): Int

    fun putInt(key: String, value: Int)

    fun getLong(key: String, default: Long): Long

    fun putLong(key: String, value: Long)

    fun getBoolean(key: String, default: Boolean): Boolean

    fun putBoolean(key: String, value: Boolean)

    fun getStringSet(key: String): Set<String>?

    fun putStringSet(key: String, value: Set<String>)

    fun contains(key: String): Boolean

    fun remove(key: String)

    /** 이 묶음의 키를 전부 지운다(개발자 초기화). */
    fun clear()

    /** 이 묶음에서 바뀐 키 — 안드로이드 `OnSharedPreferenceChangeListener`와 같은 몫. */
    val changedKeys: SharedFlow<String>
}

/** 이름으로 묶음을 연다 — 같은 이름은 같은 묶음이다(프로세스에 하나). */
internal fun interface KeyValueStoreFactory {
    fun open(name: String): KeyValueStore
}

/**
 * [key]가 바뀔 때마다 [read]를 다시 읽어 흘린다. 처음 모을 때 한 번 흘린다 — 안드로이드 `callbackFlow` 구현과 같은 모양.
 * ⚠️ 알림 구독을 **먼저** 건다(`UNDISPATCHED`) — 첫 값을 읽은 뒤 구독이 걸리기 전에 지나간 저장은 알림이 사라진다
 * (`SharedFlow`는 구독자가 없으면 버린다). 안드로이드는 리스너 등록이 즉시라 이 틈이 없다.
 */
internal fun <T> KeyValueStore.watch(key: String, read: () -> T): Flow<T> = callbackFlow {
    val job = launch(start = CoroutineStart.UNDISPATCHED) { changedKeys.filter { it == key }.collect { trySend(read()) } }
    send(read())
    awaitClose { job.cancel() }
}.conflate()

/** 메모리 구현 — JVM 시험과 iOS 카탈로그용. 값은 프로세스와 함께 사라진다. */
internal class InMemoryKeyValueStore : KeyValueStore {
    private val values = mutableMapOf<String, Any>()
    private val changes = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val changedKeys: SharedFlow<String> = changes.asSharedFlow()

    private fun put(key: String, value: Any?) {
        if (value == null) values.remove(key) else values[key] = value
        changes.tryEmit(key)
    }

    override fun getString(key: String): String? = values[key] as? String

    override fun putString(key: String, value: String?) = put(key, value)

    override fun getInt(key: String, default: Int): Int = values[key] as? Int ?: default

    override fun putInt(key: String, value: Int) = put(key, value)

    override fun getLong(key: String, default: Long): Long = values[key] as? Long ?: default

    override fun putLong(key: String, value: Long) = put(key, value)

    override fun getBoolean(key: String, default: Boolean): Boolean = values[key] as? Boolean ?: default

    override fun putBoolean(key: String, value: Boolean) = put(key, value)

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String): Set<String>? = (values[key] as? Set<String>)?.toSet()

    override fun putStringSet(key: String, value: Set<String>) = put(key, value.toSet())

    override fun contains(key: String): Boolean = key in values

    override fun remove(key: String) = put(key, null)

    override fun clear() {
        val keys = values.keys.toList()
        values.clear()
        keys.forEach { changes.tryEmit(it) }
    }
}

/** 메모리 묶음들 — 같은 이름은 같은 묶음. */
internal class InMemoryKeyValueStoreFactory : KeyValueStoreFactory {
    private val stores = mutableMapOf<String, InMemoryKeyValueStore>()

    override fun open(name: String): KeyValueStore = stores.getOrPut(name) { InMemoryKeyValueStore() }
}
