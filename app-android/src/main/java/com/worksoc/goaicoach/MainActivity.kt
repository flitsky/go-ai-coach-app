package com.worksoc.goaicoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.worksoc.goaicoach.engine.EngineBootstrap
import com.worksoc.goaicoach.engine.EngineIdentity
import com.worksoc.goaicoach.engine.createEngineBootstrap
import com.worksoc.goaicoach.engine.createRemoteEngineSessionClient
import com.worksoc.goaicoach.engine.identity
import com.worksoc.goaicoach.persistence.DiagnosticEventLog
import com.worksoc.goaicoach.persistence.JsonPositionAnalysisCacheStore
import com.worksoc.goaicoach.persistence.UserPreferencesStore
import com.worksoc.goaicoach.shared.EngineCoreApi
import com.worksoc.goaicoach.shared.EngineMode
import com.worksoc.goaicoach.ui.AppFontScaleState
import com.worksoc.goaicoach.ui.GoCoachApp
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
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
                // — friend/playInternal/release는 항상 빈 문자열로 고정돼 있어 이 분기를 절대 타지
                // 않는다.
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
            }
        }
    }
}
