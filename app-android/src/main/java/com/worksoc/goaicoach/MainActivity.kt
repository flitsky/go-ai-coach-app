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
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worksoc.goaicoach.application.AdapterEngineSessionClient
import com.worksoc.goaicoach.application.EngineSessionCapabilities
import com.worksoc.goaicoach.engine.EngineBootstrap
import com.worksoc.goaicoach.engine.createEngineBootstrap
import com.worksoc.goaicoach.shared.EngineMode
import com.worksoc.goaicoach.ui.GoCoachApp
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
                val engineClient = remember(bootstrap.adapter) {
                    AdapterEngineSessionClient(
                        coreApi = bootstrap.adapter,
                        capabilities = EngineSessionCapabilities(
                            supportsDeviceBenchmark = bootstrap.mode == EngineMode.LocalProcess,
                        ),
                    )
                }
                GoCoachApp(
                    engineClient = engineClient,
                    engineName = bootstrap.displayName,
                    engineDiagnostic = bootstrap.diagnostic,
                )
            }
        }
    }
}

@Composable
private fun PreparingEngineScreen() {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF2F6B4F),
            secondary = Color(0xFF546E7A),
            background = Color(0xFFF7F4EC),
            surface = Color(0xFFFFFCF4),
        ),
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
                    text = "Preparing KataGo engine assets...",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    text = "First launch can take a moment when the bundled model is copied.",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
