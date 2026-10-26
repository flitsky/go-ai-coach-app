package com.worksoc.goaicoach.kv

import com.worksoc.goaicoach.architecture.RepoPaths
import com.worksoc.goaicoach.architecture.readContractSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * iOS 저장 묶음 이름(백로그 #294 — [KvSuites])이 **한 곳에서만** 정해지고, 안드로이드 `SharedPreferences` 이름과 **같은 집합**인지.
 * 이름이 둘로 갈라지면 iOS 개발자 초기화가 새 저장소를 조용히 지나치거나(목록 밖), 안드로이드와 다른 이름으로 저장한다.
 */
class KvSuitesContractTest {
    private val suiteLiteral = Regex(""""go_ai_coach_[a-z_]+"""")

    @Test
    fun suitesAreNamedOnlyInKvSuites() {
        val roots = listOf("app-android/src/commonMain/kotlin", "app-android/src/iosMain/kotlin").map { RepoPaths.root.resolve(it) }
        val offenders = roots.flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "KvSuites.kt" }
                .filter { suiteLiteral.containsMatchIn(it.readContractSource()) }
                .map { it.relativeTo(RepoPaths.root).path }
                .toList()
        }
        assertTrue("저장 묶음 이름을 KvSuites 밖에서 글자 그대로 쓴다 — KvSuites의 상수로 열 것:\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }

    @Test
    fun theIosSuitesAreTheAndroidPrefsNames() {
        val android = RepoPaths.root.resolve("app-android/src/main/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file -> suiteLiteral.findAll(file.readContractSource()).map { it.value.trim('"') } }
            .toSet()
        // 대국 기록은 2026-09-18부터 파일이다(#151) — 옛 prefs 이름은 안드로이드가 옮겨 올 때만 읽는다. iOS에는 옛 판이 없다.
        val legacyOnAndroid = setOf("go_ai_coach_game_history")
        assertEquals("iOS 저장 묶음과 안드로이드 prefs 이름이 다르다", android - legacyOnAndroid, KvSuites.all.toSet())
        assertEquals("KvSuites.all에 같은 이름이 두 번 있다", KvSuites.all.distinct(), KvSuites.all)
        assertEquals("개발자 초기화는 기기 id만 남긴다", KvSuites.all - KvSuites.DeviceIdentity, KvSuites.wipedByFreshInstall)
    }
}
