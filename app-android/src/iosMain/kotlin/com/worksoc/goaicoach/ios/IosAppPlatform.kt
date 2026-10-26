package com.worksoc.goaicoach.ios

import com.worksoc.goaicoach.application.auth.port.AuthClientPort
import com.worksoc.goaicoach.application.auth.state.AuthState
import com.worksoc.goaicoach.application.debugreport.ClipboardPort
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.gamehistory.GameReplayData
import com.worksoc.goaicoach.application.runtime.RuntimeEventLogPort
import com.worksoc.goaicoach.kv.GameReplayCodec
import com.worksoc.goaicoach.kv.IosAppFileStore
import com.worksoc.goaicoach.kv.KvDiagnosticEventLog
import com.worksoc.goaicoach.kv.KvRuntimeEventLog
import com.worksoc.goaicoach.kv.iosAppStores
import com.worksoc.goaicoach.kv.iosWipeToFreshInstall
import com.worksoc.goaicoach.persistence.ReferenceGameAssetPath
import com.worksoc.goaicoach.persistence.ReferenceGameBoardSize
import com.worksoc.goaicoach.platform.AppUpdateCheck
import com.worksoc.goaicoach.platform.AppUpdateStatus
import com.worksoc.goaicoach.platform.BoardScanEntry
import com.worksoc.goaicoach.platform.BundledAssets
import com.worksoc.goaicoach.platform.DebugArmFiles
import com.worksoc.goaicoach.platform.DebugReportPlatformInfo
import com.worksoc.goaicoach.platform.DeveloperTools
import com.worksoc.goaicoach.platform.GoogleIdTokenSource
import com.worksoc.goaicoach.platform.PlayHapticFeedback
import com.worksoc.goaicoach.platform.appBuildInfo
import com.worksoc.goaicoach.platform.isDebugBuild
import com.worksoc.goaicoach.ui.designsystem.toByteArray
import com.worksoc.goaicoach.ui.foundation.AppPlatform
import com.worksoc.goaicoach.ui.foundation.AppStores
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.CoreHaptics.CHHapticEngine
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithContentsOfFile
import platform.UIKit.UIDevice
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.UIKit.UIPasteboard
import platform.posix.exit
import platform.posix.uname
import platform.posix.utsname

/** iOS의 [AppPlatform](백로그 #294 — S1). 루트(`MainViewController`)가 하나 만들어 넣는다. 기능은 웨이브마다 는다. */
internal class IosAppPlatform : AppPlatform {
    override val notice = IosNotice()
    override val clipboard: ClipboardPort = IosClipboard()
    override val stores: AppStores = iosAppStores
    override val haptics: PlayHapticFeedback = IosPlayHaptics

    // App Store의 새 버전 조회는 등록 뒤(#298) — 그때까지 「모른다」로 답한다(설정은 스토어 링크로 폴백, 엔진 받기는 기다리지 않는다).
    override val appUpdate = AppUpdateCheck { AppUpdateStatus.Unknown }

    // 사진 분석은 iOS 첫 판에 싣지 않는다(U-84) — 홈의 카메라 단추 · 실험실 타일이 감춰진다.
    override val boardScan: BoardScanEntry? = null

    // 안드로이드 `assets/`를 앱 묶음의 appassets/assets/로 복사해 싣는다(`scripts/ios-bundle-resources.sh`).
    override val assets = object : BundledAssets {
        override fun readBytes(path: String): ByteArray? {
            val root = NSBundle.mainBundle.resourcePath ?: return null
            return NSData.dataWithContentsOfFile("$root/appassets/assets/$path")?.toByteArray()
        }

        override fun referenceGameReplay(): GameReplayData? =
            readBytes(ReferenceGameAssetPath)?.decodeToString()?.let { raw -> GameReplayCodec.decode(raw, ReferenceGameBoardSize) }

        // iOS 오픈소스 목록은 따로 만든다(#294 W24 — Compose Multiplatform 등 iOS에서 실제로 싣는 것). 그때까지 빈 목록.
        override fun licensesJson(): String? = readBytes("licenses/aboutlibraries-ios.json")?.decodeToString()
    }

    override val developer = object : DeveloperTools {
        override fun wipeToFreshInstall() = iosWipeToFreshInstall()

        // 지우고 프로세스를 끝낸다 — 안드로이드의 「지우고 다시 띄우기」와 같은 뜻(메모리의 옛 값이 지운 저장소에 되써지지 않게). iOS는 앱이 스스로
        // 다시 뜰 수 없으니 개발자가 아이콘을 눌러 콜드 스타트한다. 부르는 곳은 디버그 개발자 메뉴뿐이다(출시 빌드에는 닿지 않는다).
        override fun restartToFreshInstall() {
            iosWipeToFreshInstall()
            exit(0)
        }

        override fun runReleaseResetAgain(): Boolean = false

        override fun resetAdConsent() {}

        override fun diagnosticLogText(): String = diagnosticEventLog.readText()
    }

    // 로그인은 꺼져 있다 — 부르면 「지원 안 함」으로 답한다(Sign in with Apple · 계정 연동은 사용자 작업 뒤).
    override val authClient: AuthClientPort = IosNoAuthClient
    override val googleIdTokens: GoogleIdTokenSource = object : GoogleIdTokenSource {}

    // 실행 기록 · 진단 로그 — 안드로이드와 같은 파일 · 같은 줄(`KvAppendOnlyLogs.kt`). 파일마다 하나씩만 만든다(매체 실패 횟수를 인스턴스가 센다).
    override val runtimeEventLog: RuntimeEventLogPort = KvRuntimeEventLog(IosAppFileStore())

    /** 엔진 묶음(`iosProcessRuntime`)과 개발자 메뉴가 같은 것을 본다. */
    val diagnosticEventLog: DiagnosticEventLogPort = KvDiagnosticEventLog(IosAppFileStore())

    override val debugReportPlatform = object : DebugReportPlatformInfo {
        override fun hapticDiagnostic(): String = IosPlayHaptics.diagnosticReport()

        // 안드로이드 `debugReportBuildStamp()`와 같은 두 줄 · 같은 구분자(` · `) — 붙여넣은 리포트를 같은 눈으로 읽는다.
        override fun buildStamp(): String = buildString {
            append("build=${appBuildInfo.versionName} (${appBuildInfo.versionCode})")
            append(" · ${appBuildInfo.buildType}")
            append(" · ${if (appBuildInfo.useTestAds) "test-ads" else "REAL-ADS"}")
            append(" · builtAt=${appBuildInfo.buildTime}")
            appendLine()
            append("device=Apple ${iosDeviceModel()}")
            append(" · ios=${UIDevice.currentDevice.systemVersion}")
        }
    }

    // 모델은 앱과 함께 싣는다(시뮬레이터는 맥의 모델 경로) — 받기 화면이 필요 없다.
    override fun isEngineSetupRequired(): Boolean = false

    override val debugArmFiles = DebugArmFiles { name -> isDebugBuild && consumeDocumentsFile(name) }
}

/** 개발 빌드의 무장 파일 — 앱의 Documents 폴더(`xcrun simctl get_app_container booted <번들> data`/Documents)에 둔다. */
@OptIn(ExperimentalForeignApi::class)
private fun consumeDocumentsFile(name: String): Boolean {
    val documents = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).firstOrNull() as? String ?: return false
    val path = "$documents/$name"
    val manager = NSFileManager.defaultManager
    return manager.fileExistsAtPath(path) && manager.removeItemAtPath(path, error = null)
}

/**
 * 착수 진동(S10) — 돌을 놓으면 묵직한 한 번, 놓을 수 없으면 경고 알림(짧게 두 번 떨린다).
 * 진단 리포트의 `[Haptics]` 절(안드로이드 `PlayHapticDiagnostics`의 짝): UIKit 피드백은 성공 신호를 돌려주지 않아 「시도」와 기기 능력만 적는다.
 */
private object IosPlayHaptics : PlayHapticFeedback {
    private var attempts = 0
    private var lastKind: String? = null

    override fun play() {
        UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy).impactOccurred()
        attempts++
        lastKind = "impact(heavy)"
    }

    override fun playInvalid() {
        UINotificationFeedbackGenerator().notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeError)
        attempts++
        lastKind = "notification(error)"
    }

    fun diagnosticReport(): String = buildString {
        appendLine("path=UIFeedbackGenerator gate=앱 토글(iOS 시스템 햅틱 설정을 따른다)")
        appendLine("attempts=$attempts last=${lastKind ?: "none"}")
        append("supportsHaptics=${CHHapticEngine.capabilitiesForHardware().supportsHaptics()}")
    }
}

/** 기기 모델 — 시뮬레이터는 흉내 내는 기종(`SIMULATOR_MODEL_IDENTIFIER`)에 표시를 붙이고, 기기는 `utsname.machine`(예: iPhone17,1). */
@OptIn(ExperimentalForeignApi::class)
private fun iosDeviceModel(): String {
    NSProcessInfo.processInfo.environment["SIMULATOR_MODEL_IDENTIFIER"]?.let { return "$it (simulator)" }
    return memScoped {
        val info = alloc<utsname>()
        uname(info.ptr)
        info.machine.toKString()
    }
}

private class IosClipboard : ClipboardPort {
    override fun setText(label: String, text: String): Boolean {
        UIPasteboard.generalPasteboard.string = text
        return true
    }
}

private object IosNoAuthClient : AuthClientPort {
    private fun unsupported() = Result.failure<AuthState>(UnsupportedOperationException("sign-in is not available on iOS yet"))

    override suspend fun signInWithGoogle(idToken: String): Result<AuthState> = unsupported()

    override suspend fun linkGoogleCredential(idToken: String): Result<AuthState> = unsupported()

    override suspend fun signInWithEmail(email: String, password: String): Result<AuthState> = unsupported()

    override suspend fun linkEmailCredential(email: String, password: String): Result<AuthState> = unsupported()

    override fun currentAuthState(): AuthState = AuthState()

    override suspend fun deleteAccount(): Result<Unit> = Result.failure(UnsupportedOperationException("no account on iOS yet"))
}
