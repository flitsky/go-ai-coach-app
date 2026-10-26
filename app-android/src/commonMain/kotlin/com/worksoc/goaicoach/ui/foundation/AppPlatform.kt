package com.worksoc.goaicoach.ui.foundation

import androidx.compose.runtime.staticCompositionLocalOf
import com.worksoc.goaicoach.application.debugreport.ClipboardPort
import com.worksoc.goaicoach.platform.AppNotice
import com.worksoc.goaicoach.platform.AppUpdateCheck
import com.worksoc.goaicoach.platform.BoardScanEntry
import com.worksoc.goaicoach.platform.DebugArmFiles
import com.worksoc.goaicoach.platform.PlayHapticFeedback

/**
 * 화면이 쓰는 플랫폼 기능의 묶음(백로그 #294 — 이식 계획 S1). 화면은 `LocalContext` 대신 [LocalAppPlatform]에서 꺼낸다 —
 * 안드로이드는 `AndroidAppPlatform`(루트, 액티비티가 만든다), iOS는 `IosAppPlatform`이다. 웨이브마다 화면이 옮겨 오며 필요한
 * 기능이 여기에 하나씩 는다 — 멤버의 타입은 공용(`:shared` · 이 모듈 공용) 타입만 쓴다.
 */
interface AppPlatform {
    val notice: AppNotice
    val clipboard: ClipboardPort
    val stores: AppStores

    /** 착수 진동 — 판마다 한 번 꺼내 쓴다(`remember`). */
    val haptics: PlayHapticFeedback

    /** 「새 버전이 있는가」 — 프로세스에 한 번 묻는다(`AppUpdateCheck`). */
    val appUpdate: AppUpdateCheck

    /** 개발 빌드의 무장 파일(미리보기 · 점검 장치를 한 번 켠다). */
    val debugArmFiles: DebugArmFiles

    /** 바둑판 사진 분석의 입구 — 없으면(`null`, iOS) 그 입구를 그리지 않는다. */
    val boardScan: BoardScanEntry?
}

/** 루트가 한 번 넣는다(안드로이드 `MainActivity`, iOS `MainViewController`). */
val LocalAppPlatform = staticCompositionLocalOf<AppPlatform> { defaultAppPlatform() }

/**
 * 루트가 넣지 않은 컴포지션의 플랫폼 — 안드로이드는 프로세스의 것(애플리케이션이 건다 — 계기 시험이 `setContent`로 화면 하나를
 * 띄워도 같은 묶음을 본다), iOS는 없다(루트가 꼭 넣는다).
 */
internal expect fun defaultAppPlatform(): AppPlatform
