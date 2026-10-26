package com.worksoc.goaicoach.kv

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import platform.Foundation.NSLock
import platform.Foundation.NSUserDefaults

/**
 * iOS의 [KeyValueStore] — `NSUserDefaults(suiteName:)` 하나(백로그 #294 — S32). 묶음 이름이 안드로이드 SharedPreferences 파일
 * 이름과 같다(`go_ai_coach_*`) — 지울 때(개발자 초기화) 같은 이름 목록을 쓴다.
 *
 * ⚠️ 정수는 `NSInteger`(64비트)로 적는다 — Int와 Long을 같은 칸에 읽어도 값이 같다. 없는 키는 `objectForKey == null`로 가른다
 * (`integerForKey`는 없는 키에 0을 돌려줘 기본값과 구분되지 않는다).
 */
internal class UserDefaultsKeyValueStore(private val suiteName: String) : KeyValueStore {
    private val defaults = NSUserDefaults(suiteName = suiteName)
    private val changes = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val changedKeys: SharedFlow<String> = changes.asSharedFlow()

    private fun changed(key: String) {
        changes.tryEmit(key)
    }

    override fun getString(key: String): String? = defaults.stringForKey(key)

    override fun putString(key: String, value: String?) {
        if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, forKey = key)
        changed(key)
    }

    override fun getInt(key: String, default: Int): Int =
        if (defaults.objectForKey(key) == null) default else defaults.integerForKey(key).toInt()

    override fun putInt(key: String, value: Int) {
        defaults.setInteger(value.toLong(), forKey = key)
        changed(key)
    }

    override fun getLong(key: String, default: Long): Long =
        if (defaults.objectForKey(key) == null) default else defaults.integerForKey(key)

    override fun putLong(key: String, value: Long) {
        defaults.setInteger(value, forKey = key)
        changed(key)
    }

    override fun getBoolean(key: String, default: Boolean): Boolean =
        if (defaults.objectForKey(key) == null) default else defaults.boolForKey(key)

    override fun putBoolean(key: String, value: Boolean) {
        defaults.setBool(value, forKey = key)
        changed(key)
    }

    override fun getStringSet(key: String): Set<String>? =
        defaults.arrayForKey(key)?.filterIsInstance<String>()?.toSet()

    override fun putStringSet(key: String, value: Set<String>) {
        defaults.setObject(value.sorted(), forKey = key)
        changed(key)
    }

    override fun contains(key: String): Boolean = defaults.objectForKey(key) != null

    override fun remove(key: String) {
        defaults.removeObjectForKey(key)
        changed(key)
    }

    override fun clear() {
        val keys = defaults.persistentDomainForName(suiteName)?.keys?.filterIsInstance<String>().orEmpty()
        defaults.removePersistentDomainForName(suiteName)
        keys.forEach { changed(it) }
    }
}

/** 프로세스에 묶음 하나씩 — 같은 이름은 같은 객체라 바뀐 키 알림이 모든 구독자에게 간다. */
internal object UserDefaultsKeyValueStores : KeyValueStoreFactory {
    private val lock = NSLock()
    private val opened = mutableMapOf<String, UserDefaultsKeyValueStore>()

    override fun open(name: String): KeyValueStore {
        lock.lock()
        try {
            return opened.getOrPut(name) { UserDefaultsKeyValueStore(name) }
        } finally {
            lock.unlock()
        }
    }
}
