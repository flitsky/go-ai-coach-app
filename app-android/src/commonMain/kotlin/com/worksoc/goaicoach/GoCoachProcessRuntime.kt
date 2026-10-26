package com.worksoc.goaicoach

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.worksoc.goaicoach.application.analysis.PositionAnalysisCacheStore
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.engine.EngineSessionBackend
import com.worksoc.goaicoach.application.engine.EngineSessionCapabilities
import com.worksoc.goaicoach.application.engine.EngineSessionClient
import com.worksoc.goaicoach.application.engine.LocalEngineSessionClient
import com.worksoc.goaicoach.engine.DeferredEngineCoreApi
import com.worksoc.goaicoach.engine.EngineBootstrap
import com.worksoc.goaicoach.engine.EngineIdentity
import com.worksoc.goaicoach.engine.SessionGenerationRelay
import com.worksoc.goaicoach.engine.identity
import com.worksoc.goaicoach.platform.appIoDispatcher
import com.worksoc.goaicoach.shared.enginecontract.EngineCoreApi
import com.worksoc.goaicoach.shared.enginecontract.EngineMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 엔진 묶음을 **액티비티보다 오래, 프로세스만큼** 드는 소유자(refactor backlog #110). 조립만 하고 계산하지 않는다.
 *
 * ## 왜 있나
 * 이 조립은 `MainActivity`의 컴포지션(`remember` 일곱과 부트스트랩 `LaunchedEffect(Unit)`)에 있었다. 같은 프로세스에서
 * 액티비티가 새로 만들어질 때마다(안드로이드 11 이하에서 홈 화면 뒤로가기 뒤 다시 켜기, 「활동 유지 안 함」,
 * `configChanges` 밖의 설정 변경, 계기 테스트의 반복 실행) 부트스트랩·어댑터·세션 클라이언트가 **새로** 생겼고, 옛 어댑터의
 * KataGo는 아무도 내리지 않아 한 벌씩 쌓였다(2GB 에뮬레이터에서 lowmemorykiller). 이제 `GoAiCoachApplication`이
 * `by lazy`로 하나를 들고 부트스트랩은 프로세스에 한 번 돈다 — 어댑터가 하나라 KataGo는 **종류마다 1개**다
 * (`EngineProcessSlot`의 #14 보장). 아래 줄들은 그 자리의 것을 문구째 옮겨 왔다. 계산은 바뀌지 않았다.
 *
 * ## ⚠️ 엔진을 멈추지 않는다
 * 프로덕션에서는 놓지 않는다 — 프로세스가 죽을 때 함께 죽는다(자식 프로세스는 stdin EOF로도 끝난다). 다시 만들어진
 * 화면은 따뜻한 엔진을 그대로 쓴다: 그 화면의 `LaunchedEffect(engineClient)`가 `startSession`을 다시 부르고, 어댑터의
 * `initialize`는 떠 있는 gtp 프로세스를 그대로 잡아 탐색 한도만 다시 싣는다. 옛 화면이 남긴 오퍼레이션(취소된 AI 차례의
 * 답 받기 등)이 있으면 같은 오퍼레이션 락(#15) 뒤에서 그것이 끝나기를 기다린다 — 그동안 "준비 중"이 길어질 수 있다.
 * 액티비티가 끝날 때 멈추는 안은 뒤로 미뤘다(틈 없는 종료에 shared 인터페이스 변경과 참조 계수가 필요하다 — #111과 함께).
 *
 * ## 생성자가 이음새다
 * JVM 테스트가 Application 없이 만들 수 있게 파일·BuildConfig·디스패처를 받는다. 앱은 `forApplication`(안드로이드 —
 * `GoCoachProcessRuntimeAndroid.kt`) · `iosProcessRuntime()`(iOS — `IosProcessRuntime.kt`)으로 만든다. 본체가 공용인 것은 백로그 #294다.
 * 생성자에는 부수효과가 없다 — 부트스트랩은 [startEngineBootstrap]이 시작한다.
 */
internal class GoCoachProcessRuntime(
    val diagnosticEventLog: DiagnosticEventLogPort,
    positionAnalysisCacheStore: PositionAnalysisCacheStore,
    /** 블로킹 IO 또는 PAD 에셋 팩 다운로드 대기 — [ioDispatcher]에서 부른다. */
    private val createBootstrap: suspend () -> EngineBootstrap,
    /** 원격 엔진 주소. `null`이면 원격을 쓰지 않는다 — 값은 debug 빌드에 주소가 있을 때만 온다(`forApplication`). */
    private val remoteEngineUrl: String?,
    /**
     * [remoteEngineUrl]로 원격 세션 클라이언트를 만드는 길 — 원격 HTTP 어댑터는 안드로이드 쪽에만 있다(#294). 후보를 못 고르면 `null`이고
     * 그때는 로컬로 폴백한다. 주소가 `null`이면 부르지 않는다.
     */
    private val createRemoteClient: (url: String, currentSessionGeneration: () -> Long) -> EngineSessionClient? = { _, _ -> null },
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val ioDispatcher: CoroutineDispatcher = appIoDispatcher,
) {
    // 엔진 호출은 이 `Deferred`가 완성될 때까지 `DeferredEngineCoreApi` 안에서 기다린다.
    private val coreApiDeferred = CompletableDeferred<EngineCoreApi>()

    // 세션 세대도 같은 사정이다 — 그 원천(`GameSessionStateHolder`)은 `GoCoachApp`이
    // 만든다. 클라이언트는 이 중계기를 읽고, `GoCoachApp`이 홀더를 만들면서 잇는다(refactor backlog #18).
    // 화면이 다시 만들어지면 새 홀더가 다시 잇는다 — 나중 것이 이긴다(`SessionGenerationRelay.bind`).
    val sessionGenerationRelay = SessionGenerationRelay()

    /** 준비 전에는 `null`이다(#101). 스냅숏 상태라, 이것을 읽는 화면([engineIdentity])이 도착을 보고 다시 그린다. */
    private var engineBootstrap by mutableStateOf<EngineBootstrap?>(null)

    // ⚠️ 예전에는 아래 `engineClient`를 만드는 `remember` 블록 **안에서** 컴포즈 상태
    // (`usingRemoteEngine`)에 값을 썼다 — 컴포지션 도중의 상태 쓰기이고, 그 블록은
    // 키가 그대로면 다시 돌지 않으므로 값이 어긋날 수 있었다. 후보 선택 자체를 밖으로
    // 꺼내 **평범한 값**으로 만들었다.
    private val remoteClient: EngineSessionClient? =
        if (remoteEngineUrl != null) {
            createRemoteClient(remoteEngineUrl, sessionGenerationRelay::current)
        } else {
            null
        }

    // ⚠️ **한 번 만들고 다시 만들지 않는다 — `engineBootstrap`에서 파생하지 말 것.** 부트스트랩이 도착할 때
    // 클라이언트가 새로 만들어지면 `GoCoachApp`의 `LaunchedEffect(engineClient)`가 **엔진 기동을
    // 다시** 돌린다. 그래서 부트스트랩은 **람다 안에서 읽는다.**
    val engineClient: EngineSessionClient =
        // 원격 후보가 있으면 우선 쓰고, 어떤 이유로든(엔드포인트가 비활성 등) 후보를 못
        // 고르면 항상 로컬로 폴백한다.
        remoteClient ?: DeferredEngineCoreApi(coreApiDeferred).let { deferredCoreApi ->
            LocalEngineSessionClient(
                coreApi = deferredCoreApi,
                currentSessionGeneration = sessionGenerationRelay::current,
                capabilitiesProvider = {
                    EngineSessionCapabilities(
                        // 준비 전에는 `null`이라 false다 — 없는 능력을 열어주지 않는다.
                        supportsDeviceBenchmark = engineBootstrap?.mode == EngineMode.LocalProcess,
                        backend = EngineSessionBackend.LocalEngine,
                        // 엔진이 준비되기 전에는 거짓이다(`DeferredEngineCoreApi.supportsHumanNetwork`) — 준비되면 진짜 답으로 바뀐다.
                        supportsHumanNetwork = deferredCoreApi.supportsHumanNetwork,
                    )
                },
                positionAnalysisCacheStore = positionAnalysisCacheStore,
                diagnosticEventLog = diagnosticEventLog,
            )
        }

    private val bootstrapScope = CoroutineScope(SupervisorJob() + mainDispatcher + CoroutineName("EngineBootstrap"))
    private var bootstrapStarted = false

    /**
     * 엔진의 정체 — 값이 아니라 **물을 때마다 읽는다**(`GoCoachApp`은 이것을 `() -> EngineIdentity`로 받는다, #101).
     *
     * ⚠️ **예측하지 않는다** — 준비 전에는 `EngineIdentity.Unresolved`(mode=Unknown)를
     * 그대로 넘긴다(2026-09-05 사용자 결정). 여기서 *"어차피 KataGo겠지"* 로 찍으면
     * 스텁 폴백 기기의 진단 리포트가 거짓말을 한다.
     */
    fun engineIdentity(): EngineIdentity {
        val resolved = engineBootstrap?.identity() ?: EngineIdentity.Unresolved
        return if (remoteClient != null) {
            resolved.copy(name = "${resolved.name} (remote: $remoteEngineUrl)")
        } else {
            resolved
        }
    }

    /**
     * 엔진 부트스트랩을 **프로세스에 한 번** 시작한다. 두 번째부터는 아무것도 하지 않는다 — 다시 만들어진 화면이
     * 부르면 이미 끝났거나 도는 중인 그 결과를 쓴다(최초 실행의 모델 복사가 같은 파일에 두 벌 돌지 않는다).
     *
     * `MainActivity`의 `LaunchedEffect(Unit)`이 메인 스레드에서 부른다 — 첫 컴포지션 직후라는 시점은 옮기기 전과 같다
     * (#125 스플래시와 함께 부팅, #123 `setContent` 뒤). 부트스트랩 잡은 화면과 무관하게 끝까지 돈다 — 쓰는 것이 이
     * 런타임의 `Deferred`와 [engineBootstrap]뿐이다. 실패는 삼키지 않는다 — 옮기기 전에도 `LaunchedEffect` 안의
     * 예외는 앱을 죽였고, `SupervisorJob` 아래 `launch`의 예외도 기본 처리기로 앱을 죽인다.
     */
    fun startEngineBootstrap() {
        if (bootstrapStarted) return
        bootstrapStarted = true
        bootstrapScope.launch {
            val ready = withContext(ioDispatcher) { createBootstrap() }
            // ⚠️ **엔진을 먼저 풀어주고 정체를 나중에 알린다.** 반대로 하면, 정체를 보고
            // 재구성된 화면이 *"엔진 준비됨"* 으로 보이는 찰나에 엔진은 아직 잠겨 있다.
            coreApiDeferred.complete(ready.coreApi)
            engineBootstrap = ready
        }
    }

    /** 앱이 만드는 길은 플랫폼 쪽 확장이다 — 안드로이드 `forApplication`(`GoCoachProcessRuntimeAndroid.kt`). */
    companion object
}
