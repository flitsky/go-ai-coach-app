package com.worksoc.goaicoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.worksoc.goaicoach.persistence.UserPreferencesStore
import com.worksoc.goaicoach.ui.AppFontScaleState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.diagnostic.DiagnosticEventLogPort
import com.worksoc.goaicoach.application.engine.EngineSessionBackend
import com.worksoc.goaicoach.application.engine.EngineSessionCapabilities
import com.worksoc.goaicoach.application.engine.LocalEngineSessionClient
import com.worksoc.goaicoach.application.engine.RemoteEngineCandidate
import com.worksoc.goaicoach.engine.EngineBootstrap
import com.worksoc.goaicoach.engine.createEngineBootstrap
import com.worksoc.goaicoach.engine.createRemoteEngineSessionClient
import com.worksoc.goaicoach.persistence.DiagnosticEventLog
import com.worksoc.goaicoach.ui.AppLightColorScheme
import com.worksoc.goaicoach.ui.LocalUiStrings
import com.worksoc.goaicoach.persistence.JsonPositionAnalysisCacheStore
import com.worksoc.goaicoach.shared.EngineMode
import com.worksoc.goaicoach.ui.GoCoachApp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // ⚠️ **앱 글꼴 배율은 여기서 적용해야 한다**(백로그 #81) — 컴포지션 전체를 감싸야
            // 모든 화면이 함께 바뀐다. `GoCoachApp`에서는 할 수 없다(라인 예산 880/880, 함정 3번).
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
            var engineBootstrap by remember { mutableStateOf<EngineBootstrap?>(null) }

            LaunchedEffect(Unit) {
                engineBootstrap = withContext(Dispatchers.IO) {
                    createEngineBootstrap(
                        context = applicationContext,
                        nativeLibraryDir = applicationInfo.nativeLibraryDir,
                    )
                }
            }

            val bootstrap = engineBootstrap
            if (bootstrap == null) {
                PreparingEngineScreen()
            } else {
                val positionAnalysisCacheStore = remember(applicationContext) {
                    JsonPositionAnalysisCacheStore(applicationContext)
                }
                val diagnosticEventLog: DiagnosticEventLogPort = remember(applicationContext) {
                    DiagnosticEventLog(File(applicationContext.filesDir, DiagnosticEventLog.FileName))
                }
                // 개발용 원격 엔진 스파이크(docs/work/roadmap/LAYERED_ARCHITECTURE_REFACTORING_PLAN_260803_1500.md
                // Stage E-3). BuildConfig.REMOTE_ENGINE_URL은 debug 빌드에서만, local.properties의
                // debug.remoteEngineUrl 키가 있을 때만 비어있지 않다(app-android/build.gradle.kts 참고)
                // — friend/playInternal/release는 항상 빈 문자열로 고정돼 있어 이 분기를 절대 타지
                // 않는다.
                val remoteEngineUrl = BuildConfig.REMOTE_ENGINE_URL
                val remoteEngineRequested = BuildConfig.DEBUG && remoteEngineUrl.isNotBlank()
                var usingRemoteEngine by remember { mutableStateOf(false) }
                val engineClient = remember(bootstrap.coreApi, diagnosticEventLog) {
                    val remoteClient = if (remoteEngineRequested) {
                        createRemoteEngineSessionClient(
                            candidates = listOf(RemoteEngineCandidate(endpointUrl = remoteEngineUrl, enabled = true)),
                            positionAnalysisCacheStore = positionAnalysisCacheStore,
                            diagnosticEventLog = diagnosticEventLog,
                        )
                    } else {
                        null
                    }
                    usingRemoteEngine = remoteClient != null
                    // 원격 후보가 있으면 우선 쓰고, 어떤 이유로든(엔드포인트가 비활성 등) 후보를 못
                    // 고르면 항상 로컬로 폴백한다.
                    remoteClient ?: LocalEngineSessionClient(
                        coreApi = bootstrap.coreApi,
                        capabilitiesProvider = {
                            EngineSessionCapabilities(
                                supportsDeviceBenchmark = bootstrap.mode == EngineMode.LocalProcess,
                                backend = EngineSessionBackend.LocalEngine,
                            )
                        },
                        positionAnalysisCacheStore = positionAnalysisCacheStore,
                        diagnosticEventLog = diagnosticEventLog,
                    )
                }
                GoCoachApp(
                    engineClient = engineClient,
                    engineName = if (usingRemoteEngine) {
                        "${bootstrap.displayName} (remote: $remoteEngineUrl)"
                    } else {
                        bootstrap.displayName
                    },
                    engineDiagnostic = bootstrap.diagnostic,
                    engineMode = bootstrap.mode,
                    diagnosticEventLog = diagnosticEventLog,
                )
            }
            }
        }
    }
}

@Composable
private fun PreparingEngineScreen() {
    MaterialTheme(
        colorScheme = AppLightColorScheme,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    // ⚠️ **`UiStrings`가 아니라 안드로이드 리소스다** — 이 화면은
                    // `ProvideUiLanguage` **바깥**에서 그려져 `LocalUiStrings`에 닿지 않는다.
                    // 그래서 여기만 `res/values/strings.xml`을 쓴다(사유는 그 파일 머리말).
                    // ⚠️ 예전 값은 `"Go AI Coach POC"`였다 — 출시 앱의 **첫 프레임**에 'POC'가
                    // 찍혀 있었다(2026-09-05 발견).
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.preparing_engine),
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    // ⚠️ **여기도 리소스여야 한다** — #97에서 위 두 줄만 바꾸고 이 줄을 놓쳤다.
                    // 이 화면은 `ProvideUiLanguage` 바깥이라 `LocalUiStrings`가 **예외 없이
                    // 한국어 기본값**으로 떨어진다(`staticCompositionLocalOf { UiStringsKorean }`).
                    // 그 결과 영어 기기에서 `Go AI` / `Getting ready…` / **한국어 문장**이 함께 떴다.
                    text = stringResource(R.string.engine_copy_notice),
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
