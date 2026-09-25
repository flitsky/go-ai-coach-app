package com.worksoc.goaicoach.persistence
 
import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperimentalFeaturesStoreTest {

    private fun createFakePreferences(initial: Map<String, Any?> = emptyMap()): Pair<SharedPreferences, MutableMap<String, Any?>> {
        val store = HashMap<String, Any?>(initial)
        val pending = HashMap<String, Any?>()

        lateinit var editor: SharedPreferences.Editor
        editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java),
        ) { _, method, args ->
            when (method.name) {
                "putBoolean" -> {
                    pending[args[0] as String] = args[1] as Boolean
                    editor
                }
                "apply", "commit" -> {
                    store.putAll(pending)
                    pending.clear()
                    true
                }
                else -> null
            }
        } as SharedPreferences.Editor

        val prefs = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getBoolean" -> store[args[0] as String] as? Boolean ?: (args[1] as Boolean)
                "edit" -> editor
                else -> null
            }
        } as SharedPreferences

        return prefs to store
    }

    @Test
    fun defaultIsCameraBoardScanEnabledIsFalse() {
        val (prefs, _) = createFakePreferences()
        val store = ExperimentalFeaturesStore(prefs)

        assertFalse(
            "실험실 기능의 기본값은 비활성화(false)여야 메인 브랜치에 안전하게 소프트랜딩할 수 있다.",
            store.isCameraBoardScanEnabled(),
        )
    }

    @Test
    fun setCameraBoardScanEnabledPersistsTrueAndFalse() {
        val (prefs, rawMap) = createFakePreferences()
        val store = ExperimentalFeaturesStore(prefs)

        store.setCameraBoardScanEnabled(true)
        assertTrue(store.isCameraBoardScanEnabled())
        assertEquals(true, rawMap[ExperimentalFeaturesStore.CameraBoardScanKey])

        store.setCameraBoardScanEnabled(false)
        assertFalse(store.isCameraBoardScanEnabled())
        assertEquals(false, rawMap[ExperimentalFeaturesStore.CameraBoardScanKey])
    }

    @Test
    fun storeUsesExpectedPreferencesNameAndKey() {
        assertEquals("go_ai_coach_experimental_features", ExperimentalFeaturesStore.PrefsName)
        assertEquals("camera_board_scan_enabled", ExperimentalFeaturesStore.CameraBoardScanKey)
    }
}
