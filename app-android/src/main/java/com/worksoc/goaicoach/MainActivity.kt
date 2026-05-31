package com.worksoc.goaicoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.worksoc.goaicoach.engine.createEngineBootstrap
import com.worksoc.goaicoach.ui.GoCoachApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val engineBootstrap = createEngineBootstrap(
            filesDir = filesDir,
            nativeLibraryDir = applicationInfo.nativeLibraryDir,
        )
        setContent {
            GoCoachApp(
                engineAdapter = engineBootstrap.adapter,
                engineName = engineBootstrap.displayName,
                engineDiagnostic = engineBootstrap.diagnostic,
            )
        }
    }
}
