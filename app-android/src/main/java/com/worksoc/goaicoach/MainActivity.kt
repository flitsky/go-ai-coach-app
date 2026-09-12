package com.worksoc.goaicoach

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.engine.EngineSessionBackend
import com.worksoc.goaicoach.application.engine.EngineSessionCapabilities
import com.worksoc.goaicoach.application.engine.LocalEngineSessionClient
import com.worksoc.goaicoach.application.engine.RemoteEngineCandidate
import com.worksoc.goaicoach.engine.DeferredEngineCoreApi
import com.worksoc.goaicoach.ui.allowsRotation
import com.worksoc.goaicoach.engine.EngineBootstrap
import com.worksoc.goaicoach.engine.EngineIdentity
import com.worksoc.goaicoach.engine.createEngineBootstrap
import com.worksoc.goaicoach.engine.createRemoteEngineSessionClient
import com.worksoc.goaicoach.engine.identity
import com.worksoc.goaicoach.persistence.DiagnosticEventLog
import com.worksoc.goaicoach.persistence.JsonPositionAnalysisCacheStore
import com.worksoc.goaicoach.persistence.UserPreferencesStore
import com.worksoc.goaicoach.ui.AdsConsentManager
import com.worksoc.goaicoach.shared.EngineCoreApi
import com.worksoc.goaicoach.shared.EngineMode
import com.worksoc.goaicoach.ui.AppFontScaleState
import com.worksoc.goaicoach.ui.AppSplash
import com.worksoc.goaicoach.ui.GoCoachApp
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
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
                val positionAnalysisCacheStore = remember(applicationContext) {
                    JsonPositionAnalysisCacheStore(applicationContext)
                }
                val diagnosticEventLog: DiagnosticEventLogPort = remember(applicationContext) {
                    DiagnosticEventLog(File(applicationContext.filesDir, DiagnosticEventLog.FileName))
                }
                // 엔진 호출은 이 `Deferred`가 완성될 때까지 `DeferredEngineCoreApi` 안에서 기다린다.
                val coreApiDeferred = remember { CompletableDeferred<EngineCoreApi>() }
                var engineBootstrap by remember { mutableStateOf<EngineBootstrap?>(null) }
                LaunchedEffect(Unit) {
                    val ready = withContext(Dispatchers.IO) {
                        createEngineBootstrap(
                            context = applicationContext,
                            nativeLibraryDir = applicationInfo.nativeLibraryDir,
                        )
                    }
                    // ⚠️ **엔진을 먼저 풀어주고 정체를 나중에 알린다.** 반대로 하면, 정체를 보고
                    // 재구성된 화면이 *"엔진 준비됨"* 으로 보이는 찰나에 엔진은 아직 잠겨 있다.
                    coreApiDeferred.complete(ready.coreApi)
                    engineBootstrap = ready
                }
                // 개발용 원격 엔진 스파이크(docs/work/roadmap/LAYERED_ARCHITECTURE_REFACTORING_PLAN_260803_1500.md
                // Stage E-3). BuildConfig.REMOTE_ENGINE_URL은 debug 빌드에서만, local.properties의
                // debug.remoteEngineUrl 키가 있을 때만 비어있지 않다(app-android/build.gradle.kts 참고)
                // — playInternal/release는 항상 빈 문자열로 고정돼 있어 이 분기를 절대 타지 않는다.
                val remoteEngineUrl = BuildConfig.REMOTE_ENGINE_URL
                val remoteEngineRequested = BuildConfig.DEBUG && remoteEngineUrl.isNotBlank()
                // ⚠️ 예전에는 아래 `engineClient`를 만드는 `remember` 블록 **안에서** 컴포즈 상태
                // (`usingRemoteEngine`)에 값을 썼다 — 컴포지션 도중의 상태 쓰기이고, 그 블록은
                // 키가 그대로면 다시 돌지 않으므로 값이 어긋날 수 있었다. 후보 선택 자체를 밖으로
                // 꺼내 **평범한 값**으로 만들었다.
                val remoteClient = remember(positionAnalysisCacheStore, diagnosticEventLog) {
                    if (remoteEngineRequested) {
                        createRemoteEngineSessionClient(
                            candidates = listOf(RemoteEngineCandidate(endpointUrl = remoteEngineUrl, enabled = true)),
                            positionAnalysisCacheStore = positionAnalysisCacheStore,
                            diagnosticEventLog = diagnosticEventLog,
                        )
                    } else {
                        null
                    }
                }
                // ⚠️ **`engineBootstrap`을 키로 쓰지 말 것.** 부트스트랩이 도착할 때 클라이언트가
                // 새로 만들어지면 `GoCoachApp`의 `LaunchedEffect(engineClient)`가 **엔진 기동을
                // 다시** 돌린다. 그래서 부트스트랩은 키가 아니라 **람다 안에서 읽는다.**
                val engineClient = remember(remoteClient, positionAnalysisCacheStore, diagnosticEventLog) {
                    // 원격 후보가 있으면 우선 쓰고, 어떤 이유로든(엔드포인트가 비활성 등) 후보를 못
                    // 고르면 항상 로컬로 폴백한다.
                    remoteClient ?: LocalEngineSessionClient(
                        coreApi = DeferredEngineCoreApi(coreApiDeferred),
                        capabilitiesProvider = {
                            EngineSessionCapabilities(
                                // 준비 전에는 `null`이라 false다 — 없는 능력을 열어주지 않는다.
                                supportsDeviceBenchmark = engineBootstrap?.mode == EngineMode.LocalProcess,
                                backend = EngineSessionBackend.LocalEngine,
                            )
                        },
                        positionAnalysisCacheStore = positionAnalysisCacheStore,
                        diagnosticEventLog = diagnosticEventLog,
                    )
                }
                // ⚠️ **홈을 스플래시 뒤에 두지 않고 함께 컴포즈한다**(백로그 #125). 스플래시가
                // 도는 1초 동안 홈은 이미 조립되고 엔진 부트스트랩도 돌고 있어야, 그 1초가
                // **버려지는 시간이 아니라 벌어 두는 시간**이 된다. `AppSplash`는 위에 얹혀
                // 터치를 먹고 스스로 사라진다.
                Box {
                    GoCoachApp(
                        engineClient = engineClient,
                        // ⚠️ **예측하지 않는다** — 준비 전에는 `EngineIdentity.Unresolved`(mode=Unknown)를
                        // 그대로 넘긴다(2026-09-05 사용자 결정). 여기서 *"어차피 KataGo겠지"* 로 찍으면
                        // 스텁 폴백 기기의 진단 리포트가 거짓말을 한다.
                        engineIdentity = {
                            val resolved = engineBootstrap?.identity() ?: EngineIdentity.Unresolved
                            if (remoteClient != null) {
                                resolved.copy(name = "${resolved.name} (remote: $remoteEngineUrl)")
                            } else {
                                resolved
                            }
                        },
                        diagnosticEventLog = diagnosticEventLog,
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
