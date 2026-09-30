package com.worksoc.goaicoach.ui.settings

import com.mikepenz.aboutlibraries.Libs

/**
 * 오픈소스 라이선스 화면(백로그 #195)이 그리는 항목 하나 — AboutLibraries의 엔티티를 화면이 쓰는 모양으로 옮긴 것.
 * 화면이 라이브러리 타입을 직접 쥐지 않게 해 두면, 정렬·구획 규칙을 JVM 단위 테스트로 잴 수 있다.
 */
internal data class OpenSourceEntry(
    val id: String,
    val name: String,
    val version: String?,
    val authors: String?,
    val description: String?,
    val website: String?,
    val licenses: List<OpenSourceLicense>,
    /** 앱이 직접 싣는 엔진·신경망·엔진 안 부품(Gradle 의존성이 아니다) — 목록 맨 위 구획. */
    val isBundledEngine: Boolean,
)

internal data class OpenSourceLicense(val name: String, val url: String?, val content: String?)

/**
 * `app-android/config/aboutlibraries/libraries/`의 손 항목이 다는 태그. 플러그인은 `.so`·에셋을 못 찾으므로
 * KataGo 엔진·신경망과 엔진에 컴파일된 C++ 부품은 거기 직접 적는다.
 */
internal const val BundledNativeTag = "bundled-native"

/**
 * 맨 위 구획의 순서. ⚠️ **KataGo 코드와 신경망이 첫 두 줄이다** — 고지 의무가 가장 분명한 둘이고
 * (둘 다 "사본에 저작권·허가 고지를 넣을 것"), 사용자 결정(2026-09-30)이 "맨 위"였다.
 * 여기 없는 손 항목은 이 뒤에 이름순으로 온다(새 항목을 더하고 여기 적는 걸 잊어도 사라지지는 않는다).
 */
internal val BundledNativeOrder: List<String> = listOf(
    "bundled.native:katago-engine",
    "bundled.native:katago-network",
    "bundled.native:eigen",
    "bundled.native:tclap",
    "bundled.native:ghc-filesystem",
    "bundled.native:nlohmann-json",
    "bundled.native:sha2",
)

/** 생성된 `aboutlibraries.json` 본문 → 화면 항목. 엔진 구획이 먼저, 그다음 앱 라이브러리를 이름순으로. */
internal fun openSourceEntriesFrom(json: String): List<OpenSourceEntry> {
    val libs = Libs.Builder().withJson(json).build()
    val entries = libs.libraries.map { library ->
        OpenSourceEntry(
            id = library.uniqueId,
            name = library.name,
            version = library.artifactVersion?.takeIf { it.isNotBlank() },
            authors = library.developers.mapNotNull { it.name?.takeIf(String::isNotBlank) }
                .ifEmpty { listOfNotNull(library.organization?.name?.takeIf(String::isNotBlank)) }
                .joinToString(", ")
                .ifBlank { null },
            description = library.description?.takeIf { it.isNotBlank() },
            website = library.website?.takeIf { it.isNotBlank() },
            licenses = library.licenses.map { license ->
                OpenSourceLicense(
                    name = license.name,
                    url = license.url?.takeIf { it.isNotBlank() },
                    content = license.licenseContent?.takeIf { it.isNotBlank() },
                )
            },
            isBundledEngine = library.tag == BundledNativeTag,
        )
    }
    val (engine, apps) = entries.partition { it.isBundledEngine }
    val engineOrdered = engine.sortedWith(
        compareBy<OpenSourceEntry> { entry ->
            BundledNativeOrder.indexOf(entry.id).let { if (it < 0) Int.MAX_VALUE else it }
        }.thenBy { it.name.lowercase() },
    )
    return engineOrdered + apps.sortedBy { it.name.lowercase() }
}
