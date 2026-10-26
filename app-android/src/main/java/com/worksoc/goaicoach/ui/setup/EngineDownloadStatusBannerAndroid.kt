package com.worksoc.goaicoach.ui.setup

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import com.worksoc.goaicoach.engine.currentEngineDownloadTracker

/**
 * 「모바일 데이터로 계속」이 띄울 Play 공식 승인창(`showConfirmationDialog`)을 이 화면이 떠 있는 동안 등록한다.
 * 그 창은 Activity가 있어야 열리므로 트래커가 아니라 화면이 등록한다 — 배너가 있는 화면마다 부른다.
 */
@Composable
internal actual fun RegisterCellularConfirmationHandler() {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    DisposableEffect(activity) {
        val handler: () -> Unit = {
            if (activity != null) {
                try {
                    val manager = AssetPackManagerFactory.getInstance(activity)
                    val task = manager.showConfirmationDialog(activity)
                    task.addOnSuccessListener { resultCode ->
                        android.util.Log.d("EngineDownloadGuide", "Cellular confirmation result: $resultCode")
                    }.addOnFailureListener { exception ->
                        android.util.Log.w("EngineDownloadGuide", "Cellular confirmation dialog failed", exception)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("EngineDownloadGuide", "Cannot show cellular confirmation dialog", e)
                }
            }
        }
        currentEngineDownloadTracker().registerCellularConfirmationHandler(handler)
        onDispose {
            currentEngineDownloadTracker().unregisterCellularConfirmationHandler(handler)
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
