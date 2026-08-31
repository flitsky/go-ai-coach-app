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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
                // 개발용 원격 엔진 스파이크(docs/roadmap/LAYERED_ARCHITECTURE_REFACTORING_PLAN_260803_1500.md
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
                        capabilities = EngineSessionCapabilities(
                            supportsDeviceBenchmark = bootstrap.mode == EngineMode.LocalProcess,
                            backend = EngineSessionBackend.LocalEngine,
                        ),
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
                    text = "Go AI Coach POC",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Preparing ...",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    text = LocalUiStrings.current.engineCopyNotice,
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
