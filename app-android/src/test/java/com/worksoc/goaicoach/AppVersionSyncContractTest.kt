package com.worksoc.goaicoach

import com.worksoc.goaicoach.architecture.RepoPaths
import com.worksoc.goaicoach.architecture.readContractSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * iOS 앱의 버전 번호가 안드로이드와 같은지(백로그 #294 — 사용자 2026-10-27 「안드로이드와 같게 1.8.4」). 정본은 `version.properties` 하나이고,
 * `scripts/bump-version.sh`가 Xcode 프로젝트의 두 값을 함께 고친다. 손으로 한쪽만 고치면 iOS가 조용히 옛 번호에 남는다.
 */
class AppVersionSyncContractTest {
    @Test
    fun theXcodeProjectCarriesTheVersionFromVersionProperties() {
        val properties = RepoPaths.root.resolve("version.properties").readContractSource()
        val name = Regex("""VERSION_NAME=(\S+)""").find(properties)?.groupValues?.get(1)
        val code = Regex("""VERSION_CODE=(\d+)""").find(properties)?.groupValues?.get(1)
        val pbxproj = RepoPaths.root.resolve("app-ios/GoCoach.xcodeproj/project.pbxproj").readContractSource()
        val marketing = Regex("""MARKETING_VERSION = ([0-9.]+);""").findAll(pbxproj).map { it.groupValues[1] }.toSet()
        val build = Regex("""CURRENT_PROJECT_VERSION = (\d+);""").findAll(pbxproj).map { it.groupValues[1] }.toSet()
        assertTrue("version.properties에서 번호를 읽지 못했다", name != null && code != null)
        assertEquals("Xcode MARKETING_VERSION이 VERSION_NAME과 다르다 — bump-version.sh로 함께 올릴 것", setOf(name), marketing)
        assertEquals("Xcode CURRENT_PROJECT_VERSION이 VERSION_CODE와 다르다 — bump-version.sh로 함께 올릴 것", setOf(code), build)
    }
}
