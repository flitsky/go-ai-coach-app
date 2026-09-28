package com.worksoc.goaicoach

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.lifecycleScope
import com.worksoc.goaicoach.persistence.UserPreferencesStore
import com.worksoc.goaicoach.platform.AdsConsentManager
import com.worksoc.goaicoach.ui.play.allowsRotation
import com.worksoc.goaicoach.ui.settings.AppFontScaleState
import com.worksoc.goaicoach.ui.shell.GoCoachApp
import com.worksoc.goaicoach.ui.splash.AppSplash
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** 엔진 묶음의 프로세스 수명 소유자(refactor backlog #110) — 사유는 [GoCoachProcessRuntime]의 KDoc. */
    private val processRuntime: GoCoachProcessRuntime by lazy { (application as GoAiCoachApplication).processRuntime }

    /**
     * 회전 정책(백로그 #147) — 폰은 세로 고정, 큰 화면(sw≥600dp)은 자유. 판정은 `allowsRotation` 하나가 한다.
     *
     * ⚠️ **매니페스트의 `portrait`는 그대로 둔다** — 이 줄이 돌기 전(첫 프레임)까지의 기본값이고, 큰 화면에서만
     * 여기서 풀어 준다. `SCREEN_ORIENTATION_USER`는 시스템의 회전 잠금 설정을 존중한다.
     * ⚠️ `onConfigurationChanged`에서도 다시 적용해야 한다 — 폴드를 접었다 펴면 폭이 바뀌는데(`configChanges`
     * 덕분에 액티비티는 살아 있다) 그때 정책도 따라가야 접은 화면에서 가로로 남는 일이 없다.
     */
    private fun applyRotationPolicy(configuration: Configuration) {
        requestedOrientation = if (allowsRotation(configuration.smallestScreenWidthDp)) {
            ActivityInfo.SCREEN_ORIENTATION_USER
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyRotationPolicy(newConfig)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // ⚠️ **앱 글꼴 배율은 여기서 적용해야 한다**(백로그 #81) — 컴포지션 전체를 감싸야
            // 모든 화면이 함께 바뀐다. `GoCoachApp`에서는 할 수 없다(그 파일은 상태 훅 42/42로
            // 여유가 없다, 함정 3번).
            //
            // ⚠️ **이 앱은 시스템 배율을 따르지 않는다.** 저장된 값(기본 1.0)을 그대로 쓰므로,
            // 시스템에서 글자를 키운 사용자도 이 앱에서는 1.0으로 본다 — 2026-09-04 사용자 결정이고
            // **접근성 비용이 있다**(사유와 되돌리는 방법은 `DefaultAppFontScale`의 KDoc).
            //
            // ⚠️ `load`를 여기서 한 번 부르지 않으면 **저장값이 무시된다** — 저장은 되는데 반영이
            // 안 되는 것처럼 보이는, 원인 찾기 어려운 종류다.
            val preferencesStore = remember(applicationContext) { UserPreferencesStore(applicationContext) }
            LaunchedEffect(preferencesStore) { AppFontScaleState.load(preferencesStore) }
            val baseDensity = LocalDensity.current
            val density = Density(density = baseDensity.density, fontScale = AppFontScaleState.scale)
            CompositionLocalProvider(LocalDensity provides density) {
                // ⚠️ **여기에 준비 화면이 있었다**(백로그 #101, 2026-09-05 제거). `engineBootstrap`이
                // null인 동안 앱 전체를 막고 *"준비 중…"* 을 그렸는데, 최초 실행에는 약 100MB 모델
                // 복사가 돌아 **몇 초 동안 사용자가 아무것도 못 했다.**
                //
                // 이제 홈이 곧 랜딩이다. 준비는 뒤에서 돌고, 사용자가 온보딩·홈을 훑는 **그 시간에**
                // 끝난다. 준비 전에 대국을 시작하려 하면 로비의 시작 버튼이 막아 준다(#101 0단계).
                // ⚠️ **엔진 묶음은 여기서 만들지 않는다**(refactor backlog #110) — 부트스트랩·세션 클라이언트·진단 로그는
                // 프로세스가 든다(`GoCoachProcessRuntime`). 여기서 만들면 액티비티가 다시 만들어질 때마다 KataGo가 한 벌씩 쌓인다.
                // 부트스트랩은 프로세스에 한 번이다 — 다시 만들어진 액티비티가 부르면 아무것도 하지 않는다.
                LaunchedEffect(Unit) { processRuntime.startEngineBootstrap() }
                // ⚠️ **홈을 스플래시 뒤에 두지 않고 함께 컴포즈한다**(백로그 #125). 스플래시가
                // 도는 1초 동안 홈은 이미 조립되고 엔진 부트스트랩도 돌고 있어야, 그 1초가
                // **버려지는 시간이 아니라 벌어 두는 시간**이 된다. `AppSplash`는 위에 얹혀
                // 터치를 먹고 스스로 사라진다.
                Box {
                    GoCoachApp(
                        engineClient = processRuntime.engineClient,
                        // ⚠️ 값이 아니라 공급자로 넘긴다 — 준비 전에는 `Unresolved`다(#101, `engineIdentity`의 KDoc).
                        engineIdentity = { processRuntime.engineIdentity() },
                        diagnosticEventLog = processRuntime.diagnosticEventLog,
                        sessionGenerationRelay = processRuntime.sessionGenerationRelay,
                    )
                    AppSplash()
                }
            }
        }
        // ⚠️ **동의 상태 조회는 앱 기동마다 한 번** — UMP 규격이다(백로그 #89).
        // 폼은 여기서 띄우지 **않는다.** 규격이 요구하는 것은 조회이고, 폼은 광고를 요청하기
        // 직전에 `showRewardedAdOnce`가 띄운다 — #101이 세운 *"홈이 곧 랜딩"* 을 지키기 위해서다
        // (공식 샘플대로 여기서 폼까지 띄우면 EEA 사용자는 앱을 켜자마자 전면 다이얼로그를 만난다).
        // ⚠️ 이 호출이 끝나기 전에 광고 버튼이 눌릴 수 있는데, 그쪽이 스스로 다시 조회하므로
        // 여기 결과를 기다리게 만들 필요가 없다 — 공유 상태를 두지 않은 이유다.
        //
        // ⚠️⚠️ **`setContent`보다 앞에 두지 말 것 — 이 줄의 위치가 곧 이 줄의 버그였다**(백로그 #123).
        // UMP는 `requestConsentInfoUpdate` 안에서 **자기 백그라운드 스레드**로 WebView 프로바이더를
        // 로드하고(`WebViewFactory.getProvider`), 그 과정이 앱 프로세스의 `Resources`/`AssetManager`를
        // 갈아끼운다. 이것이 `setContent`보다 앞에 있으면 그 스레드가 **메인 스레드가 창의 decor를
        // 인플레이트하는 바로 그 순간**과 겹쳐, `android.R.id.content`가 사라진 decor가 만들어진다.
        // 실측(Pixel 7 / API 35, `playInternal`): **앞에 두면 9/120, 뒤에 두면 0/120.**
        // ⚠️ 되돌리기 쉬운 만큼 `StartupOrderContractTest`가 이 순서를 소스 계약으로 지킨다.
        // 회전 정책(#147)을 건다. ⚠️ **`setContent`보다 앞에 두지 말 것** — `super.onCreate`와 `setContent` 사이는
        // #123이 실측으로 비워 둔 구간이고(`StartupOrderContractTest`), 그 계약은 이 줄을 실제로 잡아냈다.
        // 창이 세워진 뒤에 걸어도 결과는 같다: 큰 화면이면 잠금이 풀리고, `configChanges` 덕분에 대국은 살아 있다.
        applyRotationPolicy(resources.configuration)
        lifecycleScope.launch { AdsConsentManager.refresh(this@MainActivity) }
    }
}
