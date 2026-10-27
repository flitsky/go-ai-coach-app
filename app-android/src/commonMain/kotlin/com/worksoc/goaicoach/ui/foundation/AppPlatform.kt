package com.worksoc.goaicoach.ui.foundation

import androidx.compose.runtime.staticCompositionLocalOf
import com.worksoc.goaicoach.application.auth.port.AuthClientPort
import com.worksoc.goaicoach.application.debugreport.ClipboardPort
import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import com.worksoc.goaicoach.platform.AppNotice
import com.worksoc.goaicoach.platform.AppStoreKind
import com.worksoc.goaicoach.platform.AppUpdateCheck
import com.worksoc.goaicoach.platform.BoardScanEntry
import com.worksoc.goaicoach.platform.BundledAssets
import com.worksoc.goaicoach.platform.DebugArmFiles
import com.worksoc.goaicoach.platform.DebugReportPlatformInfo
import com.worksoc.goaicoach.platform.DeveloperTools
import com.worksoc.goaicoach.platform.GoogleIdTokenSource
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

    /** 앱에 실은 파일(기보 · 참고 대국 — 안드로이드 `assets/`). */
    val assets: BundledAssets

    /** 개발자 메뉴의 초기화 · 진단 손잡이. */
    val developer: DeveloperTools

    /** 로그인 어댑터와 구글 토큰 출처 — 셸이 한 번씩 꺼내 쓴다(로그인은 지금 꺼져 있다). */
    val authClient: AuthClientPort
    val googleIdTokens: GoogleIdTokenSource

    /** 실행 기록(진단 리포트의 뒤쪽 꼬리). */
    val runtimeEventLog: RuntimeEventLogPort

    /** 진단 리포트의 기기 정보. */
    val debugReportPlatform: DebugReportPlatformInfo

    /** 기기에 쌓인 기록이 어디로 백업되는가 — 「내 기록은 어떻게 보관되나요」가 그 플랫폼의 사실을 말한다(#294). */
    val dataBackup: DataBackupKind

    /** 앱을 파는 스토어 — 구독 문구의 스토어 이름 · App Store 결제 화면의 복원 · 약관 줄(#296). */
    val store: AppStoreKind

    /** 엔진 모델이 아직 없어 준비 화면부터 보여야 하는가(S16) — 컴포지션 첫 순간에 한 번 묻는다. */
    fun isEngineSetupRequired(): Boolean
}

/** 루트가 한 번 넣는다(안드로이드 `MainActivity`, iOS `MainViewController`). */
val LocalAppPlatform = staticCompositionLocalOf<AppPlatform> { defaultAppPlatform() }

/**
 * 루트가 넣지 않은 컴포지션의 플랫폼 — 안드로이드는 프로세스의 것(애플리케이션이 건다 — 계기 시험이 `setContent`로 화면 하나를
 * 띄워도 같은 묶음을 본다), iOS는 없다(루트가 꼭 넣는다).
 */
internal expect fun defaultAppPlatform(): AppPlatform
