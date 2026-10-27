package com.worksoc.goaicoach.ios

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.uikit.ComposeUIViewControllerDelegate
import androidx.compose.ui.window.ComposeUIViewController
import com.worksoc.goaicoach.GoCoachProcessRuntime
import com.worksoc.goaicoach.ui.foundation.LocalAppPlatform
import com.worksoc.goaicoach.ui.monetization.IosAppMonetization
import com.worksoc.goaicoach.ui.monetization.LocalIosAppMonetization
import com.worksoc.goaicoach.ui.settings.AppTheme
import com.worksoc.goaicoach.ui.settings.IosStatusBarAppearance
import com.worksoc.goaicoach.ui.settings.LocalIosStatusBarAppearance
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIStatusBarStyle
import platform.UIKit.UIStatusBarStyleDarkContent
import platform.UIKit.UIStatusBarStyleLightContent
import platform.UIKit.UIViewController

/**
 * iOS 앱의 뿌리 묶음(백로그 #294) — 안드로이드 `GoAiCoachApplication`의 짝. Swift의 `AppDelegate`가 **프로세스에 하나** 들고(`let root = IosAppRoot(…)`),
 * 장면이 창을 열 때마다 [makeViewController]로 화면만 새로 받는다. 플랫폼 묶음 · 시작 처리([runIosAppStartup]) · 엔진 묶음([iosProcessRuntime])은
 * 여기서 한 번 생긴다 — 장면이 끊겼다 다시 붙어도 따뜻한 엔진을 그대로 쓴다(안드로이드 #110: 화면이 새로 생길 때마다 KataGo가 한 벌 더 뜨던 사고).
 * Kotlin 전역(object)에 두지 않는 이유: 컴포즈 상태를 든 전역은 셸 원장이 막는다(`ShellStateLedgerContractTest`).
 *
 * 보통은 앱 전체([GoCoachRoot])다. 환경 변수로 다른 화면을 연다: 엔진 확인(`make ios-engine-probe`) · 이식 카탈로그(`make ios-catalog`).
 *
 * ⚠️ Compose Multiplatform은 `Info.plist`에 `CADisableMinimumFrameDurationOnPhone`이 없으면 시작할 때 멈춘다(엄격한 plist 검사가 기본이다) —
 *    그 키는 `app-ios/Info.plist`에 있다.
 * ⚠️ 상태 표시줄 모양은 **창의 루트** 화면 컨트롤러가 정한다 — 그래서 Swift 쪽이 SwiftUI 호스트가 아니라 이 컨트롤러를 루트로 둔다.
 */
class IosAppRoot(private val modelSource: IosEngineModelSource?, storeSource: IosStoreSource?, adsSource: IosAdsSource?) {
    // ⚠️ 첫 필드 — 다른 무엇보다 먼저(안드로이드 `GoAiCoachApplication.onCreate`의 순서, #99). 저장소가 생기기 전에 지워야 한다.
    @Suppress("unused")
    private val developerModeReset = applyIosDeveloperModeReset()
    private val probe = EngineProbe.requested()
    private val catalog = NSProcessInfo.processInfo.environment["GOCOACH_PORT_CATALOG_PAGE"] != null
    private val platform = IosAppPlatform(modelSource)
    private val runtime: GoCoachProcessRuntime
    private val statusBar = IosStatusBarAppearance()
    private val music = IosBackgroundMusic(platform.stores.backgroundMusic)
    private val monetization = IosAppMonetization(storeSource, adsSource, music)

    init {
        runIosAppStartup(platform, music)
        runtime = iosProcessRuntime(platform.diagnosticEventLog, modelSource)
    }

    /** 장면마다 새 화면 컨트롤러 — 안의 묶음은 같다. */
    fun makeViewController(): UIViewController = ComposeUIViewController(
        configure = {
            delegate = object : ComposeUIViewControllerDelegate {
                override val preferredStatusBarStyle: UIStatusBarStyle?
                    get() = if (statusBar.dark) UIStatusBarStyleLightContent else UIStatusBarStyleDarkContent
            }
        },
    ) {
        CompositionLocalProvider(
            LocalAppPlatform provides platform,
            LocalIosStatusBarAppearance provides statusBar,
            LocalIosAppMonetization provides monetization,
        ) {
            Box {
                when {
                    probe != null -> EngineProbeScreen(probe)
                    catalog -> PortCatalog()
                    else -> GoCoachRoot(runtime)
                }
                // 알림은 앱 테마 안에서 — 어두운 화면 모드의 색을 따른다(층은 IosNoticeOverlay가 대화상자 위로 올린다).
                AppTheme { IosNoticeOverlay(platform.notice) }
            }
        }
    }
}
