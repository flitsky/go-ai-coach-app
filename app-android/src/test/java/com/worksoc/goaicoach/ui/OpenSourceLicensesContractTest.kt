package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.architecture.RepoPaths
import com.worksoc.goaicoach.architecture.readContractSource
import com.worksoc.goaicoach.ui.l10n.UiLanguage
import com.worksoc.goaicoach.ui.l10n.openSourceLicensesEngineSectionFor
import com.worksoc.goaicoach.ui.l10n.openSourceLicensesIntroFor
import com.worksoc.goaicoach.ui.l10n.openSourceLicensesLibrarySectionFor
import com.worksoc.goaicoach.ui.l10n.openSourceLicensesLoadFailedFor
import com.worksoc.goaicoach.ui.l10n.openSourceLicensesTitleFor
import com.worksoc.goaicoach.ui.l10n.openSourceLicensesViewLicenseFor
import com.worksoc.goaicoach.ui.settings.BundledNativeOrder
import com.worksoc.goaicoach.ui.settings.BundledNativeTag
import com.worksoc.goaicoach.ui.settings.openSourceEntriesFrom
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 백로그 #195 — 오픈소스 라이선스 화면. **KataGo 고지가 빠져도, 목록 순서가 바뀌어도, 릴리스에서 목록이 비어도
 * 컴파일은 되고 화면도 뜬다** — 그래서 그물로 든다.
 *
 * ⚠️ 생성된 `aboutlibraries.json`(빌드 산출물)은 읽지 않는다 — 저장소 밖 상태가 초록을 만든다(함정 20).
 * 대신 **손 항목 원본**(`config/aboutlibraries/`)을 플러그인 출력과 같은 모양으로 묶어 실제 파서에 넣는다.
 */
class OpenSourceLicensesContractTest {

    private val configDir = RepoPaths.root.resolve("app-android/config/aboutlibraries")

    private fun jsonFiles(sub: String): List<File> =
        configDir.resolve(sub).listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }

    /** 플러그인 출력 모양: `{"libraries":[…], "licenses":{hash:{…}}}` — 손 항목 + 가짜 Gradle 라이브러리 하나. */
    private fun bundleJson(): String {
        val libraries = JSONArray()
        jsonFiles("libraries").forEach { libraries.put(JSONObject(it.readText())) }
        libraries.put(
            JSONObject()
                .put("uniqueId", "androidx.activity:activity")
                .put("artifactVersion", "1.10.1")
                .put("name", "Activity")
                .put("developers", JSONArray())
                .put("licenses", JSONArray().put("Apache-2.0")),
        )
        val licenses = JSONObject()
        jsonFiles("licenses").forEach { file ->
            val license = JSONObject(file.readText())
            licenses.put(license.getString("hash"), license)
        }
        return JSONObject().put("libraries", libraries).put("licenses", licenses).toString()
    }

    private fun licenseContent(hash: String): String =
        JSONObject(configDir.resolve("licenses/$hash.json").readText()).getString("content")

    @Test
    fun kataGoCodeAndNetworkComeFirstThenTheRestOfTheEngineThenAppLibraries() {
        val entries = openSourceEntriesFrom(bundleJson())
        assertEquals(
            "맨 위 두 줄은 KataGo 코드·신경망이어야 한다(사용자 결정 2026-09-30)",
            listOf("bundled.native:katago-engine", "bundled.native:katago-network"),
            entries.take(2).map { it.id },
        )
        val engineIds = entries.takeWhile { it.isBundledEngine }.map { it.id }
        assertEquals("손 항목이 전부 엔진 구획에, 정해진 순서로 와야 한다", BundledNativeOrder, engineIds)
        // 번들에 싣는 신경망은 둘이다(백로그 #215) — 사람 모델도 같은 신경망 라이선스의 고지를 단다(그 라이선스가 Human SL Network를 이름으로 든다).
        val humanNetwork = entries.first { it.id == "bundled.native:katago-human-network" }
        assertEquals(listOf("KataGo Neural Network License"), humanNetwork.licenses.map { it.name })
        assertEquals("엔진 구획 뒤에 앱 라이브러리가 온다", "androidx.activity:activity", entries[engineIds.size].id)
    }

    /** 손 항목은 전부 [BundledNativeTag]를 단다 — 태그를 잊으면 엔진 구획에서 빠져 이름순 목록 한가운데로 숨는다. */
    @Test
    fun everyHandWrittenEntryIsTaggedAndCarriesItsLicenseText() {
        val libraryFiles = jsonFiles("libraries")
        assertTrue("손 항목이 없다 — 설정 경로가 바뀌었나", libraryFiles.size >= BundledNativeOrder.size)
        libraryFiles.forEach { file ->
            val library = JSONObject(file.readText())
            assertEquals("${file.name}: 태그", BundledNativeTag, library.getString("tag"))
        }
        openSourceEntriesFrom(bundleJson()).filter { it.isBundledEngine }.forEach { entry ->
            assertTrue("${entry.id}: 라이선스 원문이 비었다", entry.licenses.isNotEmpty() && entry.licenses.all { !it.content.isNullOrBlank() })
        }
    }

    /**
     * ⚠️ **원문 그대로여야 한다** — 요약하거나 지어낸 문구는 고지가 아니다. 여기서는 두 라이선스의 요건
     * (저작권 표시 + 허가 고지)이 실제로 들어 있는지를 잰다. 원문 출처는 `app-android/build.gradle.kts`의 주석.
     */
    @Test
    fun kataGoNoticesAreTheRealTexts() {
        val code = licenseContent("katago-code")
        listOf(
            "Copyright 2025 David J Wu (\"lightvector\")",
            "The above copyright notice and this permission notice shall be included in all copies or",
            "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND",
        ).forEach { assertTrue("KataGo 코드 라이선스에 없다: $it", code.contains(it)) }

        val network = licenseContent("katago-network")
        listOf(
            "KataGo Neural Network License",
            "David J Wu (\"lightvector\")",
            "obtaining a copy of the neural net files or training weight files",
            "The above copyright notice and this permission notice shall be included in all copies or",
        ).forEach { assertTrue("KataGo 신경망 라이선스에 없다: $it", network.contains(it)) }
    }

    /** 오프라인 빌드에서 플러그인은 SPDX 본문을 못 받는다 — 앱 라이브러리 대부분(Apache-2.0)이 이름만 남지 않게 원문을 싣는다. */
    @Test
    fun theCommonSpdxTextsAreVendoredForOfflineBuilds() {
        assertTrue(licenseContent("Apache-2.0").contains("TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION"))
        assertTrue(licenseContent("MIT").contains("Permission is hereby granted, free of charge"))
        assertTrue(licenseContent("BSD-3-Clause").contains("Redistribution and use in source and binary forms"))
        val build = RepoPaths.appAndroidBuildScript.readText()
        assertTrue("오프라인 모드가 꺼졌다 — 빌드가 네트워크에 기댄다", build.contains("offlineMode = true"))
    }

    /** `android.r8.optimizedResourceShrinking=true`에서 생성 리소스가 지워지면 **릴리스에서만** 목록이 빈다. */
    @Test
    fun theGeneratedListSurvivesResourceShrinking() {
        val keep = RepoPaths.root.resolve("app-android/src/main/res/raw/keep_aboutlibraries.xml")
        assertTrue("리소스 keep 파일이 없다", keep.exists())
        assertTrue(keep.readText().contains("tools:keep=\"@raw/aboutlibraries\""))
    }

    /** 사용자 결정(2026-09-30): 링크는 **버전 줄 바로 아래**, 개인정보처리방침보다 위. */
    @Test
    fun theSettingsLinkSitsDirectlyBelowTheVersionLine() {
        val settings = RepoPaths.uiFile("SettingsScreen.kt").readContractSource()
        val version = settings.indexOf("strings.settingsVersionLabel")
        val link = settings.indexOf("openSourceLicensesTitleFor(strings.language)")
        val privacy = settings.indexOf("strings.settingsPrivacyPolicyLabel")
        assertTrue("표식을 못 찾았다(함정 66): $version / $link / $privacy", version >= 0 && link >= 0 && privacy >= 0)
        assertTrue("링크가 버전 줄과 개인정보처리방침 사이에 있지 않다", link in (version + 1) until privacy)
        assertTrue(
            "라이선스 화면에 중첩 `BackHandler`가 없다 — 시스템 뒤로가기가 설정을 건너뛰고 홈으로 튄다",
            RepoPaths.uiFile("OpenSourceLicensesScreen.kt").readContractSource().contains("BackHandler"),
        )
    }

    @Test
    fun everyScreenStringExistsInEveryLanguage() {
        UiLanguage.entries.forEach { language ->
            listOf(
                openSourceLicensesTitleFor(language),
                openSourceLicensesIntroFor(language),
                openSourceLicensesEngineSectionFor(language),
                openSourceLicensesLibrarySectionFor(language),
                openSourceLicensesViewLicenseFor(language),
                openSourceLicensesLoadFailedFor(language),
            ).forEach { text -> assertTrue("$language 문구가 비었다", text.isNotBlank()) }
        }
        listOf(
            openSourceLicensesTitleFor(UiLanguage.Korean),
            openSourceLicensesIntroFor(UiLanguage.Korean),
            openSourceLicensesLibrarySectionFor(UiLanguage.Korean),
            openSourceLicensesViewLicenseFor(UiLanguage.Korean),
            openSourceLicensesLoadFailedFor(UiLanguage.Korean),
        ).forEach { text -> assertTrue("한국어가 아닌 문구: $text", text.containsHangul()) }
        UiLanguage.entries.filter { it != UiLanguage.Korean }.forEach { language ->
            assertTrue("$language 제목에 한글이 섞였다", !openSourceLicensesTitleFor(language).containsHangul())
        }
    }
}
